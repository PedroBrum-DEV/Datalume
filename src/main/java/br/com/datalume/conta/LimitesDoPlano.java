package br.com.datalume.conta;

/** Limites do plano de uma conta (API pública do módulo conta). */
public record LimitesDoPlano(String plano, int limiteUsuarios, int limiteNichos, int limiteAlertas,
        int limiteExportacao) {
}