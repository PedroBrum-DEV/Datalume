package br.com.datalume.alerta.web;

import br.com.datalume.alerta.aplicacao.AlertaService;
import br.com.datalume.alerta.aplicacao.AlertaService.AlertaDetalhe;
import br.com.datalume.alerta.dominio.RepositorioAlerta.AlertaResumo;
import br.com.datalume.compartilhado.erro.RequisicaoInvalidaException;
import br.com.datalume.conta.ContaAtual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Feed de alertas da conta (por usuário: lido/não lido), preferências e cancelamento por link. */
@RestController
@RequestMapping("/api/alertas")
class AlertaController {

    private final AlertaService alertas;

    AlertaController(AlertaService alertas) {
        this.alertas = alertas;
    }

    /** Mais recentes primeiro. Para a próxima página, passe antesDe = id do último item recebido. */
    @GetMapping
    List<AlertaResumo> listar(@RequestParam(defaultValue = "false") boolean apenasNaoLidos,
            @RequestParam(required = false) Long antesDe, @RequestParam(defaultValue = "20") int limite) {
        return alertas.listar(conta(), usuario(), apenasNaoLidos, antesDe, limite);
    }

    @GetMapping("/contagem")
    Map<String, Long> contagem() {
        return Map.of("naoLidos", alertas.contarNaoLidos(conta(), usuario()));
    }

    @GetMapping("/{id}")
    AlertaDetalhe detalhar(@PathVariable long id) {
        return alertas.detalhar(conta(), usuario(), id);
    }

    @PostMapping("/{id}/lido")
    ResponseEntity<Void> marcarLido(@PathVariable long id) {
        alertas.marcarLido(conta(), usuario(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lidos")
    Map<String, Integer> marcarTodosLidos() {
        return Map.of("marcados", alertas.marcarTodosLidos(conta(), usuario()));
    }

    @GetMapping("/preferencias")
    PreferenciasDto preferencias() {
        return new PreferenciasDto(alertas.emailAtivo(usuario()));
    }

    @PutMapping("/preferencias")
    PreferenciasDto atualizarPreferencias(@Valid @RequestBody PreferenciasDto req) {
        alertas.definirEmailAtivo(usuario(), req.emailAtivo());
        return new PreferenciasDto(req.emailAtivo());
    }

    /** Link do e-mail (público, assinado). Atenção: alguns antivírus abrem links; o cancelamento é reversível em PUT /preferencias. */
    @GetMapping(value = "/cancelar", produces = "text/html;charset=UTF-8")
    String cancelar(@RequestParam("u") String usuario, @RequestParam("t") String token) {
        UUID id;
        try {
            id = UUID.fromString(usuario);
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("Link de cancelamento inválido.");
        }
        alertas.cancelarPorLink(id, token);
        return "<!doctype html><meta charset=\"utf-8\"><title>Datalume</title>"
                + "<p style=\"font-family:Arial,sans-serif\">Pronto: você não receberá mais os resumos por e-mail do Datalume. "
                + "Os alertas continuam disponíveis no painel.</p>";
    }

    private static UUID conta() {
        return ContaAtual.contaId().orElseThrow();
    }

    private static UUID usuario() {
        return ContaAtual.usuarioId().orElseThrow();
    }

    record PreferenciasDto(@NotNull(message = "Informe emailAtivo.") Boolean emailAtivo) {
    }
}