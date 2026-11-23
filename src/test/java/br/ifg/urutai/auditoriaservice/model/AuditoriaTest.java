package br.ifg.urutai.auditoriaservice.model;

import br.ifg.urutai.auditoriaservice.dto.AuditoriaResposta;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditoriaTest {

    // Ao adicionar um resultado, ele fica ligado à auditoria.
    @Test
    void adicionarResultadoLigaOsDoisLados() {
        Auditoria auditoria = new Auditoria();
        ResultadoAuditoria resultado = new ResultadoAuditoria(10L, ResultadoAuditoria.PRESENTE, ResultadoAuditoria.RECONHECIDA);

        auditoria.adicionarResultado(resultado);

        assertSame(auditoria, resultado.getAuditoria());
        assertEquals(1, auditoria.getResultados().size());
    }

    // Ao adicionar uma correção, ela fica ligada à auditoria.
    @Test
    void adicionarCorrecaoLigaOsDoisLados() {
        Auditoria auditoria = new Auditoria();
        CorrecaoAuditoria correcao = new CorrecaoAuditoria(7L, LocalDateTime.of(2026, 11, 20, 10, 0), "motivo");

        auditoria.adicionarCorrecao(correcao);

        assertSame(auditoria, correcao.getAuditoria());
        assertEquals(7L, correcao.getUsuarioId());
        assertEquals("motivo", correcao.getJustificativa());
    }

    // Sem situação do rosto, o padrão é NAO_RECONHECIDA.
    @Test
    void situacaoFaceNulaViraNaoReconhecida() {
        assertEquals(ResultadoAuditoria.NAO_RECONHECIDA, new ResultadoAuditoria(1L, ResultadoAuditoria.PRESENTE, null).getSituacaoFace());
        assertEquals(ResultadoAuditoria.RECONHECIDA, new ResultadoAuditoria(1L, ResultadoAuditoria.PRESENTE, ResultadoAuditoria.RECONHECIDA).getSituacaoFace());
    }

    // A resposta da API copia todos os dados da auditoria.
    @Test
    void respostaCopiaTodosOsCampos() {
        Auditoria auditoria = new Auditoria();
        auditoria.setId(1L);
        auditoria.setTurmaId(2L);
        auditoria.setUsuarioId(3L);
        auditoria.setData(LocalDate.of(2026, 11, 20));
        auditoria.setHorario(LocalTime.of(8, 15));
        auditoria.setSituacao(Auditoria.CORRIGIDA);
        auditoria.adicionarResultado(new ResultadoAuditoria(10L, ResultadoAuditoria.PRESENTE, ResultadoAuditoria.RECONHECIDA));
        LocalDateTime quando = LocalDateTime.of(2026, 11, 21, 9, 0);
        auditoria.adicionarCorrecao(new CorrecaoAuditoria(4L, quando, "atraso"));

        AuditoriaResposta resposta = AuditoriaResposta.de(auditoria);

        assertEquals(new AuditoriaResposta(1L, 2L, 3L, LocalDate.of(2026, 11, 20), LocalTime.of(8, 15), Auditoria.CORRIGIDA,
                        List.of(new AuditoriaResposta.Resultado(10L, ResultadoAuditoria.PRESENTE, ResultadoAuditoria.RECONHECIDA)),
                        List.of(new AuditoriaResposta.Correcao(4L, quando, "atraso"))),
                resposta);
    }
}
