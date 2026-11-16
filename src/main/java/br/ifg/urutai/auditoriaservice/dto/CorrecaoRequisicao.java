package br.ifg.urutai.auditoriaservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** RF-HIS-02: justificativa obrigatória (RSEG-06). */
public record CorrecaoRequisicao(
        @NotBlank @Size(max = 255) String justificativa,
        @NotEmpty List<@Valid ResultadoRequisicao> resultados) {}
