package br.com.datalume.estabelecimento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.datalume.TestcontainersConfig;
import br.com.datalume.estabelecimento.EstabelecimentoCarga.Destino;
import br.com.datalume.estabelecimento.EstabelecimentoMudancas.MudancaDetalhada;
import br.com.datalume.estabelecimento.EstabelecimentoMudancas.ResultadoMudancas;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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
class EstabelecimentoEventosIntegrationTest {

    private static final String REF1 = "2026-09";
    private static final String REF2 = "2026-10";
    private static final FiltroImportacao SEM_FILTRO = FiltroImportacao.semFiltro();

    @Autowired
    EstabelecimentoCarga carga;

    @Autowired
    EstabelecimentoMudancas mudancas;

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void preparar() throws Exception {
        limpar();
        jdbc.update("INSERT INTO municipio (codigo_ibge, nome, uf_sigla, nome_normalizado) "
                + "VALUES (3550308, 'São Paulo', 'SP', 'SAO PAULO') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO municipio (codigo_ibge, nome, uf_sigla, nome_normalizado) "
                + "VALUES (3304557, 'Rio de Janeiro', 'RJ', 'RIO DE JANEIRO') ON CONFLICT DO NOTHING");
        jdbc.update("""
                INSERT INTO municipio_receita (codigo_receita, nome_receita, nome_normalizado, uf_sigla, codigo_ibge, origem)
                VALUES ('7107', 'SAO PAULO', 'SAO PAULO', 'SP', 3550308, 'TABELA'),
                       ('6001', 'RIO DE JANEIRO', 'RIO DE JANEIRO', 'RJ', 3304557, 'TABELA')
                ON CONFLICT (codigo_receita) DO UPDATE SET codigo_ibge = EXCLUDED.codigo_ibge
                """);
        for (String c : List.of("8630504", "4789099")) {
            jdbc.update("INSERT INTO cnae_subclasse (codigo, descricao, classe, grupo, divisao, secao) "
                    + "VALUES (?, 'Teste', '00000', '000', '00', 'Z') ON CONFLICT DO NOTHING", c);
        }
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM estabelecimento_evento WHERE cnpj LIKE '9100000%'");
        jdbc.update("DELETE FROM estabelecimento WHERE cnpj_basico LIKE '9100000%'");
    }

    @Test
    void cargaInicialNaoGeraEventosEAsMudancasDaSegundaReferenciaSim() throws Exception {
        String mes1 = estab("91000001", "02", "2015-01-01", "8630504", "SP", "7107", "01000000")
                + estab("91000002", "02", "2015-01-01", "8630504", "SP", "7107", "01000000")
                + estab("91000003", "02", "2015-01-01", "8630504", "SP", "7107", "01000000")
                + estab("91000004", "02", "2015-01-01", "8630504", "SP", "7107", "01000000")
                + estab("91000007", "02", "2015-01-01", "8630504", "SP", "7107", "01000000");
        importar(mes1, REF1, false);
        assertThat(eventos(REF1)).isEmpty();

        String mes2 = estab("91000001", "08", "2015-01-01", "8630504", "SP", "7107", "01000000")   // baixa
                + estab("91000002", "02", "2015-01-01", "4789099", "SP", "7107", "01000000")        // CNAE
                + estab("91000003", "02", "2015-01-01", "8630504", "RJ", "6001", "20000000")        // endereço
                + estab("91000004", "03", "2015-01-01", "8630504", "SP", "7107", "01000000")        // suspensa
                + estab("91000005", "02", "2026-09-15", "8630504", "SP", "7107", "01000000")        // abertura recente
                + estab("91000006", "02", "2015-03-03", "8630504", "SP", "7107", "01000000")        // empresa antiga nova na base
                + estab("91000007", "02", "2015-01-01", "8630504", "SP", "7107", "01000000");       // sem mudança
        importar(mes2, REF2, true);

        assertThat(eventos(REF2)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "91000001000100", "BAIXA",
                "91000002000100", "MUDANCA_CNAE",
                "91000003000100", "MUDANCA_ENDERECO",
                "91000004000100", "SITUACAO",
                "91000005000100", "ABERTURA"));

