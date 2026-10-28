package br.com.datalume.estabelecimento;

import java.util.List;

/**
 * Contagens do recorte atual, agrupadas.
 *
 * @param parcial verdadeiro quando o recorte tem mais de 100.000 resultados e as contagens
 *                foram calculadas sobre uma amostra
 */
public record Facetas(boolean parcial, List<Faceta> municipios, List<Faceta> cnaes, List<Faceta> portes) {

    public record Faceta(String chave, String rotulo, long total) {
    }
}