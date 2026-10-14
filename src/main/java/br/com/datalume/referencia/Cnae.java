package br.com.datalume.referencia;

/** Subclasse CNAE (7 dígitos) com a hierarquia derivada do código. */
public record Cnae(String codigo, String descricao, String classe, String grupo, String divisao, String secao) {
}
