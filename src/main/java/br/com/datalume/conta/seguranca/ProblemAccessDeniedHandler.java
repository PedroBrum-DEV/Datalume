package br.com.datalume.conta.seguranca;

import br.com.datalume.compartilhado.erro.ProblemaWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemaWriter writer;

    ProblemAccessDeniedHandler(ProblemaWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        writer.escrever(response, HttpStatus.FORBIDDEN, "Acesso negado",
                "Você não tem permissão para este recurso.");
    }
}
