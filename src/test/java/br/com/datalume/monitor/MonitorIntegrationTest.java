package br.com.datalume.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.datalume.SessaoTeste;
import br.com.datalume.TestcontainersConfig;
import br.com.datalume.monitor.aplicacao.ProcessadorMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
class MonitorIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ProcessadorMonitor processador;

    @BeforeEach
    void catalogo() {
        jdbc.update("INSERT INTO cnae_subclasse (codigo, descricao, classe, grupo, divisao, secao) "
                + "VALUES ('9960001', 'Teste monitor', '99600', '996', '99', 'Z') ON CONFLICT DO NOTHING");
    }

    @Test
    void regrasRespeitamLimiteDoPlanoEIsolamentoEntreContas() throws Exception {
        SessaoTeste s = SessaoTeste.nova(mvc, json);
        String nichoId = criarNicho(s, "Monitorado");

        definir(s, nichoId, List.of("ABERTURA", "BAIXA")).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipos.length()").value(2));
        mvc.perform(get("/api/nichos/" + nichoId + "/monitoramento").header("Authorization", s.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tipos.length()").value(2));

        // plano GRATUITO: 3 regras. Quatro tipos não cabem.
        definir(s, nichoId, List.of("ABERTURA", "BAIXA", "SITUACAO", "MUDANCA_CNAE"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        // substituir por três cabe
        definir(s, nichoId, List.of("ABERTURA", "BAIXA", "SITUACAO")).andExpect(status().isOk());

        SessaoTeste outra = SessaoTeste.nova(mvc, json);
        mvc.perform(get("/api/nichos/" + nichoId + "/monitoramento").header("Authorization", outra.bearer()))
                .andExpect(status().isNotFound());
        definir(outra, nichoId, List.of("ABERTURA")).andExpect(status().isNotFound());

        definir(s, nichoId, List.of()).andExpect(status().isOk()).andExpect(jsonPath("$.tipos.length()").value(0));
        mvc.perform(delete("/api/nichos/" + nichoId + "/monitoramento").header("Authorization", s.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/nichos/" + nichoId + "/monitoramento").header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tipos\":[\"INVENTADO\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processaSoOsTiposAssinadosEEhIdempotente() throws Exception {
        String ref = "2032-01";
        SessaoTeste s = SessaoTeste.nova(mvc, json);
        String nichoId = criarNicho(s, "Processar");
        definir(s, nichoId, List.of("ABERTURA", "BAIXA")).andExpect(status().isOk());

        evento("99600001000100", ref, "ABERTURA", null, "9960001", null, "SP", "LOJA NOVA");
        evento("99600002000100", ref, "BAIXA", "9960001", "9960001", "SP", "SP", "LOJA BAIXADA");
        evento("99600003000100", ref, "MUDANCA_ENDERECO", "9960001", "9960001", "SP", "RJ", "LOJA QUE MUDOU");
        evento("99600004000100", ref, "ABERTURA", null, "9960001", null, "RJ", "LOJA FORA DO NICHO");

        processador.processar(ref, false);

        Long resultadoId = jdbc.queryForObject(
                "SELECT id FROM monitor_resultado WHERE nicho_id = CAST(? AS uuid) AND referencia = ?", Long.class,
                nichoId, ref);
        assertThat(jdbc.queryForObject("SELECT total FROM monitor_resultado WHERE id = ?", Long.class, resultadoId))
                .isEqualTo(2); // ABERTURA no nicho + BAIXA; o endereço não foi assinado e o RJ está fora
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor_resultado_item WHERE resultado_id = ?",
                Long.class, resultadoId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM monitor_execucao WHERE referencia = ?", String.class, ref))
                .isEqualTo("CONCLUIDA");
        assertThat(jdbc.queryForList("SELECT descricao FROM monitor_resultado_item WHERE resultado_id = ? ORDER BY id",
                String.class, resultadoId)).anyMatch(d -> d.contains("Nova empresa")).anyMatch(d -> d.contains("baixada"));

        // mesma referência de novo: ignorada; forçada: regrava os mesmos dados, sem duplicar
        processador.processar(ref, false);
        processador.processar(ref, true);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM monitor_resultado WHERE nicho_id = CAST(? AS uuid) AND referencia = ?",
                Long.class, nichoId, ref)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor_resultado_item WHERE resultado_id = ?",
                Long.class, resultadoId)).isEqualTo(2);
    }

    @Test
    void consultaAoVivoMostraTodasAsMudancasDoNichoComDirecao() throws Exception {
        String ref = "2032-02";
        SessaoTeste s = SessaoTeste.nova(mvc, json);
        String nichoId = criarNicho(s, "Ao vivo");

        evento("99601001000100", ref, "ABERTURA", null, "9960001", null, "SP", "ABRIU");
        evento("99601002000100", ref, "MUDANCA_ENDERECO", "9960001", "9960001", "SP", "RJ", "SAIU DE SP");
        evento("99601003000100", ref, "ABERTURA", null, "9960001", null, "RJ", "OUTRA REGIAO");

        String corpo = mvc.perform(get("/api/nichos/" + nichoId + "/mudancas?referencia=" + ref)
                        .header("Authorization", s.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referencia").value(ref))
                .andExpect(jsonPath("$.total").value(2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> direcoes = JsonPath.read(corpo, "$.itens[?(@.tipo=='MUDANCA_ENDERECO')].direcao");
        assertThat(direcoes).containsExactly("SAIU");
        List<String> descricoes = JsonPath.read(corpo, "$.itens[?(@.tipo=='MUDANCA_ENDERECO')].descricao");
        assertThat(descricoes.get(0)).contains("saiu do nicho");

        mvc.perform(get("/api/nichos/" + nichoId + "/mudancas?referencia=" + ref + "&tipo=ABERTURA")
                        .header("Authorization", s.bearer()))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/nichos/" + nichoId + "/mudancas?referencia=2032-0x")
                        .header("Authorization", s.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminProcessaEListaExecucoes() throws Exception {
        SessaoTeste comum = SessaoTeste.nova(mvc, json);
        mvc.perform(get("/api/admin/monitor/execucoes").header("Authorization", comum.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/monitor/processar").header("Authorization", comum.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"referencia\":\"2032-03\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private String criarNicho(SessaoTeste s, String nome) throws Exception {
        String corpo = mvc.perform(post("/api/nichos").header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", nome, "cnaes", List.of("99600"),
                                "ufs", List.of("SP")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(corpo, "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions definir(SessaoTeste s, String nichoId,
            List<String> tipos) throws Exception {
        return mvc.perform(put("/api/nichos/" + nichoId + "/monitoramento").header("Authorization", s.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("tipos", tipos))));
    }

    private void evento(String cnpj, String ref, String tipo, String cnaeAntes, String cnaeDepois, String ufAntes,
            String ufDepois, String nome) {
        jdbc.update("""
                INSERT INTO estabelecimento (cnpj, cnpj_basico, cnpj_ordem, cnpj_dv, matriz, nome_fantasia,
                    situacao_cadastral, cnae_principal, uf, referencia_alteracao, row_hash)
                VALUES (?, ?, '0001', '00', TRUE, ?, '02', ?, ?, ?, 'h') ON CONFLICT DO NOTHING
                """, cnpj, cnpj.substring(0, 8), nome, cnaeDepois, ufDepois, ref);
        jdbc.update("""
                INSERT INTO estabelecimento_evento (cnpj, referencia, tipo, cnae_antes, cnae_depois, uf_antes, uf_depois)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, cnpj, ref, tipo, cnaeAntes, cnaeDepois, ufAntes, ufDepois);
    }
}