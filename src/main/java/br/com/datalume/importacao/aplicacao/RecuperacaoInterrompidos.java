package br.com.datalume.importacao.aplicacao;

import br.com.datalume.importacao.ImportacaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class RecuperacaoInterrompidos {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoInterrompidos.class);

    private final ImportacaoProperties props;
    private final RepositorioImportacao repo;

    RecuperacaoInterrompidos(ImportacaoProperties props, RepositorioImportacao repo) {
        this.props = props;
        this.repo = repo;
    }

    @EventListener(ApplicationReadyEvent.class)
    void aoIniciar() {
        if (!props.recuperarInterrompidos()) {
            return;
        }
        int n = repo.recuperarInterrompidos();
        if (n > 0) {
            log.warn("{} importação(ões) interrompida(s) marcada(s) como FALHOU; use /retomar para continuar.", n);
        }
    }
}