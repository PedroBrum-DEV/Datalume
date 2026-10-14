package br.com.datalume.referencia.carga;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Endpoints administrativos (papel ADMIN_PLATAFORMA, ver SecurityConfig). */
@RestController
@RequestMapping("/api/admin/referencia")
class ReferenciaCargaController {

    private final CargaReferenciaService carga;

    ReferenciaCargaController(CargaReferenciaService carga) {
        this.carga = carga;
    }

    @GetMapping("/resumo")
    Map<String, Long> resumo() {
        return carga.resumo();
    }

    @PostMapping("/carga/ibge")
    ResultadoCarga ibge() {
        return carga.carregarMunicipiosIbge();
    }

    @PostMapping("/carga/cnae")
    ResultadoCarga cnae() {
        return carga.carregarCnaesIbge();
    }

    /** Municipios.csv da Receita (padrão ISO-8859-1). */
    @PostMapping("/carga/municipios-receita")
    ResultadoCarga municipiosReceita(@RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(defaultValue = "ISO-8859-1") String charset) throws IOException {
        return carga.importarMunicipiosReceita(arquivo.getInputStream(), Charset.forName(charset));
    }

    /** CSV "codigo_receita;codigo_ibge" com cabeçalho (UTF-8). */
    @PostMapping("/carga/conversao")
    ResultadoCarga conversao(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        return carga.importarConversao(arquivo.getInputStream(), StandardCharsets.UTF_8);
    }
}
