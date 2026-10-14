package br.com.datalume.compartilhado.erro;

import org.springframework.http.HttpStatus;

public class RecursoNaoEncontradoException extends ApiException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(HttpStatus.NOT_FOUND, "Recurso não encontrado", mensagem);
    }
}
