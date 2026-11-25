package br.ifg.urutai.auditoriaservice.client;

import br.ifg.urutai.auditoriaservice.exception.IntegracaoException;
import br.ifg.urutai.auditoriaservice.exception.RecursoNaoEncontradoException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/** Consulta turma e alunos vinculados no turma-service, repassando o JWT do usuário. */
@Component
public class TurmaClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Turma(Long id, String nome, Long cursoId, List<Vinculo> alunos) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vinculo(Long alunoId, String situacao) {
        /** "ATIVO" e "MATRICULADO" (situação padrão do app e dos exemplos do turma-service) contam como ativos. */
        public boolean ativo() {
            return "ATIVO".equalsIgnoreCase(situacao) || "MATRICULADO".equalsIgnoreCase(situacao);
        }
    }

    private final RestClient http;

    public TurmaClient(@Value("${servicos.turma.url}") String url) {
        this.http = RestClient.create(url);
    }

    public Turma buscar(Long turmaId, String token) {
        try {
            return http.get().uri("/turmas/{id}", turmaId)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve().body(Turma.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new RecursoNaoEncontradoException("Turma não encontrada");
        } catch (RestClientException e) {
            throw falha(e);
        }
    }

    /** Turmas que o usuário do token enxerga (o turma-service limita à instituição dele). */
    public List<Turma> listar(String token) {
        try {
            return http.get().uri("/turmas")
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve().body(new ParameterizedTypeReference<>() {});
        } catch (RestClientException e) {
            throw falha(e);
        }
    }

    public List<Turma> listarPorCurso(Long cursoId, String token) {
        try {
            return http.get().uri("/turmas/curso/{cursoId}", cursoId)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve().body(new ParameterizedTypeReference<>() {});
        } catch (RestClientException e) {
            throw falha(e);
        }
    }

    private IntegracaoException falha(RestClientException e) {
        if (e instanceof HttpClientErrorException.Forbidden || e instanceof HttpClientErrorException.Unauthorized) {
            return new IntegracaoException(HttpStatus.FORBIDDEN, "Sem acesso à turma no serviço de turmas");
        }
        return new IntegracaoException(HttpStatus.BAD_GATEWAY, "Serviço de turmas indisponível. Tente novamente.");
    }
}
