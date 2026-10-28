package br.com.datalume.estabelecimento;

import br.com.datalume.compartilhado.erro.RequisicaoInvalidaException;
import br.com.datalume.referencia.ReferenciaCatalogo;
import br.com.datalume.referencia.ReferenciaCatalogo.ExpansaoCnae;
import br.com.datalume.referencia.ReferenciaConsulta;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pública de pesquisa de estabelecimentos por critérios estruturados.
 *
 * <p>Ordenação fixa: abertura mais recente primeiro (sem data = por último), CNPJ como desempate.
 * A paginação é por cursor (keyset): estável mesmo que a base mude entre uma página e outra.
 * Prefixos de CNAE são expandidos para códigos exatos para aproveitar os índices.
 * Faz junção com a tabela de municípios da Referência porque é leitura em lote para listagem.
 */
@Service
@Transactional(readOnly = true)
public class EstabelecimentoPesquisa {

    public static final int PAGINA_PADRAO = 50;
    public static final int PAGINA_MAXIMA = 200;
    static final int LIMITE_CONTAGEM = 10_000;
    static final int LIMITE_FACETAS = 100_000;
    static final int TOP_FACETAS = 20;
    private static final int LOTE_EXPORTACAO = 1000;

    private static final LocalDate SEM_DATA = LocalDate.of(1900, 1, 1);
    private static final String ABERTURA = "COALESCE(e.data_inicio_atividade, DATE '1900-01-01')";
    private static final String FROM =
            " FROM estabelecimento e LEFT JOIN empresa emp ON emp.cnpj_basico = e.cnpj_basico ";
    private static final String SELECT_ITEM = """
            SELECT e.cnpj, e.matriz, e.nome_fantasia, e.situacao_cadastral, e.data_inicio_atividade,
                   COALESCE(e.data_inicio_atividade, DATE '1900-01-01') AS abertura_ord,
                   e.cnae_principal, e.tipo_logradouro, e.logradouro, e.numero, e.complemento, e.bairro,
                   e.cep, e.uf, e.municipio_ibge, mu.nome AS municipio, e.ddd1, e.telefone1, e.email,
                   emp.razao_social, emp.porte
            """;
    private static final Pattern CNPJ = Pattern.compile("[A-Z0-9]{14}");

    private final JdbcClient jdbc;
    private final ReferenciaCatalogo catalogo;
    private final ReferenciaConsulta referencia;

    public EstabelecimentoPesquisa(JdbcClient jdbc, ReferenciaCatalogo catalogo, ReferenciaConsulta referencia) {
        this.jdbc = jdbc;
        this.catalogo = catalogo;
        this.referencia = referencia;
    }

    // ------------------------------------------------------------------ pesquisa paginada

    /**
     * @param limite    itens por página (1 a 200)
     * @param cursor    {@code proximoCursor} da página anterior, ou nulo na primeira
     * @param contatos  inclui e-mail e telefone nos itens
     */
    public PaginaEstabelecimentos pesquisar(CriteriosBusca criterios, int limite, String cursor, boolean contatos) {
        return pagina(criterios, Math.min(Math.max(limite, 1), PAGINA_MAXIMA), cursor, contatos, true);
    }

