package br.com.datalume.conta.web;

import br.com.datalume.conta.aplicacao.ContaService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conta")
class ContaController {

    private final ContaService contas;

    ContaController(ContaService contas) {
        this.contas = contas;
    }

    @GetMapping("/me")
    PerfilResponse me(@AuthenticationPrincipal Jwt jwt) {
        return contas.perfilDoUsuario(UUID.fromString(jwt.getSubject()));
    }
}
