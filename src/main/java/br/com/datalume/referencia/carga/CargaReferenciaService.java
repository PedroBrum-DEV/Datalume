package br.com.datalume.referencia.carga;

import br.com.datalume.compartilhado.erro.RegraDeNegocioException;
import br.com.datalume.compartilhado.texto.Textos;
import br.com.datalume.referencia.ReferenciaConsulta;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cargas das tabelas de referência. Todas são idempotentes (upsert) e podem ser repetidas.
 * Ordem recomendada: municípios IBGE -> CNAEs -> municípios da Receita -> (opcional) tabela de conversão.
 */
@Service
class CargaReferenciaService {

    private static final Logger log = LoggerFactory.getLogger(CargaReferenciaService.class);

    private final IbgeClient ibge;
    private final JdbcTemplate jdbc;

    CargaReferenciaService(IbgeClient ibge, JdbcTemplate jdbc) {
        this.ibge = ibge;
        this.jdbc = jdbc;
    }

    @Transactional
    ResultadoCarga carregarMunicipiosIbge() {
        JsonNode raiz = ibge.municipios();
        if (raiz == null || !raiz.isArray() || raiz.size() < 5000) {
            throw new RegraDeNegocioException("Resposta inesperada da API de municípios do IBGE.");
        }
        Map<Integer, String> siglaPorCodigoUf = new HashMap<>();
        jdbc.query("SELECT codigo_ibge, sigla FROM uf",
                rs -> {
                    siglaPorCodigoUf.put(rs.getInt(1), rs.getString(2));
                });

        List<Object[]> linhas = new ArrayList<>();
        int ignorados = 0;
        for (JsonNode no : raiz) {
            int codigo = no.path("id").asInt();
            String nome = no.path("nome").asText();
            String uf = siglaPorCodigoUf.get(codigo / 100000);
            if (codigo == 0 || nome.isBlank() || uf == null) {
                ignorados++;
                continue;
            }
            linhas.add(new Object[] {codigo, nome, uf, Textos.normalizar(nome)});
        }
        jdbc.batchUpdate("""
                INSERT INTO municipio (codigo_ibge, nome, uf_sigla, nome_normalizado) VALUES (?, ?, ?, ?)
                ON CONFLICT (codigo_ibge) DO UPDATE
                   SET nome = EXCLUDED.nome, uf_sigla = EXCLUDED.uf_sigla,
                       nome_normalizado = EXCLUDED.nome_normalizado
                """, linhas);
        log.info("Municípios IBGE carregados: {} ({} ignorados)", linhas.size(), ignorados);
        return new ResultadoCarga("IBGE/municipios", linhas.size(), ignorados, null);
    }

    @Transactional
    ResultadoCarga carregarCnaesIbge() {
        JsonNode raiz = ibge.cnaeSubclasses();
        if (raiz == null || !raiz.isArray() || raiz.size() < 1000) {
            throw new RegraDeNegocioException("Resposta inesperada da API de CNAE do IBGE.");
        }
        List<Object[]> linhas = new ArrayList<>();
        int ignorados = 0;
        for (JsonNode no : raiz) {
            String codigo = Textos.somenteDigitos(no.path("id").asText());
            String descricao = no.path("descricao").asText();
            if (codigo.length() != 7 || descricao.isBlank()) {
                ignorados++;
                continue;
            }
            String secao = no.path("classe").path("grupo").path("divisao").path("secao").path("id").asText("");
            linhas.add(new Object[] {
                    codigo, descricao.trim(),
                    codigo.substring(0, 5), codigo.substring(0, 3), codigo.substring(0, 2),
                    secao.isBlank() ? null : secao});
        }
        jdbc.batchUpdate("""
                INSERT INTO cnae_subclasse (codigo, descricao, classe, grupo, divisao, secao)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (codigo) DO UPDATE
                   SET descricao = EXCLUDED.descricao, classe = EXCLUDED.classe, grupo = EXCLUDED.grupo,
                       divisao = EXCLUDED.divisao, secao = EXCLUDED.secao
                """, linhas);
        log.info("CNAEs carregados: {} ({} ignorados)", linhas.size(), ignorados);
        return new ResultadoCarga("IBGE/cnae-subclasses", linhas.size(), ignorados, null);
    }

