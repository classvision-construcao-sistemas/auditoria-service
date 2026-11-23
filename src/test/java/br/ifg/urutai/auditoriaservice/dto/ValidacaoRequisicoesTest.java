package br.ifg.urutai.auditoriaservice.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ValidacaoRequisicoesTest {

    private static final ValidatorFactory FABRICA = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDADOR = FABRICA.getValidator();

    @AfterAll
    static void fechar() {
        FABRICA.close();
    }

    private static Set<String> camposInvalidos(Object objeto) {
        return VALIDADOR.validate(objeto).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private static final ResultadoRequisicao VALIDO = new ResultadoRequisicao(10L, "PRESENTE", null);

    // Uma confirmação correta passa sem erros.
    @Test
    void confirmacaoValida() {
        assertTrue(camposInvalidos(new ConfirmacaoRequisicao(1L, null, null, List.of(VALIDO))).isEmpty());
    }

    // A confirmação exige turma positiva e ao menos um resultado.
    @Test
    void confirmacaoExigeTurmaPositivaEResultados() {
        assertEquals(Set.of("turmaId", "resultados"), camposInvalidos(new ConfirmacaoRequisicao(null, null, null, List.of())));
        assertEquals(Set.of("turmaId"), camposInvalidos(new ConfirmacaoRequisicao(0L, null, null, List.of(VALIDO))));
        assertEquals(Set.of("resultados"), camposInvalidos(new ConfirmacaoRequisicao(1L, null, null, null)));
    }

    // Cada resultado da lista também é validado.
    @Test
    void confirmacaoValidaCadaResultado() {
        Set<String> campos = camposInvalidos(new ConfirmacaoRequisicao(1L, null, null,
                List.of(VALIDO, new ResultadoRequisicao(-1L, "AUSENTE", "x".repeat(31)))));
        assertEquals(Set.of("resultados[1].alunoId", "resultados[1].situacaoPresenca", "resultados[1].situacaoFace"), campos);
    }

    // A presença só aceita PRESENTE ou POSSIVEL_FALTA.
    @Test
    void situacaoPresencaSoAceitaOsDoisValores() {
        assertTrue(camposInvalidos(new ResultadoRequisicao(1L, "POSSIVEL_FALTA", null)).isEmpty());
        assertEquals(Set.of("situacaoPresenca"), camposInvalidos(new ResultadoRequisicao(1L, "presente", null)));
        assertEquals(Set.of("situacaoPresenca"), camposInvalidos(new ResultadoRequisicao(1L, null, null)));

        ConstraintViolation<ResultadoRequisicao> violacao = VALIDADOR.validate(new ResultadoRequisicao(1L, "FALTOU", null)).iterator().next();
        assertEquals("use PRESENTE ou POSSIVEL_FALTA", violacao.getMessage());
    }

    // A situação do rosto aceita até 30 caracteres.
    @Test
    void situacaoFaceAte30Caracteres() {
        assertTrue(camposInvalidos(new ResultadoRequisicao(1L, "PRESENTE", "x".repeat(30))).isEmpty());
    }

    // A correção exige justificativa de até 255 caracteres e ao menos um resultado.
    @Test
    void correcaoExigeJustificativa() {
        assertTrue(camposInvalidos(new CorrecaoRequisicao("atraso", List.of(VALIDO))).isEmpty());
        assertEquals(Set.of("justificativa"), camposInvalidos(new CorrecaoRequisicao("   ", List.of(VALIDO))));
        assertEquals(Set.of("justificativa"), camposInvalidos(new CorrecaoRequisicao(null, List.of(VALIDO))));
        assertEquals(Set.of("justificativa"), camposInvalidos(new CorrecaoRequisicao("x".repeat(256), List.of(VALIDO))));
        assertTrue(camposInvalidos(new CorrecaoRequisicao("x".repeat(255), List.of(VALIDO))).isEmpty());
        assertEquals(Set.of("resultados"), camposInvalidos(new CorrecaoRequisicao("ok", List.of())));
    }
}
