package br.com.datalume.conta;

import br.com.datalume.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.datalume.conta.dominio.Conta;
import br.com.datalume.conta.dominio.ContaRepository;
import br.com.datalume.conta.dominio.Plano;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pública: consulta dos limites contratados por uma conta. */
@Service
public class ContaConsulta {

    private final ContaRepository contas;

    public ContaConsulta(ContaRepository contas) {
        this.contas = contas;
    }

    @Transactional(readOnly = true)
    public LimitesDoPlano limitesDaConta(UUID contaId) {
        Conta conta = contas.findById(contaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada."));
        Plano p = conta.getPlano();
        return new LimitesDoPlano(p.getCodigo(), p.getLimiteUsuarios(), p.getLimiteNichos(),
                p.getLimiteAlertas(), p.getLimiteExportacao());
    }
}