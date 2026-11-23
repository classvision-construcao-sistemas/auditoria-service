package br.ifg.urutai.auditoriaservice;

import br.ifg.urutai.auditoriaservice.support.MySqlTeste;
import br.ifg.urutai.auditoriaservice.support.ServidorFalso;
import br.ifg.urutai.auditoriaservice.support.TokensTeste;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

// Testa a aplicação rodando num servidor de verdade: limite da foto e várias requisições ao mesmo tempo.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTeste.class)
@ActiveProfiles("teste")
@Testcontainers(disabledWithoutDocker = true)
class ServidorRealIT {

    private static final ServidorFalso TURMA_SERVICE = new ServidorFalso();
    private static final ServidorFalso RECONHECIMENTO = new ServidorFalso();
    private static final String COORDENADOR = "Bearer " + TokensTeste.coordenador(7L);
    private static final int MB = 1024 * 1024;

    @DynamicPropertySource
    static void servicosExternos(DynamicPropertyRegistry registro) {
        registro.add("servicos.turma.url", TURMA_SERVICE::url);
        registro.add("servicos.reconhecimento.url", RECONHECIMENTO::url);
    }

    @LocalServerPort
    int porta;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void servicosRespondendo() {
        TURMA_SERVICE.responder("GET", "/turmas/1", 200, """
                {"id": 1, "nome": "ADS", "cursoId": 5, "alunos": [
                  {"alunoId": 10, "situacao": "ATIVO"}, {"alunoId": 20, "situacao": "ATIVO"}]}""");
        RECONHECIMENTO.responder("POST", "/api/v1/auditar-sala", 200, """
                {"recognized_students": [{"student_id": "10", "confidence_score": 0.9}], "unrecognized_count": 0, "total_faces_detected": 1}""");
    }

    @AfterEach
    void limpar() {
        TURMA_SERVICE.limpar();
        RECONHECIMENTO.limpar();
        jdbc.update("DELETE FROM cv10_correcoes_auditoria");
        jdbc.update("DELETE FROM cv08_resultados_auditoria");
        jdbc.update("DELETE FROM cv07_auditorias");
    }

    @AfterAll
    static void pararServidores() {
        TURMA_SERVICE.close();
        RECONHECIMENTO.close();
    }

    // Envia uma foto com o tamanho pedido para a prévia.
    private HttpResponse<String> enviarFoto(int tamanho) throws Exception {
        String limite = "limite" + System.nanoTime();
        ByteArrayOutputStream corpo = new ByteArrayOutputStream();
        corpo.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"foto\"; filename=\"sala.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        corpo.write(new byte[tamanho]);
        corpo.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/auditorias/previa?turmaId=1"))
                .header("Authorization", COORDENADOR)
                .header("Content-Type", "multipart/form-data; boundary=" + limite)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray()))
                .build();
        return http.send(requisicao, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String caminho, String json) throws Exception {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Authorization", COORDENADOR)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(requisicao, HttpResponse.BodyHandlers.ofString());
    }

    // Foto de 14 MB (abaixo do limite) é aceita.
    @Test
    void fotoAbaixoDoLimiteEAceita() throws Exception {
        HttpResponse<String> resposta = enviarFoto(14 * MB);

        assertEquals(200, resposta.statusCode(), resposta.body());
        assertEquals(1, RECONHECIMENTO.recebidas().size());
    }

    // Defeito: foto de 16 MB deveria receber 413 com a mensagem certa. Hoje a conexão é cortada no meio da resposta.
    @Test
    @Tag("defeito-conhecido")
    void fotoAcimaDoLimiteRecebe413() throws Exception {
        HttpResponse<String> resposta = enviarFoto(16 * MB);

        assertEquals(413, resposta.statusCode(), resposta.body());
        assertTrue(resposta.body().contains("A foto passa do limite de 15 MB"), resposta.body());
        assertTrue(RECONHECIMENTO.recebidas().isEmpty());
    }

    // 10 confirmações ao mesmo tempo são todas gravadas.
    @Test
    void confirmacoesSimultaneas() throws Exception {
        List<CompletableFuture<HttpResponse<String>>> envios = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            envios.add(CompletableFuture.supplyAsync(() -> enviarSemErro("/auditorias", """
                    {"turmaId": 1, "resultados": [{"alunoId": 10, "situacaoPresenca": "PRESENTE"},
                                                  {"alunoId": 20, "situacaoPresenca": "POSSIVEL_FALTA"}]}""")));
        }

        for (CompletableFuture<HttpResponse<String>> envio : envios) {
            assertEquals(201, envio.get().statusCode(), envio.get().body());
        }
        assertEquals(10, jdbc.queryForObject("SELECT COUNT(*) FROM cv07_auditorias", Integer.class));
        assertEquals(20, jdbc.queryForObject("SELECT COUNT(*) FROM cv08_resultados_auditoria", Integer.class));
    }

    // Defeito: 10 correções ao mesmo tempo na mesma auditoria deveriam dar 200. Hoje dão deadlock no MySQL e respondem 403.
    @Test
    @Tag("defeito-conhecido")
    void correcoesSimultaneasNaMesmaAuditoria() throws Exception {
        HttpResponse<String> criada = postJson("/auditorias", """
                {"turmaId": 1, "resultados": [{"alunoId": 10, "situacaoPresenca": "PRESENTE"}]}""");
        long id = ((Number) JsonPath.read(criada.body(), "$.id")).longValue();

        List<CompletableFuture<HttpResponse<String>>> envios = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String presenca = i % 2 == 0 ? "POSSIVEL_FALTA" : "PRESENTE";
            envios.add(CompletableFuture.supplyAsync(() -> enviarSemErro("/auditorias/" + id + "/correcoes",
                    "{\"justificativa\": \"ajuste\", \"resultados\": [{\"alunoId\": 10, \"situacaoPresenca\": \"" + presenca + "\"}]}")));
        }

        for (CompletableFuture<HttpResponse<String>> envio : envios) {
            assertEquals(200, envio.get().statusCode(), envio.get().body());
        }
        assertEquals(10, jdbc.queryForObject("SELECT COUNT(*) FROM cv10_correcoes_auditoria WHERE bigint_auditoria_id = ?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM cv08_resultados_auditoria WHERE bigint_auditoria_id = ?", Integer.class, id));
    }

    private HttpResponse<String> enviarSemErro(String caminho, String json) {
        try {
            return postJson(caminho, json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
