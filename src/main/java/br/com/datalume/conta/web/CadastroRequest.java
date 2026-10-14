package br.com.datalume.conta.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CadastroRequest(
        @NotBlank(message = "Informe o nome da empresa.")
        @Size(max = 150, message = "Máximo de 150 caracteres.") String nomeEmpresa,

        @NotBlank(message = "Informe seu nome.")
        @Size(max = 150, message = "Máximo de 150 caracteres.") String nome,

        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "E-mail inválido.")
        @Size(max = 254, message = "Máximo de 254 caracteres.") String email,

        @NotBlank(message = "Informe a senha.")
        @Size(min = 10, max = 72, message = "A senha deve ter entre 10 e 72 caracteres.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "A senha deve conter letras e números.") String senha) {
}
