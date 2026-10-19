package br.com.datalume.importacao.receita;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cliente HTTP dos arquivos da Receita: verifica existência e baixa com retomada
 * (cabeçalho Range sobre o arquivo .part) e novas tentativas.
 */
@Component
public class ReceitaClient {

    private static final Logger log = LoggerFactory.getLogger(ReceitaClient.class);
    private static final Pattern TOTAL = Pattern.compile("/(\\d+)\\s*$");

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /** Pausa base entre tentativas (segundos). Visível para testes. */
    long pausaBaseSegundos = 5;

    public boolean existe(String url) {
        try {
            HttpResponse<Void> head = http.send(
                    HttpRequest.newBuilder(URI.create(url)).method("HEAD", HttpRequest.BodyPublishers.noBody())
                            .timeout(Duration.ofSeconds(30)).build(),
                    HttpResponse.BodyHandlers.discarding());
            if (head.statusCode() == 200) {
                return true;
            }
            if (head.statusCode() == 404) {
                return false;
            }
            // alguns servidores não aceitam HEAD: tenta um GET de 1 byte
            HttpResponse<Void> get = http.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().header("Range", "bytes=0-0")
                            .timeout(Duration.ofSeconds(30)).build(),
                    HttpResponse.BodyHandlers.discarding());
            return get.statusCode() == 200 || get.statusCode() == 206;
        } catch (IOException e) {
            log.warn("Não foi possível consultar {}: {}", url, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Baixa para {@code destino}. Se o arquivo final já existir, não baixa de novo. Falhas no meio do
     * caminho mantêm o .part e a próxima tentativa continua dele.
     *
     * @return tamanho do arquivo em bytes
     */
    public long baixar(String url, Path destino, int tentativas) throws IOException, InterruptedException {
        Files.createDirectories(destino.toAbsolutePath().getParent());
        Path parcial = destino.resolveSibling(destino.getFileName() + ".part");
        IOException ultimo = null;
        for (int t = 1; t <= tentativas; t++) {
            try {
                return tentar(url, destino, parcial);
            } catch (IOException e) {
                ultimo = e;
                log.warn("Download de {} falhou (tentativa {}/{}): {}", url, t, tentativas, e.getMessage());
                if (t < tentativas) {
                    Thread.sleep(Duration.ofSeconds(Math.min(60, pausaBaseSegundos * t)).toMillis());
                }
            }
        }
        throw ultimo;
    }

    private long tentar(String url, Path destino, Path parcial) throws IOException, InterruptedException {
        if (Files.exists(destino)) {
            return Files.size(destino);
        }
        long existente = Files.exists(parcial) ? Files.size(parcial) : 0;
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(60));
        if (existente > 0) {
            req.header("Range", "bytes=" + existente + "-");
        }
        long inicio = System.nanoTime();
        HttpResponse<InputStream> resposta = http.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
        int status = resposta.statusCode();
        long total;
        try (InputStream in = resposta.body()) {
            if (status == 416) {
                long totalRemoto = total(resposta.headers().firstValue("Content-Range").orElse(""));
                if (totalRemoto == existente) {
                    Files.move(parcial, destino, StandardCopyOption.REPLACE_EXISTING);
                    return existente;
                }
                Files.deleteIfExists(parcial);
                throw new IOException("Arquivo parcial inconsistente com o servidor; reiniciando o download");
            }
            boolean anexar;
            if (status == 206) {
                anexar = true;
                total = total(resposta.headers().firstValue("Content-Range").orElse(""));
            } else if (status == 200) {
                anexar = false;
                total = resposta.headers().firstValueAsLong("Content-Length").orElse(-1);
            } else {
                throw new IOException("HTTP " + status + " ao baixar " + url);
            }
            StandardOpenOption[] opcoes = anexar
                    ? new StandardOpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                    : new StandardOpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE};
            try (OutputStream out = Files.newOutputStream(parcial, opcoes)) {
                in.transferTo(out);
            }
        }
        long tamanho = Files.size(parcial);
        if (total > 0 && tamanho != total) {
            throw new IOException("Download incompleto: " + tamanho + " de " + total + " bytes");
        }
        Files.move(parcial, destino, StandardCopyOption.REPLACE_EXISTING);
        log.info("Baixado {} ({} MB em {} s{})", destino.getFileName(), tamanho / 1_048_576,
                (System.nanoTime() - inicio) / 1_000_000_000L, existente > 0 ? ", retomado" : "");
        return tamanho;
    }

    private static long total(String contentRange) {
        Matcher m = TOTAL.matcher(contentRange);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }
}