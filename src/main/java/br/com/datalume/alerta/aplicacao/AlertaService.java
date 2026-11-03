package br.com.datalume.alerta.aplicacao;

import br.com.datalume.alerta.dominio.RepositorioAlerta;
import br.com.datalume.alerta.dominio.RepositorioAlerta.AlertaResumo;
import br.com.datalume.alerta.dominio.RepositorioAlerta.ItemAlerta;
import br.com.datalume.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.datalume.compartilhado.erro.RequisicaoInvalidaException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Feed de alertas da conta, com estado de leitura por usuário, e preferências de e-mail. */
@Service
public class AlertaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class);
    private static final int ITENS_NO_DETALHE = 1000;

    public record AlertaDetalhe(AlertaResumo alerta, List<ItemAlerta> itens) {
    }

    private final RepositorioAlerta repo;
    private final TokenCancelamento tokens;

    public AlertaService(RepositorioAlerta repo, TokenCancelamento tokens) {
        this.repo = repo;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public List<AlertaResumo> listar(UUID contaId, UUID usuarioId, boolean apenasNaoLidos, Long antesDe, int limite) {
        return repo.listar(contaId, usuarioId, apenasNaoLidos, antesDe, Math.min(Math.max(limite, 1), 100));
    }

    @Transactional(readOnly = true)
    public AlertaDetalhe detalhar(UUID contaId, UUID usuarioId, long id) {
        AlertaResumo alerta = exigir(contaId, usuarioId, id);
        return new AlertaDetalhe(alerta, repo.itens(id, ITENS_NO_DETALHE));
    }

    @Transactional(readOnly = true)
    public long contarNaoLidos(UUID contaId, UUID usuarioId) {
        return repo.contarNaoLidos(contaId, usuarioId);
    }

    @Transactional
    public void marcarLido(UUID contaId, UUID usuarioId, long id) {
        exigir(contaId, usuarioId, id);
        repo.marcarLido(contaId, usuarioId, id);
    }

    @Transactional
    public int marcarTodosLidos(UUID contaId, UUID usuarioId) {
        return repo.marcarTodosLidos(contaId, usuarioId);
    }

    @Transactional(readOnly = true)
    public boolean emailAtivo(UUID usuarioId) {
        return repo.emailAtivo(usuarioId);
    }

    @Transactional
    public void definirEmailAtivo(UUID usuarioId, boolean ativo) {
        repo.definirEmailAtivo(usuarioId, ativo);
    }

    /** Cancelamento pelo link do e-mail (sem login): confere a assinatura e desliga os e-mails do usuário. */
    @Transactional
    public void cancelarPorLink(UUID usuarioId, String token) {
        if (!tokens.valido(usuarioId, token)) {
            throw new RequisicaoInvalidaException("Link de cancelamento inválido.");
        }
        repo.definirEmailAtivo(usuarioId, false);
        log.info("E-mails de alerta cancelados pelo link: usuarioId={}", usuarioId);
    }

    private AlertaResumo exigir(UUID contaId, UUID usuarioId, long id) {
        return repo.buscar(contaId, usuarioId, id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alerta não encontrado."));
    }
}