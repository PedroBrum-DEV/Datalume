package br.com.datalume.importacao.aplicacao;

import br.com.datalume.compartilhado.erro.ConflitoException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Verificação periódica (cron em datalume.importacao.agendamento.cron): se saiu uma referência nova na
 * Receita, importa; se a última tentativa dela falhou, retoma.
 */
@Component
@ConditionalOnProperty(name = "datalume.importacao.agendamento.habilitado", havingValue = "true")
class ImportacaoAgendada {

    private static final Logger log = LoggerFactory.getLogger(ImportacaoAgendada.class);

    private final ImportacaoService service;

    ImportacaoAgendada(ImportacaoService service) {
        this.service = service;
    }

    @Scheduled(cron = "${datalume.importacao.agendamento.cron}")
    void verificar() {
        try {
            Optional<String> referencia = service.referenciaMaisRecente();
            if (referencia.isEmpty()) {
                log.warn("Importação agendada: nenhuma referência encontrada na Receita.");
                return;
            }
            service.agendarSeNecessario(referencia.get());
        } catch (ConflitoException e) {
            log.info("Importação agendada: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.error("Importação agendada falhou ao iniciar", e);
        }
    }
}