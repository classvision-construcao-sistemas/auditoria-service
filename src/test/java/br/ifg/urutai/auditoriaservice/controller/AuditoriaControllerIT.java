package br.ifg.urutai.auditoriaservice.controller;

import br.ifg.urutai.auditoriaservice.config.SecurityConfiguration;
import br.ifg.urutai.auditoriaservice.dto.AuditoriaResposta;
import br.ifg.urutai.auditoriaservice.dto.ConfirmacaoRequisicao;
import br.ifg.urutai.auditoriaservice.dto.CorrecaoRequisicao;
import br.ifg.urutai.auditoriaservice.dto.PreviaResposta;
import br.ifg.urutai.auditoriaservice.exception.IntegracaoException;
import br.ifg.urutai.auditoriaservice.exception.RecursoNaoEncontradoException;
import br.ifg.urutai.auditoriaservice.exception.RegraNegocioException;
import br.ifg.urutai.auditoriaservice.service.AuditoriaService;
import br.ifg.urutai.auditoriaservice.support.TokensTeste;
import com.nimbusds.jose.JWSAlgorithm;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Testa as rotas da API, a segurança e as respostas de erro. O service é simulado.
@WebMvcTest(AuditoriaController.class)
@Import(SecurityConfiguration.class)
@TestPropertySource(properties = "jwt.secret=" + TokensTeste.SEGREDO)
class AuditoriaControllerIT {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuditoriaService service;

    private static final String COORDENADOR = "Bearer " + TokensTeste.coordenador(7L);
    private static final String ADMINISTRADOR = "Bearer " + TokensTeste.comPapel(1L, "ROLE_ADMINISTRADOR");
    private static final String ALUNO = "Bearer " + TokensTeste.comPapel(50L, "ROLE_ALUNO");
    private static final String PROFESSOR = "Bearer " + TokensTeste.comPapel(60L, "ROLE_PROFESSOR");

    private static final String CONFIRMACAO = """
            {"turmaId": 1, "data": "2026-11-20", "horario": "08:15:00",
             "resultados": [{"alunoId": 10, "situacaoPresenca": "PRESENTE", "situacaoFace": "RECONHECIDA"}]}""";
    private static final String CORRECAO = """
            {"justificativa": "Chegou atrasado", "resultados": [{"alunoId": 10, "situacaoPresenca": "PRESENTE"}]}""";

    private static final AuditoriaResposta RESPOSTA = new AuditoriaResposta(3L, 1L, 7L, LocalDate.of(2026, 11, 20), LocalTime.of(8, 15),
            "CONFIRMADA", List.of(new AuditoriaResposta.Resultado(10L, "PRESENTE", "RECONHECIDA")),
            List.of(new AuditoriaResposta.Correcao(7L, LocalDateTime.of(2026, 11, 21, 9, 30), "Chegou atrasado")));

