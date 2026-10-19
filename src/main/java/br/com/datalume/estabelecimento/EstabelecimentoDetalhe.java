package br.com.datalume.estabelecimento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Visão completa de um estabelecimento (com dados da empresa e descrições das tabelas de apoio). */
public record EstabelecimentoDetalhe(
        String cnpj,
        String cnpjFormatado,
        boolean matriz,
        String razaoSocial,
        String nomeFantasia,
        Situacao situacao,
        LocalDate dataInicioAtividade,
        String naturezaJuridica,
        String naturezaJuridicaDescricao,
        String porte,
        String porteDescricao,
        BigDecimal capitalSocial,
        Cnae cnaePrincipal,
        List<String> cnaesSecundarios,
        Endereco endereco,
        Contato contato,
        String referenciaAlteracao) {

    public record Situacao(String codigo, String descricao, LocalDate data, String motivoCodigo, String motivoDescricao) {
    }

    public record Cnae(String codigo, String descricao) {
    }

    public record Endereco(String tipoLogradouro, String logradouro, String numero, String complemento,
            String bairro, String cep, String uf, Integer municipioIbge, String municipio) {
    }

    public record Contato(String telefone1, String telefone2, String email) {
    }
}