    /**
     * Importa o arquivo Municipios.csv da Receita (sem cabeçalho, formato "0001";"NOME", ISO-8859-1)
     * e concilia com o IBGE pelo nome quando o nome é único no país. Os demais ficam sem código IBGE
     * e são resolvidos por nome + UF em {@link ReferenciaConsulta#municipioIbgeDaReceita}.
     */
    @Transactional
    ResultadoCarga importarMunicipiosReceita(InputStream entrada, Charset charset) {
        int processados = 0;
        int ignorados = 0;
        for (List<String> campos : lerCsv(entrada, charset)) {
            if (campos.size() < 2 || !campos.get(0).matches("\\d{1,4}") || campos.get(1).isBlank()) {
                ignorados++;
                continue;
            }
            String codigo = ReferenciaConsulta.padronizarCodigoReceita(campos.get(0));
            String nome = campos.get(1);
            jdbc.update("""
                    INSERT INTO municipio_receita (codigo_receita, nome_receita, nome_normalizado, origem)
                    VALUES (?, ?, ?, 'RECEITA')
                    ON CONFLICT (codigo_receita) DO UPDATE
                       SET nome_receita = EXCLUDED.nome_receita, nome_normalizado = EXCLUDED.nome_normalizado
                    """, codigo, nome, Textos.normalizar(nome));
            processados++;
        }
        int conciliados = conciliarPorNomeUnico();
        String obs = conciliados + " conciliados por nome único; " + pendentes() + " ainda sem código IBGE";
        log.info("Municípios Receita: {} ({} ignorados). {}", processados, ignorados, obs);
        return new ResultadoCarga("Receita/Municipios.csv", processados, ignorados, obs);
    }

    /**
     * Importa uma tabela de conversão oficial/curada: CSV com cabeçalho e colunas
     * {@code codigo_receita;codigo_ibge}. Linhas cujo código IBGE não existe em {@code municipio}
     * são ignoradas (carregue o IBGE antes).
     */
    @Transactional
    ResultadoCarga importarConversao(InputStream entrada, Charset charset) {
        int processados = 0;
        int ignorados = 0;
        for (List<String> campos : lerCsv(entrada, charset)) {
            if (campos.size() < 2 || !campos.get(0).matches("\\d{1,4}") || !campos.get(1).matches("\\d{7}")) {
                ignorados++; // cabeçalho ou linha inválida
                continue;
            }
            String codigoReceita = ReferenciaConsulta.padronizarCodigoReceita(campos.get(0));
            int codigoIbge = Integer.parseInt(campos.get(1));
            int linhas = jdbc.update("""
                    INSERT INTO municipio_receita (codigo_receita, nome_receita, nome_normalizado, uf_sigla, codigo_ibge, origem)
                    SELECT ?, m.nome, m.nome_normalizado, m.uf_sigla, m.codigo_ibge, 'TABELA'
                    FROM municipio m WHERE m.codigo_ibge = ?
                    ON CONFLICT (codigo_receita) DO UPDATE
                       SET codigo_ibge = EXCLUDED.codigo_ibge, uf_sigla = EXCLUDED.uf_sigla, origem = 'TABELA'
                    """, codigoReceita, codigoIbge);
            if (linhas == 1) {
                processados++;
            } else {
                ignorados++;
            }
        }
        log.info("Conversão Receita->IBGE importada: {} ({} ignorados)", processados, ignorados);
        return new ResultadoCarga("Tabela de conversão", processados, ignorados, null);
    }

    int conciliarPorNomeUnico() {
        return jdbc.update("""
                UPDATE municipio_receita r
                   SET codigo_ibge = m.codigo_ibge, uf_sigla = m.uf_sigla, origem = 'NOME_UNICO'
                  FROM municipio m
                 WHERE r.codigo_ibge IS NULL
                   AND m.nome_normalizado = r.nome_normalizado
                   AND (SELECT count(*) FROM municipio x WHERE x.nome_normalizado = m.nome_normalizado) = 1
                """);
    }

    Map<String, Long> resumo() {
        Map<String, Long> r = new java.util.LinkedHashMap<>();
        r.put("ufs", contar("SELECT count(*) FROM uf"));
        r.put("municipios", contar("SELECT count(*) FROM municipio"));
        r.put("municipiosReceita", contar("SELECT count(*) FROM municipio_receita"));
        r.put("municipiosReceitaSemIbge", pendentes());
        r.put("cnaesSubclasses", contar("SELECT count(*) FROM cnae_subclasse"));
        return r;
    }

    boolean tabelaVazia(String tabela) {
        return switch (tabela) {
            case "municipio", "cnae_subclasse" -> contar("SELECT count(*) FROM " + tabela) == 0;
            default -> throw new IllegalArgumentException(tabela);
        };
    }

    private long pendentes() {
        return contar("SELECT count(*) FROM municipio_receita WHERE codigo_ibge IS NULL");
    }

    private long contar(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    /** Parser mínimo: separador ';', aspas opcionais, BOM ignorado, linhas vazias puladas. */
    static List<List<String>> lerCsv(InputStream entrada, Charset charset) {
        List<List<String>> linhas = new ArrayList<>();
        try (BufferedReader leitor = new BufferedReader(new InputStreamReader(entrada, charset))) {
            String linha;
            boolean primeira = true;
            while ((linha = leitor.readLine()) != null) {
                if (primeira) {
                    linha = linha.replace("\uFEFF", "");
                    primeira = false;
                }
                if (linha.isBlank()) {
                    continue;
                }
                List<String> campos = new ArrayList<>();
                for (String parte : linha.split(";", -1)) {
                    campos.add(parte.trim().replaceAll("^\"|\"$", "").trim());
                }
                linhas.add(campos);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o CSV", e);
        }
        return linhas;
    }
}
