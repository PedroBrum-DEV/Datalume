package br.com.datalume.busca.dominio;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Recorte salvo por uma conta: CNAEs (prefixos) + região (UFs e/ou municípios IBGE). */
public record Nicho(UUID id, UUID contaId, String nome, List<String> cnaes, boolean incluirSecundarios,
        List<String> ufs, List<Integer> municipios, OffsetDateTime criadoEm, OffsetDateTime atualizadoEm) {
}