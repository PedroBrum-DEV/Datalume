package br.com.datalume.busca;

import br.com.datalume.busca.dominio.RepositorioNicho;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pública: leitura de nichos por outros módulos (ex.: monitor). Sempre dentro de uma conta. */
@Service
public class NichoConsulta {

    private final RepositorioNicho repo;

    public NichoConsulta(RepositorioNicho repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Optional<NichoDefinicao> obterDaConta(UUID contaId, UUID nichoId) {
        return repo.buscar(contaId, nichoId).map(n -> new NichoDefinicao(n.id(), n.contaId(), n.nome(), n.cnaes(),
                n.incluirSecundarios(), n.ufs(), n.municipios()));
    }
}