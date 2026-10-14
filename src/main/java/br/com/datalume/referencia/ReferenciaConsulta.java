package br.com.datalume.referencia;

import br.com.datalume.compartilhado.texto.Textos;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pública de leitura das tabelas de referência. */
@Service
@Transactional(readOnly = true)
public class ReferenciaConsulta {

    private final JdbcClient jdbc;

    public ReferenciaConsulta(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Uf> listarUfs() {
        return jdbc.sql("SELECT codigo_ibge, sigla, nome, regiao FROM uf ORDER BY sigla")
                .query(Uf.class).list();
    }

    public Optional<Uf> buscarUf(String sigla) {
        return jdbc.sql("SELECT codigo_ibge, sigla, nome, regiao FROM uf WHERE sigla = :sigla")
                .param("sigla", sigla == null ? "" : sigla.trim().toUpperCase())
                .query(Uf.class).optional();
    }

    public List<Municipio> listarMunicipios(String ufSigla) {
        return jdbc.sql("""
                        SELECT codigo_ibge, nome, uf_sigla FROM municipio
                        WHERE uf_sigla = :uf ORDER BY nome_normalizado
                        """)
                .param("uf", ufSigla.trim().toUpperCase())
                .query(Municipio.class).list();
    }

    /** Busca por parte do nome (sem acento/caixa), opcionalmente restrita a uma UF. */
    public List<Municipio> buscarMunicipios(String termo, String ufSigla, int limite) {
        String padrao = "%" + Textos.normalizar(termo) + "%";
        int lim = Math.min(Math.max(limite, 1), 100);
        if (ufSigla == null || ufSigla.isBlank()) {
            return jdbc.sql("""
                            SELECT codigo_ibge, nome, uf_sigla FROM municipio
                            WHERE nome_normalizado LIKE :padrao
                            ORDER BY nome_normalizado LIMIT :limite
                            """)
                    .param("padrao", padrao)
                    .param("limite", lim)
                    .query(Municipio.class).list();
        }
        return jdbc.sql("""
                        SELECT codigo_ibge, nome, uf_sigla FROM municipio
                        WHERE nome_normalizado LIKE :padrao AND uf_sigla = :uf
                        ORDER BY nome_normalizado LIMIT :limite
                        """)
                .param("padrao", padrao)
                .param("uf", ufSigla.trim().toUpperCase())
                .param("limite", lim)
                .query(Municipio.class).list();
    }

    public Optional<Municipio> buscarMunicipioPorIbge(int codigoIbge) {
        return jdbc.sql("SELECT codigo_ibge, nome, uf_sigla FROM municipio WHERE codigo_ibge = :c")
                .param("c", codigoIbge)
                .query(Municipio.class).optional();
    }

    /**
     * Converte o código de município da Receita (4 dígitos) para o código IBGE.
     * Usa a conversão armazenada; se ela ainda não existir, tenta casar por nome + UF
     * (a UF vem do próprio estabelecimento), o que resolve nomes repetidos entre estados.
     */
    public Optional<Integer> municipioIbgeDaReceita(String codigoReceita, String ufSigla) {
        String codigo = padronizarCodigoReceita(codigoReceita);
        Optional<Integer> direto = jdbc.sql(
                        "SELECT codigo_ibge FROM municipio_receita WHERE codigo_receita = :c AND codigo_ibge IS NOT NULL")
                .param("c", codigo)
                .query(Integer.class).optional();
        if (direto.isPresent() || ufSigla == null || ufSigla.isBlank()) {
            return direto;
        }
        return jdbc.sql("""
                        SELECT m.codigo_ibge
                        FROM municipio_receita r
                        JOIN municipio m ON m.nome_normalizado = r.nome_normalizado AND m.uf_sigla = :uf
                        WHERE r.codigo_receita = :c
                        """)
                .param("c", codigo)
                .param("uf", ufSigla.trim().toUpperCase())
                .query(Integer.class).optional();
    }

    /** Aceita com ou sem pontuação: "8630-5/04", "8630504". */
    public Optional<Cnae> buscarCnae(String codigo) {
        return jdbc.sql("""
                        SELECT codigo, descricao, classe, grupo, divisao, secao
                        FROM cnae_subclasse WHERE codigo = :c
                        """)
                .param("c", Textos.somenteDigitos(codigo))
                .query(Cnae.class).optional();
    }

    /** Termo numérico = prefixo do código; caso contrário, busca na descrição (sem acento/caixa). */
    public List<Cnae> buscarCnaes(String termo, int limite) {
        int lim = Math.min(Math.max(limite, 1), 100);
        String digitos = Textos.somenteDigitos(termo);
        boolean numerico = !digitos.isEmpty() && termo.matches("[\\d\\s./-]+");
        if (numerico) {
            return jdbc.sql("""
                            SELECT codigo, descricao, classe, grupo, divisao, secao FROM cnae_subclasse
                            WHERE codigo LIKE :p ORDER BY codigo LIMIT :limite
                            """)
                    .param("p", digitos + "%")
                    .param("limite", lim)
                    .query(Cnae.class).list();
        }
        String texto = termo.replace("%", "").replace("_", "").trim();
        return jdbc.sql("""
                        SELECT codigo, descricao, classe, grupo, divisao, secao FROM cnae_subclasse
                        WHERE unaccent(descricao) ILIKE unaccent('%' || :t || '%')
                        ORDER BY codigo LIMIT :limite
                        """)
                .param("t", texto)
                .param("limite", lim)
                .query(Cnae.class).list();
    }

    public static String padronizarCodigoReceita(String codigo) {
        String d = Textos.somenteDigitos(codigo);
        return d.length() >= 4 ? d : "0".repeat(4 - d.length()) + d;
    }
}
