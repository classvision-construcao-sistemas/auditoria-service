package br.ifg.urutai.auditoriaservice.client;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VinculoTest {

    // "ATIVO" e "MATRICULADO" (maiúscula ou minúscula) contam como aluno ativo.
    @ParameterizedTest
    @CsvSource(value = {"ATIVO, true", "ativo, true", "Ativo, true", "MATRICULADO, true", "matriculado, true", "INATIVO, false", "TRANCADO, false", "' ATIVO', false", "NULL, false"},
            nullValues = "NULL")
    void ativo(String situacao, boolean esperado) {
        assertEquals(esperado, new TurmaClient.Vinculo(1L, situacao).ativo());
    }
}
