package br.com.datalume.compartilhado.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Atribui um requestId a cada requisição (aceita X-Request-Id válido do cliente), publica no MDC
 * (aparece nos logs estruturados), devolve no header de resposta e registra um log de acesso.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final Logger acesso = LoggerFactory.getLogger("datalume.acesso");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        if (id == null || !VALIDO.matcher(id).matches()) {
            id = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        long inicio = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (!request.getRequestURI().startsWith("/actuator")) {
                acesso.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), (System.nanoTime() - inicio) / 1_000_000);
            }
            MDC.remove(MDC_KEY);
        }
    }
}
