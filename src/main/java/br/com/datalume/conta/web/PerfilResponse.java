package br.com.datalume.conta.web;

import java.util.UUID;

public record PerfilResponse(UsuarioDto usuario, ContaDto conta, PlanoDto plano) {

    public record UsuarioDto(UUID id, String nome, String email, String papel) {
    }

    public record ContaDto(UUID id, String nome) {
    }

    public record PlanoDto(String codigo, String nome, int limiteUsuarios, int limiteNichos, int limiteAlertas) {
    }
}
