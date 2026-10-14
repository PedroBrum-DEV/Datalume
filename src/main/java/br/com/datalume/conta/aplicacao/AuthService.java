package br.com.datalume.conta.aplicacao;

import br.com.datalume.compartilhado.erro.ConflitoException;
import br.com.datalume.compartilhado.erro.CredenciaisInvalidasException;
import br.com.datalume.compartilhado.erro.RegraDeNegocioException;
import br.com.datalume.conta.dominio.Conta;
import br.com.datalume.conta.dominio.ContaRepository;
import br.com.datalume.conta.dominio.PapelUsuario;
import br.com.datalume.conta.dominio.Plano;
import br.com.datalume.conta.dominio.PlanoRepository;
import br.com.datalume.conta.dominio.Usuario;
import br.com.datalume.conta.dominio.UsuarioRepository;
import br.com.datalume.conta.seguranca.SegurancaProperties;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UsuarioRepository usuarios;
    private final ContaRepository contas;
    private final PlanoRepository planos;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final SegurancaProperties props;
    /** Usado para gastar o mesmo tempo de verificação quando o e-mail não existe. */
    private final String hashFalso;

    public AuthService(UsuarioRepository usuarios, ContaRepository contas, PlanoRepository planos,
            PasswordEncoder encoder, TokenService tokens, SegurancaProperties props) {
        this.usuarios = usuarios;
        this.contas = contas;
        this.planos = planos;
        this.encoder = encoder;
        this.tokens = tokens;
        this.props = props;
        this.hashFalso = encoder.encode("senha-falsa-para-igualar-tempo");
    }

    /** Cria a conta (no plano padrão) e o primeiro usuário, que será ADMIN da conta. */
    @Transactional
    public Usuario cadastrar(String nomeEmpresa, String nome, String email, String senha) {
        String emailNormalizado = normalizar(email);
        if (usuarios.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new ConflitoException("Já existe um usuário cadastrado com este e-mail.");
        }
        Plano plano = planos.findByCodigo(props.planoPadrao())
                .filter(Plano::isAtivo)
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Plano padrão '" + props.planoPadrao() + "' não está disponível."));
        try {
            Conta conta = contas.save(new Conta(nomeEmpresa.trim(), plano));
            Usuario usuario = usuarios.saveAndFlush(
                    new Usuario(conta, nome.trim(), emailNormalizado, encoder.encode(senha), PapelUsuario.ADMIN));
            log.info("Conta criada: contaId={} plano={}", conta.getId(), plano.getCodigo());
            return usuario;
        } catch (DataIntegrityViolationException e) {
            // corrida entre duas requisições com o mesmo e-mail
            throw new ConflitoException("Já existe um usuário cadastrado com este e-mail.");
        }
    }

    @Transactional
    public Autenticacao login(String email, String senha) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(normalizar(email)).orElse(null);
        if (usuario == null) {
            encoder.matches(senha, hashFalso);
            throw new CredenciaisInvalidasException();
        }
        boolean senhaConfere = encoder.matches(senha, usuario.getSenhaHash());
        if (!senhaConfere || !usuario.isAtivo() || !usuario.getConta().isAtiva()) {
            log.info("Login recusado: usuarioId={}", usuario.getId());
            throw new CredenciaisInvalidasException();
        }
        usuario.registrarLogin();
        log.info("Login: usuarioId={} contaId={}", usuario.getId(), usuario.getConta().getId());
        return new Autenticacao(tokens.emitir(usuario), tokens.expiracaoEmSegundos());
    }

    private static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record Autenticacao(String accessToken, long expiraEmSegundos) {
    }
}
