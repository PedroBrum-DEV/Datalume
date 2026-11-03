package br.com.datalume.estabelecimento;

import br.com.datalume.compartilhado.erro.RequisicaoInvalidaException;
import br.com.datalume.referencia.ReferenciaCatalogo;
import br.com.datalume.referencia.ReferenciaCatalogo.ExpansaoCnae;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pública do registro de mudanças. As mudanças são gravadas pela importação ({@link EstabelecimentoCarga})
 * e consultadas aqui por critérios de nicho: um evento "casa" com o nicho se o estado ANTES ou o estado DEPOIS
 * estiver dentro dele (CNAE principal, UF e município). Assim também é possível saber quando uma empresa
 * SAIU do nicho. Eventos só carregam o CNAE principal (não os secundários).
 */
@Service
@Transactional(readOnly = true)
public class EstabelecimentoMudancas {

    private static final Pattern REFERENCIA = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    /** Mudança com o estado antes/depois e se cada estado está dentro do nicho consultado. */
    public record MudancaDetalhada(long id, TipoMudanca tipo, String cnpj, String nome,
            String situacaoAntes, String situacaoDepois, String cnaeAntes, String cnaeDepois,
            String ufAntes, String ufDepois, String municipioAntes, String municipioDepois,
            boolean casaAntes, boolean casaDepois) {
    }

    public record ResultadoMudancas(long total, Map<TipoMudanca, Long> porTipo, List<MudancaDetalhada> itens) {
    }

    private final JdbcClient jdbc;
    private final ReferenciaCatalogo catalogo;

    public EstabelecimentoMudancas(JdbcClient jdbc, ReferenciaCatalogo catalogo) {
        this.jdbc = jdbc;
        this.catalogo = catalogo;
    }

    /**
     * @param tipos        tipos de interesse (vazio = nenhum resultado)
     * @param criterios    usa apenas cnaes, ufs e municípios
     * @param limiteItens  máximo de itens detalhados; os totais cobrem todas as mudanças
     */
    public ResultadoMudancas consultar(String referencia, Set<TipoMudanca> tipos, CriteriosBusca criterios,
            int limiteItens) {
        if (referencia == null || !REFERENCIA.matcher(referencia).matches()) {
            throw new RequisicaoInvalidaException("Referência inválida; use AAAA-MM.");
        }
        if (tipos == null || tipos.isEmpty()) {
            return new ResultadoMudancas(0, new EnumMap<>(TipoMudanca.class), List.of());
        }
        Casamento c = casamento(criterios);
        Map<String, Object> params = new LinkedHashMap<>(c.params());
        params.put("ref", referencia);
        params.put("tipos", tipos.stream().map(Enum::name).toList());
        params.put("lim", Math.max(limiteItens, 0));

        String filtro = "ev.referencia = :ref AND ev.tipo IN (:tipos) AND (" + c.antes() + " OR " + c.depois() + ")";

        Map<TipoMudanca, Long> porTipo = new EnumMap<>(TipoMudanca.class);
        jdbc.sql("SELECT ev.tipo AS tipo, count(*) AS n FROM estabelecimento_evento ev WHERE " + filtro
                        + " GROUP BY ev.tipo")
                .params(params)
                .query((rs, i) -> Map.entry(TipoMudanca.valueOf(rs.getString("tipo")), rs.getLong("n")))
                .list().forEach(e -> porTipo.put(e.getKey(), e.getValue()));
        long total = porTipo.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0 || limiteItens <= 0) {
            return new ResultadoMudancas(total, porTipo, List.of());
        }

