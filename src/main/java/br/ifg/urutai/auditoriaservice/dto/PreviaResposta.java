package br.ifg.urutai.auditoriaservice.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Resultado do reconhecimento para o coordenador revisar (UC-AUD-03). Nada é gravado
 * nesta etapa e a foto não é armazenada (RSEG-04).
 */
public record PreviaResposta(
        Long turmaId,
        LocalDate data,
        LocalTime horario,
        List<AlunoPrevia> alunos,
        int facesNaoIdentificadas,
        int facesForaDaTurma,
        int totalFaces) {

    public record AlunoPrevia(Long alunoId, String situacaoPresenca, String situacaoFace, Double confianca) {}
}
