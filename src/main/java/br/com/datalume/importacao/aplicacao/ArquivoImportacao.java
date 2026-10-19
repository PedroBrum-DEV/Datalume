package br.com.datalume.importacao.aplicacao;

import java.time.OffsetDateTime;

public record ArquivoImportacao(long id, long loteId, String nome, String tipo, int ordem, String status,
        Long tamanhoBytes, Long linhasLidas, Long inseridos, Long atualizados, int tentativas, String erro,
        OffsetDateTime iniciadoEm, OffsetDateTime finalizadoEm) {
}