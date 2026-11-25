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
        int totalFaces,
        List<Rosto> rostos) {

    public PreviaResposta(Long turmaId, LocalDate data, LocalTime horario, List<AlunoPrevia> alunos,
                          int facesNaoIdentificadas, int facesForaDaTurma, int totalFaces) {
        this(turmaId, data, horario, alunos, facesNaoIdentificadas, facesForaDaTurma, totalFaces, List.of());
    }

    public record AlunoPrevia(Long alunoId, String situacaoPresenca, String situacaoFace, Double confianca) {}

    /** Situações de um rosto da foto. */
    public static final String RECONHECIDO = "RECONHECIDO";
    public static final String FORA_DA_TURMA = "FORA_DA_TURMA";
    public static final String NAO_IDENTIFICADO = "NAO_IDENTIFICADO";

    /**
     * Rosto detectado na foto, para o app marcar sobre a imagem: caixa em frações da
     * foto (0 a 1), aluno da turma quando reconhecido e a situação.
     */
    public record Rosto(double x, double y, double largura, double altura, Long alunoId, String situacao) {}
}
