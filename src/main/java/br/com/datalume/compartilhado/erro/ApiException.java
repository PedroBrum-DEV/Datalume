package br.com.datalume.compartilhado.erro;

import org.springframework.http.HttpStatus;

/** Erro de aplicação com status HTTP definido; convertido em ProblemDetail pelo handler global. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String titulo;

    public ApiException(HttpStatus status, String titulo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.titulo = titulo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitulo() {
        return titulo;
    }
}
