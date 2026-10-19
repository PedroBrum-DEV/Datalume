package br.com.datalume.estabelecimento;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * API pública de carga em massa: o módulo de importação copia o CSV bruto para a área de passagem
 * (COPY) e pede a consolidação, que converte tipos, resolve o município IBGE e faz upsert
 * apenas das linhas que mudaram (comparando row_hash).
 *
 * <p>Os métodos recebem a {@link Connection} para que a cópia e a consolidação ocorram na mesma
 * transação. Usa SQL direto também sobre as tabelas de município da Referência: é uma junção em
 * lote, inviável linha a linha pela API do módulo.
 */
@Component
public class EstabelecimentoCarga {

    private static final Pattern REFERENCIA = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    public enum Destino {
        ESTABELECIMENTO("stg_estabelecimento"),
        EMPRESA("stg_empresa"),
        NATUREZA_JURIDICA("stg_codigo"),
        MOTIVO_SITUACAO("stg_codigo");

        private final String staging;

        Destino(String staging) {
            this.staging = staging;
        }

        public String staging() {
            return staging;
        }
    }

    public record Resultado(long inseridos, long atualizados) {
    }

    /** Comando COPY para os CSVs da Receita (separador ';', aspas, ISO-8859-1, sem cabeçalho). */
    public String comandoCopy(Destino destino) {
        return "COPY " + destino.staging()
                + " FROM STDIN WITH (FORMAT csv, DELIMITER ';', QUOTE '\"', ESCAPE '\"', ENCODING 'LATIN1')";
    }

    public void prepararStaging(Connection conexao, Destino destino) throws SQLException {
        try (Statement st = conexao.createStatement()) {
            st.execute("TRUNCATE " + destino.staging());
        }
    }

    public Resultado consolidar(Connection conexao, Destino destino, String referencia, FiltroImportacao filtro)
            throws SQLException {
        if (!REFERENCIA.matcher(referencia).matches()) {
            throw new IllegalArgumentException("Referência inválida: " + referencia);
        }
        return switch (destino) {
            case ESTABELECIMENTO -> executar(conexao, sqlEstabelecimento(filtro), referencia);
            case EMPRESA -> executar(conexao, SQL_EMPRESA, referencia);
            case NATUREZA_JURIDICA -> executar(conexao, sqlCodigos("natureza_juridica", 4), null);
            case MOTIVO_SITUACAO -> executar(conexao, sqlCodigos("motivo_situacao", 2), null);
        };
    }

