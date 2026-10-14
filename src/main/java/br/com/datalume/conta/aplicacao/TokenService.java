package br.com.datalume.conta.aplicacao;

import br.com.datalume.conta.dominio.Usuario;
import br.com.datalume.conta.seguranca.SegurancaProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final SegurancaProperties props;

    public TokenService(JwtEncoder encoder, SegurancaProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public long expiracaoEmSegundos() {
        return props.jwtExpiracao().toSeconds();
    }

    public String emitir(Usuario usuario) {
        Instant agora = Instant.now();
        List<String> papeis = new ArrayList<>();
        papeis.add(usuario.getPapel().name());
        if (usuario.isAdminPlataforma()) {
            papeis.add("ADMIN_PLATAFORMA");
        }
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.emissor())
                .issuedAt(agora)
                .expiresAt(agora.plus(props.jwtExpiracao()))
                .subject(usuario.getId().toString())
                .claim("contaId", usuario.getConta().getId().toString())
                .claim("email", usuario.getEmail())
                .claim("papeis", papeis)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
