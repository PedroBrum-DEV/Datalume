package br.com.datalume.conta;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.datalume.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AutenticacaoIntegrationTest {

    private static final String SENHA = "SenhaForte123";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void cadastroCriaContaNoPlanoGratuitoComUsuarioAdmin() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(cadastro(novoEmail(), SENHA)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.usuario.papel").value("ADMIN"))
                .andExpect(jsonPath("$.plano.codigo").value("GRATUITO"))
                .andExpect(jsonPath("$.conta.nome").value("Clínica Sorriso"));
    }

    @Test
    void cadastroComEmailDuplicadoRetorna409() throws Exception {
        String email = novoEmail();
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(cadastro(email, SENHA)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(cadastro(email.toUpperCase(), SENHA)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void cadastroComSenhaFracaRetorna400ComDetalhePorCampo() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(cadastro(novoEmail(), "123")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.erros[?(@.campo=='senha')]").exists());
    }

    @Test
    void loginRetornaTokenQueDaAcessoAoPerfil() throws Exception {
        String email = novoEmail();
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(cadastro(email, SENHA)))
                .andExpect(status().isCreated());

        String token = login(email, SENHA);

        mvc.perform(get("/api/conta/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.email").value(email))
                .andExpect(jsonPath("$.plano.limiteUsuarios").value(1));
    }

    @Test
    void loginComSenhaErradaRetorna401() throws Exception {
        String email = novoEmail();
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(cadastro(email, SENHA)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "senha", "outraSenha999"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Credenciais inválidas"));
    }

    @Test
    void acessoSemTokenRetorna401EmProblemJson() throws Exception {
        mvc.perform(get("/api/conta/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void usuarioComumNaoAcessaAreaAdministrativa() throws Exception {
        String email = novoEmail();
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(cadastro(email, SENHA)))
                .andExpect(status().isCreated());
        String token = login(email, SENHA);

        mvc.perform(get("/api/admin/referencia/resumo").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void healthDoActuatorEPublico() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void metricasDoActuatorExigemAdminDaPlataforma() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void rotaInexistenteRetorna404EmProblemJson() throws Exception {
        String email = novoEmail();
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(cadastro(email, SENHA)))
                .andExpect(status().isCreated());
        String token = login(email, SENHA);

        mvc.perform(get("/api/nao-existe").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(startsWith("about:blank")));
    }

    // ---- helpers ----

    private String login(String email, String senha) throws Exception {
        String corpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "senha", senha))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(corpo, "$.accessToken");
    }

    private String cadastro(String email, String senha) throws Exception {
        return json.writeValueAsString(Map.of(
                "nomeEmpresa", "Clínica Sorriso", "nome", "Ana", "email", email, "senha", senha));
    }

    private static String novoEmail() {
        return "user-" + UUID.randomUUID() + "@exemplo.com.br";
    }
}
