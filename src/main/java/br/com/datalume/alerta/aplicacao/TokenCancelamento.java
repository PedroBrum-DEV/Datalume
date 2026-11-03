package br.com.datalume.alerta.aplicacao;

import br.com.datalume.alerta.AlertaProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Assina (HMAC-SHA256) o link de cancelamento do e-mail: permite cancelar sem fazer login. */
@Component
public class TokenCancelamento {

    private final byte[] chave;

    public TokenCancelamento(AlertaProperties props) {
        this.chave = props.segredoLinks().getBytes(StandardCharsets.UTF_8);
    }

    public String gerar(UUID usuarioId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chave, "HmacSHA256"));
            byte[] assinatura = mac.doFinal(("cancelar:" + usuarioId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(assinatura);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponível", e);
        }
    }

    public boolean valido(UUID usuarioId, String token) {
        if (token == null) {
            return false;
        }
        return MessageDigest.isEqual(gerar(usuarioId).getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}