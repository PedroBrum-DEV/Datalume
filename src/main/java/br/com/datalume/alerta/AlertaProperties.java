package br.com.datalume.alerta;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração dos alertas (prefixo {@code datalume.alerta}).
 *
 * @param urlBase          endereço público da aplicação (usado nos links do e-mail)
 * @param remetente        endereço de origem dos e-mails
 * @param emailHabilitado  falso = os envios ficam PENDENTES (o feed continua funcionando)
 * @param segredoLinks     chave que assina os links de cancelamento (mín. 32 caracteres)
 * @param maxItensEmail    máximo de itens listados no corpo do e-mail
 * @param tentativasEnvio  tentativas antes de marcar o envio como FALHOU
 */
@Validated
@ConfigurationProperties("datalume.alerta")
public record AlertaProperties(
        @DefaultValue("http://localhost:8080") @NotBlank String urlBase,
        @DefaultValue("nao-responder@datalume.com.br") @NotBlank String remetente,
        @DefaultValue("true") boolean emailHabilitado,
        @NotBlank @Size(min = 32, message = "segredo-links precisa ter ao menos 32 caracteres") String segredoLinks,
        @DefaultValue("50") @Min(1) @Max(500) int maxItensEmail,
        @DefaultValue("5") @Min(1) @Max(20) int tentativasEnvio) {
}