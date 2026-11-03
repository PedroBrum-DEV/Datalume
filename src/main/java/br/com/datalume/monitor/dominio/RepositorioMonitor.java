package br.com.datalume.monitor.dominio;

import br.com.datalume.estabelecimento.TipoMudanca;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RepositorioMonitor {

    public record RegrasDoNicho(UUID contaId, UUID nichoId, Set<TipoMudanca> tipos) {
    }

    public record Execucao(String referencia, String status, int tentativas, Integer nichosProcessados,
            Integer alertasGerados, String erro, OffsetDateTime criadoEm, OffsetDateTime iniciadoEm,
            OffsetDateTime finalizadoEm) {
    }

    public record ItemResultado(long eventoId, String tipo, String direcao, String cnpj, String nome,
            String descricao) {
    }

    public record TotaisResultado(long total, long aberturas, long baixas, long situacao, long endereco, long cnae,
            boolean truncado) {
    }

    public record RegraLinha(UUID contaId, UUID nichoId, String tipo) {
    }

    private final JdbcClient jdbc;
    private final JdbcTemplate template;

    public RepositorioMonitor(JdbcClient jdbc, JdbcTemplate template) {
        this.jdbc = jdbc;
        this.template = template;
    }

    // ------------------------------------------------------------------ regras

    public List<TipoMudanca> tiposDoNicho(UUID contaId, UUID nichoId) {
        return jdbc.sql("SELECT tipo FROM monitoramento WHERE conta_id = :c AND nicho_id = :n ORDER BY tipo")
                .param("c", contaId).param("n", nichoId)
                .query((rs, i) -> TipoMudanca.valueOf(rs.getString("tipo"))).list();
    }

    public long contarRegrasDaConta(UUID contaId) {
        return jdbc.sql("SELECT count(*) FROM monitoramento WHERE conta_id = :c")
                .param("c", contaId).query(Long.class).single();
    }

    @Transactional
    public void substituirRegras(UUID contaId, UUID nichoId, Set<TipoMudanca> tipos) {
        jdbc.sql("DELETE FROM monitoramento WHERE conta_id = :c AND nicho_id = :n")
                .param("c", contaId).param("n", nichoId).update();
        for (TipoMudanca tipo : tipos) {
            jdbc.sql("INSERT INTO monitoramento (id, conta_id, nicho_id, tipo) VALUES (:id, :c, :n, :t)")
                    .param("id", UUID.randomUUID()).param("c", contaId).param("n", nichoId)
                    .param("t", tipo.name()).update();
        }
    }

    public void removerRegras(UUID contaId, UUID nichoId) {
        jdbc.sql("DELETE FROM monitoramento WHERE conta_id = :c AND nicho_id = :n")
                .param("c", contaId).param("n", nichoId).update();
    }

    /** Todas as regras ativas, agrupadas por nicho. */
    public List<RegrasDoNicho> regrasAgrupadas() {
        List<RegraLinha> linhas = jdbc.sql("SELECT conta_id, nicho_id, tipo FROM monitoramento ORDER BY nicho_id, tipo")
                .query(RegraLinha.class).list();
        Map<UUID, RegrasDoNicho> mapa = new LinkedHashMap<>();
        for (RegraLinha l : linhas) {
            mapa.computeIfAbsent(l.nichoId(),
                    k -> new RegrasDoNicho(l.contaId(), l.nichoId(), EnumSet.noneOf(TipoMudanca.class)))
                    .tipos().add(TipoMudanca.valueOf(l.tipo()));
        }
        return new ArrayList<>(mapa.values());
    }

    // ------------------------------------------------------------------ execução por referência

    public void registrarPendente(String referencia) {
        jdbc.sql("INSERT INTO monitor_execucao (referencia, status) VALUES (:r, 'PENDENTE') ON CONFLICT (referencia) DO NOTHING")
                .param("r", referencia).update();
    }

    /** Pega a execução para si (atomicamente). Falso se já concluída, em andamento ou inexistente. */
    public boolean reivindicar(String referencia, boolean forcar) {
        return jdbc.sql("""
                        UPDATE monitor_execucao
                           SET status = 'PROCESSANDO', iniciado_em = now(), finalizado_em = NULL,
                               tentativas = tentativas + 1, erro = NULL
                         WHERE referencia = :r
                           AND (status IN ('PENDENTE', 'FALHOU')
                                OR (status = 'PROCESSANDO' AND iniciado_em < now() - interval '2 hours')
                                OR (:forcar AND status = 'CONCLUIDA'))
                        """)
                .param("r", referencia).param("forcar", forcar).update() == 1;
    }

    public void concluir(String referencia, int nichos, int alertas) {
        jdbc.sql("""
                        UPDATE monitor_execucao SET status = 'CONCLUIDA', nichos_processados = :n,
                               alertas_gerados = :a, finalizado_em = now() WHERE referencia = :r
                        """)
                .param("n", nichos).param("a", alertas).param("r", referencia).update();
    }

    public void falhar(String referencia, String erro) {
        jdbc.sql("UPDATE monitor_execucao SET status = 'FALHOU', erro = :e, finalizado_em = now() WHERE referencia = :r")
                .param("e", erro).param("r", referencia).update();
    }

    public List<Execucao> listarExecucoes(int limite) {
        return jdbc.sql("""
                        SELECT referencia, status, tentativas, nichos_processados, alertas_gerados, erro,
                               criado_em, iniciado_em, finalizado_em
                        FROM monitor_execucao ORDER BY referencia DESC LIMIT :n
                        """)
                .param("n", limite).query(Execucao.class).list();
    }

    /** Referências que ficaram pelo caminho: pendentes há mais de 5 min, com falha (até 5 tentativas) ou travadas. */
    public List<String> referenciasPendentes() {
        return jdbc.sql("""
                        SELECT referencia FROM monitor_execucao
                         WHERE (status = 'PENDENTE' AND criado_em < now() - interval '5 minutes')
                            OR (status = 'FALHOU' AND tentativas < 5)
                            OR (status = 'PROCESSANDO' AND iniciado_em < now() - interval '2 hours')
                         ORDER BY referencia
                        """)
                .query(String.class).list();
    }

    // ------------------------------------------------------------------ resultados

    /** Grava (ou regrava) o resultado de um nicho numa referência. Idempotente. */
    @Transactional
    public long substituirResultado(UUID contaId, UUID nichoId, String nichoNome, String referencia,
            TotaisResultado t, List<ItemResultado> itens) {
        long id = jdbc.sql("""
                        INSERT INTO monitor_resultado (conta_id, nicho_id, nicho_nome, referencia, total,
                            total_aberturas, total_baixas, total_situacao, total_endereco, total_cnae, itens_truncados)
                        VALUES (:conta, :nicho, :nome, :ref, :total, :ab, :ba, :si, :en, :cn, :trunc)
                        ON CONFLICT (nicho_id, referencia) DO UPDATE SET
                            nicho_nome = EXCLUDED.nicho_nome, total = EXCLUDED.total,
                            total_aberturas = EXCLUDED.total_aberturas, total_baixas = EXCLUDED.total_baixas,
                            total_situacao = EXCLUDED.total_situacao, total_endereco = EXCLUDED.total_endereco,
                            total_cnae = EXCLUDED.total_cnae, itens_truncados = EXCLUDED.itens_truncados
                        RETURNING id
                        """)
                .param("conta", contaId).param("nicho", nichoId).param("nome", nichoNome).param("ref", referencia)
                .param("total", t.total()).param("ab", t.aberturas()).param("ba", t.baixas())
                .param("si", t.situacao()).param("en", t.endereco()).param("cn", t.cnae())
                .param("trunc", t.truncado())
                .query(Long.class).single();
        jdbc.sql("DELETE FROM monitor_resultado_item WHERE resultado_id = :id").param("id", id).update();
        List<Object[]> linhas = new ArrayList<>();
        for (ItemResultado i : itens) {
            linhas.add(new Object[] {id, i.eventoId(), i.tipo(), i.direcao(), i.cnpj(), i.nome(), i.descricao()});
        }
        template.batchUpdate("""
                INSERT INTO monitor_resultado_item (resultado_id, evento_id, tipo, direcao, cnpj, nome, descricao)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, linhas);
        return id;
    }

    public void removerResultado(UUID nichoId, String referencia) {
        jdbc.sql("DELETE FROM monitor_resultado WHERE nicho_id = :n AND referencia = :r")
                .param("n", nichoId).param("r", referencia).update();
    }

    /** Retenção: apaga resultados (e itens, em cascata) mais antigos que N meses. */
    public int removerResultadosMaisAntigosQue(int meses) {
        return jdbc.sql("DELETE FROM monitor_resultado WHERE criado_em < now() - make_interval(months => :m)")
                .param("m", meses).update();
    }
}