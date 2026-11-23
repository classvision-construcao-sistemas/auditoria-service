package br.ifg.urutai.auditoriaservice.client;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VinculoTest {

    // Só a situação "ATIVO" (maiúscula ou minúscula) conta como aluno ativo.
    @ParameterizedTest
    @CsvSource(value = {"ATIVO, true", "ativo, true", "Ativo, true", "INATIVO, false", "TRANCADO, false", "' ATIVO', false", "NULL, false"},
            nullValues = "NULL")
    void ativo(String situacao, boolean esperado) {
        assertEquals(esperado, new TurmaClient.Vinculo(1L, situacao).ativo());
    }
}
