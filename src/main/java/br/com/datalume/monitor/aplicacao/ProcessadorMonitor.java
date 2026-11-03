package br.com.datalume.monitor.aplicacao;

import br.com.datalume.busca.NichoConsulta;
import br.com.datalume.busca.NichoDefinicao;
import br.com.datalume.estabelecimento.EstabelecimentoMudancas;
import br.com.datalume.estabelecimento.EstabelecimentoMudancas.ResultadoMudancas;
import br.com.datalume.estabelecimento.TipoMudanca;
import br.com.datalume.monitor.AlertasGeradas;
import br.com.datalume.monitor.dominio.RepositorioMonitor;
import br.com.datalume.monitor.dominio.RepositorioMonitor.ItemResultado;
import br.com.datalume.monitor.dominio.RepositorioMonitor.RegrasDoNicho;
import br.com.datalume.monitor.dominio.RepositorioMonitor.TotaisResultado;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Casa as mudanças de uma referência com as regras de monitoramento e grava um resultado por nicho.
 * Idempotente: repetir a mesma referência regrava os mesmos resultados (UNIQUE nicho+referência) e não duplica
 * alertas nem e-mails (o envio é único por usuário e referência).
 */
@Service
public class ProcessadorMonitor {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorMonitor.class);
    /** Itens detalhados guardados por nicho e referência; os totais sempre cobrem tudo. */
    public static final int LIMITE_ITENS = 1000;

    private final RepositorioMonitor repo;
    private final NichoConsulta nichos;
    private final EstabelecimentoMudancas mudancas;
    private final ApplicationEventPublisher eventos;
    private final MeterRegistry meters;

    public ProcessadorMonitor(RepositorioMonitor repo, NichoConsulta nichos, EstabelecimentoMudancas mudancas,
            ApplicationEventPublisher eventos, MeterRegistry meters) {
        this.repo = repo;
        this.nichos = nichos;
        this.mudancas = mudancas;
        this.eventos = eventos;
        this.meters = meters;
    }

    /** @param forcar reprocessa mesmo que a referência já tenha sido concluída */
    public void processar(String referencia, boolean forcar) {
        repo.registrarPendente(referencia);
        if (!repo.reivindicar(referencia, forcar)) {
            log.info("Monitor: referência {} já processada ou em andamento", referencia);
            return;
        }
        try {
            int nichosProcessados = 0;
            int alertas = 0;
            for (RegrasDoNicho regras : repo.regrasAgrupadas()) {
                Optional<NichoDefinicao> nicho = nichos.obterDaConta(regras.contaId(), regras.nichoId());
                if (nicho.isEmpty()) {
                    continue;
                }
                nichosProcessados++;
                ResultadoMudancas r = mudancas.consultar(referencia, regras.tipos(),
                        MonitoramentoService.criteriosDe(nicho.get()), LIMITE_ITENS);
                if (r.total() == 0) {
                    repo.removerResultado(regras.nichoId(), referencia);
                    continue;
                }
                List<ItemResultado> itens = r.itens().stream().map(m -> {
                    DescricaoMudanca.Direcao d = DescricaoMudanca.direcao(m);
                    return new ItemResultado(m.id(), m.tipo().name(), d.name(), m.cnpj(), m.nome(),
                            DescricaoMudanca.descrever(m, d));
                }).toList();
                TotaisResultado totais = new TotaisResultado(r.total(), qtd(r, TipoMudanca.ABERTURA),
                        qtd(r, TipoMudanca.BAIXA), qtd(r, TipoMudanca.SITUACAO), qtd(r, TipoMudanca.MUDANCA_ENDERECO),
                        qtd(r, TipoMudanca.MUDANCA_CNAE), r.total() > itens.size());
                repo.substituirResultado(regras.contaId(), regras.nichoId(), nicho.get().nome(), referencia,
                        totais, itens);
                alertas++;
                meters.counter("datalume.monitor.mudancas").increment(r.total());
            }
            repo.concluir(referencia, nichosProcessados, alertas);
            meters.counter("datalume.monitor.alertas").increment(alertas);
            log.info("Monitor: referência {} processada: {} nichos, {} alertas", referencia, nichosProcessados, alertas);
            if (alertas > 0) {
                eventos.publishEvent(new AlertasGeradas(referencia));
            }
        } catch (RuntimeException e) {
            log.error("Monitor: falha ao processar a referência {}", referencia, e);
            String msg = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            repo.falhar(referencia, msg.length() > 1000 ? msg.substring(0, 1000) : msg);
        }
    }

    private static long qtd(ResultadoMudancas r, TipoMudanca tipo) {
        return r.porTipo().getOrDefault(tipo, 0L);
    }
}