    private Resultado executar(Connection conexao, String sql, String referencia) throws SQLException {
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            if (referencia != null) {
                ps.setString(1, referencia);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new Resultado(rs.getLong("inseridos"), rs.getLong("atualizados"));
            }
        }
    }

    private static String sqlCodigos(String tabela, int largura) {
        return """
                WITH fonte AS (
                    SELECT DISTINCT ON (lpad(btrim(c01), %2$d, '0'))
                           lpad(btrim(c01), %2$d, '0') AS codigo, btrim(c02) AS descricao
                    FROM stg_codigo
                    WHERE btrim(c01) <> ''
                    ORDER BY lpad(btrim(c01), %2$d, '0')
                ), up AS (
                    INSERT INTO %1$s AS t (codigo, descricao)
                    SELECT codigo, descricao FROM fonte
                    ON CONFLICT (codigo) DO UPDATE SET descricao = EXCLUDED.descricao
                    WHERE t.descricao IS DISTINCT FROM EXCLUDED.descricao
                    RETURNING (xmax = 0) AS inserido
                )
                SELECT count(*) FILTER (WHERE inserido)     AS inseridos,
                       count(*) FILTER (WHERE NOT inserido) AS atualizados
                FROM up
                """.formatted(tabela, largura);
    }

    private static String sqlEstabelecimento(FiltroImportacao filtro) {
        return """
                WITH fonte AS (
                    SELECT
                        lpad(btrim(c01), 8, '0')                        AS cnpj_basico,
                        lpad(btrim(c02), 4, '0')                        AS cnpj_ordem,
                        lpad(btrim(c03), 2, '0')                        AS cnpj_dv,
                        (btrim(c04) = '1')                              AS matriz,
                        NULLIF(btrim(c05), '')                          AS nome_fantasia,
                        lpad(NULLIF(btrim(c06), ''), 2, '0')            AS situacao_cadastral,
                        parse_data_rfb(btrim(c07))                      AS data_situacao,
                        NULLIF(btrim(c08), '')                          AS motivo_situacao,
                        NULLIF(btrim(c09), '')                          AS cidade_exterior,
                        NULLIF(btrim(c10), '')                          AS pais,
                        parse_data_rfb(btrim(c11))                      AS data_inicio_atividade,
                        NULLIF(btrim(c12), '')                          AS cnae_principal,
                        string_to_array(NULLIF(btrim(c13), ''), ',')    AS cnaes_secundarios,
                        NULLIF(btrim(c14), '')                          AS tipo_logradouro,
                        NULLIF(btrim(c15), '')                          AS logradouro,
                        NULLIF(btrim(c16), '')                          AS numero,
                        NULLIF(btrim(c17), '')                          AS complemento,
                        NULLIF(btrim(c18), '')                          AS bairro,
                        NULLIF(regexp_replace(c19, '\\D', '', 'g'), '') AS cep,
                        NULLIF(upper(btrim(c20)), '')                   AS uf,
                        lpad(NULLIF(btrim(c21), ''), 4, '0')            AS municipio_receita,
                        NULLIF(btrim(c22), '')                          AS ddd1,
                        NULLIF(btrim(c23), '')                          AS telefone1,
                        NULLIF(btrim(c24), '')                          AS ddd2,
                        NULLIF(btrim(c25), '')                          AS telefone2,
                        lower(NULLIF(btrim(c28), ''))                   AS email
                    FROM stg_estabelecimento
                    WHERE %s
                ), calc AS (
                    SELECT f.cnpj_basico || f.cnpj_ordem || f.cnpj_dv AS cnpj,
                           f.*,
                           COALESCE(mr.codigo_ibge, mu.codigo_ibge)   AS municipio_ibge
                    FROM fonte f
                    LEFT JOIN municipio_receita mr
                           ON mr.codigo_receita = f.municipio_receita AND mr.codigo_ibge IS NOT NULL
                    LEFT JOIN LATERAL (
                        SELECT m.codigo_ibge
                        FROM municipio_receita r2
                        JOIN municipio m ON m.nome_normalizado = r2.nome_normalizado AND m.uf_sigla = f.uf
                        WHERE mr.codigo_ibge IS NULL AND r2.codigo_receita = f.municipio_receita
                        LIMIT 1
                    ) mu ON TRUE
                ), unico AS (
                    SELECT DISTINCT ON (c.cnpj) c.*, md5(c::text) AS row_hash
                    FROM calc c
                    ORDER BY c.cnpj
                ), up AS (
                    INSERT INTO estabelecimento AS e (
                        cnpj, cnpj_basico, cnpj_ordem, cnpj_dv, matriz, nome_fantasia, situacao_cadastral,
                        data_situacao, motivo_situacao, cidade_exterior, pais, data_inicio_atividade,
                        cnae_principal, cnaes_secundarios, tipo_logradouro, logradouro, numero, complemento,
                        bairro, cep, uf, municipio_receita, municipio_ibge, ddd1, telefone1, ddd2, telefone2,
                        email, referencia_alteracao, row_hash)
                    SELECT
                        cnpj, cnpj_basico, cnpj_ordem, cnpj_dv, matriz, nome_fantasia, situacao_cadastral,
                        data_situacao, motivo_situacao, cidade_exterior, pais, data_inicio_atividade,
                        cnae_principal, cnaes_secundarios, tipo_logradouro, logradouro, numero, complemento,
                        bairro, cep, uf, municipio_receita, municipio_ibge, ddd1, telefone1, ddd2, telefone2,
                        email, ?, row_hash
                    FROM unico
                    ON CONFLICT (cnpj) DO UPDATE SET
                        matriz = EXCLUDED.matriz,
                        nome_fantasia = EXCLUDED.nome_fantasia,
                        situacao_cadastral = EXCLUDED.situacao_cadastral,
                        data_situacao = EXCLUDED.data_situacao,
                        motivo_situacao = EXCLUDED.motivo_situacao,
                        cidade_exterior = EXCLUDED.cidade_exterior,
                        pais = EXCLUDED.pais,
                        data_inicio_atividade = EXCLUDED.data_inicio_atividade,
                        cnae_principal = EXCLUDED.cnae_principal,
                        cnaes_secundarios = EXCLUDED.cnaes_secundarios,
                        tipo_logradouro = EXCLUDED.tipo_logradouro,
                        logradouro = EXCLUDED.logradouro,
                        numero = EXCLUDED.numero,
                        complemento = EXCLUDED.complemento,
                        bairro = EXCLUDED.bairro,
                        cep = EXCLUDED.cep,
                        uf = EXCLUDED.uf,
                        municipio_receita = EXCLUDED.municipio_receita,
                        municipio_ibge = EXCLUDED.municipio_ibge,
                        ddd1 = EXCLUDED.ddd1,
                        telefone1 = EXCLUDED.telefone1,
                        ddd2 = EXCLUDED.ddd2,
                        telefone2 = EXCLUDED.telefone2,
                        email = EXCLUDED.email,
                        referencia_alteracao = EXCLUDED.referencia_alteracao,
                        row_hash = EXCLUDED.row_hash,
                        atualizado_em = now()
                    WHERE e.row_hash IS DISTINCT FROM EXCLUDED.row_hash
                    RETURNING (xmax = 0) AS inserido
                )
                SELECT count(*) FILTER (WHERE inserido)     AS inseridos,
                       count(*) FILTER (WHERE NOT inserido) AS atualizados
                FROM up
                """.formatted(filtro.clausulaSql("c20", "c12"));
    }

    /** Só entram empresas que possuem estabelecimento já carregado (respeita o filtro da importação). */
    private static final String SQL_EMPRESA = """
            WITH fonte AS (
                SELECT
                    lpad(btrim(s.c01), 8, '0')                          AS cnpj_basico,
                    COALESCE(NULLIF(btrim(s.c02), ''), '')              AS razao_social,
                    NULLIF(btrim(s.c03), '')                            AS natureza_juridica,
                    NULLIF(btrim(s.c04), '')                            AS qualificacao_responsavel,
                    CASE WHEN btrim(s.c05) ~ '^[0-9]+(,[0-9]+)?$'
                         THEN replace(btrim(s.c05), ',', '.')::numeric(20,2) END AS capital_social,
                    lpad(NULLIF(btrim(s.c06), ''), 2, '0')              AS porte,
                    NULLIF(btrim(s.c07), '')                            AS ente_federativo
                FROM stg_empresa s
                WHERE EXISTS (SELECT 1 FROM estabelecimento x WHERE x.cnpj_basico = lpad(btrim(s.c01), 8, '0'))
            ), unico AS (
                SELECT DISTINCT ON (f.cnpj_basico) f.*, md5(f::text) AS row_hash
                FROM fonte f
                ORDER BY f.cnpj_basico
            ), up AS (
                INSERT INTO empresa AS e (
                    cnpj_basico, razao_social, natureza_juridica, qualificacao_responsavel, capital_social,
                    porte, ente_federativo, referencia_alteracao, row_hash)
                SELECT cnpj_basico, razao_social, natureza_juridica, qualificacao_responsavel, capital_social,
                       porte, ente_federativo, ?, row_hash
                FROM unico
                ON CONFLICT (cnpj_basico) DO UPDATE SET
                    razao_social = EXCLUDED.razao_social,
                    natureza_juridica = EXCLUDED.natureza_juridica,
                    qualificacao_responsavel = EXCLUDED.qualificacao_responsavel,
                    capital_social = EXCLUDED.capital_social,
                    porte = EXCLUDED.porte,
                    ente_federativo = EXCLUDED.ente_federativo,
                    referencia_alteracao = EXCLUDED.referencia_alteracao,
                    row_hash = EXCLUDED.row_hash,
                    atualizado_em = now()
                WHERE e.row_hash IS DISTINCT FROM EXCLUDED.row_hash
                RETURNING (xmax = 0) AS inserido
            )
            SELECT count(*) FILTER (WHERE inserido)     AS inseridos,
                   count(*) FILTER (WHERE NOT inserido) AS atualizados
            FROM up
            """;
}