package br.com.datalume.compartilhado.texto;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Textos {

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern NAO_ALFANUMERICO = Pattern.compile("[^A-Z0-9]+");

    private Textos() {
    }

    /**
     * Normaliza para comparação: maiúsculas, sem acentos, pontuação vira espaço e espaços colapsam.
     * Ex.: "Santa Bárbara d'Oeste" e "SANTA BARBARA D OESTE" resultam no mesmo texto.
     */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD);
        s = MARCAS.matcher(s).replaceAll("").toUpperCase(Locale.ROOT);
        return NAO_ALFANUMERICO.matcher(s).replaceAll(" ").trim();
    }

    /** Mantém somente dígitos ("8630-5/04" -> "8630504"). */
    public static String somenteDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }
}
