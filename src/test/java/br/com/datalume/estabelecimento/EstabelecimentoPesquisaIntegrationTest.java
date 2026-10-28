package br.com.datalume.estabelecimento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.datalume.TestcontainersConfig;
import br.com.datalume.compartilhado.erro.RequisicaoInvalidaException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfig.class)
class EstabelecimentoPesquisaIntegrationTest {

    private static final String MARCA = "0000-77";

    @Autowired
    EstabelecimentoPesquisa pesquisa;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void fixtures() {
        limpar();
        for (String c : List.of("9990001", "9990002", "9990003")) {
            jdbc.update("INSERT INTO cnae_subclasse (codigo, descricao, classe, grupo, divisao, secao) "
                    + "VALUES (?, ?, '99900', '999', '99', 'Z') ON CONFLICT DO NOTHING", c, "Teste " + c);
        }
        // 120 estabelecimentos "LOJA TESTE n": cnae 9990001, SP, ativa, porte 01, abertura = 2020-01-01 + n dias
        jdbc.update("""
                INSERT INTO estabelecimento (cnpj, cnpj_basico, cnpj_ordem, cnpj_dv, matriz, nome_fantasia,
                    situacao_cadastral, data_inicio_atividade, cnae_principal, uf, municipio_ibge, email,
                    telefone1, referencia_alteracao, row_hash)
                SELECT 'P' || lpad(i::text, 7, '0') || '000100', 'P' || lpad(i::text, 7, '0'), '0001', '00', TRUE,
                       'LOJA TESTE ' || i, '02', DATE '2020-01-01' + i, '9990001', 'SP', 3550308,
                       CASE WHEN i % 2 = 0 THEN 'loja' || i || '@exemplo.com.br' END, '999990000', ?, 'h'
                FROM generate_series(1, 120) i
                """, MARCA);
        jdbc.update("""
                INSERT INTO empresa (cnpj_basico, razao_social, porte, referencia_alteracao, row_hash)
                SELECT 'P' || lpad(i::text, 7, '0'), 'EMPRESA TESTE ' || i, '01', ?, 'h'
                FROM generate_series(1, 120) i
                """, MARCA);

        extra("Q0000001", "PADARIA ESTRELA", "02", "2015-05-05", "9990002", "{9990001}", "RJ", 3304557, "EMPRESA ESTRELA LTDA", "03");
        extra("Q0000002", "BAIXADA LTDA", "08", "2010-01-01", "9990003", null, "SP", 3550308, "BAIXADA EMPRESARIAL", "01");
        extra("Q0000003", "SEM DATA", "02", null, "9990003", null, "SP", 3550308, "SEM DATA SA", "05");
    }

    private void extra(String basico, String fantasia, String situacao, String data, String cnae, String sec,
            String uf, int municipio, String razao, String porte) {
        jdbc.update("""
                INSERT INTO estabelecimento (cnpj, cnpj_basico, cnpj_ordem, cnpj_dv, matriz, nome_fantasia,
                    situacao_cadastral, data_inicio_atividade, cnae_principal, cnaes_secundarios, uf,
                    municipio_ibge, referencia_alteracao, row_hash)
                VALUES (?, ?, '0001', '00', TRUE, ?, ?, CAST(? AS date), ?, CAST(? AS text[]), ?, ?, ?, 'h')
                """, basico + "000100", basico, fantasia, situacao, data, cnae, sec, uf, municipio, MARCA);
        jdbc.update("INSERT INTO empresa (cnpj_basico, razao_social, porte, referencia_alteracao, row_hash) "
                + "VALUES (?, ?, ?, ?, 'h')", basico, razao, porte, MARCA);
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM estabelecimento WHERE referencia_alteracao = ?", MARCA);
        jdbc.update("DELETE FROM empresa WHERE referencia_alteracao = ?", MARCA);
        jdbc.update("DELETE FROM cnae_subclasse WHERE codigo IN ('9990001', '9990002', '9990003')");
    }

    private static CriteriosBusca criterios(List<String> cnaes, boolean sec, List<String> ufs, List<Integer> mun,
            List<String> situacoes, List<String> portes, String nome) {
        return new CriteriosBusca(cnaes, sec, ufs, mun, situacoes, portes, null, null, null, false, false, nome);
    }

    private static CriteriosBusca cnae(String... prefixos) {
        return criterios(List.of(prefixos), false, null, null, null, null, null);
    }

    @Test
    void prefixoDeCnaeUfESituacaoPadraoSoAtivas() {
        PaginaEstabelecimentos p = pesquisa.pesquisar(
                criterios(List.of("99900"), false, List.of("SP"), null, null, null, null), 200, null, false);

        assertThat(p.itens()).hasSize(121); // 120 lojas + "SEM DATA"; RJ e baixada ficam de fora
        assertThat(p.total()).isEqualTo(121);
        assertThat(p.totalExato()).isTrue();

        PaginaEstabelecimentos todas = pesquisa.pesquisar(
                criterios(List.of("99900"), false, List.of("SP"), null, List.of(), null, null), 200, null, false);
        assertThat(todas.itens()).hasSize(122); // inclui a baixada
    }

