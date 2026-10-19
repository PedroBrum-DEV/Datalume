package br.com.datalume.importacao.web;

import br.com.datalume.estabelecimento.EstabelecimentoConsulta;
import br.com.datalume.importacao.aplicacao.ArquivoImportacao;
import br.com.datalume.importacao.aplicacao.ImportacaoService;
import br.com.datalume.importacao.aplicacao.Lote;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints administrativos (papel ADMIN_PLATAFORMA, ver SecurityConfig). */
@RestController
@RequestMapping("/api/admin/importacao")
class ImportacaoController {

    private final ImportacaoService service;
    private final EstabelecimentoConsulta estabelecimentos;

    ImportacaoController(ImportacaoService service, EstabelecimentoConsulta estabelecimentos) {
        this.service = service;
        this.estabelecimentos = estabelecimentos;
    }

    /** Referências publicadas na Receita (últimos meses). */
    @GetMapping("/referencias")
    List<String> referencias() {
        return service.referenciasDisponiveis();
    }

    /** Corpo opcional: {"referencia":"2026-10","forcar":false}. Sem corpo = referência mais recente. */
    @PostMapping
    ResponseEntity<Lote> iniciar(@RequestBody(required = false) IniciarRequest req) {
        IniciarRequest r = req == null ? new IniciarRequest(null, null) : req;
        return ResponseEntity.accepted().body(service.iniciar(r.referencia(), Boolean.TRUE.equals(r.forcar())));
    }

    @GetMapping("/lotes")
    List<Lote> lotes(@RequestParam(defaultValue = "20") int limite) {
        return service.lotes(limite);
    }

    @GetMapping("/lotes/{id}")
    LoteDetalhe lote(@PathVariable long id) {
        return new LoteDetalhe(service.lote(id), service.arquivos(id));
    }

    @PostMapping("/lotes/{id}/retomar")
    ResponseEntity<Lote> retomar(@PathVariable long id) {
        return ResponseEntity.accepted().body(service.retomar(id));
    }

    /** Contagens exatas das tabelas finais (pode demorar com a base nacional completa). */
    @GetMapping("/resumo")
    Map<String, Long> resumo() {
        return estabelecimentos.contagens();
    }

    record IniciarRequest(String referencia, Boolean forcar) {
    }

    record LoteDetalhe(Lote lote, List<ArquivoImportacao> arquivos) {
    }
}