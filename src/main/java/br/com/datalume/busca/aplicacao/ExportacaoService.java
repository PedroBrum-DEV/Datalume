package br.com.datalume.busca.aplicacao;

import br.com.datalume.busca.dominio.Nicho;
import br.com.datalume.busca.dominio.RegistroExportacao;
import br.com.datalume.busca.dominio.RepositorioNicho;
import br.com.datalume.conta.ContaConsulta;
import br.com.datalume.estabelecimento.CriteriosBusca;
import br.com.datalume.estabelecimento.EstabelecimentoPesquisa;
import br.com.datalume.estabelecimento.ItemEstabelecimento;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Exporta um recorte em CSV (separador ';', UTF-8 com BOM, abre direto no Excel) e registra a exportação. */
@Service
public class ExportacaoService {

    private static final Logger log = LoggerFactory.getLogger(ExportacaoService.class);
    private static final String SEPARADOR = ";";
    private static final String QUEBRA = "\r\n";

    private final EstabelecimentoPesquisa pesquisa;
    private final RepositorioNicho repo;
    private final ContaConsulta contas;
    private final MeterRegistry meters;

    public ExportacaoService(EstabelecimentoPesquisa pesquisa, RepositorioNicho repo, ContaConsulta contas,
            MeterRegistry meters) {
        this.pesquisa = pesquisa;
        this.repo = repo;
        this.contas = contas;
        this.meters = meters;
    }

    public int limiteDaConta(UUID contaId) {
        return contas.limitesDaConta(contaId).limiteExportacao();
    }

    public List<RegistroExportacao> listar(UUID contaId) {
        return repo.listarExportacoes(contaId, 50);
    }

    /**
     * Escreve o CSV em {@code saida}. O registro da exportação é gravado ao final (mesmo se o cliente
     * abortar), com a quantidade de linhas efetivamente enviadas.
     */
    public long exportar(UUID contaId, UUID usuarioId, Nicho nicho, CriteriosBusca criterios, boolean contatos,
            int limite, OutputStream saida) throws IOException {
        AtomicLong linhas = new AtomicLong();
        try (Writer w = new BufferedWriter(new OutputStreamWriter(saida, StandardCharsets.UTF_8))) {
            w.write('\uFEFF');
            w.write(cabecalho(contatos));
            pesquisa.exportar(criterios, limite, contatos, item -> {
                try {
                    w.write(linha(item, contatos));
                    linhas.incrementAndGet();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } finally {
            repo.registrarExportacao(contaId, usuarioId, nicho.id(), nicho.nome(), criterios.descricao(),
                    contatos, (int) linhas.get());
            meters.counter("datalume.busca.resultados", "tipo", "exportacao").increment(linhas.get());
            log.info("Exportação: contaId={} nichoId={} linhas={} contatos={}", contaId, nicho.id(),
                    linhas.get(), contatos);
        }
        return linhas.get();
    }

    private static String cabecalho(boolean contatos) {
        String base = String.join(SEPARADOR, "cnpj", "razao_social", "nome_fantasia", "situacao", "data_abertura",
                "cnae_principal", "porte", "matriz_filial", "logradouro", "numero", "complemento", "bairro",
                "cep", "municipio", "uf");
        return (contatos ? base + SEPARADOR + "telefone" + SEPARADOR + "email" : base) + QUEBRA;
    }

    private static String linha(ItemEstabelecimento i, boolean contatos) {
        StringBuilder sb = new StringBuilder();
        campo(sb, i.cnpj());
        campo(sb, i.razaoSocial());
        campo(sb, i.nomeFantasia());
        campo(sb, i.situacaoDescricao());
        campo(sb, i.dataInicioAtividade() == null ? null : i.dataInicioAtividade().toString());
        campo(sb, i.cnaePrincipal());
        campo(sb, i.porteDescricao());
        campo(sb, i.matriz() ? "Matriz" : "Filial");
        campo(sb, i.logradouro());
        campo(sb, i.numero());
        campo(sb, i.complemento());
        campo(sb, i.bairro());
        campo(sb, i.cep());
        campo(sb, i.municipio());
        sb.append(escapar(i.uf()));
        if (contatos) {
            sb.append(SEPARADOR).append(escapar(i.telefone())).append(SEPARADOR).append(escapar(i.email()));
        }
        return sb.append(QUEBRA).toString();
    }

    private static void campo(StringBuilder sb, String valor) {
        sb.append(escapar(valor)).append(SEPARADOR);
    }

    /** Aspas quando necessário e neutralização de fórmulas (=, +, -, @) abertas pelo Excel. */
    static String escapar(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        String v = valor;
        char primeiro = v.charAt(0);
        if (primeiro == '=' || primeiro == '+' || primeiro == '-' || primeiro == '@') {
            v = "'" + v;
        }
        if (v.contains(SEPARADOR) || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}