    private final MockMultipartFile foto = new MockMultipartFile("foto", "sala.jpg", "image/jpeg", new byte[]{1, 2, 3});

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String corpo, String token) {
        return req.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    @Nested
    class Seguranca {

        // Sem token, a resposta é 401.
        @Test
        void semTokenRecebe401() throws Exception {
            mvc.perform(get("/auditorias")).andExpect(status().isUnauthorized());
            mvc.perform(json(post("/auditorias"), CONFIRMACAO, "")).andExpect(status().isUnauthorized());
            verifyNoInteractions(service);
        }

        // Token assinado com outra chave recebe 401.
        @Test
        void tokenComAssinaturaErradaRecebe401() throws Exception {
            String falso = TokensTeste.gerar("outro-segredo-com-32-bytes-12345", JWSAlgorithm.HS256, 7L,
                    List.of("ROLE_COORDENADOR"), Instant.now().plusSeconds(60));

            mvc.perform(get("/auditorias").header("Authorization", "Bearer " + falso)).andExpect(status().isUnauthorized());
        }

        // Token vencido recebe 401.
        @Test
        void tokenExpiradoRecebe401() throws Exception {
            String expirado = TokensTeste.gerar(TokensTeste.SEGREDO, JWSAlgorithm.HS256, 7L,
                    List.of("ROLE_COORDENADOR"), Instant.now().minusSeconds(60));

            mvc.perform(get("/auditorias").header("Authorization", "Bearer " + expirado)).andExpect(status().isUnauthorized());
        }

        // Aluno e professor não podem fazer nem corrigir auditoria (403).
        @ParameterizedTest
        @ValueSource(strings = {"ALUNO", "PROFESSOR"})
        void outrosPapeisNaoGravam(String papel) throws Exception {
            String token = papel.equals("ALUNO") ? ALUNO : PROFESSOR;

            mvc.perform(multipart("/auditorias/previa").file(foto).param("turmaId", "1").header("Authorization", token))
                    .andExpect(status().isForbidden());
            mvc.perform(json(post("/auditorias"), CONFIRMACAO, token)).andExpect(status().isForbidden());
            mvc.perform(json(post("/auditorias/3/correcoes"), CORRECAO, token)).andExpect(status().isForbidden());
            verifyNoInteractions(service);
        }

        // Coordenador e administrador podem gravar e corrigir.
        @Test
        void coordenadorEAdministradorGravam() throws Exception {
            when(service.confirmar(any())).thenReturn(RESPOSTA);
            when(service.corrigir(eq(3L), any())).thenReturn(RESPOSTA);

            mvc.perform(json(post("/auditorias"), CONFIRMACAO, COORDENADOR)).andExpect(status().isCreated());
            mvc.perform(json(post("/auditorias"), CONFIRMACAO, ADMINISTRADOR)).andExpect(status().isCreated());
            mvc.perform(json(post("/auditorias/3/correcoes"), CORRECAO, ADMINISTRADOR)).andExpect(status().isOk());
        }

        // Qualquer usuário logado pode ver o histórico.
        @Test
        void historicoAbertoAQualquerAutenticado() throws Exception {
            when(service.listar(any(), any(), any(), any(), any())).thenReturn(List.of());
            when(service.buscar(3L)).thenReturn(RESPOSTA);

            mvc.perform(get("/auditorias").header("Authorization", ALUNO)).andExpect(status().isOk());
            mvc.perform(get("/auditorias/3").header("Authorization", PROFESSOR)).andExpect(status().isOk());
        }

        // Rotas que não existem na API são bloqueadas (403).
        @Test
        void rotasNaoMapeadasSaoNegadas() throws Exception {
            mvc.perform(delete("/auditorias/3").header("Authorization", ADMINISTRADOR)).andExpect(status().isForbidden());
            mvc.perform(put("/auditorias/3").header("Authorization", COORDENADOR)).andExpect(status().isForbidden());
            mvc.perform(get("/auditorias/3/correcoes").header("Authorization", COORDENADOR)).andExpect(status().isForbidden());
            mvc.perform(get("/admin").header("Authorization", ADMINISTRADOR)).andExpect(status().isForbidden());
        }

        // A API não cria sessão (sem cookie).
        @Test
        void naoCriaSessao() throws Exception {
            when(service.listar(any(), any(), any(), any(), any())).thenReturn(List.of());

            mvc.perform(get("/auditorias").header("Authorization", COORDENADOR))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
    }

    @Nested
    class Rotas {

        // A prévia devolve a lista de alunos em JSON.
        @Test
        void previaDevolveAListaParaRevisao() throws Exception {
            when(service.previa(eq(1L), any())).thenReturn(new PreviaResposta(1L, LocalDate.of(2026, 11, 20), LocalTime.of(8, 15, 30),
                    List.of(new PreviaResposta.AlunoPrevia(10L, "PRESENTE", "RECONHECIDA", 0.93),
                            new PreviaResposta.AlunoPrevia(20L, "POSSIVEL_FALTA", "NAO_RECONHECIDA", null)), 1, 2, 5));

            mvc.perform(multipart("/auditorias/previa").file(foto).param("turmaId", "1").header("Authorization", COORDENADOR))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.turmaId").value(1))
                    .andExpect(jsonPath("$.data").value("2026-11-20"))
                    .andExpect(jsonPath("$.horario").value("08:15:30"))
                    .andExpect(jsonPath("$.alunos", hasSize(2)))
                    .andExpect(jsonPath("$.alunos[0].confianca").value(0.93))
                    .andExpect(jsonPath("$.alunos[1].situacaoPresenca").value("POSSIVEL_FALTA"))
                    .andExpect(jsonPath("$.facesNaoIdentificadas").value(1))
                    .andExpect(jsonPath("$.facesForaDaTurma").value(2))
                    .andExpect(jsonPath("$.totalFaces").value(5));

            ArgumentCaptor<MultipartFile> enviada = ArgumentCaptor.forClass(MultipartFile.class);
            verify(service).previa(eq(1L), enviada.capture());
            assertEquals("sala.jpg", enviada.getValue().getOriginalFilename());
        }

        // Confirmar lê o JSON enviado e responde 201.
        @Test
        void confirmarConverteOCorpoEDevolve201() throws Exception {
            when(service.confirmar(any())).thenReturn(RESPOSTA);

            mvc.perform(json(post("/auditorias"), CONFIRMACAO, COORDENADOR))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(3))
                    .andExpect(jsonPath("$.horario").value("08:15:00"))
                    .andExpect(jsonPath("$.resultados[0].alunoId").value(10))
                    .andExpect(jsonPath("$.correcoes[0].dtCorrecao").value("2026-11-21T09:30:00"));

            ArgumentCaptor<ConfirmacaoRequisicao> corpo = ArgumentCaptor.forClass(ConfirmacaoRequisicao.class);
            verify(service).confirmar(corpo.capture());
            assertEquals(LocalDate.of(2026, 11, 20), corpo.getValue().data());
            assertEquals(LocalTime.of(8, 15), corpo.getValue().horario());
            assertEquals("RECONHECIDA", corpo.getValue().resultados().getFirst().situacaoFace());
        }

        // O histórico repassa todos os filtros para o service.
        @Test
        void listarRepassaOsFiltros() throws Exception {
            when(service.listar(any(), any(), any(), any(), any())).thenReturn(List.of(RESPOSTA));

            mvc.perform(get("/auditorias").header("Authorization", COORDENADOR)
                            .param("cursoId", "5").param("turmaId", "1").param("usuarioId", "7")
                            .param("inicio", "2026-11-01").param("fim", "2026-11-30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].situacao").value("CONFIRMADA"));

            verify(service).listar(5L, 1L, 7L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        }

        // O histórico sem filtros funciona.
        @Test
        void listarSemFiltros() throws Exception {
            when(service.listar(any(), any(), any(), any(), any())).thenReturn(List.of());

            mvc.perform(get("/auditorias").header("Authorization", COORDENADOR))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));

            verify(service).listar(null, null, null, null, null);
        }

        // Data fora do formato aaaa-mm-dd dá erro 400.
        @Test
        void listarComDataEmOutroFormatoDa400() throws Exception {
            mvc.perform(get("/auditorias").param("inicio", "20/11/2026").header("Authorization", COORDENADOR))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(service);
        }

        // Busca uma auditoria pelo id.
        @Test
        void buscarPorId() throws Exception {
            when(service.buscar(3L)).thenReturn(RESPOSTA);

            mvc.perform(get("/auditorias/3").header("Authorization", COORDENADOR))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correcoes[0].justificativa").value("Chegou atrasado"));
        }

        // Corrigir envia o id e o corpo para o service.
        @Test
        void corrigirRepassaIdECorpo() throws Exception {
            when(service.corrigir(eq(3L), any())).thenReturn(RESPOSTA);

            mvc.perform(json(post("/auditorias/3/correcoes"), CORRECAO, COORDENADOR)).andExpect(status().isOk());

            ArgumentCaptor<CorrecaoRequisicao> corpo = ArgumentCaptor.forClass(CorrecaoRequisicao.class);
            verify(service).corrigir(eq(3L), corpo.capture());
            assertEquals("Chegou atrasado", corpo.getValue().justificativa());
        }
    }

    @Nested
    class Erros {

        // Dados inválidos dão erro 400 com a lista de campos errados.
        @Test
        void corpoInvalidoDevolveCamposComErro() throws Exception {
            mvc.perform(json(post("/auditorias"), """
                            {"turmaId": -1, "resultados": [{"alunoId": 10, "situacaoPresenca": "AUSENTE"}]}""", COORDENADOR))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.mensagem").value("Dados inválidos"))
                    .andExpect(jsonPath("$.caminho").value("/auditorias"))
                    .andExpect(jsonPath("$.dataHora").exists())
                    .andExpect(jsonPath("$.campos.turmaId").exists())
                    .andExpect(jsonPath("$.campos['resultados[0].situacaoPresenca']").value("use PRESENTE ou POSSIVEL_FALTA"));
            verifyNoInteractions(service);
        }

        // Correção sem justificativa dá erro 400.
        @Test
        void correcaoSemJustificativa() throws Exception {
            mvc.perform(json(post("/auditorias/3/correcoes"), """
                            {"justificativa": " ", "resultados": [{"alunoId": 10, "situacaoPresenca": "PRESENTE"}]}""", COORDENADOR))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.justificativa").exists());
            verifyNoInteractions(service);
        }

        // JSON quebrado dá erro 400.
        @Test
        void jsonMalformado() throws Exception {
            mvc.perform(json(post("/auditorias"), "{\"turmaId\": ", COORDENADOR))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.startsWith("Requisição inválida")));
        }

        // Data em formato errado no corpo dá erro 400.
        @Test
        void dataInvalidaNoCorpo() throws Exception {
            mvc.perform(json(post("/auditorias"), """
                            {"turmaId": 1, "data": "20/11/2026", "resultados": [{"alunoId": 10, "situacaoPresenca": "PRESENTE"}]}""", COORDENADOR))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(service);
        }

        // Prévia sem foto dá erro 400.
        @Test
        void previaSemFoto() throws Exception {
            mvc.perform(multipart("/auditorias/previa").param("turmaId", "1").header("Authorization", COORDENADOR))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value(containsString("foto")));
        }

        // Prévia sem turma dá erro 400.
        @Test
        void previaSemTurma() throws Exception {
            mvc.perform(multipart("/auditorias/previa").file(foto).header("Authorization", COORDENADOR))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value(containsString("turmaId")));
        }

        // Recurso não encontrado vira 404.
        @Test
        void recursoNaoEncontradoVira404() throws Exception {
            when(service.buscar(9L)).thenThrow(new RecursoNaoEncontradoException("Auditoria não encontrada"));

            mvc.perform(get("/auditorias/9").header("Authorization", COORDENADOR))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.mensagem").value("Auditoria não encontrada"))
                    .andExpect(jsonPath("$.caminho").value("/auditorias/9"))
                    .andExpect(jsonPath("$.campos").isEmpty());
        }

        // Regra de negócio quebrada vira 409.
        @Test
        void regraDeNegocioVira409() throws Exception {
            when(service.confirmar(any())).thenThrow(new RegraNegocioException("Aluno 10 repetido na lista"));

            mvc.perform(json(post("/auditorias"), CONFIRMACAO, COORDENADOR))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.mensagem").value("Aluno 10 repetido na lista"));
        }

        // Erro de outro serviço usa o status que veio na exceção.
        @ParameterizedTest
        @ValueSource(ints = {400, 403, 502})
        void integracaoUsaOStatusDaExcecao(int status) throws Exception {
            when(service.previa(any(), any())).thenThrow(new IntegracaoException(HttpStatus.valueOf(status), "falhou"));

            mvc.perform(multipart("/auditorias/previa").file(foto).param("turmaId", "1").header("Authorization", COORDENADOR))
                    .andExpect(status().is(status))
                    .andExpect(jsonPath("$.status").value(status))
                    .andExpect(jsonPath("$.mensagem").value("falhou"));
        }

        // Erro do banco vira 409 sem mostrar detalhes do SQL.
        @Test
        void violacaoDoBancoVira409SemExporDetalhes() throws Exception {
            when(service.confirmar(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry '3-10' for key uk_cv08"));

            mvc.perform(json(post("/auditorias"), CONFIRMACAO, COORDENADOR))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.mensagem").value("Operação viola uma restrição do banco de dados"))
                    .andExpect(content().string(not(containsString("Duplicate"))));
        }

        // Foto acima do limite vira 413.
        @Test
        void fotoGrandeDemaisVira413() throws Exception {
            when(service.previa(any(), any())).thenThrow(new MaxUploadSizeExceededException(15L * 1024 * 1024));

            mvc.perform(multipart("/auditorias/previa").file(foto).param("turmaId", "1").header("Authorization", COORDENADOR))
                    .andExpect(status().is(413))
                    .andExpect(jsonPath("$.mensagem").value("A foto passa do limite de 15 MB"));
        }

        // Defeito: data inicial depois da final deveria dar erro 400. Hoje esse teste falha.
        @Test
        @Tag("defeito-conhecido")
        void periodoInvertidoDa400() throws Exception {
            when(service.listar(any(), any(), any(), any(), any())).thenReturn(List.of());

            mvc.perform(get("/auditorias").param("inicio", "2026-11-30").param("fim", "2026-11-01").header("Authorization", COORDENADOR))
                    .andExpect(status().isBadRequest());
        }
    }
}
