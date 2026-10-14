package br.com.datalume.compartilhado.erro;

import org.springframework.http.HttpStatus;

public class RegraDeNegocioException extends ApiException {

    public RegraDeNegocioException(String mensagem) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio violada", mensagem);
    }
}
