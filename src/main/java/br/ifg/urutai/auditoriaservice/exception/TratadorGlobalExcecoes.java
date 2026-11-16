package br.ifg.urutai.auditoriaservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class TratadorGlobalExcecoes {

    public record ErroResposta(Instant dataHora, int status, String mensagem, String caminho, Map<String, String> campos) {}

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResposta> recursoNaoEncontrado(RecursoNaoEncontradoException erro, HttpServletRequest requisicao) {
        return criar(HttpStatus.NOT_FOUND, erro.getMessage(), requisicao, Map.of());
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResposta> regraNegocio(RegraNegocioException erro, HttpServletRequest requisicao) {
        return criar(HttpStatus.CONFLICT, erro.getMessage(), requisicao, Map.of());
    }

    @ExceptionHandler(IntegracaoException.class)
    public ResponseEntity<ErroResposta> integracao(IntegracaoException erro, HttpServletRequest requisicao) {
        return criar(erro.getStatus(), erro.getMessage(), requisicao, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResposta> integridade(DataIntegrityViolationException erro, HttpServletRequest requisicao) {
        return criar(HttpStatus.CONFLICT, "Operação viola uma restrição do banco de dados", requisicao, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> validacao(MethodArgumentNotValidException erro, HttpServletRequest requisicao) {
        Map<String, String> campos = new LinkedHashMap<>();
        erro.getBindingResult().getFieldErrors().forEach(campo -> campos.putIfAbsent(campo.getField(), campo.getDefaultMessage()));
        return criar(HttpStatus.BAD_REQUEST, "Dados inválidos", requisicao, campos);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErroResposta> requisicaoInvalida(Exception erro, HttpServletRequest requisicao) {
        return criar(HttpStatus.BAD_REQUEST, "Requisição inválida: " + erro.getMessage(), requisicao, Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroResposta> fotoGrande(MaxUploadSizeExceededException erro, HttpServletRequest requisicao) {
        return criar(HttpStatus.CONTENT_TOO_LARGE, "A foto passa do limite de 15 MB", requisicao, Map.of());
    }

    private ResponseEntity<ErroResposta> criar(HttpStatus status, String mensagem, HttpServletRequest requisicao, Map<String, String> campos) {
        return ResponseEntity.status(status).body(new ErroResposta(Instant.now(), status.value(), mensagem, requisicao.getRequestURI(), campos));
    }
}
