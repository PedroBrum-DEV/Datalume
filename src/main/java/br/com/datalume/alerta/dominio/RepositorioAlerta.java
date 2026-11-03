package br.com.datalume.alerta.dominio;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Feed, preferências e fila de e-mails. Lê as tabelas monitor_resultado/monitor_resultado_item (do módulo monitor)
 * e usuario (do módulo conta) diretamente em SQL, apenas para leitura e junção em lote.
 */
@Repository
public class RepositorioAlerta {

    public record AlertaResumo(long id, UUID nichoId, String nichoNome, String referencia, long total,
            long totalAberturas, long totalBaixas, long totalSituacao, long totalEndereco, long totalCnae,
            boolean itensTruncados, OffsetDateTime criadoEm, boolean lido) {
    }

    public record ItemAlerta(long id, String tipo, String direcao, String cnpj, String nome, String descricao) {
    }

    public record EnvioPendente(long id, UUID usuarioId, UUID contaId, String referencia, int tentativas,
            String nome, String email, boolean ativo) {
    }

    public record ResultadoEmail(String nichoNome, long total, long totalAberturas, long totalBaixas,
            long totalSituacao, long totalEndereco, long totalCnae) {
    }

    public record ItemEmail(String nichoNome, String tipo, String direcao, String cnpj, String nome,
            String descricao) {
    }

    private static final String COLUNAS_RESUMO = """
            r.id, r.nicho_id, r.nicho_nome, r.referencia, r.total, r.total_aberturas, r.total_baixas,
            r.total_situacao, r.total_endereco, r.total_cnae, r.itens_truncados, r.criado_em,
            (l.usuario_id IS NOT NULL) AS lido""";

    private final JdbcClient jdbc;

