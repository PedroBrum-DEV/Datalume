package br.com.datalume.estabelecimento;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.datalume.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class EstabelecimentoControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void inserir() {
        limpar();
        jdbc.update("""
                INSERT INTO estabelecimento (cnpj, cnpj_basico, cnpj_ordem, cnpj_dv, matriz, nome_fantasia,
                    situacao_cadastral, uf, referencia_alteracao, row_hash)
                VALUES ('11222333000181', '11222333', '0001', '81', TRUE, 'CLINICA API', '02', 'SP', '2026-10', 'x')
                """);
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM estabelecimento WHERE cnpj = '11222333000181'");
    }

    @Test
    void retornaEstabelecimentoPorCnpjComOuSemPontuacao() throws Exception {
        String token = token();
        mvc.perform(get("/api/estabelecimentos/11222333000181").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cnpjFormatado").value("11.222.333/0001-81"))
                .andExpect(jsonPath("$.nomeFantasia").value("CLINICA API"))
                .andExpect(jsonPath("$.situacao.descricao").value("Ativa"));
        mvc.perform(get("/api/estabelecimentos/11.222.333-0001-81").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void cnpjInvalidoRetorna400() throws Exception {
        mvc.perform(get("/api/estabelecimentos/11222333000182").header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void cnpjValidoInexistenteRetorna404() throws Exception {
        mvc.perform(get("/api/estabelecimentos/00000000000191").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mvc.perform(get("/api/estabelecimentos/11222333000181")).andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioComumNaoAcessaAdministracaoDaImportacao() throws Exception {
        mvc.perform(get("/api/admin/importacao/lotes").header("Authorization", "Bearer " + token()))
                .andExpect(status().isForbidden());
    }

    private String token() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@exemplo.com.br";
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nomeEmpresa", "Empresa Teste", "nome", "Ana",
                                "email", email, "senha", "SenhaForte123"))))
                .andExpect(status().isCreated());
        String corpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "senha", "SenhaForte123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(corpo, "$.accessToken");
    }
}