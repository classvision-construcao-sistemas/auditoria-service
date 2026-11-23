package br.ifg.urutai.auditoriaservice;

import br.ifg.urutai.auditoriaservice.support.MySqlTeste;
import br.ifg.urutai.auditoriaservice.support.ServidorFalso;
import br.ifg.urutai.auditoriaservice.support.TokensTeste;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Testa a aplicação inteira: segurança, API, banco MySQL e os outros serviços (falsos).
@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTeste.class)
@ActiveProfiles("teste")
@Testcontainers(disabledWithoutDocker = true)
class AuditoriaFluxoCompletoIT {

    private static final ServidorFalso TURMA_SERVICE = new ServidorFalso();
    private static final ServidorFalso RECONHECIMENTO = new ServidorFalso();

    private static final String COORDENADOR = "Bearer " + TokensTeste.coordenador(7L);
    private static final String OUTRO_COORDENADOR = "Bearer " + TokensTeste.coordenador(8L);

    @DynamicPropertySource
    static void servicosExternos(DynamicPropertyRegistry registro) {
        registro.add("servicos.turma.url", TURMA_SERVICE::url);
        registro.add("servicos.reconhecimento.url", RECONHECIMENTO::url);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final MockMultipartFile foto = new MockMultipartFile("foto", "sala.jpg", "image/jpeg", "FOTO".getBytes());

    @BeforeEach
    void turmaPadrao() {
        // Turma 1: alunos 10 e 20 ativos, 30 inativo.
        TURMA_SERVICE.responder("GET", "/turmas/1", 200, """
                {"id": 1, "nome": "ADS", "cursoId": 5, "alunos": [
                  {"alunoId": 10, "situacao": "ATIVO"}, {"alunoId": 20, "situacao": "ATIVO"}, {"alunoId": 30, "situacao": "INATIVO"}]}""");
    }

    @AfterEach
    void limpar() {
        TURMA_SERVICE.limpar();
        RECONHECIMENTO.limpar();
        jdbc.update("DELETE FROM cv10_correcoes_auditoria");
        jdbc.update("DELETE FROM cv08_resultados_auditoria");
        jdbc.update("DELETE FROM cv07_auditorias");
    }

    @AfterAll
    static void pararServidores() {
        TURMA_SERVICE.close();
        RECONHECIMENTO.close();
    }

    private long confirmar(String corpo, String token) throws Exception {
        String resposta = mvc.perform(post("/auditorias").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    // Fluxo completo: prévia, confirmação, histórico, correção e detalhe.
    @Test
    void fluxoCompletoPreviaConfirmacaoHistoricoECorrecao() throws Exception {
        RECONHECIMENTO.responder("POST", "/api/v1/auditar-sala", 200, """
                {"recognized_students": [{"student_id": "10", "confidence_score": 0.92}, {"student_id": "77", "confidence_score": 0.6}],
                 "unrecognized_count": 1, "total_faces_detected": 3}""");

        // 1. Prévia (não grava nada).
        mvc.perform(multipart("/auditorias/previa").file(foto).param("turmaId", "1").header("Authorization", COORDENADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alunos[*].alunoId", contains(10, 20)))
                .andExpect(jsonPath("$.alunos[0].situacaoPresenca").value("PRESENTE"))
                .andExpect(jsonPath("$.alunos[0].confianca").value(0.92))
                .andExpect(jsonPath("$.alunos[1].situacaoPresenca").value("POSSIVEL_FALTA"))
                .andExpect(jsonPath("$.facesForaDaTurma").value(1))
                .andExpect(jsonPath("$.facesNaoIdentificadas").value(1));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM cv07_auditorias", Integer.class));
        assertEquals(COORDENADOR, TURMA_SERVICE.ultima().cabecalhos().getFirst("Authorization"));
        assertEquals(1, RECONHECIMENTO.recebidas().size());

        // 2. Confirmação.
        long id = confirmar("""
                {"turmaId": 1, "data": "2026-11-20", "horario": "08:15:00", "resultados": [
                  {"alunoId": 10, "situacaoPresenca": "PRESENTE", "situacaoFace": "RECONHECIDA"},
                  {"alunoId": 20, "situacaoPresenca": "POSSIVEL_FALTA"}]}""", COORDENADOR);
        assertEquals(7L, jdbc.queryForObject("SELECT bigint_usuario_id FROM cv07_auditorias WHERE id = ?", Long.class, id));
        assertEquals("NAO_RECONHECIDA", jdbc.queryForObject(
                "SELECT situacao_face FROM cv08_resultados_auditoria WHERE bigint_auditoria_id = ? AND aluno_id = 20", String.class, id));

        // 3. Histórico.
        mvc.perform(get("/auditorias").param("turmaId", "1").param("inicio", "2026-11-20").header("Authorization", COORDENADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].situacao").value("CONFIRMADA"));

        // 4. Correção feita por outro coordenador.
        mvc.perform(post("/auditorias/{id}/correcoes", id).header("Authorization", OUTRO_COORDENADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"justificativa": "  Aluno chegou atrasado  ", "resultados": [{"alunoId": 20, "situacaoPresenca": "PRESENTE"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CORRIGIDA"));

        // 5. Detalhe mostra a correção.
        mvc.perform(get("/auditorias/{id}", id).header("Authorization", COORDENADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(7))
                .andExpect(jsonPath("$.resultados[?(@.alunoId == 20)].situacaoPresenca", contains("PRESENTE")))
                .andExpect(jsonPath("$.correcoes", hasSize(1)))
                .andExpect(jsonPath("$.correcoes[0].usuarioId").value(8))
                .andExpect(jsonPath("$.correcoes[0].justificativa").value("Aluno chegou atrasado"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM cv10_correcoes_auditoria WHERE bigint_auditoria_id = ?", Integer.class, id));
    }

    // O histórico por curso busca as turmas do curso no turma-service.
    @Test
    void historicoPorCursoConsultaOTurmaService() throws Exception {
        TURMA_SERVICE.responder("GET", "/turmas/curso/5", 200, "[{\"id\": 1, \"nome\": \"ADS\", \"cursoId\": 5}]");
        TURMA_SERVICE.responder("GET", "/turmas/curso/6", 200, "[]");
        long id = confirmar("""
                {"turmaId": 1, "resultados": [{"alunoId": 10, "situacaoPresenca": "PRESENTE"}]}""", COORDENADOR);

        mvc.perform(get("/auditorias").param("cursoId", "5").header("Authorization", COORDENADOR))
                .andExpect(jsonPath("$[*].id", contains((int) id)));
        mvc.perform(get("/auditorias").param("cursoId", "6").header("Authorization", COORDENADOR))
                .andExpect(content().json("[]"));
    }

    // Aluno inativo não pode entrar na auditoria (409).
    @Test
    void alunoInativoNaoEntraNaAuditoria() throws Exception {
        mvc.perform(post("/auditorias").header("Authorization", COORDENADOR).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"turmaId": 1, "resultados": [{"alunoId": 30, "situacaoPresenca": "PRESENTE"}]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Aluno 30 não faz parte desta auditoria/turma"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM cv07_auditorias", Integer.class));
    }

    // Turma que não existe dá 404 e o reconhecimento nem é chamado.
    @Test
    void turmaInexistenteNoTurmaService() throws Exception {
        mvc.perform(multipart("/auditorias/previa").file(foto).param("turmaId", "404").header("Authorization", COORDENADOR))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Turma não encontrada"));
        assertTrue(RECONHECIMENTO.recebidas().isEmpty());
    }

    // Turma-service com erro dá 502.
    @Test
    void turmaServiceForaDoArDa502() throws Exception {
        TURMA_SERVICE.responder("GET", "/turmas/1", 503, "");

        mvc.perform(post("/auditorias").header("Authorization", COORDENADOR).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"turmaId": 1, "resultados": [{"alunoId": 10, "situacaoPresenca": "PRESENTE"}]}"""))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensagem").value("Serviço de turmas indisponível. Tente novamente."));
    }

    // Foto recusada pelo reconhecimento dá 400.
    @Test
    void fotoRecusadaPeloReconhecimentoDa400() throws Exception {
        RECONHECIMENTO.responder("POST", "/api/v1/auditar-sala", 400, "{\"detail\": \"no face\"}");

        mvc.perform(multipart("/auditorias/previa").file(foto).param("turmaId", "1").header("Authorization", COORDENADOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Imagem inválida para reconhecimento facial"));
    }

    // Reconhecimento com erro dá 502.
    @Test
    void reconhecimentoForaDoArDa502() throws Exception {
        RECONHECIMENTO.responder("POST", "/api/v1/auditar-sala", 500, "");

        mvc.perform(multipart("/auditorias/previa").file(foto).param("turmaId", "1").header("Authorization", COORDENADOR))
                .andExpect(status().isBadGateway());
    }

    // Turma que existe no turma-service mas não no banco dá 409 sem mostrar o SQL.
    @Test
    void violacaoDeFkVira409() throws Exception {
        TURMA_SERVICE.responder("GET", "/turmas/999", 200, """
                {"id": 999, "nome": "Fantasma", "cursoId": 5, "alunos": [{"alunoId": 10, "situacao": "ATIVO"}]}""");

        mvc.perform(post("/auditorias").header("Authorization", COORDENADOR).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"turmaId": 999, "resultados": [{"alunoId": 10, "situacaoPresenca": "PRESENTE"}]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Operação viola uma restrição do banco de dados"));
    }

    // Corrigir auditoria que não existe dá 404.
    @Test
    void corrigirAuditoriaInexistenteDa404() throws Exception {
        mvc.perform(post("/auditorias/123456/correcoes").header("Authorization", COORDENADOR).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"justificativa": "x", "resultados": [{"alunoId": 10, "situacaoPresenca": "PRESENTE"}]}"""))
                .andExpect(status().isNotFound());
    }

    // Token sem id do usuário não consegue gravar (403).
    @Test
    void tokenSemClaimIdNaoGrava() throws Exception {
        String semId = TokensTeste.gerar(TokensTeste.SEGREDO, JWSAlgorithm.HS256, null,
                List.of("ROLE_COORDENADOR"), Instant.now().plusSeconds(60));

        mvc.perform(post("/auditorias").header("Authorization", "Bearer " + semId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"turmaId": 1, "resultados": [{"alunoId": 10, "situacaoPresenca": "PRESENTE"}]}"""))
                .andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM cv07_auditorias", Integer.class));
    }

    // A documentação do Swagger abre sem login.
    @Test
    void documentacaoSwaggerEPublica() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Class Vision - API de Auditoria de Presença"))
                .andExpect(jsonPath("$.paths['/auditorias/previa']").exists());
    }

    // Defeitos: hoje esses testes falham.
    @Nested
    @Tag("defeito-conhecido")
    class PioresCasos {

        // Reconhecimento responde sem a lista de alunos: a API deve responder 502.
        @Test
        void reconhecimentoSemListaDa502() throws Exception {
            RECONHECIMENTO.responder("POST", "/api/v1/auditar-sala", 200, """
                    {"unrecognized_count": 0, "total_faces_detected": 0}""");

            mvc.perform(multipart("/auditorias/previa").file(foto).param("turmaId", "1").header("Authorization", COORDENADOR))
                    .andExpect(status().isBadGateway());
        }

        // Turma-service responde vazio: a API deve responder 502.
        @Test
        void turmaServiceComCorpoVazioDa502() throws Exception {
            TURMA_SERVICE.responder("GET", "/turmas/1", 200, "");

            mvc.perform(multipart("/auditorias/previa").file(foto).param("turmaId", "1").header("Authorization", COORDENADOR))
                    .andExpect(status().isBadGateway());
        }
    }
}
