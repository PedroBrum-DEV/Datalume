package br.com.datalume.estabelecimento;

/** Descrições da situação cadastral da Receita. */
public final class Situacoes {

    private Situacoes() {
    }

    public static String descricao(String codigo) {
        if (codigo == null) {
            return "Não informada";
        }
        return switch (codigo) {
            case "01" -> "Nula";
            case "02" -> "Ativa";
            case "03" -> "Suspensa";
            case "04" -> "Inapta";
            case "08" -> "Baixada";
            default -> "Desconhecida";
        };
    }
}