        String sql = "SELECT ev.id, ev.tipo, ev.cnpj, COALESCE(NULLIF(e.nome_fantasia, ''), emp.razao_social) AS nome, "
                + "ev.situacao_antes, ev.situacao_depois, ev.cnae_antes, ev.cnae_depois, ev.uf_antes, ev.uf_depois, "
                + "ma.nome AS mun_antes, md.nome AS mun_depois, "
                + "COALESCE(" + c.antes() + ", FALSE) AS casa_antes, COALESCE(" + c.depois() + ", FALSE) AS casa_depois "
                + "FROM estabelecimento_evento ev "
                + "LEFT JOIN estabelecimento e ON e.cnpj = ev.cnpj "
                + "LEFT JOIN empresa emp ON emp.cnpj_basico = e.cnpj_basico "
                + "LEFT JOIN municipio ma ON ma.codigo_ibge = ev.municipio_antes "
                + "LEFT JOIN municipio md ON md.codigo_ibge = ev.municipio_depois "
                + "WHERE " + filtro + " ORDER BY ev.tipo, ev.id LIMIT :lim";
        List<MudancaDetalhada> itens = jdbc.sql(sql).params(params).query((rs, i) -> new MudancaDetalhada(
                rs.getLong("id"), TipoMudanca.valueOf(rs.getString("tipo")), rs.getString("cnpj"),
                rs.getString("nome"), rs.getString("situacao_antes"), rs.getString("situacao_depois"),
                rs.getString("cnae_antes"), rs.getString("cnae_depois"), rs.getString("uf_antes"),
                rs.getString("uf_depois"), rs.getString("mun_antes"), rs.getString("mun_depois"),
                rs.getBoolean("casa_antes"), rs.getBoolean("casa_depois"))).list();
        return new ResultadoMudancas(total, porTipo, itens);
    }

    /** Referência mais recente com mudanças registradas. */
    public Optional<String> referenciaMaisRecente() {
        return jdbc.sql("SELECT max(referencia) FROM estabelecimento_evento")
                .query(String.class).optional().filter(r -> r != null);
    }

    public long contar(String referencia) {
        return jdbc.sql("SELECT count(*) FROM estabelecimento_evento WHERE referencia = :r")
                .param("r", referencia).query(Long.class).single();
    }

    /** Retenção: remove eventos detectados há mais de N meses. */
    @Transactional
    public int removerMaisAntigosQue(int meses) {
        return jdbc.sql("DELETE FROM estabelecimento_evento WHERE detectado_em < now() - make_interval(months => :m)")
                .param("m", meses).update();
    }

    // ------------------------------------------------------------------ casamento com o nicho

    private Casamento casamento(CriteriosBusca c) {
        Map<String, Object> p = new LinkedHashMap<>();
        List<String> antes = new ArrayList<>();
        List<String> depois = new ArrayList<>();
        if (!c.cnaes().isEmpty()) {
            ExpansaoCnae exp = catalogo.expandirCnaes(c.cnaes());
            List<String> a = new ArrayList<>();
            List<String> d = new ArrayList<>();
            if (!exp.codigos().isEmpty()) {
                a.add("ev.cnae_antes IN (:cnaes)");
                d.add("ev.cnae_depois IN (:cnaes)");
                p.put("cnaes", exp.codigos());
            }
            int i = 0;
            for (String prefixo : exp.prefixosSemCatalogo()) {
                a.add("ev.cnae_antes LIKE :pref" + i);
                d.add("ev.cnae_depois LIKE :pref" + i);
                p.put("pref" + i, prefixo + "%");
                i++;
            }
            antes.add("(" + String.join(" OR ", a) + ")");
            depois.add("(" + String.join(" OR ", d) + ")");
        }
        if (!c.ufs().isEmpty()) {
            antes.add("ev.uf_antes IN (:ufs)");
            depois.add("ev.uf_depois IN (:ufs)");
            p.put("ufs", c.ufs());
        }
        if (!c.municipios().isEmpty()) {
            antes.add("ev.municipio_antes IN (:muns)");
            depois.add("ev.municipio_depois IN (:muns)");
            p.put("muns", c.municipios());
        }
        return new Casamento(juntar(antes), juntar(depois), p);
    }

    private static String juntar(List<String> partes) {
        return partes.isEmpty() ? "TRUE" : "(" + String.join(" AND ", partes) + ")";
    }

    private record Casamento(String antes, String depois, Map<String, Object> params) {
    }
}