package br.com.datalume.estabelecimento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.datalume.TestcontainersConfig;
import br.com.datalume.estabelecimento.EstabelecimentoCarga.Destino;
import br.com.datalume.estabelecimento.EstabelecimentoCarga.Resultado;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfig.class)
class EstabelecimentoCargaIntegrationTest {

    private static final String REF = "2026-10";
    private static final String CNPJ = "11222333000181";
    private static final FiltroImportacao SEM_FILTRO = FiltroImportacao.semFiltro();

    @Autowired
    EstabelecimentoCarga carga;

    @Autowired
    EstabelecimentoConsulta consulta;

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        limpar();
        jdbc.update("INSERT INTO municipio (codigo_ibge, nome, uf_sigla, nome_normalizado) "
                + "VALUES (3550308, 'São Paulo', 'SP', 'SAO PAULO') ON CONFLICT DO NOTHING");
        jdbc.update("""
                INSERT INTO municipio_receita (codigo_receita, nome_receita, nome_normalizado, uf_sigla, codigo_ibge, origem)
                VALUES ('7107', 'SAO PAULO', 'SAO PAULO', 'SP', 3550308, 'TABELA')
                ON CONFLICT (codigo_receita) DO UPDATE SET codigo_ibge = 3550308
                """);
        jdbc.update("INSERT INTO cnae_subclasse (codigo, descricao, classe, grupo, divisao, secao) "
                + "VALUES ('8630504', 'Atividade odontológica', '86305', '863', '86', 'Q') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO natureza_juridica (codigo, descricao) VALUES ('2062', 'Sociedade Empresária Limitada') "
                + "ON CONFLICT DO NOTHING");
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM estabelecimento WHERE cnpj_basico IN ('11222333', '55666777')");
        jdbc.update("DELETE FROM empresa WHERE cnpj_basico IN ('11222333', '55666777', '99999999')");
        jdbc.update("DELETE FROM municipio_receita WHERE codigo_receita = '9998'");
        jdbc.update("DELETE FROM municipio WHERE codigo_ibge IN (9999912, 9999913)");
    }

    @Test
    void carregaEstabelecimentoEEmpresaEConsultaPorCnpj() throws Exception {
        Resultado e = importar(Destino.ESTABELECIMENTO, estab("11222333", "0001", "81", "CLINICA SORRISO", "SP", "7107"), SEM_FILTRO);
        assertThat(e.inseridos()).isEqualTo(1);
        Resultado emp = importar(Destino.EMPRESA, empresa("11222333", "CLINICA SORRISO LTDA"), SEM_FILTRO);
        assertThat(emp.inseridos()).isEqualTo(1);

        EstabelecimentoDetalhe d = consulta.buscarPorCnpj(CNPJ).orElseThrow();
        assertThat(d.cnpjFormatado()).isEqualTo("11.222.333/0001-81");
        assertThat(d.matriz()).isTrue();
        assertThat(d.razaoSocial()).isEqualTo("CLINICA SORRISO LTDA");
        assertThat(d.nomeFantasia()).isEqualTo("CLINICA SORRISO");
        assertThat(d.situacao().codigo()).isEqualTo("02");
        assertThat(d.situacao().descricao()).isEqualTo("Ativa");
        assertThat(d.dataInicioAtividade()).isEqualTo(LocalDate.of(2010, 3, 5));
        assertThat(d.capitalSocial()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(d.porteDescricao()).isEqualTo("Empresa de pequeno porte");
        assertThat(d.cnaePrincipal().codigo()).isEqualTo("8630504");
        assertThat(d.cnaePrincipal().descricao()).isEqualTo("Atividade odontológica");
        assertThat(d.cnaesSecundarios()).containsExactly("4789099", "8630503");
        assertThat(d.endereco().bairro()).isEqualTo("CONSOLAÇÃO"); // acento preservado (LATIN1 -> UTF-8)
        assertThat(d.endereco().cep()).isEqualTo("01304001");
        assertThat(d.endereco().municipioIbge()).isEqualTo(3550308);
        assertThat(d.endereco().municipio()).isEqualTo("São Paulo");
        assertThat(d.contato().telefone1()).isEqualTo("(11) 999990000");
        assertThat(d.contato().email()).isEqualTo("contato@exemplo.com.br");
        assertThat(d.referenciaAlteracao()).isEqualTo(REF);
    }

    @Test
    void reimportacaoEIdempotenteEAtualizaSomenteQuemMudou() throws Exception {
        String csv = estab("11222333", "0001", "81", "CLINICA SORRISO", "SP", "7107");
        assertThat(importar(Destino.ESTABELECIMENTO, csv, SEM_FILTRO)).isEqualTo(new Resultado(1, 0));
        assertThat(importar(Destino.ESTABELECIMENTO, csv, SEM_FILTRO)).isEqualTo(new Resultado(0, 0));

        String alterado = estab("11222333", "0001", "81", "CLINICA SORRISO PREMIUM", "SP", "7107");
        assertThat(importar(Destino.ESTABELECIMENTO, alterado, SEM_FILTRO)).isEqualTo(new Resultado(0, 1));
        assertThat(consulta.buscarPorCnpj(CNPJ).orElseThrow().nomeFantasia()).isEqualTo("CLINICA SORRISO PREMIUM");
    }

    @Test
    void filtroPorUfEPorCnaeRestringeOQueEntra() throws Exception {
        String csv = estab("11222333", "0001", "81", "CLINICA SORRISO", "SP", "7107");

        assertThat(importar(Destino.ESTABELECIMENTO, csv, new FiltroImportacao(List.of("RJ"), List.of())).inseridos())
                .isZero();
        assertThat(importar(Destino.ESTABELECIMENTO, csv, new FiltroImportacao(List.of("SP"), List.of("4635"))).inseridos())
                .isZero();
        assertThat(importar(Destino.ESTABELECIMENTO, csv, new FiltroImportacao(List.of("SP"), List.of("8630"))).inseridos())
                .isEqualTo(1);
    }

    @Test
    void empresaSemEstabelecimentoCarregadoNaoEntra() throws Exception {
        assertThat(importar(Destino.EMPRESA, empresa("99999999", "EMPRESA FORA DO FILTRO"), SEM_FILTRO).inseridos())
                .isZero();
    }

    @Test
    void resolveMunicipioPorNomeEUfQuandoNaoHaConversaoDireta() throws Exception {
        String sql = "INSERT INTO municipio (codigo_ibge, nome, uf_sigla, nome_normalizado) VALUES (?, 'Vila Fantasma', ?, 'VILA FANTASMA')";
        jdbc.update(sql, 9999912, "SP");
        jdbc.update(sql, 9999913, "RJ");
        jdbc.update("INSERT INTO municipio_receita (codigo_receita, nome_receita, nome_normalizado, origem) "
                + "VALUES ('9998', 'VILA FANTASMA', 'VILA FANTASMA', 'RECEITA')");

        importar(Destino.ESTABELECIMENTO, estab("55666777", "0001", "99", "LOJA TESTE", "RJ", "9998"), SEM_FILTRO);

        Integer ibge = jdbc.queryForObject(
                "SELECT municipio_ibge FROM estabelecimento WHERE cnpj = '55666777000199'", Integer.class);
        assertThat(ibge).isEqualTo(9999913);
    }

    @Test
    void carregaTabelasDeCodigos() throws Exception {
        Resultado r = importar(Destino.NATUREZA_JURIDICA, "\"2062\";\"Sociedade Empresária Limitada\"\n", SEM_FILTRO);
        assertThat(r.inseridos() + r.atualizados()).isLessThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT descricao FROM natureza_juridica WHERE codigo = '2062'", String.class))
                .isEqualTo("Sociedade Empresária Limitada");
    }

    // ------------------------------------------------------------------ helpers

    private Resultado importar(Destino destino, String csv, FiltroImportacao filtro) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            carga.prepararStaging(c, destino);
            c.unwrap(PGConnection.class).getCopyAPI().copyIn(carga.comandoCopy(destino),
                    new ByteArrayInputStream(csv.getBytes(StandardCharsets.ISO_8859_1)));
            Resultado r = carga.consolidar(c, destino, REF, filtro);
            c.commit();
            return r;
        }
    }

    /** Linha de 30 colunas no layout dos arquivos Estabelecimentos da Receita (todas entre aspas). */
    private static String estab(String basico, String ordem, String dv, String fantasia, String uf, String municipio) {
        List<String> campos = List.of(
                basico, ordem, dv, "1", fantasia, "02", "20200115", "00", "", "", "20100305",
                "8630504", "4789099,8630503", "RUA", "AUGUSTA", "1000", "SALA 2", "CONSOLAÇÃO", "01304-001",
                uf, municipio, "11", "999990000", "", "", "", "", "CONTATO@EXEMPLO.COM.BR", "", "");
        return linha(campos);
    }

    private static String empresa(String basico, String razao) {
        return linha(List.of(basico, razao, "2062", "49", "150000,00", "03", ""));
    }

    private static String linha(List<String> campos) {
        return campos.stream().map(v -> "\"" + v + "\"").collect(Collectors.joining(";")) + "\n";
    }
}