    public RepositorioAlerta(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ feed

    public List<AlertaResumo> listar(UUID contaId, UUID usuarioId, boolean apenasNaoLidos, Long antesDe, int limite) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUNAS_RESUMO + """

                 FROM monitor_resultado r
                 LEFT JOIN alerta_leitura l ON l.resultado_id = r.id AND l.usuario_id = :usuario
                 WHERE r.conta_id = :conta""");
        if (apenasNaoLidos) {
            sql.append(" AND l.usuario_id IS NULL");
        }
        if (antesDe != null) {
            sql.append(" AND r.id < :antes");
        }
        sql.append(" ORDER BY r.id DESC LIMIT :lim");
        var spec = jdbc.sql(sql.toString()).param("usuario", usuarioId).param("conta", contaId).param("lim", limite);
        if (antesDe != null) {
            spec = spec.param("antes", antesDe);
        }
        return spec.query(AlertaResumo.class).list();
    }

    public Optional<AlertaResumo> buscar(UUID contaId, UUID usuarioId, long id) {
        return jdbc.sql("SELECT " + COLUNAS_RESUMO + """

                 FROM monitor_resultado r
                 LEFT JOIN alerta_leitura l ON l.resultado_id = r.id AND l.usuario_id = :usuario
                 WHERE r.conta_id = :conta AND r.id = :id""")
                .param("usuario", usuarioId).param("conta", contaId).param("id", id)
                .query(AlertaResumo.class).optional();
    }

    public List<ItemAlerta> itens(long resultadoId, int limite) {
        return jdbc.sql("""
                        SELECT id, tipo, direcao, cnpj, nome, descricao FROM monitor_resultado_item
                        WHERE resultado_id = :r ORDER BY id LIMIT :n
                        """)
                .param("r", resultadoId).param("n", limite).query(ItemAlerta.class).list();
    }

    public long contarNaoLidos(UUID contaId, UUID usuarioId) {
        return jdbc.sql("""
                        SELECT count(*) FROM monitor_resultado r
                        WHERE r.conta_id = :conta
                          AND NOT EXISTS (SELECT 1 FROM alerta_leitura l
                                          WHERE l.resultado_id = r.id AND l.usuario_id = :usuario)
                        """)
                .param("conta", contaId).param("usuario", usuarioId).query(Long.class).single();
    }

    public void marcarLido(UUID contaId, UUID usuarioId, long resultadoId) {
        jdbc.sql("""
                        INSERT INTO alerta_leitura (resultado_id, usuario_id)
                        SELECT r.id, :usuario FROM monitor_resultado r WHERE r.id = :id AND r.conta_id = :conta
                        ON CONFLICT DO NOTHING
                        """)
                .param("usuario", usuarioId).param("id", resultadoId).param("conta", contaId).update();
    }

    public int marcarTodosLidos(UUID contaId, UUID usuarioId) {
        return jdbc.sql("""
                        INSERT INTO alerta_leitura (resultado_id, usuario_id)
                        SELECT r.id, :usuario FROM monitor_resultado r WHERE r.conta_id = :conta
                        ON CONFLICT DO NOTHING
                        """)
                .param("usuario", usuarioId).param("conta", contaId).update();
    }

    // ------------------------------------------------------------------ preferências

    public boolean emailAtivo(UUID usuarioId) {
        return jdbc.sql("SELECT email_ativo FROM alerta_preferencia WHERE usuario_id = :u")
                .param("u", usuarioId).query(Boolean.class).optional().orElse(true);
    }

    public void definirEmailAtivo(UUID usuarioId, boolean ativo) {
        jdbc.sql("""
                        INSERT INTO alerta_preferencia (usuario_id, email_ativo) VALUES (:u, :a)
                        ON CONFLICT (usuario_id) DO UPDATE SET email_ativo = EXCLUDED.email_ativo, atualizado_em = now()
                        """)
                .param("u", usuarioId).param("a", ativo).update();
        if (!ativo) {
            jdbc.sql("UPDATE alerta_envio SET status = 'CANCELADO' WHERE usuario_id = :u AND status = 'PENDENTE'")
                    .param("u", usuarioId).update();
        }
    }

    // ------------------------------------------------------------------ fila de envio

    /** Cria um envio por usuário ativo (com e-mail ligado) das contas que têm resultado na referência. */
    public int criarEnvios(String referencia) {
        return jdbc.sql("""
                        INSERT INTO alerta_envio (usuario_id, conta_id, referencia)
                        SELECT u.id, r.conta_id, :ref
                        FROM (SELECT DISTINCT conta_id FROM monitor_resultado WHERE referencia = :ref) r
                        JOIN usuario u ON u.conta_id = r.conta_id AND u.ativo
                        WHERE NOT EXISTS (SELECT 1 FROM alerta_preferencia p
                                          WHERE p.usuario_id = u.id AND NOT p.email_ativo)
                        ON CONFLICT (usuario_id, referencia) DO NOTHING
                        """)
                .param("ref", referencia).update();
    }

    public List<Long> idsPendentes(int limite) {
        return jdbc.sql("""
                        SELECT id FROM alerta_envio WHERE status = 'PENDENTE' AND proxima_tentativa <= now()
                        ORDER BY id LIMIT :n
                        """)
                .param("n", limite).query(Long.class).list();
    }

    /** Trava o envio para este processamento (outro nó não o pega). Vazio se não estiver mais pendente. */
    public Optional<EnvioPendente> travarEnvio(long id) {
        return jdbc.sql("""
                        SELECT e.id, e.usuario_id, e.conta_id, e.referencia, e.tentativas, u.nome, u.email, u.ativo
                        FROM alerta_envio e JOIN usuario u ON u.id = e.usuario_id
                        WHERE e.id = :id AND e.status = 'PENDENTE' AND e.proxima_tentativa <= now()
                        FOR UPDATE OF e SKIP LOCKED
                        """)
                .param("id", id).query(EnvioPendente.class).optional();
    }

    public void marcarEnviado(long id) {
        jdbc.sql("UPDATE alerta_envio SET status = 'ENVIADO', enviado_em = now(), tentativas = tentativas + 1, erro = NULL WHERE id = :id")
                .param("id", id).update();
    }

    public void marcarCancelado(long id) {
        jdbc.sql("UPDATE alerta_envio SET status = 'CANCELADO' WHERE id = :id").param("id", id).update();
    }

    /** Registra a falha; reagenda com espera crescente ou marca FALHOU ao esgotar as tentativas. */
    public void registrarFalha(long id, String erro, boolean definitiva, int minutosAteNova) {
        jdbc.sql("""
                        UPDATE alerta_envio
                           SET tentativas = tentativas + 1, erro = :erro,
                               status = CASE WHEN :definitiva THEN 'FALHOU' ELSE 'PENDENTE' END,
                               proxima_tentativa = now() + make_interval(mins => :min)
                         WHERE id = :id
                        """)
                .param("erro", erro).param("definitiva", definitiva).param("min", minutosAteNova).param("id", id)
                .update();
    }

    public List<ResultadoEmail> resultadosParaEmail(UUID contaId, String referencia) {
        return jdbc.sql("""
                        SELECT nicho_nome, total, total_aberturas, total_baixas, total_situacao, total_endereco, total_cnae
                        FROM monitor_resultado WHERE conta_id = :c AND referencia = :r ORDER BY nicho_nome
                        """)
                .param("c", contaId).param("r", referencia).query(ResultadoEmail.class).list();
    }

    public List<ItemEmail> itensParaEmail(UUID contaId, String referencia, int limite) {
        return jdbc.sql("""
                        SELECT r.nicho_nome, i.tipo, i.direcao, i.cnpj, i.nome, i.descricao
                        FROM monitor_resultado r JOIN monitor_resultado_item i ON i.resultado_id = r.id
                        WHERE r.conta_id = :c AND r.referencia = :ref
                        ORDER BY r.nicho_nome, i.id LIMIT :n
                        """)
                .param("c", contaId).param("ref", referencia).param("n", limite).query(ItemEmail.class).list();
    }
}