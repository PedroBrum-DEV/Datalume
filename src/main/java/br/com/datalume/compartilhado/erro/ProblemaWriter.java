package br.com.datalume.compartilhado.erro;

import br.com.datalume.compartilhado.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Escreve um ProblemDetail direto na resposta (para filtros de segurança, fora do MVC). */
@Component
public class ProblemaWriter {

    private final ObjectMapper mapper;

    public ProblemaWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void escrever(HttpServletResponse resposta, HttpStatus status, String titulo, String detalhe)
            throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalhe);
        pd.setTitle(titulo);
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            pd.setProperty("requestId", requestId);
        }
        resposta.setStatus(status.value());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        mapper.writeValue(resposta.getOutputStream(), pd);
    }
}
