package br.com.datalume.compartilhado.erro;

import org.springframework.http.HttpStatus;

public class RequisicaoInvalidaException extends ApiException {

    public RequisicaoInvalidaException(String mensagem) {
        super(HttpStatus.BAD_REQUEST, "Requisição inválida", mensagem);
    }
}
