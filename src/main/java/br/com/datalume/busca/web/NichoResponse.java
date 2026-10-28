package br.com.datalume.busca.web;

import br.com.datalume.busca.dominio.Nicho;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record NichoResponse(UUID id, String nome, List<String> cnaes, boolean incluirSecundarios,
        List<String> ufs, List<Integer> municipios, OffsetDateTime criadoEm, OffsetDateTime atualizadoEm) {

    static NichoResponse de(Nicho n) {
        return new NichoResponse(n.id(), n.nome(), n.cnaes(), n.incluirSecundarios(), n.ufs(), n.municipios(),
                n.criadoEm(), n.atualizadoEm());
    }
}