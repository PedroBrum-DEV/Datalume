package br.com.datalume.conta.aplicacao;

import br.com.datalume.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.datalume.conta.dominio.Conta;
import br.com.datalume.conta.dominio.Plano;
import br.com.datalume.conta.dominio.Usuario;
import br.com.datalume.conta.dominio.UsuarioRepository;
import br.com.datalume.conta.web.PerfilResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContaService {

    private final UsuarioRepository usuarios;

    public ContaService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public PerfilResponse perfilDoUsuario(UUID usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        return paraPerfil(usuario);
    }

    /** Deve ser chamado dentro de uma transação (acessa associações lazy). */
    @Transactional(readOnly = true)
    public PerfilResponse paraPerfil(Usuario usuario) {
        Conta conta = usuario.getConta();
        Plano plano = conta.getPlano();
        return new PerfilResponse(
                new PerfilResponse.UsuarioDto(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                        usuario.getPapel().name()),
                new PerfilResponse.ContaDto(conta.getId(), conta.getNome()),
                new PerfilResponse.PlanoDto(plano.getCodigo(), plano.getNome(), plano.getLimiteUsuarios(),
                        plano.getLimiteNichos(), plano.getLimiteAlertas()));
    }
}