    private PaginaEstabelecimentos pagina(CriteriosBusca criterios, int limite, String cursor, boolean contatos,
            boolean comTotal) {
        exigirFiltro(criterios);
        Filtro filtro = montar(criterios);

        Long total = null;
        boolean exato = true;
        if (comTotal && cursor == null) {
            long n = contar(filtro);
            exato = n <= LIMITE_CONTAGEM;
            total = Math.min(n, LIMITE_CONTAGEM);
        }

        String where = filtro.where();
        Map<String, Object> params = new LinkedHashMap<>(filtro.params());
        if (cursor != null) {
            Cursor c = Cursor.decodificar(cursor);
            where += " AND (" + ABERTURA + ", e.cnpj) < (:curData, :curCnpj)";
            params.put("curData", c.data());
            params.put("curCnpj", c.cnpj());
        }
        params.put("lim", limite + 1);

        String sql = SELECT_ITEM + FROM + " LEFT JOIN municipio mu ON mu.codigo_ibge = e.municipio_ibge WHERE "
                + where + " ORDER BY " + ABERTURA + " DESC, e.cnpj DESC LIMIT :lim";
        List<Linha> linhas = jdbc.sql(sql).params(params).query((rs, n) -> mapear(rs, contatos)).list();

        boolean temMais = linhas.size() > limite;
        List<Linha> pagina = temMais ? linhas.subList(0, limite) : linhas;
        String proximo = null;
        if (temMais) {
            Linha ultima = pagina.get(pagina.size() - 1);
            proximo = Cursor.codificar(ultima.ordem(), ultima.item().cnpj());
        }
        return new PaginaEstabelecimentos(pagina.stream().map(Linha::item).toList(), proximo, total, exato);
    }

    // ------------------------------------------------------------------ exportação (streaming por lotes)

    /**
     * Entrega até {@code limiteTotal} itens ao {@code destino}, lendo em lotes por cursor. Não mantém
     * transação aberta enquanto o destino consome (o cliente pode ser lento).
     *
     * @return quantidade de itens entregues
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public long exportar(CriteriosBusca criterios, int limiteTotal, boolean contatos,
            Consumer<ItemEstabelecimento> destino) {
        long emitidos = 0;
        String cursor = null;
        while (emitidos < limiteTotal) {
            int lote = (int) Math.min(LOTE_EXPORTACAO, limiteTotal - emitidos);
            PaginaEstabelecimentos pagina = pagina(criterios, lote, cursor, contatos, false);
            pagina.itens().forEach(destino);
            emitidos += pagina.itens().size();
            cursor = pagina.proximoCursor();
            if (cursor == null) {
                break;
            }
        }
        return emitidos;
    }

    // ------------------------------------------------------------------ facetas

    /** Top 20 municípios, CNAEs e portes do recorte (amostra de até 100 mil resultados). */
    public Facetas facetas(CriteriosBusca criterios) {
        exigirFiltro(criterios);
        Filtro filtro = montar(criterios);
        String sql = "WITH base AS (SELECT e.municipio_ibge AS mun, e.cnae_principal AS cnae, emp.porte AS porte"
                + FROM + "WHERE " + filtro.where() + " LIMIT " + (LIMITE_FACETAS + 1) + ") "
                + "SELECT mun, cnae, porte, GROUPING(mun) AS g_mun, GROUPING(cnae) AS g_cnae, "
                + "GROUPING(porte) AS g_porte, count(*) AS total FROM base "
                + "GROUP BY GROUPING SETS ((mun), (cnae), (porte))";

        List<Faceta3> linhas = jdbc.sql(sql).params(filtro.params()).query((rs, n) -> new Faceta3(
                (Integer) rs.getObject("mun"), rs.getString("cnae"), rs.getString("porte"),
                rs.getInt("g_mun"), rs.getInt("g_cnae"), rs.getInt("g_porte"), rs.getLong("total"))).list();

        long amostra = linhas.stream().filter(l -> l.gMun() == 0).mapToLong(Faceta3::total).sum();
        List<Facetas.Faceta> municipios = linhas.stream().filter(l -> l.gMun() == 0)
                .sorted(Comparator.comparingLong(Faceta3::total).reversed()).limit(TOP_FACETAS)
                .map(l -> new Facetas.Faceta(l.mun() == null ? "" : String.valueOf(l.mun()),
                        rotuloMunicipio(l.mun()), l.total())).toList();
        List<Facetas.Faceta> cnaes = linhas.stream().filter(l -> l.gCnae() == 0)
                .sorted(Comparator.comparingLong(Faceta3::total).reversed()).limit(TOP_FACETAS)
                .map(l -> new Facetas.Faceta(l.cnae() == null ? "" : l.cnae(), rotuloCnae(l.cnae()), l.total()))
                .toList();
        List<Facetas.Faceta> portes = linhas.stream().filter(l -> l.gPorte() == 0)
                .sorted(Comparator.comparingLong(Faceta3::total).reversed()).limit(TOP_FACETAS)
                .map(l -> new Facetas.Faceta(l.porte() == null ? "" : l.porte(),
                        l.porte() == null ? "Sem informação" : EstabelecimentoConsulta.descricaoPorte(l.porte()),
                        l.total())).toList();
        return new Facetas(amostra > LIMITE_FACETAS, municipios, cnaes, portes);
    }

