package br.com.datalume.busca;

import java.util.List;
import java.util.UUID;

/** Definição de um nicho (API pública do módulo busca). */
public record NichoDefinicao(UUID id, UUID contaId, String nome, List<String> cnaes, boolean incluirSecundarios,
        List<String> ufs, List<Integer> municipios) {
}