package br.com.datalume.conta.seguranca;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("datalume.seguranca")
public record SegurancaProperties(
        @NotBlank @Size(min = 32, message = "jwt-secret precisa ter ao menos 32 caracteres") String jwtSecret,
        @NotNull Duration jwtExpiracao,
        @NotBlank String emissor,
        @NotBlank String planoPadrao) {
}
