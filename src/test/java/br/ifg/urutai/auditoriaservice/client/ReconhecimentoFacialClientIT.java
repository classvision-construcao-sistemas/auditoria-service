package br.ifg.urutai.auditoriaservice.client;

import br.ifg.urutai.auditoriaservice.exception.IntegracaoException;
import br.ifg.urutai.auditoriaservice.support.ServidorFalso;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// ReconhecimentoFacialClient chamando um serviço de reconhecimento falso por HTTP.
class ReconhecimentoFacialClientIT {

    private static final String ROTA = "/api/v1/auditar-sala";
    private static final ServidorFalso RECONHECIMENTO = new ServidorFalso();
    private final ReconhecimentoFacialClient client = new ReconhecimentoFacialClient(RECONHECIMENTO.url());

    private final MockMultipartFile foto = new MockMultipartFile("foto", "sala.jpg", "image/jpeg", "JPEG-BYTES-DA-FOTO".getBytes());

    @BeforeEach
    void limpar() {
        RECONHECIMENTO.limpar();
    }

    @AfterAll
    static void parar() {
        RECONHECIMENTO.close();
    }

    // Envia a foto no campo "image" e lê os alunos reconhecidos.
    @Test
    void enviaAFotoNoCampoImageELeOResultado() {
        RECONHECIMENTO.responder("POST", ROTA, 200, """
                {"recognized_students": [{"student_id": "10", "confidence_score": 0.93, "bbox": [1, 2, 3, 4]},
                                         {"student_id": "20", "confidence_score": 0.71}],
                 "unrecognized_count": 1, "total_faces_detected": 3, "processing_time_ms": 120}
                """);

        ReconhecimentoFacialClient.Resultado resultado = client.auditarSala(foto);

        assertEquals(List.of(new ReconhecimentoFacialClient.Reconhecido("10", 0.93), new ReconhecimentoFacialClient.Reconhecido("20", 0.71)),
                resultado.reconhecidos());
        assertEquals(1, resultado.naoReconhecidos());
        assertEquals(3, resultado.totalFaces());

        ServidorFalso.Requisicao requisicao = RECONHECIMENTO.ultima();
        assertTrue(requisicao.cabecalhos().getFirst("Content-Type").startsWith("multipart/form-data"));
        String corpo = requisicao.corpoTexto();
        assertTrue(corpo.contains("name=\"image\""), corpo);
        assertTrue(corpo.contains("filename=\"sala.jpg\""), corpo);
        assertTrue(corpo.contains("JPEG-BYTES-DA-FOTO"));
    }

    // A foto vai com o tamanho informado (Content-Length), que o serviço Python exige.
    @Test
    void uploadVaiComContentLengthENaoChunked() {
        RECONHECIMENTO.responder("POST", ROTA, 200, """
                {"recognized_students": [], "unrecognized_count": 0, "total_faces_detected": 0}""");

        client.auditarSala(foto);

        ServidorFalso.Requisicao requisicao = RECONHECIMENTO.ultima();
        assertNull(requisicao.cabecalhos().getFirst("Transfer-Encoding"));
        assertEquals(String.valueOf(requisicao.corpo().length), requisicao.cabecalhos().getFirst("Content-Length"));
    }

    // Foto recusada pelo serviço vira erro 400.
    @Test
    void imagemRecusadaViraBadRequest() {
        RECONHECIMENTO.responder("POST", ROTA, 400, "{\"detail\": \"Invalid image\"}");

        IntegracaoException erro = assertThrows(IntegracaoException.class, () -> client.auditarSala(foto));
        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatus());
        assertEquals("Imagem inválida para reconhecimento facial", erro.getMessage());
    }

    // Outros erros do serviço viram erro 502.
    @ParameterizedTest
    @ValueSource(ints = {404, 422, 500, 503})
    void outrosErrosViramBadGateway(int status) {
        RECONHECIMENTO.responder("POST", ROTA, status, "{}");

        IntegracaoException erro = assertThrows(IntegracaoException.class, () -> client.auditarSala(foto));
        assertEquals(HttpStatus.BAD_GATEWAY, erro.getStatus());
        assertEquals("Serviço de reconhecimento facial indisponível. Tente novamente.", erro.getMessage());
    }

    // Serviço desligado vira erro 502.
    @Test
    void servicoForaDoArViraBadGateway() {
        ReconhecimentoFacialClient semServidor = new ReconhecimentoFacialClient(ServidorFalso.urlSemServidor());

        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(IntegracaoException.class, () -> semServidor.auditarSala(foto)).getStatus());
    }

    // Defeitos: hoje esses testes falham.
    @Nested
    @Tag("defeito-conhecido")
    class PioresCasos {

        // Resposta 200 sem corpo deve virar erro 502.
        @Test
        void respostaComCorpoVazio() {
            RECONHECIMENTO.responder("POST", ROTA, 200, "");

            assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(IntegracaoException.class, () -> client.auditarSala(foto)).getStatus());
        }

        // Serviço travado: deve desistir em até 40 segundos com erro 502.
        @Test
        void servicoLentoDesisteComErro() {
            RECONHECIMENTO.responderComAtraso("POST", ROTA, Duration.ofMinutes(3), 200, "{}");

            IntegracaoException erro = assertTimeoutPreemptively(Duration.ofSeconds(40),
                    () -> assertThrows(IntegracaoException.class, () -> client.auditarSala(foto)));
            assertEquals(HttpStatus.BAD_GATEWAY, erro.getStatus());
        }
    }
}
