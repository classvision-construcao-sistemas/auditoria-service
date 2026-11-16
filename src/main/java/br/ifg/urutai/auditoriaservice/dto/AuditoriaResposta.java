package br.ifg.urutai.auditoriaservice.dto;

import br.ifg.urutai.auditoriaservice.model.Auditoria;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record AuditoriaResposta(
        Long id,
        Long turmaId,
        Long usuarioId,
        LocalDate data,
        LocalTime horario,
        String situacao,
        List<Resultado> resultados,
        List<Correcao> correcoes) {

    public record Resultado(Long alunoId, String situacaoPresenca, String situacaoFace) {}

    public record Correcao(Long usuarioId, LocalDateTime dtCorrecao, String justificativa) {}

    public static AuditoriaResposta de(Auditoria a) {
        return new AuditoriaResposta(a.getId(), a.getTurmaId(), a.getUsuarioId(), a.getData(), a.getHorario(), a.getSituacao(),
                a.getResultados().stream().map(r -> new Resultado(r.getAlunoId(), r.getSituacaoPresenca(), r.getSituacaoFace())).toList(),
                a.getCorrecoes().stream().map(c -> new Correcao(c.getUsuarioId(), c.getDtCorrecao(), c.getJustificativa())).toList());
    }
}
