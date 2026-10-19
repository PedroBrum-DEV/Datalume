package br.com.datalume.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.datalume.TestcontainersConfig;
import br.com.datalume.importacao.aplicacao.ImportacaoService;
import br.com.datalume.importacao.aplicacao.Lote;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Importação de ponta a ponta contra um "servidor da Receita" local: download, ZIP, COPY, consolidação,
 * controle de lote e reexecução idempotente.
 */
@SpringBootTest(properties = {
        "datalume.importacao.partes=1",
        "datalume.importacao.tentativas-download=1",
        "datalume.importacao.filtro.ufs=SP",
        "datalume.importacao.filtro.cnae-prefixos=8630"
})
@Import(TestcontainersConfig.class)
class ImportacaoEndToEndTest {

    private static final String REF = "2026-10";
    private static HttpServer servidor;
    private static Path diretorio;
    private static final Map<String, byte[]> arquivos = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) throws IOException {
        diretorio = Files.createTempDirectory("datalume-receita");
        montarArquivos();
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", ex -> {
            byte[] corpo = arquivos.get(ex.getRequestURI().getPath());
            if (corpo == null) {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            if ("HEAD".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(200, -1);
                ex.close();
                return;
            }
            ex.sendResponseHeaders(200, corpo.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(corpo);
            }
        });
        servidor.start();
        registry.add("datalume.importacao.base-url", () -> "http://127.0.0.1:" + servidor.getAddress().getPort());
        registry.add("datalume.importacao.diretorio", () -> diretorio.toString());
    }

    @AfterAll
    static void parar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Autowired
    ImportacaoService service;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void importaUmaReferenciaCompletaERespeitaFiltroEIdempotencia() {
        jdbc.update("INSERT INTO municipio (codigo_ibge, nome, uf_sigla, nome_normalizado) "
                + "VALUES (3550308, 'São Paulo', 'SP', 'SAO PAULO') ON CONFLICT DO NOTHING");

        Lote lote = service.iniciar(REF, false);
        Lote fim = aguardar(lote.id());

        assertThat(fim.status()).as(fim.erro()).isEqualTo(Lote.CONCLUIDO);
        assertThat(service.arquivos(lote.id())).allMatch(a -> "CONCLUIDO".equals(a.status()));

        // entrou só o estabelecimento de SP com CNAE 8630; o do RJ ficou de fora pelo filtro
        List<String> cnpjs = jdbc.queryForList("SELECT cnpj FROM estabelecimento WHERE cnpj_basico IN ('11222333', '22333444')", String.class);
        assertThat(cnpjs).containsExactly("11222333000181");
        assertThat(jdbc.queryForObject("SELECT razao_social FROM empresa WHERE cnpj_basico = '11222333'", String.class))
                .isEqualTo("CLINICA SORRISO LTDA");
        assertThat(jdbc.queryForList("SELECT cnpj_basico FROM empresa WHERE cnpj_basico = '22333444'")).isEmpty();
        // município da Receita (7107) foi conciliado com o IBGE
        assertThat(jdbc.queryForObject("SELECT municipio_ibge FROM estabelecimento WHERE cnpj = '11222333000181'", Integer.class))
                .isEqualTo(3550308);
        assertThat(jdbc.queryForObject("SELECT descricao FROM natureza_juridica WHERE codigo = '2062'", String.class))
                .isEqualTo("Sociedade Empresária Limitada");

        // segunda execução forçada: nada novo para gravar
        Lote segundo = aguardar(service.iniciar(REF, true).id());
        assertThat(segundo.status()).as(segundo.erro()).isEqualTo(Lote.CONCLUIDO);
        assertThat(service.arquivos(segundo.id()))
                .filteredOn(a -> "ESTABELECIMENTOS".equals(a.tipo()))
                .allMatch(a -> a.inseridos() == 0 && a.atualizados() == 0 && a.linhasLidas() == 2);
    }

    @Test
    void referenciaInexistenteNaReceitaEhRejeitada() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.iniciar("2020-01", false))
                .hasMessageContaining("não encontrada");
    }

    private Lote aguardar(long id) {
        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(300))
                .until(() -> !Lote.EXECUTANDO.equals(service.lote(id).status()));
        return service.lote(id);
    }

    // ------------------------------------------------------------------ dados do "servidor"

    private static void montarArquivos() throws IOException {
        String p = "/" + REF + "/";
        arquivos.put(p + "Municipios.zip", zip("MUNICCSV", "\"7107\";\"SAO PAULO\"\n"));
        arquivos.put(p + "Naturezas.zip", zip("NATJUCSV", "\"2062\";\"Sociedade Empresária Limitada\"\n"));
        arquivos.put(p + "Motivos.zip", zip("MOTICSV", "\"00\";\"SEM MOTIVO\"\n"));
        arquivos.put(p + "Estabelecimentos0.zip", zip("ESTABELE",
                estab("11222333", "0001", "81", "SP", "7107", "8630504")
                        + estab("22333444", "0001", "00", "RJ", "6001", "8630504")));
        arquivos.put(p + "Empresas0.zip", zip("EMPRECSV",
                empresa("11222333", "CLINICA SORRISO LTDA") + empresa("22333444", "CLINICA CARIOCA LTDA")));
    }

    private static String estab(String basico, String ordem, String dv, String uf, String municipio, String cnae) {
        List<String> campos = List.of(basico, ordem, dv, "1", "FANTASIA " + basico, "02", "20200115", "00", "", "",
                "20100305", cnae, "", "RUA", "AUGUSTA", "1000", "", "CENTRO", "01304001", uf, municipio,
                "11", "999990000", "", "", "", "", "", "", "");
        return linha(campos);
    }

    private static String empresa(String basico, String razao) {
        return linha(List.of(basico, razao, "2062", "49", "150000,00", "03", ""));
    }

    private static String linha(List<String> campos) {
        return campos.stream().map(v -> "\"" + v + "\"").collect(Collectors.joining(";")) + "\n";
    }

    private static byte[] zip(String nomeInterno, String csv) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("K3241.K03200Y0.D60110." + nomeInterno));
            zos.write(csv.getBytes(StandardCharsets.ISO_8859_1));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }
}