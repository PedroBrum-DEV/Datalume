package br.com.datalume.alerta.aplicacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class EnvioAgendado {

    private static final Logger log = LoggerFactory.getLogger(EnvioAgendado.class);

    private final EnvioService envios;

    EnvioAgendado(EnvioService envios) {
        this.envios = envios;
    }

    @Scheduled(fixedDelayString = "${datalume.alerta.intervalo-envio-ms:60000}",
            initialDelayString = "${datalume.alerta.intervalo-envio-ms:60000}")
    void processar() {
        try {
            envios.processarPendentes();
        } catch (RuntimeException e) {
            log.error("Falha ao processar a fila de e-mails de alerta", e);
        }
    }
}