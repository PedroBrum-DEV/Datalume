package br.com.datalume.busca.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RegistroExportacao(long id, UUID usuarioId, UUID nichoId, String nichoNome, String criterios,
        boolean contatos, int linhas, OffsetDateTime criadoEm) {
}