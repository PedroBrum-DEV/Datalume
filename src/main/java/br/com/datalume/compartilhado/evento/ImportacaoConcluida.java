package br.com.datalume.compartilhado.evento;

/** Publicado quando uma importação mensal termina com sucesso. O monitor reage a ele. */
public record ImportacaoConcluida(String referencia) {
}