package br.com.datalume.estabelecimento;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utilitário de CNPJ, já com o formato alfanumérico (12 primeiros caracteres A-Z/0-9 e 2 dígitos
 * verificadores numéricos). Cada caractere vale (código ASCII - 48) no cálculo do DV; para CNPJs
 * somente numéricos o resultado é idêntico ao algoritmo tradicional.
 */
public final class Cnpj {

    private static final Pattern FORMATO = Pattern.compile("[A-Z0-9]{12}[0-9]{2}");
    private static final int[] PESOS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private Cnpj() {
    }

    /** Remove pontuação e coloca em maiúsculas: "11.222.333/0001-81" -> "11222333000181". */
    public static String normalizar(String texto) {
        return texto == null ? "" : texto.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean valido(String texto) {
        String c = normalizar(texto);
        if (!FORMATO.matcher(c).matches() || c.chars().distinct().count() == 1) {
            return false;
        }
        return digito(c, 12) == c.charAt(12) - '0' && digito(c, 13) == c.charAt(13) - '0';
    }

    /** Formata como XX.XXX.XXX/XXXX-XX. Devolve o texto normalizado se não tiver 14 caracteres. */
    public static String formatar(String texto) {
        String c = normalizar(texto);
        if (c.length() != 14) {
            return c;
        }
        return c.substring(0, 2) + "." + c.substring(2, 5) + "." + c.substring(5, 8) + "/"
                + c.substring(8, 12) + "-" + c.substring(12);
    }

    private static int digito(String c, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (c.charAt(i) - 48) * PESOS[i + 13 - quantidade];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}