    @Test
    void secundariosSoEntramQuandoSolicitado() {
        assertThat(pesquisa.pesquisar(criterios(List.of("9990001"), false, null, null, null, null, null), 200, null, false)
                .itens()).hasSize(120);
        assertThat(pesquisa.pesquisar(criterios(List.of("9990001"), true, null, null, null, null, null), 200, null, false)
                .itens()).hasSize(121); // + PADARIA ESTRELA (secundario 9990001)
    }

    @Test
    void filtraPorMunicipioPorteNomeEAbertura() {
        assertThat(pesquisa.pesquisar(criterios(List.of("99900"), false, null, List.of(3304557), null, null, null),
                50, null, false).itens()).extracting(ItemEstabelecimento::nomeFantasia).containsExactly("PADARIA ESTRELA");

        assertThat(pesquisa.pesquisar(criterios(List.of("99900"), false, null, null, null, List.of("3"), null),
                50, null, false).itens()).extracting(ItemEstabelecimento::cnpj).containsExactly("Q0000001000100");

        assertThat(pesquisa.pesquisar(criterios(List.of("99900"), false, null, null, null, null, "estrela"),
                50, null, false).itens()).extracting(ItemEstabelecimento::nomeFantasia).containsExactly("PADARIA ESTRELA");

        CriteriosBusca fevereiro = new CriteriosBusca(List.of("9990001"), false, null, null, null, null, null,
                LocalDate.of(2020, 2, 1), LocalDate.of(2020, 2, 10), false, false, null);
        assertThat(pesquisa.pesquisar(fevereiro, 50, null, false).itens()).hasSize(10);
    }

    @Test
    void paginacaoPorCursorNaoRepeteNemPerdeItens() {
        CriteriosBusca c = criterios(List.of("99900"), false, List.of("SP"), null, null, null, null);
        Set<String> vistos = new HashSet<>();
        List<ItemEstabelecimento> todos = new ArrayList<>();
        String cursor = null;
        int paginas = 0;
        do {
            PaginaEstabelecimentos p = pesquisa.pesquisar(c, 50, cursor, false);
            if (cursor == null) {
                assertThat(p.total()).isEqualTo(121);
            } else {
                assertThat(p.total()).isNull();
            }
            p.itens().forEach(i -> assertThat(vistos.add(i.cnpj())).as("repetido: " + i.cnpj()).isTrue());
            todos.addAll(p.itens());
            cursor = p.proximoCursor();
            paginas++;
        } while (cursor != null);

        assertThat(paginas).isEqualTo(3);
        assertThat(todos).hasSize(121);
        // mais recente primeiro; sem data por ultimo
        assertThat(todos.get(0).dataInicioAtividade()).isEqualTo(LocalDate.of(2020, 1, 1).plusDays(120));
        assertThat(todos.get(todos.size() - 1).nomeFantasia()).isEqualTo("SEM DATA");
        for (int i = 1; i < todos.size() - 1; i++) {
            assertThat(todos.get(i).dataInicioAtividade()).isBeforeOrEqualTo(todos.get(i - 1).dataInicioAtividade());
        }
    }

    @Test
    void contatosSoVemQuandoPedidos() {
        CriteriosBusca c = cnae("9990001");
        assertThat(pesquisa.pesquisar(c, 50, null, false).itens())
                .allSatisfy(i -> {
                    assertThat(i.email()).isNull();
                    assertThat(i.telefone()).isNull();
                })
                .anyMatch(ItemEstabelecimento::temEmail);
        assertThat(pesquisa.pesquisar(c, 50, null, true).itens()).anyMatch(i -> i.email() != null);
    }

    @Test
    void facetasAgrupamPorMunicipioCnaeEPorte() {
        Facetas f = pesquisa.facetas(criterios(List.of("99900"), false, List.of("SP"), null, null, null, null));

        assertThat(f.parcial()).isFalse();
        assertThat(f.municipios().get(0).chave()).isEqualTo("3550308");
        assertThat(f.municipios().get(0).total()).isEqualTo(121);
        assertThat(f.cnaes()).extracting(Facetas.Faceta::chave).contains("9990001", "9990003");
        assertThat(f.cnaes().stream().filter(x -> x.chave().equals("9990001")).findFirst().orElseThrow().total())
                .isEqualTo(120);
        assertThat(f.portes()).extracting(Facetas.Faceta::chave).contains("01", "05");
    }

    @Test
    void exigeAoMenosUmFiltroERejeitaEntradasInvalidas() {
        CriteriosBusca vazio = criterios(null, false, null, null, null, null, null);
        assertThatThrownBy(() -> pesquisa.pesquisar(vazio, 10, null, false))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> criterios(List.of("99900"), false, null, null, null, null, "ab"))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> pesquisa.pesquisar(cnae("99900"), 10, "cursor-invalido", false))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void exportarRespeitaOLimite() {
        List<String> recebidos = new ArrayList<>();
        long n = pesquisa.exportar(cnae("9990001"), 70, false, i -> recebidos.add(i.cnpj()));
        assertThat(n).isEqualTo(70);
        assertThat(new HashSet<>(recebidos)).hasSize(70);

        List<String> todos = new ArrayList<>();
        assertThat(pesquisa.exportar(cnae("9990001"), 5000, false, i -> todos.add(i.cnpj()))).isEqualTo(120);
    }
}