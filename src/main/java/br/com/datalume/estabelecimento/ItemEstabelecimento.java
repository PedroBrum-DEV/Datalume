package br.com.datalume.estabelecimento;

import java.time.LocalDate;

/**
 * Linha de resultado da pesquisa. {@code email} e {@code telefone} só vêm preenchidos quando a
 * pesquisa pede contatos (podem ser dados pessoais de MEI); {@code temEmail}/{@code temTelefone} sempre vêm.
 */
public record ItemEstabelecimento(
        String cnpj,
        String cnpjFormatado,
        boolean matriz,
        String razaoSocial,
        String nomeFantasia,
        String situacao,
        String situacaoDescricao,
        LocalDate dataInicioAtividade,
        String cnaePrincipal,
        String porte,
        String porteDescricao,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cep,
        String uf,
        Integer municipioIbge,
        String municipio,
        boolean temEmail,
        boolean temTelefone,
        String telefone,
        String email) {
}