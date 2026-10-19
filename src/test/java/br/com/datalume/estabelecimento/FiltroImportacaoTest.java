package br.com.datalume.estabelecimento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class FiltroImportacaoTest {

    @Test
    void normalizaUfsECnaes() {
        FiltroImportacao f = new FiltroImportacao(List.of(" sp ", "rj"), List.of("8630-5/04", "4635"));
        assertThat(f.ufs()).containsExactly("SP", "RJ");
        assertThat(f.cnaePrefixos()).containsExactly("8630504", "4635");
        assertThat(f.vazio()).isFalse();
    }

    @Test
    void aceitaNulosEVazios() {
        assertThat(new FiltroImportacao(null, null).vazio()).isTrue();
        assertThat(new FiltroImportacao(List.of(""), List.of(" ")).vazio()).isTrue();
        assertThat(FiltroImportacao.semFiltro().clausulaSql("c20", "c12")).isEqualTo("TRUE");
    }

    @Test
    void rejeitaValoresQuePoderiamQuebrarOSql() {
        assertThatThrownBy(() -> new FiltroImportacao(List.of("SP'; DROP TABLE x;--"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FiltroImportacao(List.of(), List.of("1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void geraClausulaSql() {
        FiltroImportacao f = new FiltroImportacao(List.of("SP"), List.of("8630", "4635"));
        assertThat(f.clausulaSql("c20", "c12"))
                .isEqualTo("upper(btrim(c20)) IN ('SP') AND (btrim(c12) LIKE '8630%' OR btrim(c12) LIKE '4635%')");
    }
}