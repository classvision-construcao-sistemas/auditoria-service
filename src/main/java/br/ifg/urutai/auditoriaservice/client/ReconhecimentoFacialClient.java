package br.ifg.urutai.auditoriaservice.client;

import br.ifg.urutai.auditoriaservice.exception.IntegracaoException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Chama POST /api/v1/auditar-sala do reconhecimento-facial-service (RF-AUD-03). */
@Component
public class ReconhecimentoFacialClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resultado(
            @JsonProperty("recognized_students") List<Reconhecido> reconhecidos,
            @JsonProperty("unrecognized_count") int naoReconhecidos,
            @JsonProperty("total_faces_detected") int totalFaces) {}

    /** student_id é a matrícula cadastrada no Milvus. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reconhecido(
            @JsonProperty("student_id") String matricula,
            @JsonProperty("confidence_score") Double confianca) {}

    private final RestClient http;

    public ReconhecimentoFacialClient(@Value("${servicos.reconhecimento.url}") String url) {
        // Bufferiza para enviar Content-Length: sem ele o upload vai chunked e o servidor Python não lê o arquivo.
        this.http = RestClient.builder().baseUrl(url)
                .requestFactory(new BufferingClientHttpRequestFactory(new JdkClientHttpRequestFactory()))
                .build();
    }

    public Resultado auditarSala(MultipartFile foto) {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("image", foto.getResource());
        try {
            return http.post().uri("/api/v1/auditar-sala")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(corpo)
                    .retrieve().body(Resultado.class);
        } catch (HttpClientErrorException.BadRequest e) {
            throw new IntegracaoException(HttpStatus.BAD_REQUEST, "Imagem inválida para reconhecimento facial");
        } catch (RestClientException e) {
            throw new IntegracaoException(HttpStatus.BAD_GATEWAY, "Serviço de reconhecimento facial indisponível. Tente novamente.");
        }
    }
}
