package br.com.datalume.busca;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.datalume.TestcontainersConfig;
import br.com.datalume.estabelecimento.CriteriosBusca;
import br.com.datalume.estabelecimento.EstabelecimentoPesquisa;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Teste de desempenho (desligado por padrão): mvn verify -Dperf=true [-Dperf.linhas=300000]
 * Gera dados sintéticos e exige p95 abaixo de 300 ms para o recorte do piloto (primeira página + contagem).
 */
@EnabledIfSystemProperty(named = "perf", matches = "true")
@SpringBootTest
@Import(TestcontainersConfig.class)
class BuscaDesempenhoTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EstabelecimentoPesquisa pesquisa;


    @Test
    void recorteDoPilotoRetornaEmMenosDe300msNoP95() throws IOException {
        String linhas = System.getProperty("perf.linhas", "300000");
        String sql = Files.readString(Path.of("scripts/sinteticos/gerar.sql")).replace(":quantidade", linhas);
        jdbc.execute(sql);
        try {
            CriteriosBusca odontoSp = new CriteriosBusca(List.of("8630504"), false, List.of("SP"), null, null, null,
                    null, null, null, false, false, null);
            CriteriosBusca comSecundarios = new CriteriosBusca(List.of("8630504"), true, List.of("SP"), null, null,
                    null, null, null, null, false, false, null);

            for (int i = 0; i < 5; i++) { // aquecimento
                pesquisa.pesquisar(odontoSp, 50, null, false);
            }
            List<Long> tempos = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                CriteriosBusca c = i % 2 == 0 ? odontoSp : comSecundarios;
                long inicio = System.nanoTime();
                pesquisa.pesquisar(c, 50, null, false);
                tempos.add((System.nanoTime() - inicio) / 1_000_000);
            }
            Collections.sort(tempos);
            long p95 = tempos.get((int) Math.ceil(tempos.size() * 0.95) - 1);
            System.out.println("p95 da busca com " + linhas + " linhas: " + p95 + " ms (todas: " + tempos + ")");
            assertThat(p95).isLessThan(300);
        } finally {
            jdbc.update("DELETE FROM estabelecimento WHERE referencia_alteracao = '0000-00'");
            jdbc.update("DELETE FROM empresa WHERE referencia_alteracao = '0000-00'");
        }
    }
}