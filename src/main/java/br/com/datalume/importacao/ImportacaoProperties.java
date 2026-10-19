package br.com.datalume.importacao;

import br.com.datalume.estabelecimento.FiltroImportacao;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração da importação mensal (prefixo {@code datalume.importacao}).
 *
 * @param baseUrl                  diretório da Receita que contém as pastas AAAA-MM
 * @param diretorio                onde guardar os ZIPs durante a importação
 * @param partes                   quantidade de partes de Estabelecimentos/Empresas (a Receita publica 10)
 * @param apagarZipAposImportar    apaga cada ZIP depois de importado (economiza disco)
 * @param tentativasDownload       tentativas por arquivo (cada uma retoma de onde parou)
 * @param mesesBusca               quantos meses para trás procurar a referência mais recente
 * @param recuperarInterrompidos   ao iniciar a aplicação, marca como FALHOU lotes que ficaram EXECUTANDO
 */
@Validated
@ConfigurationProperties("datalume.importacao")
public record ImportacaoProperties(
        @DefaultValue("https://arquivos.receitafederal.gov.br/dados/cnpj/dados_abertos_cnpj") @NotBlank String baseUrl,
        @DefaultValue("./dados/receita") @NotBlank String diretorio,
        @DefaultValue("10") @Min(1) @Max(20) int partes,
        @DefaultValue("true") boolean apagarZipAposImportar,
        @DefaultValue("5") @Min(1) @Max(20) int tentativasDownload,
        @DefaultValue("6") @Min(1) @Max(36) int mesesBusca,
        @DefaultValue("true") boolean recuperarInterrompidos,
        @DefaultValue Filtro filtro,
        @DefaultValue Agendamento agendamento) {

    public record Filtro(List<String> ufs, List<String> cnaePrefixos) {
    }

    public record Agendamento(@DefaultValue("false") boolean habilitado, @DefaultValue("0 0 3 * * *") String cron) {
    }

    public FiltroImportacao filtroImportacao() {
        return new FiltroImportacao(filtro.ufs(), filtro.cnaePrefixos());
    }
}