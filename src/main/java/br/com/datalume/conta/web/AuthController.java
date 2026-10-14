package br.com.datalume.conta.web;

import br.com.datalume.conta.aplicacao.AuthService;
import br.com.datalume.conta.aplicacao.ContaService;
import br.com.datalume.conta.dominio.Usuario;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService auth;
    private final ContaService contas;

    AuthController(AuthService auth, ContaService contas) {
        this.auth = auth;
        this.contas = contas;
    }

    @PostMapping("/cadastro")
    ResponseEntity<PerfilResponse> cadastro(@Valid @RequestBody CadastroRequest req) {
        Usuario usuario = auth.cadastrar(req.nomeEmpresa(), req.nome(), req.email(), req.senha());
        return ResponseEntity.status(HttpStatus.CREATED).body(contas.perfilDoUsuario(usuario.getId()));
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest req) {
        AuthService.Autenticacao a = auth.login(req.email(), req.senha());
        return TokenResponse.bearer(a.accessToken(), a.expiraEmSegundos());
    }
}
