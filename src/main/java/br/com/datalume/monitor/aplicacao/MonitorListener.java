package br.com.datalume.monitor.aplicacao;

import br.com.datalume.compartilhado.evento.ImportacaoConcluida;
import br.com.datalume.monitor.dominio.RepositorioMonitor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Ao concluir uma importação, registra a referência como pendente e processa em segundo plano. */
@Component
class MonitorListener {

    private final RepositorioMonitor repo;
    private final MonitorExecutor executor;
    private final ProcessadorMonitor processador;

    MonitorListener(RepositorioMonitor repo, MonitorExecutor executor, ProcessadorMonitor processador) {
        this.repo = repo;
        this.executor = executor;
        this.processador = processador;
    }

    @EventListener
    void aoConcluirImportacao(ImportacaoConcluida evento) {
        repo.registrarPendente(evento.referencia()); // sobrevive a um reinício antes do processamento
        executor.submeter(() -> processador.processar(evento.referencia(), false));
    }
}