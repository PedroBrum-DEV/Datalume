package br.com.datalume.alerta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.datalume.SessaoTeste;
import br.com.datalume.TestcontainersConfig;
import br.com.datalume.alerta.aplicacao.EnvioService;
import br.com.datalume.alerta.aplicacao.TokenCancelamento;
import br.com.datalume.monitor.aplicacao.ProcessadorMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Feed, e-mail (GreenMail, SMTP de mentira em localhost:3025), preferências e cancelamento por link. */
@SpringBootTest(properties = {
        "datalume.alerta.email-habilitado=true",
        "datalume.alerta.intervalo-envio-ms=3600000",
        "spring.mail.host=localhost",
        "spring.mail.port=3025"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AlertaIntegrationTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP).withPerMethodLifecycle(false);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ProcessadorMonitor processador;

    @Autowired
    EnvioService envios;

    @Autowired
    TokenCancelamento tokens;

    record Cenario(SessaoTeste sessao, String nichoId, String ref, String cnpjAbertura) {
    }

    @Test
    void geraFeedEEnviaUmEmailPorUsuarioEReferencia() throws Exception {
        Cenario c = cenario("9970001", "2033-01", "99700010");
        processador.processar(c.ref(), false);

        mvc.perform(get("/api/alertas").header("Authorization", c.sessao().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].total").value(2))
                .andExpect(jsonPath("$[0].lido").value(false))
                .andExpect(jsonPath("$[0].nichoNome").value("Nicho Alerta"));
        long alertaId = primeiroAlerta(c);
        mvc.perform(get("/api/alertas/" + alertaId).header("Authorization", c.sessao().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(2));
        mvc.perform(get("/api/alertas/contagem").header("Authorization", c.sessao().bearer()))
                .andExpect(jsonPath("$.naoLidos").value(1));

        assertThat(enviosDoUsuario(c)).isEqualTo(1);
        envios.processarPendentes();

        List<MimeMessage> msgs = mensagensPara(c.sessao().email());
        assertThat(msgs).hasSize(1);
        MimeMessage m = msgs.get(0);
        assertThat(m.getSubject()).contains("2 mudanças").contains("01/2033");
        assertThat(m.getHeader("List-Unsubscribe")).isNotNull();
        String texto = textoSimples(m);
        assertThat(texto).contains("Nicho Alerta").contains("99.700.010/0001-00").contains("/api/alertas/cancelar?u=");
        assertThat(jdbc.queryForObject("SELECT status FROM alerta_envio WHERE usuario_id = ?", String.class,
                c.sessao().usuarioId())).isEqualTo("ENVIADO");

        // reprocessar a mesma referência não duplica resultado, envio nem e-mail
        processador.processar(c.ref(), true);
        envios.processarPendentes();
        assertThat(enviosDoUsuario(c)).isEqualTo(1);
        assertThat(mensagensPara(c.sessao().email())).hasSize(1);
    }

    @Test
    void marcaComoLidoPorUsuario() throws Exception {
        Cenario c = cenario("9970002", "2033-02", "99700020");
        processador.processar(c.ref(), false);
        long id = primeiroAlerta(c);

        mvc.perform(post("/api/alertas/" + id + "/lido").header("Authorization", c.sessao().bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/alertas/contagem").header("Authorization", c.sessao().bearer()))
                .andExpect(jsonPath("$.naoLidos").value(0));
        mvc.perform(get("/api/alertas?apenasNaoLidos=true").header("Authorization", c.sessao().bearer()))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/alertas").header("Authorization", c.sessao().bearer()))
                .andExpect(jsonPath("$[0].lido").value(true));
        mvc.perform(post("/api/alertas/lidos").header("Authorization", c.sessao().bearer()))
                .andExpect(jsonPath("$.marcados").value(0));
    }

    @Test
    void contasNaoEnxergamAlertasUmasDasOutras() throws Exception {
        Cenario c = cenario("9970003", "2033-03", "99700030");
        processador.processar(c.ref(), false);
        long id = primeiroAlerta(c);

        SessaoTeste outra = SessaoTeste.nova(mvc, json);
        mvc.perform(get("/api/alertas").header("Authorization", outra.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/alertas/" + id).header("Authorization", outra.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/alertas/" + id + "/lido").header("Authorization", outra.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/alertas")).andExpect(status().isUnauthorized());
    }

    @Test
    void cancelamentoPorLinkInterrompeOsEmails() throws Exception {
        Cenario c = cenario("9970004", "2033-04", "99700040");
        processador.processar(c.ref(), false);
        assertThat(enviosDoUsuario(c)).isEqualTo(1); // pendente, ainda não enviado

        mvc.perform(get("/api/alertas/cancelar?u=" + c.sessao().usuarioId() + "&t=token-falso"))
                .andExpect(status().isBadRequest());
        String token = tokens.gerar(c.sessao().usuarioId());
        mvc.perform(get("/api/alertas/cancelar?u=" + c.sessao().usuarioId() + "&t=" + token)) // sem login
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));

        mvc.perform(get("/api/alertas/preferencias").header("Authorization", c.sessao().bearer()))
                .andExpect(jsonPath("$.emailAtivo").value(false));
        assertThat(jdbc.queryForObject("SELECT status FROM alerta_envio WHERE usuario_id = ?", String.class,
                c.sessao().usuarioId())).isEqualTo("CANCELADO");
        envios.processarPendentes();
        assertThat(mensagensPara(c.sessao().email())).isEmpty();

        // novas mudanças não geram envio para quem cancelou, mas o feed continua
        evento("99700041000100", "2033-05", "ABERTURA", null, "9970004", null, "SP", "ABRIU DEPOIS");
        processador.processar("2033-05", false);
        assertThat(enviosDoUsuario(c)).isEqualTo(1);
        mvc.perform(get("/api/alertas").header("Authorization", c.sessao().bearer()))
                .andExpect(jsonPath("$.length()").value(2));

        // reativar
        mvc.perform(put("/api/alertas/preferencias").header("Authorization", c.sessao().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"emailAtivo\":true}"))
                .andExpect(jsonPath("$.emailAtivo").value(true));
    }

    // ------------------------------------------------------------------ helpers

    /** Conta nova + nicho (CNAE 99700...) monitorando ABERTURA e BAIXA, com 1 abertura e 1 baixa na referência. */
    private Cenario cenario(String cnae, String ref, String basico) throws Exception {
        jdbc.update("INSERT INTO cnae_subclasse (codigo, descricao, classe, grupo, divisao, secao) "
                + "VALUES (?, 'Teste alerta', '99700', '997', '99', 'Z') ON CONFLICT DO NOTHING", cnae);
        SessaoTeste s = SessaoTeste.nova(mvc, json);
        String corpo = mvc.perform(post("/api/nichos").header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", "Nicho Alerta", "cnaes", List.of(cnae),
                                "ufs", List.of("SP")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String nichoId = JsonPath.read(corpo, "$.id");
        mvc.perform(put("/api/nichos/" + nichoId + "/monitoramento").header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tipos\":[\"ABERTURA\",\"BAIXA\"]}"))
                .andExpect(status().isOk());
        evento(basico + "0001" + "00", ref, "ABERTURA", null, cnae, null, "SP", "EMPRESA NOVA");
        evento(basico.substring(0, 7) + "9" + "0001" + "00", ref, "BAIXA", cnae, cnae, "SP", "SP", "EMPRESA BAIXADA");
        return new Cenario(s, nichoId, ref, basico + "000100");
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

    private long primeiroAlerta(Cenario c) throws Exception {
        String corpo = mvc.perform(get("/api/alertas").header("Authorization", c.sessao().bearer()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Number id = JsonPath.read(corpo, "$[0].id");
        return id.longValue();
    }

    private long enviosDoUsuario(Cenario c) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM alerta_envio WHERE usuario_id = ?", Long.class,
                c.sessao().usuarioId());
        return n == null ? 0 : n;
    }

    private List<MimeMessage> mensagensPara(String email) throws Exception {
        List<MimeMessage> r = new ArrayList<>();
        for (MimeMessage m : greenMail.getReceivedMessages()) {
            if (m.getAllRecipients() != null && m.getAllRecipients()[0].toString().contains(email)) {
                r.add(m);
            }
        }
        return r;
    }

    /** Texto da parte text/plain, já decodificada (percorre multiparts). */
    private static String textoSimples(Part parte) throws Exception {
        if (parte.isMimeType("text/plain")) {
            return (String) parte.getContent();
        }
        if (parte.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) parte.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                String t = textoSimples(bp);
                if (!t.isEmpty()) {
                    return t;
                }
            }
        }
        return "";
    }
}