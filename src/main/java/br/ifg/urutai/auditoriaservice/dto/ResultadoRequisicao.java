package br.ifg.urutai.auditoriaservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ResultadoRequisicao(
        @NotNull @Positive Long alunoId,
        @NotNull @Pattern(regexp = "PRESENTE|POSSIVEL_FALTA", message = "use PRESENTE ou POSSIVEL_FALTA") String situacaoPresenca,
        @Size(max = 30) String situacaoFace) {}
