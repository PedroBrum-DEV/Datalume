package br.com.datalume.busca.aplicacao;

import br.com.datalume.estabelecimento.CriteriosBusca;
import br.com.datalume.estabelecimento.EstabelecimentoPesquisa;
import br.com.datalume.estabelecimento.Facetas;
import br.com.datalume.estabelecimento.PaginaEstabelecimentos;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Executa pesquisas com métricas (tempo e volume). O log registra só contagens e duração:
 * os critérios (ex.: trecho de nome) não vão para o log.
 */
@Service
public class BuscaService {

    private static final Logger log = LoggerFactory.getLogger(BuscaService.class);

    private final EstabelecimentoPesquisa pesquisa;
    private final MeterRegistry meters;

    public BuscaService(EstabelecimentoPesquisa pesquisa, MeterRegistry meters) {
        this.pesquisa = pesquisa;
        this.meters = meters;
    }

    public PaginaEstabelecimentos pesquisar(CriteriosBusca criterios, int limite, String cursor, boolean contatos) {
        Timer.Sample amostra = Timer.start(meters);
        try {
            PaginaEstabelecimentos pagina = pesquisa.pesquisar(criterios, limite, cursor, contatos);
            meters.counter("datalume.busca.resultados", "tipo", "pesquisa").increment(pagina.itens().size());
            log.debug("Busca: {} itens, primeira página={}", pagina.itens().size(), cursor == null);
            return pagina;
        } finally {
            amostra.stop(meters.timer("datalume.busca.duracao", "tipo", "pesquisa"));
        }
    }

    public Facetas facetas(CriteriosBusca criterios) {
        Timer.Sample amostra = Timer.start(meters);
        try {
            return pesquisa.facetas(criterios);
        } finally {
            amostra.stop(meters.timer("datalume.busca.duracao", "tipo", "facetas"));
        }
    }
}