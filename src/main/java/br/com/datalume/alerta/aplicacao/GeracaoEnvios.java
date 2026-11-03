package br.com.datalume.alerta.aplicacao;

import br.com.datalume.alerta.dominio.RepositorioAlerta;
import br.com.datalume.monitor.AlertasGeradas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Quando o monitor gera resultados, enfileira um resumo por usuário (único por usuário e referência). */
@Component
class GeracaoEnvios {

    private static final Logger log = LoggerFactory.getLogger(GeracaoEnvios.class);

    private final RepositorioAlerta repo;

    GeracaoEnvios(RepositorioAlerta repo) {
        this.repo = repo;
    }

    @EventListener
    void aoGerarAlertas(AlertasGeradas evento) {
        int criados = repo.criarEnvios(evento.referencia());
        log.info("Alertas: {} envios enfileirados para a referência {}", criados, evento.referencia());
    }
}