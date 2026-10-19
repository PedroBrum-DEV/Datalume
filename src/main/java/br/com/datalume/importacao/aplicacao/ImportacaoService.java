package br.com.datalume.importacao.aplicacao;

import br.com.datalume.compartilhado.erro.ConflitoException;
import br.com.datalume.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.datalume.compartilhado.erro.RegraDeNegocioException;
import br.com.datalume.compartilhado.erro.RequisicaoInvalidaException;
import br.com.datalume.estabelecimento.EstabelecimentoCarga;
import br.com.datalume.estabelecimento.EstabelecimentoCarga.Destino;
import br.com.datalume.importacao.ImportacaoProperties;
import br.com.datalume.importacao.receita.ReceitaClient;
import br.com.datalume.referencia.ReferenciaManutencao;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Orquestra a importação mensal: descobre a referência, baixa cada ZIP (com retomada), copia o CSV para
 * a área de passagem via COPY e consolida nas tabelas finais. Cada arquivo é uma transação; o lote para
 * no primeiro erro e pode ser retomado (arquivos CONCLUIDOs são pulados).
 */
@Service
public class ImportacaoService {

    private static final Logger log = LoggerFactory.getLogger(ImportacaoService.class);
    private static final Pattern REFERENCIA = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final ImportacaoProperties props;
    private final ReceitaClient receita;
    private final RepositorioImportacao repo;
    private final EstabelecimentoCarga carga;
    private final ReferenciaManutencao referencia;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final ImportacaoExecutor executor;

    public ImportacaoService(ImportacaoProperties props, ReceitaClient receita, RepositorioImportacao repo,
            EstabelecimentoCarga carga, ReferenciaManutencao referencia, DataSource dataSource,
            JdbcTemplate jdbc, ImportacaoExecutor executor) {
        this.props = props;
        this.receita = receita;
        this.repo = repo;
        this.carga = carga;
        this.referencia = referencia;
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.executor = executor;
    }

    // ------------------------------------------------------------------ consultas

    /** Referências (AAAA-MM) publicadas na Receita nos últimos N meses, da mais nova para a mais antiga. */
    public List<String> referenciasDisponiveis() {
        List<String> encontradas = new ArrayList<>();
        YearMonth atual = YearMonth.now(FUSO);
        for (int i = 0; i < props.mesesBusca(); i++) {
            String ref = atual.minusMonths(i).toString();
            if (receita.existe(urlArquivo(ref, "Municipios.zip"))) {
                encontradas.add(ref);
            }
        }
        return encontradas;
    }

    public Optional<String> referenciaMaisRecente() {
        return referenciasDisponiveis().stream().findFirst();
    }

    public Lote lote(long id) {
        return repo.buscarLote(id).orElseThrow(() -> new RecursoNaoEncontradoException("Lote não encontrado: " + id));
    }

    public List<Lote> lotes(int limite) {
        return repo.listarLotes(Math.min(Math.max(limite, 1), 100));
    }

    public List<ArquivoImportacao> arquivos(long loteId) {
        return repo.arquivos(loteId);
    }

    // ------------------------------------------------------------------ comandos

    /** Inicia a importação em segundo plano. Sem referência, usa a mais recente publicada. */
    public Lote iniciar(String referenciaPedida, boolean forcar) {
        String ref = referenciaPedida != null && !referenciaPedida.isBlank()
                ? validarReferencia(referenciaPedida.trim())
                : referenciaMaisRecente().orElseThrow(() -> new RegraDeNegocioException(
                        "Nenhuma referência encontrada na Receita nos últimos " + props.mesesBusca() + " meses."));
        if (!forcar && repo.existeLoteConcluido(ref)) {
            throw new ConflitoException("A referência " + ref + " já foi importada (use forcar=true para repetir).");
        }
        if (!receita.existe(urlArquivo(ref, "Municipios.zip"))) {
            throw new RecursoNaoEncontradoException("Referência não encontrada na Receita: " + ref);
        }
        List<String> nomes = new ArrayList<>();
        List<TipoArquivo> tipos = new ArrayList<>();
        for (ArquivoPlanejado a : planoDeArquivos()) {
            nomes.add(a.nome());
            tipos.add(a.tipo());
        }
        long id = repo.criarLote(ref, props.filtroImportacao().descricao(), nomes, tipos);
        executor.submeter(() -> executar(id));
        log.info("Importação enfileirada: lote={} referencia={} filtro={}", id, ref, props.filtroImportacao().descricao());
        return lote(id);
    }

    /** Retoma um lote FALHOU, pulando os arquivos já concluídos. */
    public Lote retomar(long loteId) {
        lote(loteId);
        repo.reabrirLote(loteId);
        executor.submeter(() -> executar(loteId));
        return lote(loteId);
    }

    /** Usado pelo agendamento: importa se nunca tentou, retoma se falhou, ignora se já concluiu/está rodando. */
    public void agendarSeNecessario(String ref) {
        Optional<Lote> ultimo = repo.ultimoLoteDaReferencia(ref);
        if (ultimo.isEmpty()) {
            iniciar(ref, false);
        } else if (Lote.FALHOU.equals(ultimo.get().status())) {
            retomar(ultimo.get().id());
        }
    }

    // ------------------------------------------------------------------ execução

