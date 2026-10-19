package br.com.datalume.importacao.aplicacao;

import java.time.OffsetDateTime;

/** Uma execução de importação de uma referência (AAAA-MM). */
public record Lote(long id, String referencia, String status, String filtro, String erro,
        OffsetDateTime criadoEm, OffsetDateTime finalizadoEm) {

    public static final String EXECUTANDO = "EXECUTANDO";
    public static final String CONCLUIDO = "CONCLUIDO";
    public static final String FALHOU = "FALHOU";
}