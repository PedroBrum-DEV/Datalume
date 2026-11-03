package br.com.datalume.estabelecimento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Limpeza diária dos eventos mais antigos que a retenção (padrão: 24 meses). */
@Component
class RetencaoEventos {

    private static final Logger log = LoggerFactory.getLogger(RetencaoEventos.class);

    private final EstabelecimentoMudancas mudancas;
    private final int meses;

    RetencaoEventos(EstabelecimentoMudancas mudancas,
            @Value("${datalume.estabelecimento.retencao-eventos-meses:24}") int meses) {
        this.mudancas = mudancas;
        this.meses = meses;
    }

    @Scheduled(cron = "0 30 4 * * *")
    void limpar() {
        int n = mudancas.removerMaisAntigosQue(meses);
        if (n > 0) {
            log.info("Retenção: {} eventos com mais de {} meses removidos", n, meses);
        }
    }
}