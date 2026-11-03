package br.com.datalume.estabelecimento;

/** Tipos de mudança detectados entre duas referências da Receita. */
public enum TipoMudanca {
    /** Empresa nova, com início de atividade nos últimos 90 dias da referência. */
    ABERTURA,
    /** Passou para situação baixada. */
    BAIXA,
    /** Outra mudança de situação: suspensa, inapta, nula ou reativada. */
    SITUACAO,
    /** Mudou UF, município ou CEP. */
    MUDANCA_ENDERECO,
    /** Mudou o CNAE principal. */
    MUDANCA_CNAE
}