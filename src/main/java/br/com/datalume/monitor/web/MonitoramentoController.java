package br.com.datalume.monitor.web;

import br.com.datalume.conta.ContaAtual;
import br.com.datalume.estabelecimento.TipoMudanca;
import br.com.datalume.monitor.aplicacao.MonitoramentoService;
import br.com.datalume.monitor.aplicacao.MudancasDoNicho;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nichos/{nichoId}")
class MonitoramentoController {

    private final MonitoramentoService servico;

    MonitoramentoController(MonitoramentoService servico) {
        this.servico = servico;
    }

    /** Define os tipos de evento monitorados (substitui os atuais). Lista vazia desliga o monitoramento. */
    @PutMapping("/monitoramento")
    MonitoramentoResponse definir(@PathVariable UUID nichoId, @Valid @RequestBody MonitoramentoRequest req) {
        return new MonitoramentoResponse(nichoId, servico.definir(conta(), nichoId, req.tipos()));
    }

    @GetMapping("/monitoramento")
    MonitoramentoResponse obter(@PathVariable UUID nichoId) {
        return new MonitoramentoResponse(nichoId, servico.obter(conta(), nichoId));
    }

    @DeleteMapping("/monitoramento")
    ResponseEntity<Void> remover(@PathVariable UUID nichoId) {
        servico.remover(conta(), nichoId);
        return ResponseEntity.noContent().build();
    }

    /** O que mudou no nicho numa referência (padrão: a mais recente), sem exigir monitoramento ativo. */
    @GetMapping("/mudancas")
    MudancasDoNicho mudancas(@PathVariable UUID nichoId, @RequestParam(required = false) String referencia,
            @RequestParam(required = false) List<TipoMudanca> tipo, @RequestParam(defaultValue = "50") int limite) {
        return servico.consultar(conta(), nichoId, referencia,
                tipo == null || tipo.isEmpty() ? null : EnumSet.copyOf(tipo), limite);
    }

    private static UUID conta() {
        return ContaAtual.contaId().orElseThrow();
    }

    record MonitoramentoRequest(@NotNull(message = "Informe a lista de tipos.")
            @Size(max = 5, message = "No máximo 5 tipos.") List<TipoMudanca> tipos) {
    }

    record MonitoramentoResponse(UUID nichoId, List<TipoMudanca> tipos) {
    }
}