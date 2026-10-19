package br.com.datalume.estabelecimento.web;

import br.com.datalume.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.datalume.compartilhado.erro.RequisicaoInvalidaException;
import br.com.datalume.estabelecimento.Cnpj;
import br.com.datalume.estabelecimento.EstabelecimentoConsulta;
import br.com.datalume.estabelecimento.EstabelecimentoDetalhe;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/estabelecimentos")
class EstabelecimentoController {

    private final EstabelecimentoConsulta consulta;

    EstabelecimentoController(EstabelecimentoConsulta consulta) {
        this.consulta = consulta;
    }

    /** CNPJ com ou sem pontuação, exceto a barra (use 14 caracteres, ex.: 11222333000181). */
    @GetMapping("/{cnpj}")
    EstabelecimentoDetalhe porCnpj(@PathVariable String cnpj) {
        if (!Cnpj.valido(cnpj)) {
            throw new RequisicaoInvalidaException("CNPJ inválido.");
        }
        return consulta.buscarPorCnpj(cnpj)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estabelecimento não encontrado."));
    }
}