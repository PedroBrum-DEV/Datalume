package br.com.datalume.referencia;

import br.com.datalume.referencia.carga.CargaReferenciaService;
import java.io.InputStream;
import java.nio.charset.Charset;
import org.springframework.stereotype.Service;

/** API pública de escrita: usada pela importação mensal para atualizar os municípios da Receita. */
@Service
public class ReferenciaManutencao {

    private final CargaReferenciaService carga;

    public ReferenciaManutencao(CargaReferenciaService carga) {
        this.carga = carga;
    }

    /** Importa o Municipios.csv da Receita e concilia com o IBGE. Retorna quantos registros processou. */
    public int importarMunicipiosReceita(InputStream csv, Charset charset) {
        return carga.importarMunicipiosReceita(csv, charset).processados();
    }
}