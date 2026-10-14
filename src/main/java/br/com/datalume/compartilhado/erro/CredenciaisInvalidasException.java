package br.com.datalume.compartilhado.erro;

import org.springframework.http.HttpStatus;

public class CredenciaisInvalidasException extends ApiException {

    public CredenciaisInvalidasException() {
        super(HttpStatus.UNAUTHORIZED, "Credenciais inválidas", "E-mail ou senha inválidos.");
    }
}
