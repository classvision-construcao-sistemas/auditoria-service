package br.ifg.urutai.auditoriaservice.service;

import br.ifg.urutai.auditoriaservice.client.ReconhecimentoFacialClient;
import br.ifg.urutai.auditoriaservice.client.ReconhecimentoFacialClient.Reconhecido;
import br.ifg.urutai.auditoriaservice.client.TurmaClient;
import br.ifg.urutai.auditoriaservice.client.TurmaClient.Vinculo;
import br.ifg.urutai.auditoriaservice.dto.ConfirmacaoRequisicao;
import br.ifg.urutai.auditoriaservice.dto.PreviaResposta;
import br.ifg.urutai.auditoriaservice.dto.ResultadoRequisicao;
import br.ifg.urutai.auditoriaservice.exception.IntegracaoException;
import br.ifg.urutai.auditoriaservice.exception.RegraNegocioException;
import br.ifg.urutai.auditoriaservice.model.ResultadoAuditoria;
import br.ifg.urutai.auditoriaservice.repository.AuditoriaRepository;
import br.ifg.urutai.auditoriaservice.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Piores casos do service: dados estranhos dos outros serviços e volume alto.
class AuditoriaServicePioresCasosTest {

    private final AuditoriaRepository repositorio = mock(AuditoriaRepository.class);
    private final TurmaClient turmas = mock(TurmaClient.class);
    private final ReconhecimentoFacialClient reconhecimento = mock(ReconhecimentoFacialClient.class);
    private final UsuarioAutenticado usuario = mock(UsuarioAutenticado.class);
    private final AuditoriaService service = new AuditoriaService(repositorio, turmas, reconhecimento, usuario);

    private final MockMultipartFile foto = new MockMultipartFile("foto", "sala.jpg", "image/jpeg", new byte[]{1});