    // ------------------------------------------------------------------ montagem da consulta

    private Filtro montar(CriteriosBusca c) {
        List<String> cond = new ArrayList<>();
        Map<String, Object> p = new LinkedHashMap<>();

        if (!c.cnaes().isEmpty()) {
            ExpansaoCnae exp = catalogo.expandirCnaes(c.cnaes());
            List<String> partes = new ArrayList<>();
            if (!exp.codigos().isEmpty()) {
                partes.add("e.cnae_principal IN (:cnaes)");
                p.put("cnaes", exp.codigos());
                if (c.incluirSecundarios()) {
                    partes.add("e.cnaes_secundarios && CAST(:cnaesSec AS text[])");
                    p.put("cnaesSec", "{" + String.join(",", exp.codigos()) + "}");
                }
            }
            int i = 0;
            for (String prefixo : exp.prefixosSemCatalogo()) {
                // catálogo de CNAE não carregado: cai para LIKE (correto, porém sem índice)
                partes.add("e.cnae_principal LIKE :cnaePref" + i);
                if (c.incluirSecundarios()) {
                    partes.add("EXISTS (SELECT 1 FROM unnest(e.cnaes_secundarios) s WHERE s LIKE :cnaePref" + i + ")");
                }
                p.put("cnaePref" + i, prefixo + "%");
                i++;
            }
            cond.add("(" + String.join(" OR ", partes) + ")");
        }
        if (!c.ufs().isEmpty()) {
            cond.add("e.uf IN (:ufs)");
            p.put("ufs", c.ufs());
        }
        if (!c.municipios().isEmpty()) {
            cond.add("e.municipio_ibge IN (:municipios)");
            p.put("municipios", c.municipios());
        }
        if (!c.situacoes().isEmpty()) {
            cond.add("e.situacao_cadastral IN (:situacoes)");
            p.put("situacoes", c.situacoes());
        }
        if (!c.portes().isEmpty()) {
            cond.add("emp.porte IN (:portes)");
            p.put("portes", c.portes());
        }
        if (c.matriz() != null) {
            cond.add("e.matriz = :matriz");
            p.put("matriz", c.matriz());
        }
        if (c.abertoDe() != null) {
            cond.add("e.data_inicio_atividade >= :abertoDe");
            p.put("abertoDe", c.abertoDe());
        }
        if (c.abertoAte() != null) {
            cond.add("e.data_inicio_atividade <= :abertoAte");
            p.put("abertoAte", c.abertoAte());
        }
        if (c.comEmail()) {
            cond.add("e.email IS NOT NULL");
        }
        if (c.comTelefone()) {
            cond.add("e.telefone1 IS NOT NULL");
        }
        if (c.nome() != null) {
            cond.add("(e.nome_fantasia ILIKE :nome OR emp.razao_social ILIKE :nome)");
            p.put("nome", "%" + c.nome() + "%");
        }
        return new Filtro(cond.isEmpty() ? "TRUE" : String.join(" AND ", cond), p);
    }

