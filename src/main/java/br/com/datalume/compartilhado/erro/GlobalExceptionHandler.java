package br.com.datalume.compartilhado.erro;

import br.com.datalume.compartilhado.web.RequestIdFilter;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tratamento global de erros no formato RFC 7807 (application/problem+json).
 * Toda resposta de erro leva o campo "requestId" para facilitar o suporte.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApi(ApiException ex, WebRequest request) {
        return problema(ex, ex.getStatus(), ex.getTitulo(), ex.getMessage(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraint(ConstraintViolationException ex, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais parâmetros são inválidos.");
        pd.setTitle("Requisição inválida");
        pd.setProperty("erros", ex.getConstraintViolations().stream()
                .map(v -> Map.of("campo", v.getPropertyPath().toString(), "mensagem", v.getMessage()))
                .toList());
        return handleExceptionInternal(ex, pd, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleIntegridade(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return problema(ex, HttpStatus.CONFLICT, "Conflito", "A operação conflita com dados já existentes.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAcessoNegado(AccessDeniedException ex, WebRequest request) {
        return problema(ex, HttpStatus.FORBIDDEN, "Acesso negado", "Você não tem permissão para este recurso.", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleNaoAutenticado(AuthenticationException ex, WebRequest request) {
        return problema(ex, HttpStatus.UNAUTHORIZED, "Não autenticado", "Autenticação necessária.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleInesperado(Exception ex, WebRequest request) {
        log.error("Erro não tratado", ex);
        return problema(ex, HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado. Informe o requestId ao suporte.", request);
    }

    // ---- erros do Spring MVC, com mensagens em português ----

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        pd.setTitle("Requisição inválida");
        List<Map<String, String>> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of(
                        "campo", f.getField(),
                        "mensagem", Objects.requireNonNullElse(f.getDefaultMessage(), "valor inválido")))
                .toList();
        pd.setProperty("erros", erros);
        return handleExceptionInternal(ex, pd, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Corpo da requisição ausente ou malformado.");
        pd.setTitle("Requisição inválida");
        return handleExceptionInternal(ex, pd, headers, status, request);
    }

    /** Ponto único por onde passam todas as respostas: acrescenta o requestId. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> resposta = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (resposta != null && resposta.getBody() instanceof ProblemDetail pd) {
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                pd.setProperty("requestId", requestId);
            }
        }
        return resposta;
    }

    private ResponseEntity<Object> problema(Exception ex, HttpStatus status, String titulo, String detalhe,
            WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalhe);
        pd.setTitle(titulo);
        return handleExceptionInternal(ex, pd, new HttpHeaders(), status, request);
    }
}