    @BeforeEach
    void preparar() {
        when(usuario.token()).thenReturn("t");
        when(usuario.id()).thenReturn(7L);
        when(turmas.buscar(eq(1L), any())).thenReturn(new TurmaClient.Turma(1L, "ADS", 5L,
                List.of(new Vinculo(10L, "ATIVO"), new Vinculo(20L, "ATIVO"))));
        when(repositorio.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // Defeitos: hoje esses testes falham.
    @Nested
    @Tag("defeito-conhecido")
    class RespostasIncompletasDosOutrosServicos {

        // Reconhecimento responde sem a lista de alunos: deve dar erro 502.
        @Test
        void reconhecimentoSemListaDeAlunos() {
            when(reconhecimento.auditarSala(foto, "t")).thenReturn(new ReconhecimentoFacialClient.Resultado(null, 0, 0));

            IntegracaoException erro = assertThrows(IntegracaoException.class, () -> service.previa(1L, foto));
            assertEquals(HttpStatus.BAD_GATEWAY, erro.getStatus());
        }

        // Reconhecimento responde vazio: deve dar erro 502.
        @Test
        void reconhecimentoComCorpoVazio() {
            when(reconhecimento.auditarSala(foto, "t")).thenReturn(null);

            IntegracaoException erro = assertThrows(IntegracaoException.class, () -> service.previa(1L, foto));
            assertEquals(HttpStatus.BAD_GATEWAY, erro.getStatus());
        }

        // Turma-service responde vazio na prévia: deve dar erro 502.
        @Test
        void turmaServiceComCorpoVazioNaPrevia() {
            when(turmas.buscar(eq(2L), any())).thenReturn(null);

            IntegracaoException erro = assertThrows(IntegracaoException.class, () -> service.previa(2L, foto));
            assertEquals(HttpStatus.BAD_GATEWAY, erro.getStatus());
        }

        // Turma-service responde vazio na confirmação: deve dar erro 502.
        @Test
        void turmaServiceComCorpoVazioNaConfirmacao() {
            when(turmas.buscar(eq(2L), any())).thenReturn(null);

            IntegracaoException erro = assertThrows(IntegracaoException.class, () -> service.confirmar(
                    new ConfirmacaoRequisicao(2L, null, null, List.of(new ResultadoRequisicao(10L, "PRESENTE", null)))));
            assertEquals(HttpStatus.BAD_GATEWAY, erro.getStatus());
        }

        // Turma-service responde vazio na lista de turmas do curso: deve dar erro 502.
        @Test
        void turmaServiceComCorpoVazioNoHistoricoPorCurso() {
            when(turmas.listarPorCurso(eq(5L), any())).thenReturn(null);

            IntegracaoException erro = assertThrows(IntegracaoException.class, () -> service.listar(5L, null, null, null, null));
            assertEquals(HttpStatus.BAD_GATEWAY, erro.getStatus());
        }

        // Aluno sem id na turma deve ser ignorado, sem quebrar a prévia.
        @Test
        void vinculoSemAlunoIdNaoDerrubaAPrevia() {
            when(turmas.buscar(eq(3L), any())).thenReturn(new TurmaClient.Turma(3L, "X", 5L,
                    List.of(new Vinculo(10L, "ATIVO"), new Vinculo(null, "ATIVO"))));
            when(reconhecimento.auditarSala(foto, "t")).thenReturn(new ReconhecimentoFacialClient.Resultado(List.of(), 0, 0));

            PreviaResposta previa = assertDoesNotThrow(() -> service.previa(3L, foto));
            assertEquals(List.of(10L), previa.alunos().stream().map(PreviaResposta.AlunoPrevia::alunoId).toList());
        }
    }

    @Nested
    class DadosEstranhosJaTratados {

        // Rosto sem matrícula conta como rosto de fora da turma.
        @Test
        void reconhecidoSemMatriculaContaComoForaDaTurma() {
            when(reconhecimento.auditarSala(foto, "t")).thenReturn(new ReconhecimentoFacialClient.Resultado(
                    List.of(new Reconhecido(null, 0.9), new Reconhecido("10", 0.8)), 0, 2));

            PreviaResposta previa = service.previa(1L, foto);

            assertEquals(1, previa.facesForaDaTurma());
            assertEquals(ResultadoAuditoria.PRESENTE, previa.alunos().getFirst().situacaoPresenca());
        }

        // Aluno reconhecido sem nota de confiança continua presente.
        @Test
        void reconhecidoSemConfiancaAindaContaComoPresente() {
            when(reconhecimento.auditarSala(foto, "t")).thenReturn(new ReconhecimentoFacialClient.Resultado(
                    List.of(new Reconhecido("10", null)), 0, 1));

            PreviaResposta.AlunoPrevia aluno = service.previa(1L, foto).alunos().getFirst();

            assertEquals(ResultadoAuditoria.PRESENTE, aluno.situacaoPresenca());
            assertNull(aluno.confianca());
        }

        // Matrícula enorme ou negativa não quebra a prévia.
        @Test
        void matriculaGiganteNaoDerruba() {
            when(reconhecimento.auditarSala(foto, "t")).thenReturn(new ReconhecimentoFacialClient.Resultado(
                    List.of(new Reconhecido("99999999999999999999999", 0.9), new Reconhecido("-10", 0.9)), 0, 2));

            assertEquals(2, service.previa(1L, foto).facesForaDaTurma());
        }

        // Aluno sem situação não é tratado como ativo.
        @Test
        void turmaSoComSituacaoNula() {
            when(turmas.buscar(eq(4L), any())).thenReturn(new TurmaClient.Turma(4L, "X", 5L, List.of(new Vinculo(10L, null))));

            assertThrows(RegraNegocioException.class, () -> service.previa(4L, foto));
        }
    }

    @Nested
    class Volume {

        private static final int ALUNOS = 5_000;

        @BeforeEach
        void turmaGrande() {
            List<Vinculo> vinculos = LongStream.rangeClosed(1, ALUNOS).mapToObj(id -> new Vinculo(id, "ATIVO")).toList();
            when(turmas.buscar(eq(9L), any())).thenReturn(new TurmaClient.Turma(9L, "Enorme", 5L, vinculos));
        }

        // Prévia com 5.000 alunos e 2.000 rostos de fora termina rápido e com a conta certa.
        @Test
        void previaComTurmaGrandeEMuitosRostos() {
            List<Reconhecido> reconhecidos = new ArrayList<>();
            for (long id = ALUNOS; id >= 1; id -= 2) {
                reconhecidos.add(new Reconhecido(String.valueOf(id), 0.9));
            }
            for (int i = 0; i < 2_000; i++) {
                reconhecidos.add(new Reconhecido(String.valueOf(100_000 + i), 0.5));
            }
            when(reconhecimento.auditarSala(foto, "t")).thenReturn(new ReconhecimentoFacialClient.Resultado(reconhecidos, 0, reconhecidos.size()));

            PreviaResposta previa = assertTimeout(Duration.ofSeconds(2), () -> service.previa(9L, foto));

            assertEquals(ALUNOS, previa.alunos().size());
            assertEquals(LongStream.rangeClosed(1, ALUNOS).boxed().toList(),
                    previa.alunos().stream().map(PreviaResposta.AlunoPrevia::alunoId).toList());
            assertEquals(ALUNOS / 2, previa.alunos().stream().filter(a -> a.situacaoPresenca().equals(ResultadoAuditoria.PRESENTE)).count());
            assertEquals(2_000, previa.facesForaDaTurma());
        }

        // Confirmação com 5.000 alunos termina rápido.
        @Test
        void confirmacaoComTurmaInteira() {
            List<ResultadoRequisicao> resultados = LongStream.rangeClosed(1, ALUNOS)
                    .mapToObj(id -> new ResultadoRequisicao(id, "PRESENTE", null)).toList();

            var resposta = assertTimeout(Duration.ofSeconds(2),
                    () -> service.confirmar(new ConfirmacaoRequisicao(9L, null, null, resultados)));

            assertEquals(ALUNOS, resposta.resultados().size());
        }
    }
}
