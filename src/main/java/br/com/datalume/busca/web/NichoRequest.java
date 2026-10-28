package br.com.datalume.busca.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record NichoRequest(
        @NotBlank(message = "Informe o nome do nicho.")
        @Size(max = 100, message = "Máximo de 100 caracteres.") String nome,

        @NotEmpty(message = "Informe ao menos um CNAE.")
        @Size(max = 50, message = "No máximo 50 CNAEs.") List<String> cnaes,

        Boolean incluirSecundarios,

        @Size(max = 27, message = "No máximo 27 UFs.") List<String> ufs,

        @Size(max = 200, message = "No máximo 200 municípios.") List<Integer> municipios) {

    boolean secundarios() {
        return Boolean.TRUE.equals(incluirSecundarios);
    }
}