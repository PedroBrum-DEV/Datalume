package br.com.datalume.referencia.web;

import br.com.datalume.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.datalume.compartilhado.erro.RequisicaoInvalidaException;
import br.com.datalume.referencia.Cnae;
import br.com.datalume.referencia.Municipio;
import br.com.datalume.referencia.ReferenciaConsulta;
import br.com.datalume.referencia.Uf;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/referencia")
class ReferenciaController {

    private final ReferenciaConsulta consulta;

    ReferenciaController(ReferenciaConsulta consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/ufs")
    List<Uf> ufs() {
        return consulta.listarUfs();
    }

    @GetMapping("/ufs/{sigla}/municipios")
    List<Municipio> municipiosDaUf(@PathVariable String sigla) {
        consulta.buscarUf(sigla).orElseThrow(() -> new RecursoNaoEncontradoException("UF não encontrada: " + sigla));
        return consulta.listarMunicipios(sigla);
    }

    @GetMapping("/municipios")
    List<Municipio> municipios(@RequestParam String q, @RequestParam(required = false) String uf,
            @RequestParam(defaultValue = "20") int limite) {
        exigirTermo(q);
        return consulta.buscarMunicipios(q, uf, limite);
    }

    @GetMapping("/cnaes")
    List<Cnae> cnaes(@RequestParam String q, @RequestParam(defaultValue = "20") int limite) {
        exigirTermo(q);
        return consulta.buscarCnaes(q, limite);
    }

    @GetMapping("/cnaes/{codigo}")
    Cnae cnae(@PathVariable String codigo) {
        return consulta.buscarCnae(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CNAE não encontrado: " + codigo));
    }

    private static void exigirTermo(String q) {
        if (q == null || q.trim().length() < 2) {
            throw new RequisicaoInvalidaException("O parâmetro 'q' precisa ter ao menos 2 caracteres.");
        }
    }
}
