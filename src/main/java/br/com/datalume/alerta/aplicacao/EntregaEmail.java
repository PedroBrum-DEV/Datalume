package br.com.datalume.alerta.aplicacao;

import br.com.datalume.alerta.AlertaProperties;
import br.com.datalume.alerta.aplicacao.EnviadorEmail.MensagemEmail;
import br.com.datalume.alerta.dominio.RepositorioAlerta;
import br.com.datalume.alerta.dominio.RepositorioAlerta.EnvioPendente;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entrega de UM envio, em transação própria (a trava do envio vale até o fim). */
@Service
public class EntregaEmail {

    private static final Logger log = LoggerFactory.getLogger(EntregaEmail.class);

    private final RepositorioAlerta repo;
    private final EnviadorEmail enviador;
    private final TokenCancelamento tokens;
    private final AlertaProperties props;
    private final MeterRegistry meters;

    EntregaEmail(RepositorioAlerta repo, EnviadorEmail enviador, TokenCancelamento tokens, AlertaProperties props,
            MeterRegistry meters) {
        this.repo = repo;
        this.enviador = enviador;
        this.tokens = tokens;
        this.props = props;
        this.meters = meters;
    }

    @Transactional
    public void entregar(long envioId) {
        Optional<EnvioPendente> achado = repo.travarEnvio(envioId);
        if (achado.isEmpty()) {
            return;
        }
        EnvioPendente envio = achado.get();
        if (!envio.ativo() || !repo.emailAtivo(envio.usuarioId())) {
            repo.marcarCancelado(envio.id());
            return;
        }
        String base = props.urlBase().replaceAll("/+$", "");
        String linkCancelamento = base + "/api/alertas/cancelar?u=" + envio.usuarioId() + "&t="
                + tokens.gerar(envio.usuarioId());
        List<RepositorioAlerta.ResultadoEmail> resultados = repo.resultadosParaEmail(envio.contaId(), envio.referencia());
        if (resultados.isEmpty()) {
            repo.marcarCancelado(envio.id());
            return;
        }
        List<RepositorioAlerta.ItemEmail> itens = repo.itensParaEmail(envio.contaId(), envio.referencia(),
                props.maxItensEmail());
        ModeloEmail.Conteudo c = ModeloEmail.montar(envio.nome(), envio.referencia(), resultados, itens,
                props.maxItensEmail(), base + "/api/alertas", linkCancelamento);
        try {
            enviador.enviar(new MensagemEmail(envio.email(), c.assunto(), c.texto(), c.html(), linkCancelamento));
            repo.marcarEnviado(envio.id());
            meters.counter("datalume.alerta.emails", "status", "enviado").increment();
            log.info("E-mail de alerta enviado: envioId={} usuarioId={}", envio.id(), envio.usuarioId());
        } catch (RuntimeException e) {
            int tentativa = envio.tentativas() + 1;
            boolean definitiva = tentativa >= props.tentativasEnvio();
            int minutos = (int) Math.min(720, 5L * (1L << Math.min(tentativa - 1, 7)));
            String msg = e.getClass().getSimpleName();
            repo.registrarFalha(envio.id(), msg, definitiva, minutos);
            meters.counter("datalume.alerta.emails", "status", "falha").increment();
            log.warn("Falha ao enviar e-mail de alerta: envioId={} tentativa={} definitiva={} ({})", envio.id(),
                    tentativa, definitiva, msg);
        }
    }
}