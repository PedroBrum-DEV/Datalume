package br.com.datalume.monitor.aplicacao;

import br.com.datalume.monitor.dominio.RepositorioMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retoma referências pendentes, com falha ou travadas (ex.: a aplicação caiu no meio do processamento). */
@Component
class MonitorAgendado {

    private static final Logger log = LoggerFactory.getLogger(MonitorAgendado.class);

    private final RepositorioMonitor repo;
    private final MonitorExecutor executor;
    private final ProcessadorMonitor processador;

    MonitorAgendado(RepositorioMonitor repo, MonitorExecutor executor, ProcessadorMonitor processador) {
        this.repo = repo;
        this.executor = executor;
        this.processador = processador;
    }

    @Scheduled(fixedDelayString = "${datalume.monitor.intervalo-pendencias-ms:600000}",
            initialDelayString = "${datalume.monitor.intervalo-pendencias-ms:600000}")
    void retomarPendencias() {
        for (String referencia : repo.referenciasPendentes()) {
            log.info("Monitor: retomando a referência {}", referencia);
            executor.submeter(() -> processador.processar(referencia, false));
        }
    }
}