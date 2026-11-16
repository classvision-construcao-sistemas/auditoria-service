package br.ifg.urutai.auditoriaservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** data e horario são opcionais: sem eles vale a hora do servidor (RF-AUD-01). */
public record ConfirmacaoRequisicao(
        @NotNull @Positive Long turmaId,
        LocalDate data,
        LocalTime horario,
        @NotEmpty List<@Valid ResultadoRequisicao> resultados) {}
