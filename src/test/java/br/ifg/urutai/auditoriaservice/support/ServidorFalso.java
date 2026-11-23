package br.ifg.urutai.auditoriaservice.support;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

// Servidor HTTP falso que faz o papel do turma-service e do reconhecimento facial.
public final class ServidorFalso implements AutoCloseable {

    public record Requisicao(String metodo, String caminho, Headers cabecalhos, byte[] corpo) {
        public String corpoTexto() {
            return new String(corpo, StandardCharsets.ISO_8859_1);
        }
    }

    private record Resposta(int status, String json, Duration atraso) {}

    private final HttpServer servidor;
    private final Map<String, Resposta> respostas = new ConcurrentHashMap<>();
    private final List<Requisicao> recebidas = new CopyOnWriteArrayList<>();

    public ServidorFalso() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        servidor.createContext("/", this::tratar);
        servidor.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "servidor-falso");
            t.setDaemon(true);
            return t;
        }));
        servidor.start();
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    // Define o que responder para "METODO /caminho". Sem isso, responde 404.
    public ServidorFalso responder(String metodo, String caminho, int status, String json) {
        respostas.put(metodo + " " + caminho, new Resposta(status, json, Duration.ZERO));
        return this;
    }

    // Igual ao responder, mas demora para responder (serviço lento).
    public ServidorFalso responderComAtraso(String metodo, String caminho, Duration atraso, int status, String json) {
        respostas.put(metodo + " " + caminho, new Resposta(status, json, atraso));
        return this;
    }

    public List<Requisicao> recebidas() {
        return List.copyOf(recebidas);
    }

    public Requisicao ultima() {
        return recebidas.getLast();
    }

    public void limpar() {
        respostas.clear();
        recebidas.clear();
    }

    @Override
    public void close() {
        servidor.stop(0);
    }

    // Endereço onde nenhum servidor escuta (serviço fora do ar).
    public static String urlSemServidor() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "http://127.0.0.1:" + socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void tratar(HttpExchange troca) throws IOException {
        try (troca; InputStream entrada = troca.getRequestBody()) {
            String caminho = troca.getRequestURI().getPath();
            recebidas.add(new Requisicao(troca.getRequestMethod(), caminho, troca.getRequestHeaders(), entrada.readAllBytes()));

            Resposta resposta = respostas.getOrDefault(troca.getRequestMethod() + " " + caminho, new Resposta(404, "", Duration.ZERO));
            if (!resposta.atraso().isZero()) {
                try {
                    Thread.sleep(resposta.atraso());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            byte[] corpo = resposta.json().getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().set("Content-Type", "application/json");
            troca.sendResponseHeaders(resposta.status(), corpo.length == 0 ? -1 : corpo.length);
            if (corpo.length > 0) {
                try (OutputStream saida = troca.getResponseBody()) {
                    saida.write(corpo);
                }
            }
        }
    }
}
