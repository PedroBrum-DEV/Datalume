package br.com.datalume.referencia.carga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Se datalume.referencia.carga-inicial=true, carrega municípios (IBGE) e CNAEs quando as tabelas
 * estiverem vazias. Falhas de rede não derrubam a aplicação: use os endpoints administrativos depois.
 */
@Component
@ConditionalOnProperty(name = "datalume.referencia.carga-inicial", havingValue = "true")
class CargaInicialRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CargaInicialRunner.class);

    private final CargaReferenciaService carga;

    CargaInicialRunner(CargaReferenciaService carga) {
        this.carga = carga;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (carga.tabelaVazia("municipio")) {
                carga.carregarMunicipiosIbge();
            }
            if (carga.tabelaVazia("cnae_subclasse")) {
                carga.carregarCnaesIbge();
            }
        } catch (RuntimeException e) {
            log.error("Carga inicial de referência falhou; repita via POST /api/admin/referencia/carga/*", e);
        }
    }
}
