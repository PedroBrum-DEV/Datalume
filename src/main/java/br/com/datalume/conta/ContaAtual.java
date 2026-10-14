package br.com.datalume.conta;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** API pública: identifica a conta/usuário da requisição atual para os demais módulos. */
public final class ContaAtual {

    private ContaAtual() {
    }

    public static Optional<UUID> contaId() {
        return jwt().map(j -> UUID.fromString(j.getClaimAsString("contaId")));
    }

    public static Optional<UUID> usuarioId() {
        return jwt().map(j -> UUID.fromString(j.getSubject()));
    }

    private static Optional<Jwt> jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(jwt);
        }
        return Optional.empty();
    }
}
