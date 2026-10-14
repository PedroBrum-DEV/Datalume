package br.com.datalume.compartilhado.texto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextosTest {

    @Test
    void normalizaAcentosCaixaEPontuacao() {
        assertThat(Textos.normalizar("Santa Bárbara d'Oeste")).isEqualTo("SANTA BARBARA D OESTE");
        assertThat(Textos.normalizar("GUAJARA-MIRIM")).isEqualTo(Textos.normalizar("Guajará-Mirim"));
        assertThat(Textos.normalizar("  São   Paulo ")).isEqualTo("SAO PAULO");
        assertThat(Textos.normalizar(null)).isEmpty();
    }

    @Test
    void mantemSomenteDigitos() {
        assertThat(Textos.somenteDigitos("8630-5/04")).isEqualTo("8630504");
    }
}
