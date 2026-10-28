package br.com.datalume.busca.web;

import br.com.datalume.busca.aplicacao.BuscaService;
import br.com.datalume.busca.aplicacao.ExportacaoService;
import br.com.datalume.busca.aplicacao.NichoService;
import br.com.datalume.busca.dominio.Nicho;
import br.com.datalume.conta.ContaAtual;
import br.com.datalume.estabelecimento.CriteriosBusca;
import br.com.datalume.estabelecimento.Facetas;
import br.com.datalume.estabelecimento.PaginaEstabelecimentos;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nichos")
class NichoController {

    private final NichoService nichos;
    private final BuscaService busca;
    private final ExportacaoService exportacoes;

    NichoController(NichoService nichos, BuscaService busca, ExportacaoService exportacoes) {
        this.nichos = nichos;
        this.busca = busca;
        this.exportacoes = exportacoes;
    }

    @PostMapping
    ResponseEntity<NichoResponse> criar(@Valid @RequestBody NichoRequest req) {
        Nicho n = nichos.criar(conta(), req.nome(), req.cnaes(), req.secundarios(), req.ufs(), req.municipios());
        return ResponseEntity.status(HttpStatus.CREATED).body(NichoResponse.de(n));
    }

    @GetMapping
    List<NichoResponse> listar() {
        return nichos.listar(conta()).stream().map(NichoResponse::de).toList();
    }

    @GetMapping("/{id}")
    NichoResponse obter(@PathVariable UUID id) {
        return NichoResponse.de(nichos.obter(conta(), id));
    }

    @PutMapping("/{id}")
    NichoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody NichoRequest req) {
        return NichoResponse.de(nichos.atualizar(conta(), id, req.nome(), req.cnaes(), req.secundarios(),
                req.ufs(), req.municipios()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> remover(@PathVariable UUID id) {
        nichos.remover(conta(), id);
        return ResponseEntity.noContent().build();
    }

    /** Empresas do nicho. Filtros extras (situacao, porte, matriz, abertoDe/Ate, nome...) refinam o recorte. */
    @GetMapping("/{id}/empresas")
    PaginaEstabelecimentos empresas(@PathVariable UUID id, @ModelAttribute ParametrosBusca p) {
        Nicho nicho = nichos.obter(conta(), id);
        return busca.pesquisar(p.paraNicho(nicho), p.limiteOuPadrao(), p.cursor(), p.pedeContatos());
    }

    @GetMapping("/{id}/facetas")
    Facetas facetas(@PathVariable UUID id, @ModelAttribute ParametrosBusca p) {
        return busca.facetas(p.paraNicho(nichos.obter(conta(), id)));
    }

    /**
     * Exporta o recorte em CSV (até o limite do plano). E-mail e telefone só saem com {@code contatos=true}.
     * A exportação fica registrada na conta (ver GET /api/exportacoes).
     */
    @PostMapping("/{id}/exportacoes")
    void exportar(@PathVariable UUID id, @ModelAttribute ParametrosBusca p, HttpServletResponse resposta)
            throws IOException {
        UUID contaId = conta();
        UUID usuarioId = ContaAtual.usuarioId().orElseThrow();
        Nicho nicho = nichos.obter(contaId, id);
        CriteriosBusca criterios = p.paraNicho(nicho);
        int limite = exportacoes.limiteDaConta(contaId);

        resposta.setStatus(HttpStatus.OK.value());
        resposta.setContentType("text/csv; charset=UTF-8");
        resposta.setHeader("Content-Disposition",
                "attachment; filename=\"datalume-nicho-" + LocalDate.now() + ".csv\"");
        resposta.setHeader("X-Limite-Exportacao", String.valueOf(limite));
        exportacoes.exportar(contaId, usuarioId, nicho, criterios, p.pedeContatos(), limite,
                resposta.getOutputStream());
    }

    private static UUID conta() {
        return ContaAtual.contaId().orElseThrow();
    }
}