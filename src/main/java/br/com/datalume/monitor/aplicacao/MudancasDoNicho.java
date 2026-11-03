package br.com.datalume.monitor.aplicacao;

import br.com.datalume.estabelecimento.TipoMudanca;
import java.util.List;
import java.util.Map;

/** Mudanças de uma referência dentro de um nicho (consulta ao vivo, sem exigir assinatura de alertas). */
public record MudancasDoNicho(String referencia, long total, Map<TipoMudanca, Long> porTipo, List<Item> itens) {

    public record Item(long eventoId, TipoMudanca tipo, String direcao, String cnpj, String nome, String descricao) {
    }
}