        // repetir a mesma referência não duplica nada
        importar(mes2, REF2, true);
        assertThat(eventos(REF2)).hasSize(5);
    }

    @Test
    void consultaPorNichoInformaSeAEmpresaEntrouOuSaiu() throws Exception {
        importar(estab("91000001", "02", "2015-01-01", "8630504", "SP", "7107", "01000000")
                + estab("91000002", "02", "2015-01-01", "8630504", "SP", "7107", "01000000")
                + estab("91000003", "02", "2015-01-01", "8630504", "SP", "7107", "01000000"), REF1, false);
        importar(estab("91000001", "08", "2015-01-01", "8630504", "SP", "7107", "01000000")
                + estab("91000002", "02", "2015-01-01", "4789099", "SP", "7107", "01000000")
                + estab("91000003", "02", "2015-01-01", "8630504", "RJ", "6001", "20000000")
                + estab("91000005", "02", "2026-09-15", "8630504", "SP", "7107", "01000000"), REF2, true);

        CriteriosBusca odontoSp = new CriteriosBusca(List.of("8630504"), false, List.of("SP"), null, List.of(),
                null, null, null, null, false, false, null);
        ResultadoMudancas r = mudancas.consultar(REF2, EnumSet.allOf(TipoMudanca.class), odontoSp, 100);

        assertThat(r.total()).isEqualTo(4);
        assertThat(r.porTipo()).containsEntry(TipoMudanca.BAIXA, 1L).containsEntry(TipoMudanca.MUDANCA_CNAE, 1L)
                .containsEntry(TipoMudanca.MUDANCA_ENDERECO, 1L).containsEntry(TipoMudanca.ABERTURA, 1L);

        Map<String, MudancaDetalhada> porCnpj = r.itens().stream()
                .collect(Collectors.toMap(MudancaDetalhada::cnpj, m -> m));
        assertThat(porCnpj.get("91000001000100").casaAntes()).isTrue();   // baixada: continua no nicho
        assertThat(porCnpj.get("91000001000100").casaDepois()).isTrue();
        assertThat(porCnpj.get("91000002000100").casaDepois()).isFalse(); // trocou de CNAE: saiu
        assertThat(porCnpj.get("91000003000100").casaAntes()).isTrue();   // mudou para o RJ: saiu
        assertThat(porCnpj.get("91000003000100").casaDepois()).isFalse();
        assertThat(porCnpj.get("91000003000100").municipioAntes()).isEqualTo("São Paulo");
        assertThat(porCnpj.get("91000003000100").municipioDepois()).isEqualTo("Rio de Janeiro");
        assertThat(porCnpj.get("91000005000100").casaAntes()).isFalse();  // abertura: entrou
        assertThat(porCnpj.get("91000005000100").casaDepois()).isTrue();

        // quem passou a ter o CNAE 4789099 (qualquer UF) é só a empresa 2
        CriteriosBusca varejo = new CriteriosBusca(List.of("4789099"), false, null, null, List.of(), null, null,
                null, null, false, false, null);
        ResultadoMudancas v = mudancas.consultar(REF2, EnumSet.allOf(TipoMudanca.class), varejo, 100);
        assertThat(v.itens()).extracting(MudancaDetalhada::cnpj).containsExactly("91000002000100");

        // tipos não pedidos ficam de fora
        assertThat(mudancas.consultar(REF2, EnumSet.of(TipoMudanca.BAIXA), odontoSp, 100).total()).isEqualTo(1);
        assertThat(mudancas.consultar(REF2, EnumSet.noneOf(TipoMudanca.class), odontoSp, 100).total()).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, String> eventos(String referencia) {
        return jdbc.queryForList("SELECT cnpj, tipo FROM estabelecimento_evento WHERE referencia = ? AND cnpj LIKE '9100000%'",
                        referencia).stream()
                .collect(Collectors.toMap(m -> (String) m.get("cnpj"), m -> (String) m.get("tipo")));
    }

    private void importar(String csv, String referencia, boolean gerarEventos) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            carga.prepararStaging(c, Destino.ESTABELECIMENTO);
            c.unwrap(PGConnection.class).getCopyAPI().copyIn(carga.comandoCopy(Destino.ESTABELECIMENTO),
                    new ByteArrayInputStream(csv.getBytes(StandardCharsets.ISO_8859_1)));
            carga.consolidar(c, Destino.ESTABELECIMENTO, referencia, SEM_FILTRO, gerarEventos);
            c.commit();
        }
    }

    /** Linha de 30 colunas no layout dos arquivos Estabelecimentos (datas AAAA-MM-DD viram AAAAMMDD). */
    private static String estab(String basico, String situacao, String inicio, String cnae, String uf,
            String municipio, String cep) {
        List<String> campos = List.of(basico, "0001", "00", "1", "FANTASIA " + basico, situacao, "20250101", "00",
                "", "", inicio.replace("-", ""), cnae, "", "RUA", "TESTE", "10", "", "CENTRO", cep, uf, municipio,
                "11", "999990000", "", "", "", "", "", "", "");
        return campos.stream().map(v -> "\"" + v + "\"").collect(Collectors.joining(";")) + "\n";
    }
}