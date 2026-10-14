package br.com.datalume.referencia.carga;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.datalume.TestcontainersConfig;
import br.com.datalume.referencia.ReferenciaConsulta;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfig.class)
class ReferenciaIntegrationTest {

    // Códigos fictícios (faixa 99999xx / 99xx) para não colidir com dados reais.
    private static final int VILA_SP = 9999901;
    private static final int VILA_RJ = 9999902;
    private static final int UNICA_SP = 9999903;

    @Autowired
    ReferenciaConsulta consulta;

    @Autowired
    CargaReferenciaService carga;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        limpar();
        String sql = "INSERT INTO municipio (codigo_ibge, nome, uf_sigla, nome_normalizado) VALUES (?, ?, ?, ?)";
        jdbc.update(sql, VILA_SP, "Vila Teste", "SP", "VILA TESTE");
        jdbc.update(sql, VILA_RJ, "Vila Teste", "RJ", "VILA TESTE");
        jdbc.update(sql, UNICA_SP, "Cidade Única", "SP", "CIDADE UNICA");
        jdbc.update("INSERT INTO cnae_subclasse (codigo, descricao, classe, grupo, divisao, secao) "
                + "VALUES ('9999901', 'Atividade odontológica de teste', '99999', '999', '99', 'Q')");
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM municipio_receita WHERE codigo_receita LIKE '99%'");
        jdbc.update("DELETE FROM municipio WHERE codigo_ibge IN (?, ?, ?)", VILA_SP, VILA_RJ, UNICA_SP);
        jdbc.update("DELETE FROM cnae_subclasse WHERE codigo = '9999901'");
    }

    @Test
    void migracaoCarregaAs27Ufs() {
        assertThat(consulta.listarUfs()).hasSize(27);
        assertThat(consulta.buscarUf("sp")).get().extracting("nome").isEqualTo("São Paulo");
    }

    @Test
    void conciliaPorNomeUnicoEResolveAmbiguosPorUf() {
        String csv = "\"9991\";\"VILA TESTE\"\n\"9992\";\"CIDADE UNICA\"\n";
        ResultadoCarga r = carga.importarMunicipiosReceita(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.ISO_8859_1)), StandardCharsets.ISO_8859_1);

        assertThat(r.processados()).isEqualTo(2);
        // nome único no país: conciliado direto
        assertThat(consulta.municipioIbgeDaReceita("9992", null)).contains(UNICA_SP);
        // nome repetido: sem UF não há resposta segura; com UF resolve
        assertThat(consulta.municipioIbgeDaReceita("9991", null)).isEmpty();
        assertThat(consulta.municipioIbgeDaReceita("9991", "RJ")).contains(VILA_RJ);
        assertThat(consulta.municipioIbgeDaReceita("9991", "SP")).contains(VILA_SP);
    }

    @Test
    void tabelaDeConversaoTemPrecedenciaEIgnoraIbgeInexistente() {
        String csv = "codigo_receita;codigo_ibge\n9991;" + VILA_RJ + "\n9993;1234567\n";
        ResultadoCarga r = carga.importarConversao(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);

        assertThat(r.processados()).isEqualTo(1);
        assertThat(r.ignorados()).isEqualTo(2); // cabeçalho + IBGE inexistente
        assertThat(consulta.municipioIbgeDaReceita("9991", null)).contains(VILA_RJ);
    }

    @Test
    void buscaCnaePorCodigoComPontuacaoPrefixoEDescricao() {
        assertThat(consulta.buscarCnae("9999-9/01")).isPresent();
        assertThat(consulta.buscarCnaes("99999", 10)).extracting("codigo").contains("9999901");
        assertThat(consulta.buscarCnaes("odontologica de teste", 10)).extracting("codigo").contains("9999901");
    }

    @Test
    void buscaMunicipioPorNomeSemAcento() {
        assertThat(consulta.buscarMunicipios("cidade unica", "SP", 10)).extracting("codigoIbge").contains(UNICA_SP);
        assertThat(consulta.buscarMunicipios("cidade unica", "RJ", 10)).isEmpty();
    }
}
