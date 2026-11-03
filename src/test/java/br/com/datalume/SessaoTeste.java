package br.com.datalume;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Cria conta + usuário (plano GRATUITO) pela API e devolve o token de acesso. */
public record SessaoTeste(String token, UUID contaId, UUID usuarioId, String email) {

    public static final String SENHA = "SenhaForte123";

    public static SessaoTeste nova(MockMvc mvc, ObjectMapper json) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@exemplo.com.br";
        String cadastro = mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nomeEmpresa", "Empresa Teste", "nome", "Ana Teste",
                                "email", email, "senha", SENHA))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "senha", SENHA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new SessaoTeste(JsonPath.read(login, "$.accessToken"),
                UUID.fromString(JsonPath.read(cadastro, "$.conta.id")),
                UUID.fromString(JsonPath.read(cadastro, "$.usuario.id")), email);
    }

    public String bearer() {
        return "Bearer " + token;
    }
}