package br.com.datalume.busca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.datalume.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
class NichoApiIntegrationTest {

    private static final String MARCA = "0000-78";
    private static final String SENHA = "SenhaForte123";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    record Sessao(String token, UUID contaId) {
    }

    @BeforeEach
    void fixtures() {
        limpar();
        jdbc.update("INSERT INTO cnae_subclasse (codigo, descricao, classe, grupo, divisao, secao) "
                + "VALUES ('9980001', 'Teste nicho', '99800', '998', '99', 'Z') ON CONFLICT DO NOTHING");
        jdbc.update("""
                INSERT INTO estabelecimento (cnpj, cnpj_basico, cnpj_ordem, cnpj_dv, matriz, nome_fantasia,
                    situacao_cadastral, data_inicio_atividade, cnae_principal, uf, municipio_ibge, email,
                    telefone1, referencia_alteracao, row_hash)
                SELECT 'R' || lpad(i::text, 7, '0') || '000100', 'R' || lpad(i::text, 7, '0'), '0001', '00', TRUE,
                       'NICHO ' || i, '02', DATE '2021-01-01' + i, '9980001', 'SP', 3550308,
                       'nicho' || i || '@exemplo.com.br', '999990000', ?, 'h'
                FROM generate_series(1, 120) i
                """, MARCA);
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM estabelecimento WHERE referencia_alteracao = ?", MARCA);
        jdbc.update("DELETE FROM cnae_subclasse WHERE codigo = '9980001'");
    }

