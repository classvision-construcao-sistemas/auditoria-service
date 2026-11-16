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
import br.ifg.urutai.auditoriaservice.exception.RegraNegocioException;
import br.ifg.urutai.auditoriaservice.model.Auditoria;
import br.ifg.urutai.auditoriaservice.model.ResultadoAuditoria;
import br.ifg.urutai.auditoriaservice.repository.AuditoriaRepository;
import br.ifg.urutai.auditoriaservice.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
        // Alunos 10 e 20 ativos, 30 inativo (RN-06).
        when(turmas.buscar(eq(1L), any())).thenReturn(new TurmaClient.Turma(1L, "ADS", 5L, List.of(
                new Vinculo(10L, "ATIVO"), new Vinculo(20L, "ativo"), new Vinculo(30L, "INATIVO"))));
        when(repositorio.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repositorio.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void previaMarcaPresentesPossiveisFaltasEFacesDeFora() {
        // 10 é da turma, 30 é inativo, 99 é de outra turma, "abc" não é matrícula numérica.
        when(reconhecimento.auditarSala(foto)).thenReturn(new ReconhecimentoFacialClient.Resultado(List.of(
                new Reconhecido("10", 0.91), new Reconhecido("30", 0.8), new Reconhecido("99", 0.7), new Reconhecido("abc", 0.6)), 2, 6));

        PreviaResposta previa = service.previa(1L, foto);

        assertEquals(List.of(10L, 20L), previa.alunos().stream().map(PreviaResposta.AlunoPrevia::alunoId).toList());
        assertEquals(ResultadoAuditoria.PRESENTE, previa.alunos().get(0).situacaoPresenca());
        assertEquals(0.91, previa.alunos().get(0).confianca());
        assertEquals(ResultadoAuditoria.POSSIVEL_FALTA, previa.alunos().get(1).situacaoPresenca());
        assertEquals(2, previa.facesNaoIdentificadas());
        assertEquals(3, previa.facesForaDaTurma());
        assertEquals(6, previa.totalFaces());
        verify(repositorio, never()).save(any());
    }

    @Test
    void previaSemFotoFalha() {
        assertThrows(RegraNegocioException.class, () -> service.previa(1L, new MockMultipartFile("foto", new byte[0])));
        verifyNoInteractions(reconhecimento);
    }

    @Test
    void confirmarGravaResultadosComUsuarioDoToken() {
        AuditoriaResposta resposta = service.confirmar(new ConfirmacaoRequisicao(1L, null, null, List.of(
                new ResultadoRequisicao(10L, "PRESENTE", "RECONHECIDA"), new ResultadoRequisicao(20L, "POSSIVEL_FALTA", null))));

        assertEquals(7L, resposta.usuarioId());
        assertEquals(Auditoria.CONFIRMADA, resposta.situacao());
        assertNotNull(resposta.data());
        assertEquals(2, resposta.resultados().size());
    }

    @Test
    void confirmarRecusaAlunoInativoOuRepetido() {
        assertThrows(RegraNegocioException.class, () -> service.confirmar(new ConfirmacaoRequisicao(1L, null, null,
                List.of(new ResultadoRequisicao(30L, "PRESENTE", null)))));
        assertThrows(RegraNegocioException.class, () -> service.confirmar(new ConfirmacaoRequisicao(1L, null, null,
                List.of(new ResultadoRequisicao(10L, "PRESENTE", null), new ResultadoRequisicao(10L, "POSSIVEL_FALTA", null)))));
        verify(repositorio, never()).save(any());
    }

    @Test
    void corrigirAtualizaPresencaERegistraJustificativa() {
        Auditoria auditoria = new Auditoria();
        auditoria.setId(3L);
        auditoria.setSituacao(Auditoria.CONFIRMADA);
        auditoria.adicionarResultado(new ResultadoAuditoria(10L, "POSSIVEL_FALTA", null));
        when(repositorio.findById(3L)).thenReturn(Optional.of(auditoria));

        AuditoriaResposta resposta = service.corrigir(3L, new CorrecaoRequisicao("  Aluno chegou atrasado ",
                List.of(new ResultadoRequisicao(10L, "PRESENTE", null))));

        assertEquals("PRESENTE", resposta.resultados().get(0).situacaoPresenca());
        assertEquals(Auditoria.CORRIGIDA, resposta.situacao());
        assertEquals("Aluno chegou atrasado", resposta.correcoes().get(0).justificativa());
        assertEquals(7L, resposta.correcoes().get(0).usuarioId());

        assertThrows(RegraNegocioException.class, () -> service.corrigir(3L, new CorrecaoRequisicao("x",
                List.of(new ResultadoRequisicao(20L, "PRESENTE", null)))));
    }
}
