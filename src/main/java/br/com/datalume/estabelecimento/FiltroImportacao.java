package br.com.datalume.estabelecimento;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Restringe o que entra em {@code estabelecimento} durante a importação (o piloto não precisa dos
 * ~60 milhões de registros do país). Lista vazia = sem restrição naquele critério.
 *
 * @param ufs           siglas, ex.: SP
 * @param cnaePrefixos  prefixos do CNAE principal, ex.: 8630 ou 4635402
 */
public record FiltroImportacao(List<String> ufs, List<String> cnaePrefixos) {

    public FiltroImportacao {
        List<String> ufsOk = new ArrayList<>();
        if (ufs != null) {
            for (String uf : ufs) {
                String s = uf == null ? "" : uf.trim().toUpperCase(Locale.ROOT);
                if (s.isEmpty()) {
                    continue;
                }
                if (!s.matches("[A-Z]{2}")) {
                    throw new IllegalArgumentException("UF inválida no filtro de importação: " + uf);
                }
                ufsOk.add(s);
            }
        }
        List<String> cnaesOk = new ArrayList<>();
        if (cnaePrefixos != null) {
            for (String cnae : cnaePrefixos) {
                String s = cnae == null ? "" : cnae.replaceAll("\\D", "");
                if (s.isEmpty()) {
                    continue;
                }
                if (!s.matches("\\d{2,7}")) {
                    throw new IllegalArgumentException("Prefixo de CNAE inválido no filtro de importação: " + cnae);
                }
                cnaesOk.add(s);
            }
        }
        ufs = List.copyOf(ufsOk);
        cnaePrefixos = List.copyOf(cnaesOk);
    }

    public static FiltroImportacao semFiltro() {
        return new FiltroImportacao(List.of(), List.of());
    }

    public boolean vazio() {
        return ufs.isEmpty() && cnaePrefixos.isEmpty();
    }

    public String descricao() {
        if (vazio()) {
            return "sem filtro (Brasil inteiro)";
        }
        return "ufs=" + (ufs.isEmpty() ? "todas" : String.join(",", ufs))
                + "; cnaes=" + (cnaePrefixos.isEmpty() ? "todos" : String.join(",", cnaePrefixos));
    }

    /**
     * Condição SQL sobre as colunas de staging. Os valores foram validados no construtor
     * (apenas A-Z e dígitos), então a concatenação é segura.
     */
    String clausulaSql(String colunaUf, String colunaCnae) {
        List<String> partes = new ArrayList<>();
        if (!ufs.isEmpty()) {
            partes.add("upper(btrim(" + colunaUf + ")) IN ("
                    + ufs.stream().map(u -> "'" + u + "'").collect(Collectors.joining(", ")) + ")");
        }
        if (!cnaePrefixos.isEmpty()) {
            partes.add("(" + cnaePrefixos.stream()
                    .map(p -> "btrim(" + colunaCnae + ") LIKE '" + p + "%'")
                    .collect(Collectors.joining(" OR ")) + ")");
        }
        return partes.isEmpty() ? "TRUE" : String.join(" AND ", partes);
    }
}