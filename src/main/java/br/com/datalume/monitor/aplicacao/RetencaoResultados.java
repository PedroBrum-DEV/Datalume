package br.com.datalume.monitor.aplicacao;

import br.com.datalume.monitor.dominio.RepositorioMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Limpeza diária dos resultados de monitoramento mais antigos que a retenção (padrão: 24 meses). */
@Component
class RetencaoResultados {

    private static final Logger log = LoggerFactory.getLogger(RetencaoResultados.class);

    private final RepositorioMonitor repo;
    private final int meses;

    RetencaoResultados(RepositorioMonitor repo, @Value("${datalume.monitor.retencao-meses:24}") int meses) {
        this.repo = repo;
        this.meses = meses;
    }

    @Scheduled(cron = "0 45 4 * * *")
    void limpar() {
        int n = repo.removerResultadosMaisAntigosQue(meses);
        if (n > 0) {
            log.info("Retenção: {} resultados de monitoramento com mais de {} meses removidos", n, meses);
        }
    }
}