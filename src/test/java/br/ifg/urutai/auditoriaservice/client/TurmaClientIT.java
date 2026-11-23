package br.ifg.urutai.auditoriaservice.client;

import br.ifg.urutai.auditoriaservice.exception.IntegracaoException;
import br.ifg.urutai.auditoriaservice.exception.RecursoNaoEncontradoException;
import br.ifg.urutai.auditoriaservice.support.ServidorFalso;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// TurmaClient chamando um turma-service falso por HTTP.
class TurmaClientIT {

    private static final ServidorFalso TURMA_SERVICE = new ServidorFalso();
    private final TurmaClient client = new TurmaClient(TURMA_SERVICE.url());

    @BeforeEach
    void limpar() {
        TURMA_SERVICE.limpar();
    }

    @AfterAll
    static void parar() {
        TURMA_SERVICE.close();
    }

    // Lê a turma com os alunos e envia o token no cabeçalho.
    @Test
    void buscarLeTurmaEAlunosERepassaOToken() {
        TURMA_SERVICE.responder("GET", "/turmas/1", 200, """
                {"id": 1, "nome": "ADS 2026/1", "cursoId": 5, "campoNovo": "ignorado",
                 "alunos": [{"alunoId": 10, "situacao": "ATIVO", "nome": "Ana"}, {"alunoId": 30, "situacao": "INATIVO"}]}
                """);

        TurmaClient.Turma turma = client.buscar(1L, "meu.jwt");

        assertEquals(new TurmaClient.Turma(1L, "ADS 2026/1", 5L,
                List.of(new TurmaClient.Vinculo(10L, "ATIVO"), new TurmaClient.Vinculo(30L, "INATIVO"))), turma);
        ServidorFalso.Requisicao requisicao = TURMA_SERVICE.ultima();
        assertEquals("GET", requisicao.metodo());
        assertEquals("Bearer meu.jwt", requisicao.cabecalhos().getFirst("Authorization"));
    }

    // Turma sem a lista de alunos vem com alunos nulo.
    @Test
    void buscarTurmaSemAlunos() {
        TURMA_SERVICE.responder("GET", "/turmas/2", 200, """
                {"id": 2, "nome": "Vazia", "cursoId": 5}""");

        assertNull(client.buscar(2L, "t").alunos());
    }

    // Turma que não existe dá erro de não encontrado.
    @Test
    void buscarTurmaInexistente() {
        TURMA_SERVICE.responder("GET", "/turmas/404", 404, "{\"mensagem\": \"não achou\"}");

        RecursoNaoEncontradoException erro = assertThrows(RecursoNaoEncontradoException.class, () -> client.buscar(404L, "t"));
        assertEquals("Turma não encontrada", erro.getMessage());
    }

    // Sem permissão no turma-service vira erro 403.
    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void semAcessoATurmaViraForbidden(int status) {
        TURMA_SERVICE.responder("GET", "/turmas/1", status, "");

        IntegracaoException erro = assertThrows(IntegracaoException.class, () -> client.buscar(1L, "t"));
        assertEquals(HttpStatus.FORBIDDEN, erro.getStatus());
        assertEquals("Sem acesso à turma no serviço de turmas", erro.getMessage());
    }

    // Outros erros do turma-service viram erro 502.
    @ParameterizedTest
    @ValueSource(ints = {400, 500, 503})
    void outrosErrosViramBadGateway(int status) {
        TURMA_SERVICE.responder("GET", "/turmas/1", status, "");

        IntegracaoException erro = assertThrows(IntegracaoException.class, () -> client.buscar(1L, "t"));
        assertEquals(HttpStatus.BAD_GATEWAY, erro.getStatus());
    }

    // Resposta que não é JSON vira erro 502.
    @Test
    void respostaQueNaoEJsonViraBadGateway() {
        TURMA_SERVICE.responder("GET", "/turmas/1", 200, "<html>proxy</html>");

        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(IntegracaoException.class, () -> client.buscar(1L, "t")).getStatus());
    }

    // Turma-service desligado vira erro 502.
    @Test
    void servicoForaDoArViraBadGateway() {
        TurmaClient semServidor = new TurmaClient(ServidorFalso.urlSemServidor());

        IntegracaoException erro = assertThrows(IntegracaoException.class, () -> semServidor.buscar(1L, "t"));
        assertEquals(HttpStatus.BAD_GATEWAY, erro.getStatus());
        assertEquals("Serviço de turmas indisponível. Tente novamente.", erro.getMessage());
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(IntegracaoException.class, () -> semServidor.listarPorCurso(5L, "t")).getStatus());
    }

    // Lista as turmas de um curso e envia o token.
    @Test
    void listarPorCurso() {
        TURMA_SERVICE.responder("GET", "/turmas/curso/5", 200, """
                [{"id": 1, "nome": "ADS A", "cursoId": 5}, {"id": 2, "nome": "ADS B", "cursoId": 5}]""");

        List<TurmaClient.Turma> turmas = client.listarPorCurso(5L, "meu.jwt");

        assertEquals(List.of(1L, 2L), turmas.stream().map(TurmaClient.Turma::id).toList());
        assertEquals("Bearer meu.jwt", TURMA_SERVICE.ultima().cabecalhos().getFirst("Authorization"));
    }

    // Curso sem turmas devolve lista vazia.
    @Test
    void listarPorCursoVazio() {
        TURMA_SERVICE.responder("GET", "/turmas/curso/6", 200, "[]");

        assertTrue(client.listarPorCurso(6L, "t").isEmpty());
    }

    // Sem permissão para listar as turmas vira erro 403.
    @Test
    void listarPorCursoSemAcesso() {
        TURMA_SERVICE.responder("GET", "/turmas/curso/5", 403, "");

        assertEquals(HttpStatus.FORBIDDEN, assertThrows(IntegracaoException.class, () -> client.listarPorCurso(5L, "t")).getStatus());
    }

    // Defeitos: hoje esses testes falham.
    @Nested
    @Tag("defeito-conhecido")
    class PioresCasos {

        // Resposta 200 sem corpo na busca da turma deve virar erro 502.
        @Test
        void buscarComCorpoVazio() {
            TURMA_SERVICE.responder("GET", "/turmas/1", 200, "");

            assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(IntegracaoException.class, () -> client.buscar(1L, "t")).getStatus());
        }

        // Resposta 200 sem corpo na lista do curso deve virar erro 502.
        @Test
        void listarPorCursoComCorpoVazio() {
            TURMA_SERVICE.responder("GET", "/turmas/curso/5", 200, "");

            assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(IntegracaoException.class, () -> client.listarPorCurso(5L, "t")).getStatus());
        }

        // Turma-service travado: deve desistir em até 15 segundos com erro 502.
        @Test
        void servicoLentoDesisteComErro() {
            TURMA_SERVICE.responderComAtraso("GET", "/turmas/1", Duration.ofMinutes(2), 200, "{\"id\": 1}");

            IntegracaoException erro = assertTimeoutPreemptively(Duration.ofSeconds(15),
                    () -> assertThrows(IntegracaoException.class, () -> client.buscar(1L, "t")));
            assertEquals(HttpStatus.BAD_GATEWAY, erro.getStatus());
        }
    }
}
