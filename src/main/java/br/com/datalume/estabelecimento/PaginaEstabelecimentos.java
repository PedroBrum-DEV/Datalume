package br.com.datalume.estabelecimento;

import java.util.List;

/**
 * Uma página de resultados.
 *
 * @param proximoCursor token opaco para a próxima página; nulo na última
 * @param total         só na primeira página; no máximo 10.000
 * @param totalExato    falso quando existem mais de 10.000 resultados ("10.000+")
 */
public record PaginaEstabelecimentos(List<ItemEstabelecimento> itens, String proximoCursor, Long total,
        boolean totalExato) {
}