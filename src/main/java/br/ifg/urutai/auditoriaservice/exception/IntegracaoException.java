package br.ifg.urutai.auditoriaservice.exception;

import org.springframework.http.HttpStatus;

/** Falha ao falar com turma-service ou reconhecimento facial (RF-AUD-05: o cliente pode tentar de novo). */
public class IntegracaoException extends RuntimeException {

    private final HttpStatus status;

    public IntegracaoException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
