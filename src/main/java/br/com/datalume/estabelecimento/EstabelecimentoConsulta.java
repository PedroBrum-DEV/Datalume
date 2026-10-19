package br.com.datalume.estabelecimento;

import br.com.datalume.referencia.ReferenciaConsulta;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pública de leitura de estabelecimentos. */
@Service
@Transactional(readOnly = true)
public class EstabelecimentoConsulta {

    private static final String SQL_DETALHE = """
            SELECT e.cnpj, e.matriz, e.nome_fantasia, e.situacao_cadastral, e.data_situacao, e.motivo_situacao,
                   e.data_inicio_atividade, e.cnae_principal, e.cnaes_secundarios, e.tipo_logradouro,
                   e.logradouro, e.numero, e.complemento, e.bairro, e.cep, e.uf, e.municipio_ibge,
                   e.ddd1, e.telefone1, e.ddd2, e.telefone2, e.email, e.referencia_alteracao,
                   emp.razao_social, emp.natureza_juridica, nj.descricao AS natureza_descricao,
                   emp.porte, emp.capital_social, ms.descricao AS motivo_descricao
            FROM estabelecimento e
            LEFT JOIN empresa emp          ON emp.cnpj_basico = e.cnpj_basico
            LEFT JOIN natureza_juridica nj ON nj.codigo = emp.natureza_juridica
            LEFT JOIN motivo_situacao ms   ON ms.codigo = e.motivo_situacao
            WHERE e.cnpj = :cnpj
            """;

    private final JdbcClient jdbc;
    private final ReferenciaConsulta referencia;

    public EstabelecimentoConsulta(JdbcClient jdbc, ReferenciaConsulta referencia) {
        this.jdbc = jdbc;
        this.referencia = referencia;
    }

    /** Aceita CNPJ com ou sem pontuação. Não valida o DV: use {@link Cnpj#valido(String)} antes. */
    public Optional<EstabelecimentoDetalhe> buscarPorCnpj(String cnpj) {
        return jdbc.sql(SQL_DETALHE)
                .param("cnpj", Cnpj.normalizar(cnpj))
                .query((rs, n) -> mapear(rs))
                .optional();
    }

    /** Contagens exatas (podem demorar com a base nacional completa). */
    public Map<String, Long> contagens() {
        Map<String, Long> r = new LinkedHashMap<>();
        r.put("estabelecimentos", contar("SELECT count(*) FROM estabelecimento"));
        r.put("estabelecimentosAtivos", contar("SELECT count(*) FROM estabelecimento WHERE situacao_cadastral = '02'"));
        r.put("empresas", contar("SELECT count(*) FROM empresa"));
        r.put("semMunicipioIbge", contar("SELECT count(*) FROM estabelecimento WHERE municipio_ibge IS NULL AND uf <> 'EX'"));
        return r;
    }

    private long contar(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private EstabelecimentoDetalhe mapear(ResultSet rs) throws SQLException {
        String cnpj = rs.getString("cnpj");
        String situacao = rs.getString("situacao_cadastral");
        String cnaePrincipal = rs.getString("cnae_principal");
        Integer ibge = (Integer) rs.getObject("municipio_ibge");
        String porte = rs.getString("porte");
        String uf = rs.getString("uf");

        EstabelecimentoDetalhe.Cnae cnae = cnaePrincipal == null ? null
                : new EstabelecimentoDetalhe.Cnae(cnaePrincipal,
                        referencia.buscarCnae(cnaePrincipal).map(c -> c.descricao()).orElse(null));
        String municipio = ibge == null ? null
                : referencia.buscarMunicipioPorIbge(ibge).map(m -> m.nome()).orElse(null);

        return new EstabelecimentoDetalhe(
                cnpj,
                Cnpj.formatar(cnpj),
                rs.getBoolean("matriz"),
                rs.getString("razao_social"),
                rs.getString("nome_fantasia"),
                new EstabelecimentoDetalhe.Situacao(situacao, descricaoSituacao(situacao),
                        rs.getObject("data_situacao", LocalDate.class),
                        rs.getString("motivo_situacao"), rs.getString("motivo_descricao")),
                rs.getObject("data_inicio_atividade", LocalDate.class),
                rs.getString("natureza_juridica"),
                rs.getString("natureza_descricao"),
                porte,
                descricaoPorte(porte),
                (BigDecimal) rs.getObject("capital_social"),
                cnae,
                listar(rs.getArray("cnaes_secundarios")),
                new EstabelecimentoDetalhe.Endereco(rs.getString("tipo_logradouro"), rs.getString("logradouro"),
                        rs.getString("numero"), rs.getString("complemento"), rs.getString("bairro"),
                        rs.getString("cep"), uf, ibge, municipio),
                new EstabelecimentoDetalhe.Contato(
                        telefone(rs.getString("ddd1"), rs.getString("telefone1")),
                        telefone(rs.getString("ddd2"), rs.getString("telefone2")),
                        rs.getString("email")),
                rs.getString("referencia_alteracao"));
    }

    private static List<String> listar(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.asList((String[]) array.getArray());
    }

    private static String telefone(String ddd, String numero) {
        if (numero == null || numero.isBlank()) {
            return null;
        }
        return ddd == null || ddd.isBlank() ? numero : "(" + ddd + ") " + numero;
    }

    static String descricaoSituacao(String codigo) {
        if (codigo == null) {
            return null;
        }
        return switch (codigo) {
            case "01" -> "Nula";
            case "02" -> "Ativa";
            case "03" -> "Suspensa";
            case "04" -> "Inapta";
            case "08" -> "Baixada";
            default -> "Desconhecida";
        };
    }

    static String descricaoPorte(String codigo) {
        if (codigo == null) {
            return null;
        }
        return switch (codigo) {
            case "00" -> "Não informado";
            case "01" -> "Microempresa";
            case "03" -> "Empresa de pequeno porte";
            case "05" -> "Demais";
            default -> "Desconhecido";
        };
    }
}