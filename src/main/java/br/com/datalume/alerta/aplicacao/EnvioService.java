package br.com.datalume.alerta.aplicacao;

import br.com.datalume.alerta.AlertaProperties;
import br.com.datalume.alerta.dominio.RepositorioAlerta;
import java.util.List;
import org.springframework.stereotype.Service;

/** Processa a fila de e-mails pendentes (um a um, cada um em transação própria). */
@Service
public class EnvioService {

    private static final int LOTE = 50;
    private static final int MAX_RODADAS = 20;

    private final RepositorioAlerta repo;
    private final EntregaEmail entrega;
    private final AlertaProperties props;

    EnvioService(RepositorioAlerta repo, EntregaEmail entrega, AlertaProperties props) {
        this.repo = repo;
        this.entrega = entrega;
        this.props = props;
    }

    /**
     * Entrega os envios vencidos. Cada envio processado sai da fila (enviado, cancelado ou reagendado),
     * então o laço termina; MAX_RODADAS é só uma trava de segurança. Sem efeito se o e-mail estiver desligado.
     *
     * @return quantos envios foram tratados
     */
    public int processarPendentes() {
        if (!props.emailHabilitado()) {
            return 0;
        }
        int total = 0;
        for (int rodada = 0; rodada < MAX_RODADAS; rodada++) {
            List<Long> ids = repo.idsPendentes(LOTE);
            if (ids.isEmpty()) {
                break;
            }
            for (long id : ids) {
                entrega.entregar(id);
            }
            total += ids.size();
        }
        return total;
    }
}