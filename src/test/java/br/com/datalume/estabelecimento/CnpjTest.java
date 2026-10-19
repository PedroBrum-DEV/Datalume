package br.com.datalume.estabelecimento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CnpjTest {

    @Test
    void validaCnpjNumericoComEPontuacao() {
        assertThat(Cnpj.valido("11222333000181")).isTrue();
        assertThat(Cnpj.valido("11.222.333/0001-81")).isTrue();
        assertThat(Cnpj.valido("00000000000191")).isTrue();
    }

    @Test
    void rejeitaDvErradoTamanhoErradoERepetidos() {
        assertThat(Cnpj.valido("11222333000182")).isFalse();
        assertThat(Cnpj.valido("1122233300018")).isFalse();
        assertThat(Cnpj.valido("00000000000000")).isFalse();
        assertThat(Cnpj.valido(null)).isFalse();
    }

    @Test
    void validaCnpjAlfanumerico() {
        assertThat(Cnpj.valido("12ABC34501DE35")).isTrue();
        assertThat(Cnpj.valido("12abc34501de35")).isTrue();
        assertThat(Cnpj.valido("12ABC34501DE36")).isFalse();
    }

    @Test
    void formata() {
        assertThat(Cnpj.formatar("11222333000181")).isEqualTo("11.222.333/0001-81");
        assertThat(Cnpj.formatar("12ABC34501DE35")).isEqualTo("12.ABC.345/01DE-35");
    }
}