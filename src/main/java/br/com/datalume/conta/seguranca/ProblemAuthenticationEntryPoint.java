package br.com.datalume.conta.seguranca;

import br.com.datalume.compartilhado.erro.ProblemaWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemaWriter writer;

    ProblemAuthenticationEntryPoint(ProblemaWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        writer.escrever(response, HttpStatus.UNAUTHORIZED, "Não autenticado",
                "Token ausente, inválido ou expirado.");
    }
}
