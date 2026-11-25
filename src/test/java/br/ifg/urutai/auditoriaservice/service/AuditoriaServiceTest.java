package br.ifg.urutai.auditoriaservice.service;

import br.ifg.urutai.auditoriaservice.client.ReconhecimentoFacialClient;
import br.ifg.urutai.auditoriaservice.client.ReconhecimentoFacialClient.Reconhecido;
import br.ifg.urutai.auditoriaservice.client.TurmaClient;
import br.ifg.urutai.auditoriaservice.client.TurmaClient.Vinculo;
import br.ifg.urutai.auditoriaservice.dto.AuditoriaResposta;
import br.ifg.urutai.auditoriaservice.dto.ConfirmacaoRequisicao;
import br.ifg.urutai.auditoriaservice.dto.CorrecaoRequisicao;
import br.ifg.urutai.auditoriaservice.dto.PreviaResposta;
import br.ifg.urutai.auditoriaservice.dto.ResultadoRequisicao;
import br.ifg.urutai.auditoriaservice.exception.IntegracaoException;
import br.ifg.urutai.auditoriaservice.exception.RecursoNaoEncontradoException;
import br.ifg.urutai.auditoriaservice.exception.RegraNegocioException;
import br.ifg.urutai.auditoriaservice.model.Auditoria;
import br.ifg.urutai.auditoriaservice.model.ResultadoAuditoria;
import br.ifg.urutai.auditoriaservice.repository.AuditoriaRepository;
import br.ifg.urutai.auditoriaservice.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuditoriaServiceTest {

    private final AuditoriaRepository repositorio = mock(AuditoriaRepository.class);
    private final TurmaClient turmas = mock(TurmaClient.class);
    private final ReconhecimentoFacialClient reconhecimento = mock(ReconhecimentoFacialClient.class);
    private final UsuarioAutenticado usuario = mock(UsuarioAutenticado.class);
    private final AuditoriaService service = new AuditoriaService(repositorio, turmas, reconhecimento, usuario);

    private final MockMultipartFile foto = new MockMultipartFile("foto", "sala.jpg", "image/jpeg", new byte[]{1, 2, 3});

    @BeforeEach
    void preparar() {
        when(usuario.token()).thenReturn("token");
        when(usuario.id()).thenReturn(7L);
        // Turma 1: alunos 10 e 20 ativos, 30 inativo.
        when(turmas.buscar(eq(1L), any())).thenReturn(new TurmaClient.Turma(1L, "ADS", 5L, List.of(
                new Vinculo(10L, "ATIVO"), new Vinculo(20L, "ativo"), new Vinculo(30L, "INATIVO"))));
        when(repositorio.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repositorio.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Auditoria auditoriaConfirmada(Long id, Long... alunos) {
        Auditoria auditoria = new Auditoria();
        auditoria.setId(id);
        auditoria.setTurmaId(1L);
        auditoria.setUsuarioId(7L);
        auditoria.setData(LocalDate.of(2026, 11, 20));
        auditoria.setHorario(LocalTime.of(8, 0));
        auditoria.setSituacao(Auditoria.CONFIRMADA);
        for (Long aluno : alunos) {
            auditoria.adicionarResultado(new ResultadoAuditoria(aluno, ResultadoAuditoria.POSSIVEL_FALTA, null));
        }
        return auditoria;
    }

    @Nested
    class Previa {

        // Devolve a caixa de cada rosto com a situação, para o app marcar a foto.
        @Test
        void devolveOsRostosComASituacao() {
            var area = new ReconhecimentoFacialClient.Area(0.1, 0.2, 0.05, 0.07);
            when(reconhecimento.auditarSala(foto, "token")).thenReturn(new ReconhecimentoFacialClient.Resultado(
                    List.of(new Reconhecido("10", 0.91, area), new Reconhecido("99", 0.7, area), new Reconhecido("20", 0.8)),
                    2, 5,
                    List.of(new ReconhecimentoFacialClient.Desconhecido(area),
                            new ReconhecimentoFacialClient.Desconhecido(new ReconhecimentoFacialClient.Area(0, 0, 0, 0)))));

            PreviaResposta previa = service.previa(1L, foto);

            assertEquals(List.of(
                    new PreviaResposta.Rosto(0.1, 0.2, 0.05, 0.07, 10L, PreviaResposta.RECONHECIDO),
                    new PreviaResposta.Rosto(0.1, 0.2, 0.05, 0.07, null, PreviaResposta.FORA_DA_TURMA),
                    new PreviaResposta.Rosto(0.1, 0.2, 0.05, 0.07, null, PreviaResposta.NAO_IDENTIFICADO)),
                    previa.rostos());
            assertEquals(2, previa.alunos().stream().filter(a -> ResultadoAuditoria.PRESENTE.equals(a.situacaoPresenca())).count());
        }

        // Separa presentes, possíveis faltas e rostos de fora da turma, sem gravar nada.
        @Test
        void marcaPresentesPossiveisFaltasEFacesDeFora() {
            when(reconhecimento.auditarSala(foto, "token")).thenReturn(new ReconhecimentoFacialClient.Resultado(List.of(
                    new Reconhecido("10", 0.91), new Reconhecido("30", 0.8), new Reconhecido("99", 0.7), new Reconhecido("abc", 0.6)), 2, 6));

            PreviaResposta previa = service.previa(1L, foto);

            assertEquals(1L, previa.turmaId());
            assertEquals(List.of(10L, 20L), previa.alunos().stream().map(PreviaResposta.AlunoPrevia::alunoId).toList());
            assertEquals(ResultadoAuditoria.PRESENTE, previa.alunos().get(0).situacaoPresenca());
            assertEquals(ResultadoAuditoria.RECONHECIDA, previa.alunos().get(0).situacaoFace());
            assertEquals(0.91, previa.alunos().get(0).confianca());
            assertEquals(ResultadoAuditoria.POSSIVEL_FALTA, previa.alunos().get(1).situacaoPresenca());
            assertEquals(ResultadoAuditoria.NAO_RECONHECIDA, previa.alunos().get(1).situacaoFace());
            assertNull(previa.alunos().get(1).confianca());
            assertEquals(2, previa.facesNaoIdentificadas());
            assertEquals(3, previa.facesForaDaTurma());
            assertEquals(6, previa.totalFaces());
            assertNotNull(previa.data());
            assertEquals(0, previa.horario().getNano());
            verify(repositorio, never()).save(any());
        }

        // Aluno reconhecido duas vezes fica com a primeira confiança.
        @Test
        void alunoReconhecidoDuasVezesFicaComAPrimeiraConfianca() {
            when(reconhecimento.auditarSala(foto, "token")).thenReturn(new ReconhecimentoFacialClient.Resultado(List.of(
                    new Reconhecido(" 20 ", 0.95), new Reconhecido("20", 0.5)), 0, 2));

            PreviaResposta previa = service.previa(1L, foto);

            assertEquals(0.95, previa.alunos().get(1).confianca());
            assertEquals(0, previa.facesForaDaTurma());
        }

        // Se ninguém foi reconhecido, todos ficam como possível falta.
        @Test
        void ninguemReconhecidoViraTudoPossivelFalta() {
            when(reconhecimento.auditarSala(foto, "token")).thenReturn(new ReconhecimentoFacialClient.Resultado(List.of(), 4, 4));

            PreviaResposta previa = service.previa(1L, foto);

            assertTrue(previa.alunos().stream().allMatch(a -> a.situacaoPresenca().equals(ResultadoAuditoria.POSSIVEL_FALTA)));
            assertEquals(4, previa.facesNaoIdentificadas());
        }

        // Sem foto, dá erro e não chama nenhum serviço.
        @Test
        void semFotoFalha() {
            assertThrows(RegraNegocioException.class, () -> service.previa(1L, new MockMultipartFile("foto", new byte[0])));
            assertThrows(RegraNegocioException.class, () -> service.previa(1L, null));
            verifyNoInteractions(reconhecimento, turmas);
        }

        // Turma sem alunos ativos dá erro e não chama o reconhecimento.
        @Test
        void turmaSemAlunosAtivosFalhaSemChamarReconhecimento() {
            when(turmas.buscar(eq(2L), any())).thenReturn(new TurmaClient.Turma(2L, "Vazia", 5L, List.of(new Vinculo(30L, "INATIVO"))));
            when(turmas.buscar(eq(3L), any())).thenReturn(new TurmaClient.Turma(3L, "Sem lista", 5L, null));

            RegraNegocioException erro = assertThrows(RegraNegocioException.class, () -> service.previa(2L, foto));
            assertEquals("A turma não tem alunos ativos", erro.getMessage());
            assertThrows(RegraNegocioException.class, () -> service.previa(3L, foto));
            verifyNoInteractions(reconhecimento);
        }

        // Envia o token do usuário para o turma-service.
        @Test
        void repassaOTokenDoUsuarioParaOTurmaService() {
            when(reconhecimento.auditarSala(foto, "token")).thenReturn(new ReconhecimentoFacialClient.Resultado(List.of(), 0, 0));

            service.previa(1L, foto);

            verify(turmas).buscar(1L, "token");
        }

        // Erro do reconhecimento chega igual para quem chamou.
        @Test
        void falhaDeIntegracaoSobeSemAlteracao() {
            IntegracaoException falha = new IntegracaoException(HttpStatus.BAD_GATEWAY, "fora do ar");
            when(reconhecimento.auditarSala(foto, "token")).thenThrow(falha);

            assertSame(falha, assertThrows(IntegracaoException.class, () -> service.previa(1L, foto)));
        }
    }

    @Nested
    class Confirmar {

        // Grava a auditoria com o usuário do token.
        @Test
        void gravaResultadosComUsuarioDoToken() {
            AuditoriaResposta resposta = service.confirmar(new ConfirmacaoRequisicao(1L, null, null, List.of(
                    new ResultadoRequisicao(10L, "PRESENTE", "RECONHECIDA"), new ResultadoRequisicao(20L, "POSSIVEL_FALTA", null))));

            assertEquals(7L, resposta.usuarioId());
            assertEquals(1L, resposta.turmaId());
            assertEquals(Auditoria.CONFIRMADA, resposta.situacao());
            assertNotNull(resposta.data());
            assertNotNull(resposta.horario());
            assertEquals(2, resposta.resultados().size());
            assertEquals(ResultadoAuditoria.RECONHECIDA, resposta.resultados().get(0).situacaoFace());
            assertEquals(ResultadoAuditoria.NAO_RECONHECIDA, resposta.resultados().get(1).situacaoFace());
            assertTrue(resposta.correcoes().isEmpty());
        }

        // Usa a data e o horário enviados.
        @Test
        void usaDataEHorarioInformados() {
            LocalDate data = LocalDate.of(2026, 3, 10);
            LocalTime horario = LocalTime.of(19, 30);

            AuditoriaResposta resposta = service.confirmar(new ConfirmacaoRequisicao(1L, data, horario,
                    List.of(new ResultadoRequisicao(10L, "PRESENTE", null))));

            assertEquals(data, resposta.data());
            assertEquals(horario, resposta.horario());
        }

        // Cada resultado gravado aponta para a sua auditoria.
        @Test
        void resultadosApontamParaAAuditoria() {
            service.confirmar(new ConfirmacaoRequisicao(1L, null, null, List.of(new ResultadoRequisicao(10L, "PRESENTE", null))));

            ArgumentCaptor<Auditoria> gravada = ArgumentCaptor.forClass(Auditoria.class);
            verify(repositorio).save(gravada.capture());
            assertSame(gravada.getValue(), gravada.getValue().getResultados().get(0).getAuditoria());
        }

        // Recusa aluno inativo ou repetido e não grava nada.
        @Test
        void recusaAlunoInativoOuRepetido() {
            assertThrows(RegraNegocioException.class, () -> service.confirmar(new ConfirmacaoRequisicao(1L, null, null,
                    List.of(new ResultadoRequisicao(30L, "PRESENTE", null)))));
            RegraNegocioException repetido = assertThrows(RegraNegocioException.class, () -> service.confirmar(new ConfirmacaoRequisicao(1L, null, null,
                    List.of(new ResultadoRequisicao(10L, "PRESENTE", null), new ResultadoRequisicao(10L, "POSSIVEL_FALTA", null)))));
            assertEquals("Aluno 10 repetido na lista", repetido.getMessage());
            verify(repositorio, never()).save(any());
        }

        // Recusa aluno que não é da turma.
        @Test
        void recusaAlunoDeOutraTurma() {
            RegraNegocioException erro = assertThrows(RegraNegocioException.class, () -> service.confirmar(new ConfirmacaoRequisicao(1L, null, null,
                    List.of(new ResultadoRequisicao(99L, "PRESENTE", null)))));
            assertEquals("Aluno 99 não faz parte desta auditoria/turma", erro.getMessage());
        }

        // Turma inexistente dá erro de não encontrado e não grava nada.
        @Test
        void turmaInexistentePropagaNaoEncontrado() {
            when(turmas.buscar(eq(404L), any())).thenThrow(new RecursoNaoEncontradoException("Turma não encontrada"));

            assertThrows(RecursoNaoEncontradoException.class, () -> service.confirmar(new ConfirmacaoRequisicao(404L, null, null,
                    List.of(new ResultadoRequisicao(10L, "PRESENTE", null)))));
            verify(repositorio, never()).save(any());
        }
    }

    @Nested
    class Corrigir {

        // Muda a presença do aluno e guarda a justificativa e quem corrigiu.
        @Test
        void atualizaPresencaERegistraJustificativa() {
            when(repositorio.findById(3L)).thenReturn(Optional.of(auditoriaConfirmada(3L, 10L, 20L)));

            AuditoriaResposta resposta = service.corrigir(3L, new CorrecaoRequisicao("  Aluno chegou atrasado ",
                    List.of(new ResultadoRequisicao(10L, "PRESENTE", null))));

            assertEquals("PRESENTE", resposta.resultados().get(0).situacaoPresenca());
            assertEquals("POSSIVEL_FALTA", resposta.resultados().get(1).situacaoPresenca());
            assertEquals(Auditoria.CORRIGIDA, resposta.situacao());
            assertEquals("Aluno chegou atrasado", resposta.correcoes().get(0).justificativa());
            assertEquals(7L, resposta.correcoes().get(0).usuarioId());
            assertEquals(0, resposta.correcoes().get(0).dtCorrecao().getNano());
            verify(repositorio).saveAndFlush(any());
        }

        // Várias correções ficam todas guardadas.
        @Test
        void correcoesSeAcumulam() {
            when(repositorio.findById(3L)).thenReturn(Optional.of(auditoriaConfirmada(3L, 10L)));

            service.corrigir(3L, new CorrecaoRequisicao("primeira", List.of(new ResultadoRequisicao(10L, "PRESENTE", null))));
            AuditoriaResposta resposta = service.corrigir(3L, new CorrecaoRequisicao("segunda",
                    List.of(new ResultadoRequisicao(10L, "POSSIVEL_FALTA", null))));

            assertEquals(List.of("primeira", "segunda"), resposta.correcoes().stream().map(AuditoriaResposta.Correcao::justificativa).toList());
            assertEquals("POSSIVEL_FALTA", resposta.resultados().get(0).situacaoPresenca());
        }

        // Recusa aluno que não está na auditoria ou repetido, sem mudar nada.
        @Test
        void recusaAlunoForaDaAuditoriaOuRepetido() {
            Auditoria auditoria = auditoriaConfirmada(3L, 10L);
            when(repositorio.findById(3L)).thenReturn(Optional.of(auditoria));

            assertThrows(RegraNegocioException.class, () -> service.corrigir(3L, new CorrecaoRequisicao("x",
                    List.of(new ResultadoRequisicao(20L, "PRESENTE", null)))));
            assertThrows(RegraNegocioException.class, () -> service.corrigir(3L, new CorrecaoRequisicao("x",
                    List.of(new ResultadoRequisicao(10L, "PRESENTE", null), new ResultadoRequisicao(10L, "PRESENTE", null)))));
            assertEquals(Auditoria.CONFIRMADA, auditoria.getSituacao());
            assertTrue(auditoria.getCorrecoes().isEmpty());
            verify(repositorio, never()).saveAndFlush(any());
        }

        // Corrigir auditoria que não existe dá erro de não encontrado.
        @Test
        void auditoriaInexistente() {
            when(repositorio.findById(9L)).thenReturn(Optional.empty());

            assertThrows(RecursoNaoEncontradoException.class, () -> service.corrigir(9L, new CorrecaoRequisicao("x",
                    List.of(new ResultadoRequisicao(10L, "PRESENTE", null)))));
        }
    }

    @Nested
    class Consultar {

        // Busca uma auditoria pelo id.
        @Test
        void buscarDevolveAuditoria() {
            when(repositorio.findById(3L)).thenReturn(Optional.of(auditoriaConfirmada(3L, 10L)));

            AuditoriaResposta resposta = service.buscar(3L);

            assertEquals(3L, resposta.id());
            assertEquals(List.of(10L), resposta.resultados().stream().map(AuditoriaResposta.Resultado::alunoId).toList());
        }

        // Buscar id que não existe dá erro de não encontrado.
        @Test
        void buscarInexistente() {
            when(repositorio.findById(9L)).thenReturn(Optional.empty());

            RecursoNaoEncontradoException erro = assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(9L));
            assertEquals("Auditoria não encontrada", erro.getMessage());
        }

        // O histórico vem da mais recente para a mais antiga.
        @Test
        @SuppressWarnings("unchecked")
        void listarOrdenaPorDataEHorarioDecrescentes() {
            when(repositorio.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(auditoriaConfirmada(1L)));

            List<AuditoriaResposta> lista = service.listar(null, 1L, null, null, null);

            assertEquals(1, lista.size());
            ArgumentCaptor<Sort> ordem = ArgumentCaptor.forClass(Sort.class);
            verify(repositorio).findAll(any(Specification.class), ordem.capture());
            assertEquals(Sort.by(Sort.Direction.DESC, "data", "horario"), ordem.getValue());
            verifyNoInteractions(turmas);
        }

        // Filtrar por curso busca as turmas do curso no turma-service.
        @Test
        @SuppressWarnings("unchecked")
        void listarPorCursoConsultaAsTurmasDoCurso() {
            when(turmas.listarPorCurso(5L, "token")).thenReturn(List.of(new TurmaClient.Turma(1L, "ADS", 5L, null)));
            when(repositorio.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

            service.listar(5L, null, null, null, null);

            verify(turmas).listarPorCurso(5L, "token");
            verify(repositorio).findAll(any(Specification.class), any(Sort.class));
        }

        // Curso sem turmas devolve lista vazia sem consultar o banco.
        @Test
        @SuppressWarnings("unchecked")
        void listarCursoSemTurmasNemConsultaOBanco() {
            when(turmas.listarPorCurso(eq(6L), anyString())).thenReturn(List.of());

            assertTrue(service.listar(6L, null, null, null, null).isEmpty());
            verify(repositorio, never()).findAll(any(Specification.class), any(Sort.class));
        }

        // Se vierem turma e curso, vale a turma.
        @Test
        @SuppressWarnings("unchecked")
        void listarTurmaTemPrioridadeSobreCurso() {
            when(repositorio.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

            service.listar(5L, 1L, null, null, null);

            verify(turmas, never()).listarPorCurso(any(), any());
        }
    }

    // Converte a matrícula em id do aluno, ou null se não for número.
    @ParameterizedTest
    @CsvSource(value = {"10, 10", "' 42 ', 42", "abc, NULL", "'', NULL", "12.5, NULL"}, nullValues = "NULL")
    void matriculaParaAlunoId(String matricula, Long esperado) {
        assertEquals(esperado, AuditoriaService.matriculaParaAlunoId(matricula));
    }

    // Matrícula nula vira null.
    @Test
    void matriculaNulaViraNull() {
        assertNull(AuditoriaService.matriculaParaAlunoId(null));
    }

    // O fuso usado é o de São Paulo.
    @Test
    void fusoEDeSaoPaulo() {
        assertEquals("America/Sao_Paulo", AuditoriaService.FUSO.getId());
    }
}
