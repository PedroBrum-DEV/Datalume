package br.com.datalume.importacao.receita;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReceitaClientTest {

    private HttpServer servidor;
    private String base;
    private byte[] conteudo;
    private final AtomicReference<String> ultimoRange = new AtomicReference<>();

    @TempDir
    Path tmp;

    @BeforeEach
    void subirServidor() throws Exception {
        conteudo = new byte[200_000];
        new Random(42).nextBytes(conteudo);
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/2026-10/Arquivo.zip", ex -> {
            if ("HEAD".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(200, -1);
                ex.close();
                return;
            }
            String range = ex.getRequestHeaders().getFirst("Range");
            ultimoRange.set(range);
            int inicio = 0;
            int status = 200;
            if (range != null && range.startsWith("bytes=")) {
                inicio = Integer.parseInt(range.substring(6, range.indexOf('-')));
                status = 206;
                ex.getResponseHeaders().add("Content-Range",
                        "bytes " + inicio + "-" + (conteudo.length - 1) + "/" + conteudo.length);
            }
            int tamanho = conteudo.length - inicio;
            ex.sendResponseHeaders(status, tamanho);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(conteudo, inicio, tamanho);
            }
        });
        servidor.start();
        base = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void pararServidor() {
        servidor.stop(0);
    }

    @Test
    void existeDistingueArquivoPresenteDeAusente() {
        ReceitaClient client = new ReceitaClient();
        assertThat(client.existe(base + "/2026-10/Arquivo.zip")).isTrue();
        assertThat(client.existe(base + "/2026-10/NaoExiste.zip")).isFalse();
    }

    @Test
    void baixaArquivoCompleto() throws Exception {
        Path destino = tmp.resolve("2026-10/Arquivo.zip");
        long tamanho = new ReceitaClient().baixar(base + "/2026-10/Arquivo.zip", destino, 2);

        assertThat(tamanho).isEqualTo(conteudo.length);
        assertThat(Files.readAllBytes(destino)).isEqualTo(conteudo);
        assertThat(ultimoRange.get()).isNull();
    }

    @Test
    void retomaDeOndeParouUsandoRange() throws Exception {
        Path destino = tmp.resolve("2026-10/Arquivo.zip");
        Files.createDirectories(destino.getParent());
        Path parcial = destino.resolveSibling("Arquivo.zip.part");
        Files.write(parcial, java.util.Arrays.copyOf(conteudo, 80_000));

        long tamanho = new ReceitaClient().baixar(base + "/2026-10/Arquivo.zip", destino, 2);

        assertThat(ultimoRange.get()).isEqualTo("bytes=80000-");
        assertThat(tamanho).isEqualTo(conteudo.length);
        assertThat(Files.readAllBytes(destino)).isEqualTo(conteudo);
        assertThat(Files.exists(parcial)).isFalse();
    }

    @Test
    void naoBaixaDeNovoQuandoOArquivoFinalJaExiste() throws Exception {
        Path destino = tmp.resolve("2026-10/Arquivo.zip");
        Files.createDirectories(destino.getParent());
        Files.write(destino, new byte[] {1, 2, 3});

        long tamanho = new ReceitaClient().baixar(base + "/2026-10/Arquivo.zip", destino, 2);

        assertThat(tamanho).isEqualTo(3);
    }
}