    private long contar(Filtro filtro) {
        String sql = "SELECT count(*) FROM (SELECT 1" + FROM + "WHERE " + filtro.where()
                + " LIMIT " + (LIMITE_CONTAGEM + 1) + ") t";
        return jdbc.sql(sql).params(filtro.params()).query(Long.class).single();
    }

    private static void exigirFiltro(CriteriosBusca c) {
        if (c.cnaes().isEmpty() && c.ufs().isEmpty() && c.municipios().isEmpty() && c.nome() == null) {
            throw new RequisicaoInvalidaException("Informe ao menos um filtro: cnae, uf, municipio ou nome.");
        }
    }

    // ------------------------------------------------------------------ mapeamento

    private Linha mapear(ResultSet rs, boolean contatos) throws SQLException {
        String cnpj = rs.getString("cnpj");
        String situacao = rs.getString("situacao_cadastral");
        String porte = rs.getString("porte");
        String ddd = rs.getString("ddd1");
        String telefone1 = rs.getString("telefone1");
        String email = rs.getString("email");
        String telefone = telefone1 == null || telefone1.isBlank() ? null
                : (ddd == null || ddd.isBlank() ? telefone1 : "(" + ddd + ") " + telefone1);

        ItemEstabelecimento item = new ItemEstabelecimento(
                cnpj,
                Cnpj.formatar(cnpj),
                rs.getBoolean("matriz"),
                rs.getString("razao_social"),
                rs.getString("nome_fantasia"),
                situacao,
                EstabelecimentoConsulta.descricaoSituacao(situacao),
                rs.getObject("data_inicio_atividade", LocalDate.class),
                rs.getString("cnae_principal"),
                porte,
                EstabelecimentoConsulta.descricaoPorte(porte),
                juntar(rs.getString("tipo_logradouro"), rs.getString("logradouro")),
                rs.getString("numero"),
                rs.getString("complemento"),
                rs.getString("bairro"),
                rs.getString("cep"),
                rs.getString("uf"),
                (Integer) rs.getObject("municipio_ibge"),
                rs.getString("municipio"),
                email != null,
                telefone != null,
                contatos ? telefone : null,
                contatos ? email : null);
        return new Linha(item, rs.getObject("abertura_ord", LocalDate.class));
    }

    private static String juntar(String a, String b) {
        if (a == null || a.isBlank()) {
            return b;
        }
        return b == null || b.isBlank() ? a : a + " " + b;
    }

    private String rotuloMunicipio(Integer ibge) {
        if (ibge == null) {
            return "Sem município";
        }
        return referencia.buscarMunicipioPorIbge(ibge).map(m -> m.nome() + "/" + m.ufSigla()).orElse(null);
    }

    private String rotuloCnae(String codigo) {
        if (codigo == null) {
            return "Sem CNAE";
        }
        return referencia.buscarCnae(codigo).map(c -> c.descricao()).orElse(null);
    }

    // ------------------------------------------------------------------ tipos internos

    private record Filtro(String where, Map<String, Object> params) {
    }

    private record Linha(ItemEstabelecimento item, LocalDate ordem) {
    }

    private record Faceta3(Integer mun, String cnae, String porte, int gMun, int gCnae, int gPorte, long total) {
    }

    private record Cursor(LocalDate data, String cnpj) {

        static String codificar(LocalDate data, String cnpj) {
            LocalDate d = data == null ? SEM_DATA : data;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((d + "|" + cnpj).getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decodificar(String texto) {
            try {
                String bruto = new String(Base64.getUrlDecoder().decode(texto), StandardCharsets.UTF_8);
                int barra = bruto.indexOf('|');
                String cnpj = bruto.substring(barra + 1);
                if (barra < 0 || !CNPJ.matcher(cnpj).matches()) {
                    throw new IllegalArgumentException("formato");
                }
                return new Cursor(LocalDate.parse(bruto.substring(0, barra)), cnpj);
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw new RequisicaoInvalidaException("Cursor inválido.");
            }
        }
    }
}