    void executar(long loteId) {
        MDC.put("loteId", String.valueOf(loteId));
        try {
            Lote lote = lote(loteId);
            for (ArquivoImportacao arquivo : repo.arquivos(loteId)) {
                if ("CONCLUIDO".equals(arquivo.status())) {
                    continue;
                }
                try {
                    processar(lote, arquivo);
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    String msg = resumirErro(e);
                    log.error("Falha ao importar {}: {}", arquivo.nome(), msg, e);
                    repo.falharArquivo(arquivo.id(), msg);
                    repo.finalizarLote(loteId, Lote.FALHOU, arquivo.nome() + ": " + msg);
                    return;
                }
            }
            analisarTabelas();
            repo.finalizarLote(loteId, Lote.CONCLUIDO, null);
            log.info("Importação concluída: lote={} referencia={}", loteId, lote.referencia());
        } catch (Throwable t) {
            log.error("Importação interrompida inesperadamente", t);
            repo.finalizarLote(loteId, Lote.FALHOU, resumirErro(t));
        } finally {
            MDC.remove("loteId");
        }
    }

    private void processar(Lote lote, ArquivoImportacao arquivo)
            throws IOException, InterruptedException, SQLException {
        TipoArquivo tipo = TipoArquivo.valueOf(arquivo.tipo());
        Path zip = Path.of(props.diretorio(), lote.referencia(), arquivo.nome());

        repo.iniciarArquivo(arquivo.id(), "BAIXANDO");
        long tamanho = receita.baixar(urlArquivo(lote.referencia(), arquivo.nome()), zip, props.tentativasDownload());
        repo.atualizarArquivo(arquivo.id(), "IMPORTANDO", tamanho);

        Contagem contagem;
        long inicio = System.nanoTime();
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry entrada = zf.stream().filter(e -> !e.isDirectory()).findFirst()
                    .orElseThrow(() -> new IOException("ZIP sem conteúdo: " + arquivo.nome()));
            try (InputStream csv = zf.getInputStream(entrada)) {
                contagem = tipo == TipoArquivo.MUNICIPIOS
                        ? importarMunicipios(csv)
                        : importarViaCopy(tipo.destino(), csv, lote.referencia());
            }
        } catch (ZipException e) {
            Files.deleteIfExists(zip); // arquivo corrompido: força novo download na retomada
            throw e;
        }
        repo.concluirArquivo(arquivo.id(), contagem.lidas(), contagem.inseridos(), contagem.atualizados());
        log.info("Importado {}: {} linhas, {} inseridos, {} atualizados ({} s)", arquivo.nome(), contagem.lidas(),
                contagem.inseridos(), contagem.atualizados(), (System.nanoTime() - inicio) / 1_000_000_000L);
        if (props.apagarZipAposImportar()) {
            Files.deleteIfExists(zip);
        }
    }

    private Contagem importarMunicipios(InputStream csv) {
        int n = referencia.importarMunicipiosReceita(csv, StandardCharsets.ISO_8859_1);
        return new Contagem(n, n, 0);
    }

    private Contagem importarViaCopy(Destino destino, InputStream csv, String ref)
            throws SQLException, IOException {
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                carga.prepararStaging(c, destino);
                long linhas = c.unwrap(PGConnection.class).getCopyAPI().copyIn(carga.comandoCopy(destino), csv);
                EstabelecimentoCarga.Resultado r = carga.consolidar(c, destino, ref, props.filtroImportacao());
                carga.prepararStaging(c, destino); // libera o espaço da área de passagem
                c.commit();
                return new Contagem(linhas, r.inseridos(), r.atualizados());
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        }
    }

    private void analisarTabelas() {
        try {
            jdbc.execute("ANALYZE estabelecimento");
            jdbc.execute("ANALYZE empresa");
        } catch (RuntimeException e) {
            log.warn("ANALYZE falhou (não crítico): {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ auxiliares

    List<ArquivoPlanejado> planoDeArquivos() {
        List<ArquivoPlanejado> plano = new ArrayList<>();
        plano.add(new ArquivoPlanejado("Municipios.zip", TipoArquivo.MUNICIPIOS));
        plano.add(new ArquivoPlanejado("Naturezas.zip", TipoArquivo.NATUREZAS));
        plano.add(new ArquivoPlanejado("Motivos.zip", TipoArquivo.MOTIVOS));
        for (int i = 0; i < props.partes(); i++) {
            plano.add(new ArquivoPlanejado("Estabelecimentos" + i + ".zip", TipoArquivo.ESTABELECIMENTOS));
        }
        for (int i = 0; i < props.partes(); i++) {
            plano.add(new ArquivoPlanejado("Empresas" + i + ".zip", TipoArquivo.EMPRESAS));
        }
        return plano;
    }

    String urlArquivo(String ref, String nome) {
        return props.baseUrl().replaceAll("/+$", "") + "/" + ref + "/" + nome;
    }

    private static String validarReferencia(String ref) {
        if (!REFERENCIA.matcher(ref).matches()) {
            throw new RequisicaoInvalidaException("Referência inválida; use o formato AAAA-MM (ex.: 2026-10).");
        }
        return ref;
    }

    private static String resumirErro(Throwable t) {
        String msg = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
        return msg.length() > 1000 ? msg.substring(0, 1000) : msg;
    }

    record ArquivoPlanejado(String nome, TipoArquivo tipo) {
    }

    private record Contagem(long lidas, long inseridos, long atualizados) {
    }
}