package br.com.datalume.busca.dominio;

import br.com.datalume.estabelecimento.CriteriosBusca;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acesso a nichos e ao registro de exportações. Toda leitura/escrita filtra por conta (isolamento).
 * Os arrays são montados como literais Postgres a partir de valores já validados (só letras e dígitos).
 */
@Repository
public class RepositorioNicho {

    private static final String COLUNAS =
            "id, conta_id, nome, cnaes, incluir_secundarios, ufs, municipios, criado_em, atualizado_em";

    private final JdbcClient jdbc;

    public RepositorioNicho(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Nicho inserir(UUID contaId, String nome, CriteriosBusca c) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO nicho (id, conta_id, nome, cnaes, incluir_secundarios, ufs, municipios)
                        VALUES (:id, :conta, :nome, CAST(:cnaes AS text[]), :sec, CAST(:ufs AS text[]),
                                CAST(:mun AS integer[]))
                        """)
                .param("id", id)
                .param("conta", contaId)
                .param("nome", nome)
                .param("cnaes", literal(c.cnaes()))
                .param("sec", c.incluirSecundarios())
                .param("ufs", literal(c.ufs()))
                .param("mun", literal(c.municipios()))
                .update();
        return buscar(contaId, id).orElseThrow();
    }

    /** @return false se o nicho não existe nesta conta */
    public boolean atualizar(UUID contaId, UUID id, String nome, CriteriosBusca c) {
        int n = jdbc.sql("""
                        UPDATE nicho
                           SET nome = :nome, cnaes = CAST(:cnaes AS text[]), incluir_secundarios = :sec,
                               ufs = CAST(:ufs AS text[]), municipios = CAST(:mun AS integer[]),
                               atualizado_em = now()
                         WHERE id = :id AND conta_id = :conta
                        """)
                .param("nome", nome)
                .param("cnaes", literal(c.cnaes()))
                .param("sec", c.incluirSecundarios())
                .param("ufs", literal(c.ufs()))
                .param("mun", literal(c.municipios()))
                .param("id", id)
                .param("conta", contaId)
                .update();
        return n > 0;
    }

    public Optional<Nicho> buscar(UUID contaId, UUID id) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM nicho WHERE id = :id AND conta_id = :conta")
                .param("id", id).param("conta", contaId)
                .query((rs, n) -> mapear(rs)).optional();
    }

    public List<Nicho> listar(UUID contaId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM nicho WHERE conta_id = :conta ORDER BY lower(nome)")
                .param("conta", contaId)
                .query((rs, n) -> mapear(rs)).list();
    }

    public boolean remover(UUID contaId, UUID id) {
        return jdbc.sql("DELETE FROM nicho WHERE id = :id AND conta_id = :conta")
                .param("id", id).param("conta", contaId).update() > 0;
    }

    public long contar(UUID contaId) {
        return jdbc.sql("SELECT count(*) FROM nicho WHERE conta_id = :conta")
                .param("conta", contaId).query(Long.class).single();
    }

    // ------------------------------------------------------------------ exportações

    public void registrarExportacao(UUID contaId, UUID usuarioId, UUID nichoId, String nichoNome,
            String criterios, boolean contatos, int linhas) {
        jdbc.sql("""
                        INSERT INTO exportacao (conta_id, usuario_id, nicho_id, nicho_nome, criterios, contatos, linhas)
                        VALUES (:conta, :usuario, :nicho, :nome, :criterios, :contatos, :linhas)
                        """)
                .param("conta", contaId)
                .param("usuario", usuarioId)
                .param("nicho", nichoId)
                .param("nome", nichoNome)
                .param("criterios", criterios)
                .param("contatos", contatos)
                .param("linhas", linhas)
                .update();
    }

    public List<RegistroExportacao> listarExportacoes(UUID contaId, int limite) {
        return jdbc.sql("""
                        SELECT id, usuario_id, nicho_id, nicho_nome, criterios, contatos, linhas, criado_em
                        FROM exportacao WHERE conta_id = :conta ORDER BY id DESC LIMIT :n
                        """)
                .param("conta", contaId).param("n", limite)
                .query(RegistroExportacao.class).list();
    }

    // ------------------------------------------------------------------ auxiliares

    private static String literal(List<?> valores) {
        return valores.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }

    private static Nicho mapear(ResultSet rs) throws SQLException {
        return new Nicho(
                rs.getObject("id", UUID.class),
                rs.getObject("conta_id", UUID.class),
                rs.getString("nome"),
                texto(rs.getArray("cnaes")),
                rs.getBoolean("incluir_secundarios"),
                texto(rs.getArray("ufs")),
                inteiros(rs.getArray("municipios")),
                rs.getObject("criado_em", OffsetDateTime.class),
                rs.getObject("atualizado_em", OffsetDateTime.class));
    }

    private static List<String> texto(Array array) throws SQLException {
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }

    private static List<Integer> inteiros(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((Integer[]) array.getArray());
    }
}