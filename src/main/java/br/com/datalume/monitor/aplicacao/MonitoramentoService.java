package br.com.datalume.monitor.aplicacao;

import br.com.datalume.busca.NichoConsulta;
import br.com.datalume.busca.NichoDefinicao;
import br.com.datalume.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.datalume.compartilhado.erro.RegraDeNegocioException;
import br.com.datalume.conta.ContaConsulta;
import br.com.datalume.estabelecimento.CriteriosBusca;
import br.com.datalume.estabelecimento.EstabelecimentoMudancas;
import br.com.datalume.estabelecimento.EstabelecimentoMudancas.ResultadoMudancas;
import br.com.datalume.estabelecimento.TipoMudanca;
import br.com.datalume.monitor.dominio.RepositorioMonitor;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoramentoService {

    private final RepositorioMonitor repo;
    private final NichoConsulta nichos;
    private final ContaConsulta contas;
    private final EstabelecimentoMudancas mudancas;

    public MonitoramentoService(RepositorioMonitor repo, NichoConsulta nichos, ContaConsulta contas,
            EstabelecimentoMudancas mudancas) {
        this.repo = repo;
        this.nichos = nichos;
        this.contas = contas;
        this.mudancas = mudancas;
    }

    /** Define (substitui) os tipos monitorados do nicho. Lista vazia desliga o monitoramento. */
    @Transactional
    public List<TipoMudanca> definir(UUID contaId, UUID nichoId, Collection<TipoMudanca> tipos) {
        exigirNicho(contaId, nichoId);
        Set<TipoMudanca> novos = tipos == null || tipos.isEmpty()
                ? EnumSet.noneOf(TipoMudanca.class) : EnumSet.copyOf(tipos);
        int limite = contas.limitesDaConta(contaId).limiteAlertas();
        long outras = repo.contarRegrasDaConta(contaId) - repo.tiposDoNicho(contaId, nichoId).size();
        if (outras + novos.size() > limite) {
            throw new RegraDeNegocioException("Limite de alertas do plano atingido (" + limite
                    + " regras; cada tipo de evento em cada nicho conta uma).");
        }
        repo.substituirRegras(contaId, nichoId, novos);
        return repo.tiposDoNicho(contaId, nichoId);
    }

    @Transactional(readOnly = true)
    public List<TipoMudanca> obter(UUID contaId, UUID nichoId) {
        exigirNicho(contaId, nichoId);
        return repo.tiposDoNicho(contaId, nichoId);
    }

    @Transactional
    public void remover(UUID contaId, UUID nichoId) {
        exigirNicho(contaId, nichoId);
        repo.removerRegras(contaId, nichoId);
    }

    /** Consulta ao vivo do que mudou no nicho. Sem referência, usa a mais recente com mudanças. */
    @Transactional(readOnly = true)
    public MudancasDoNicho consultar(UUID contaId, UUID nichoId, String referencia, Set<TipoMudanca> tipos,
            int limite) {
        NichoDefinicao nicho = exigirNicho(contaId, nichoId);
        String ref = referencia != null ? referencia : mudancas.referenciaMaisRecente().orElse(null);
        if (ref == null) {
            return new MudancasDoNicho(null, 0, new EnumMap<>(TipoMudanca.class), List.of());
        }
        Set<TipoMudanca> filtro = tipos == null || tipos.isEmpty() ? EnumSet.allOf(TipoMudanca.class) : tipos;
        ResultadoMudancas r = mudancas.consultar(ref, filtro, criteriosDe(nicho), Math.min(Math.max(limite, 1), 200));
        List<MudancasDoNicho.Item> itens = r.itens().stream().map(m -> {
            DescricaoMudanca.Direcao d = DescricaoMudanca.direcao(m);
            return new MudancasDoNicho.Item(m.id(), m.tipo(), d.name(), m.cnpj(), m.nome(),
                    DescricaoMudanca.descrever(m, d));
        }).toList();
        return new MudancasDoNicho(ref, r.total(), r.porTipo(), itens);
    }

    /** Critérios de recorte do nicho para casar com eventos (situação irrelevante). */
    static CriteriosBusca criteriosDe(NichoDefinicao n) {
        return new CriteriosBusca(n.cnaes(), false, n.ufs(), n.municipios(), List.of(), null, null, null, null,
                false, false, null);
    }

    private NichoDefinicao exigirNicho(UUID contaId, UUID nichoId) {
        return nichos.obterDaConta(contaId, nichoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nicho não encontrado."));
    }
}