    @Test
    void crudCompletoDeNichos() throws Exception {
        Sessao s = novaSessao();
        String id = criarNicho(s, "Odonto SP", List.of("99800"), List.of("SP")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String nichoId = JsonPath.read(id, "$.id");

        mvc.perform(get("/api/nichos").header("Authorization", bearer(s)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Odonto SP"));

        mvc.perform(put("/api/nichos/" + nichoId).header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", "Odonto SP v2", "cnaes", List.of("9980001"),
                                "incluirSecundarios", true, "ufs", List.of("SP", "RJ")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Odonto SP v2"))
                .andExpect(jsonPath("$.incluirSecundarios").value(true))
                .andExpect(jsonPath("$.ufs.length()").value(2));

        mvc.perform(delete("/api/nichos/" + nichoId).header("Authorization", bearer(s)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/nichos/" + nichoId).header("Authorization", bearer(s)))
                .andExpect(status().isNotFound());
    }

    @Test
    void respeitaOLimiteDeNichosDoPlano() throws Exception {
        Sessao s = novaSessao(); // plano GRATUITO: 1 nicho
        criarNicho(s, "Primeiro", List.of("99800"), List.of("SP")).andExpect(status().isCreated());
        criarNicho(s, "Segundo", List.of("99800"), List.of("RJ"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejeitaNomeDuplicadoCnaeDesconhecidoEEntradaInvalida() throws Exception {
        Sessao s = novaSessao();
        mudarPlano(s, "PILOTO");
        criarNicho(s, "Mesmo nome", List.of("99800"), List.of("SP")).andExpect(status().isCreated());
        criarNicho(s, "mesmo NOME", List.of("99800"), List.of("RJ")).andExpect(status().isConflict());
        criarNicho(s, "Cnae ruim", List.of("77777"), List.of("SP")).andExpect(status().isBadRequest());
        criarNicho(s, "Sem cnae", List.of(), List.of("SP")).andExpect(status().isBadRequest());
        criarNicho(s, "UF ruim", List.of("99800"), List.of("XX")).andExpect(status().isBadRequest());
    }

    @Test
    void contasNaoEnxergamNichosUmasDasOutras() throws Exception {
        Sessao a = novaSessao();
        Sessao b = novaSessao();
        String corpo = criarNicho(a, "Da conta A", List.of("99800"), List.of("SP")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(corpo, "$.id");

        mvc.perform(get("/api/nichos/" + id).header("Authorization", bearer(b))).andExpect(status().isNotFound());
        mvc.perform(get("/api/nichos/" + id + "/empresas").header("Authorization", bearer(b)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/nichos/" + id).header("Authorization", bearer(b))).andExpect(status().isNotFound());
        mvc.perform(get("/api/nichos").header("Authorization", bearer(b)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listaEmpresasDoNichoComPaginacaoEContatosOpcionais() throws Exception {
        Sessao s = novaSessao();
        String id = idDe(criarNicho(s, "Lista", List.of("99800"), List.of("SP")));

        mvc.perform(get("/api/nichos/" + id + "/empresas").header("Authorization", bearer(s)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(50))
                .andExpect(jsonPath("$.total").value(120))
                .andExpect(jsonPath("$.totalExato").value(true))
                .andExpect(jsonPath("$.proximoCursor").exists())
                .andExpect(jsonPath("$.itens[0].temEmail").value(true))
                .andExpect(jsonPath("$.itens[0].email").doesNotExist());

        mvc.perform(get("/api/nichos/" + id + "/empresas?contatos=true&limite=5").header("Authorization", bearer(s)))
                .andExpect(jsonPath("$.itens[0].email").exists());

        mvc.perform(get("/api/nichos/" + id + "/facetas").header("Authorization", bearer(s)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.municipios[0].chave").value("3550308"));
    }

    @Test
    void buscaLivreExigeFiltroEAceitaParametros() throws Exception {
        Sessao s = novaSessao();
        mvc.perform(get("/api/busca/estabelecimentos").header("Authorization", bearer(s)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/busca/estabelecimentos?cnae=99800&uf=SP&abertoDe=2021-03-01&abertoAte=2021-03-10")
                        .header("Authorization", bearer(s)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10));
        mvc.perform(get("/api/busca/estabelecimentos")).andExpect(status().isUnauthorized());
    }

    @Test
    void exportaCsvRespeitandoLimiteDoPlanoERegistrandoAExportacao() throws Exception {
        Sessao s = novaSessao(); // GRATUITO: exporta no maximo 100 linhas
        String id = idDe(criarNicho(s, "Exportar", List.of("99800"), List.of("SP")));

        var resposta = mvc.perform(post("/api/nichos/" + id + "/exportacoes").header("Authorization", bearer(s)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Limite-Exportacao", "100"))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse();
        String csv = resposta.getContentAsString(StandardCharsets.UTF_8);
        String[] linhas = csv.replace("\uFEFF", "").trim().split("\r\n");

        assertThat(linhas).hasSize(101); // cabecalho + 100
        assertThat(linhas[0]).startsWith("cnpj;razao_social").doesNotContain("email");
        assertThat(csv).doesNotContain("@exemplo.com.br");

        mvc.perform(get("/api/exportacoes").header("Authorization", bearer(s)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].linhas").value(100))
                .andExpect(jsonPath("$[0].contatos").value(false))
                .andExpect(jsonPath("$[0].nichoNome").value("Exportar"));

        // com contatos e plano maior: sai tudo, com email
        mudarPlano(s, "PILOTO");
        String completo = mvc.perform(post("/api/nichos/" + id + "/exportacoes?contatos=true")
                        .header("Authorization", bearer(s)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(completo.replace("\uFEFF", "").trim().split("\r\n")).hasSize(121);
        assertThat(completo).contains("telefone;email").contains("@exemplo.com.br");
    }

    // ------------------------------------------------------------------ helpers

    private org.springframework.test.web.servlet.ResultActions criarNicho(Sessao s, String nome, List<String> cnaes,
            List<String> ufs) throws Exception {
        return mvc.perform(post("/api/nichos").header("Authorization", bearer(s))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("nome", nome, "cnaes", cnaes, "ufs", ufs))));
    }

    private String idDe(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        r.andExpect(status().isCreated());
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
    }

    private void mudarPlano(Sessao s, String codigo) {
        jdbc.update("UPDATE conta SET plano_id = (SELECT id FROM plano WHERE codigo = ?) WHERE id = ?",
                codigo, s.contaId());
    }

    private static String bearer(Sessao s) {
        return "Bearer " + s.token();
    }

    private Sessao novaSessao() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@exemplo.com.br";
        String cadastro = mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nomeEmpresa", "Empresa Teste", "nome", "Ana",
                                "email", email, "senha", SENHA))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID contaId = UUID.fromString(JsonPath.read(cadastro, "$.conta.id"));
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "senha", SENHA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Sessao(JsonPath.read(login, "$.accessToken"), contaId);
    }
}