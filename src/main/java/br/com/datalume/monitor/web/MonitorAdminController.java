package br.com.datalume.monitor.web;

import br.com.datalume.compartilhado.erro.RequisicaoInvalidaException;
import br.com.datalume.monitor.aplicacao.MonitorExecutor;
import br.com.datalume.monitor.aplicacao.ProcessadorMonitor;
import br.com.datalume.monitor.dominio.RepositorioMonitor;
import br.com.datalume.monitor.dominio.RepositorioMonitor.Execucao;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints administrativos (papel ADMIN_PLATAFORMA, ver SecurityConfig). */
@RestController
@RequestMapping("/api/admin/monitor")
class MonitorAdminController {

    private static final Pattern REFERENCIA = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private final RepositorioMonitor repo;
    private final MonitorExecutor executor;
    private final ProcessadorMonitor processador;

    MonitorAdminController(RepositorioMonitor repo, MonitorExecutor executor, ProcessadorMonitor processador) {
        this.repo = repo;
        this.executor = executor;
        this.processador = processador;
    }

    @GetMapping("/execucoes")
    List<Execucao> execucoes() {
        return repo.listarExecucoes(50);
    }

    /** Reprocessa uma referência em segundo plano. {@code forcar=true} repete mesmo se já concluída. */
    @PostMapping("/processar")
    ResponseEntity<Map<String, Object>> processar(@RequestBody ProcessarRequest req) {
        if (req == null || req.referencia() == null || !REFERENCIA.matcher(req.referencia()).matches()) {
            throw new RequisicaoInvalidaException("Informe a referência no formato AAAA-MM.");
        }
        boolean forcar = Boolean.TRUE.equals(req.forcar());
        repo.registrarPendente(req.referencia());
        executor.submeter(() -> processador.processar(req.referencia(), forcar));
        return ResponseEntity.accepted().body(Map.of("referencia", req.referencia(), "forcar", forcar));
    }

    record ProcessarRequest(String referencia, Boolean forcar) {
    }
}