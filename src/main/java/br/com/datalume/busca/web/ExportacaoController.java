package br.com.datalume.busca.web;

import br.com.datalume.busca.aplicacao.ExportacaoService;
import br.com.datalume.busca.dominio.RegistroExportacao;
import br.com.datalume.conta.ContaAtual;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Histórico das exportações da própria conta (rastro para auditoria/LGPD). */
@RestController
@RequestMapping("/api/exportacoes")
class ExportacaoController {

    private final ExportacaoService exportacoes;

    ExportacaoController(ExportacaoService exportacoes) {
        this.exportacoes = exportacoes;
    }

    @GetMapping
    List<RegistroExportacao> listar() {
        return exportacoes.listar(ContaAtual.contaId().orElseThrow());
    }
}