package br.com.datalume.busca.web;

import br.com.datalume.busca.aplicacao.BuscaService;
import br.com.datalume.estabelecimento.Facetas;
import br.com.datalume.estabelecimento.PaginaEstabelecimentos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Busca livre (sem nicho salvo). Exige ao menos um filtro: cnae, uf, municipio ou nome. */
@RestController
@RequestMapping("/api/busca")
class BuscaController {

    private final BuscaService busca;

    BuscaController(BuscaService busca) {
        this.busca = busca;
    }

    @GetMapping("/estabelecimentos")
    PaginaEstabelecimentos estabelecimentos(@ModelAttribute ParametrosBusca p) {
        return busca.pesquisar(p.criterios(), p.limiteOuPadrao(), p.cursor(), p.pedeContatos());
    }

    @GetMapping("/facetas")
    Facetas facetas(@ModelAttribute ParametrosBusca p) {
        return busca.facetas(p.criterios());
    }
}