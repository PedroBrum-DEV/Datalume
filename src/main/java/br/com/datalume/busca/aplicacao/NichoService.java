package br.com.datalume.busca.aplicacao;

import br.com.datalume.busca.dominio.Nicho;
import br.com.datalume.busca.dominio.RepositorioNicho;
import br.com.datalume.compartilhado.erro.ConflitoException;
import br.com.datalume.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.datalume.compartilhado.erro.RegraDeNegocioException;
import br.com.datalume.compartilhado.erro.RequisicaoInvalidaException;
import br.com.datalume.conta.ContaConsulta;
import br.com.datalume.estabelecimento.CriteriosBusca;
import br.com.datalume.referencia.ReferenciaCatalogo;
import br.com.datalume.referencia.ReferenciaConsulta;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NichoService {

    private static final Logger log = LoggerFactory.getLogger(NichoService.class);
    private static final int MAX_CNAES = 50;
    private static final int MAX_MUNICIPIOS = 200;

    private final RepositorioNicho repo;
    private final ContaConsulta contas;
    private final ReferenciaCatalogo catalogo;
    private final ReferenciaConsulta referencia;

    public NichoService(RepositorioNicho repo, ContaConsulta contas, ReferenciaCatalogo catalogo,
            ReferenciaConsulta referencia) {
        this.repo = repo;
        this.contas = contas;
        this.catalogo = catalogo;
        this.referencia = referencia;
    }

    @Transactional
    public Nicho criar(UUID contaId, String nome, List<String> cnaes, boolean incluirSecundarios,
            List<String> ufs, List<Integer> municipios) {
        CriteriosBusca c = validar(cnaes, incluirSecundarios, ufs, municipios);
        int limite = contas.limitesDaConta(contaId).limiteNichos();
        if (repo.contar(contaId) >= limite) {
            throw new RegraDeNegocioException("Limite de nichos do plano atingido (" + limite
                    + "). Remova um nicho ou mude de plano.");
        }
        try {
            Nicho nicho = repo.inserir(contaId, nome.trim(), c);
            log.info("Nicho criado: contaId={} nichoId={}", contaId, nicho.id());
            return nicho;
        } catch (DuplicateKeyException e) {
            throw new ConflitoException("Já existe um nicho com este nome.");
        }
    }

    @Transactional
    public Nicho atualizar(UUID contaId, UUID id, String nome, List<String> cnaes, boolean incluirSecundarios,
            List<String> ufs, List<Integer> municipios) {
        CriteriosBusca c = validar(cnaes, incluirSecundarios, ufs, municipios);
        try {
            if (!repo.atualizar(contaId, id, nome.trim(), c)) {
                throw new RecursoNaoEncontradoException("Nicho não encontrado.");
            }
        } catch (DuplicateKeyException e) {
            throw new ConflitoException("Já existe um nicho com este nome.");
        }
        return obter(contaId, id);
    }

    @Transactional(readOnly = true)
    public Nicho obter(UUID contaId, UUID id) {
        return repo.buscar(contaId, id).orElseThrow(() -> new RecursoNaoEncontradoException("Nicho não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<Nicho> listar(UUID contaId) {
        return repo.listar(contaId);
    }

    @Transactional
    public void remover(UUID contaId, UUID id) {
        if (!repo.remover(contaId, id)) {
            throw new RecursoNaoEncontradoException("Nicho não encontrado.");
        }
    }

    /** Normaliza (via CriteriosBusca) e confere CNAEs, UFs e municípios contra a Referência. */
    private CriteriosBusca validar(List<String> cnaes, boolean incluirSecundarios, List<String> ufs,
            List<Integer> municipios) {
        CriteriosBusca c = new CriteriosBusca(cnaes, incluirSecundarios, ufs, municipios,
                null, null, null, null, null, false, false, null);
        if (c.cnaes().isEmpty()) {
            throw new RequisicaoInvalidaException("Informe ao menos um CNAE.");
        }
        if (c.cnaes().size() > MAX_CNAES) {
            throw new RequisicaoInvalidaException("No máximo " + MAX_CNAES + " CNAEs por nicho.");
        }
        if (c.municipios().size() > MAX_MUNICIPIOS) {
            throw new RequisicaoInvalidaException("No máximo " + MAX_MUNICIPIOS + " municípios por nicho.");
        }
        for (String uf : c.ufs()) {
            if (referencia.buscarUf(uf).isEmpty()) {
                throw new RequisicaoInvalidaException("UF desconhecida: " + uf);
            }
        }
        if (catalogo.cnaesCarregados()) {
            List<String> desconhecidos = catalogo.prefixosSemCorrespondencia(c.cnaes());
            if (!desconhecidos.isEmpty()) {
                throw new RequisicaoInvalidaException("CNAE desconhecido: " + String.join(", ", desconhecidos));
            }
        }
        if (!c.municipios().isEmpty() && catalogo.municipiosCarregados()) {
            Set<Integer> existentes = catalogo.municipiosExistentes(c.municipios());
            List<Integer> faltando = c.municipios().stream().filter(m -> !existentes.contains(m)).toList();
            if (!faltando.isEmpty()) {
                throw new RequisicaoInvalidaException("Município desconhecido: "
                        + faltando.stream().map(String::valueOf).collect(Collectors.joining(", ")));
            }
        }
        return c;
    }
}