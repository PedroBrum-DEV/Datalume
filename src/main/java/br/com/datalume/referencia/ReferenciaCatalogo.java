package br.com.datalume.referencia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pública para a busca: transforma prefixos de CNAE em códigos exatos (permite usar índices B-tree/GIN,
 * que não atendem LIKE em bancos com collation não-C) e valida códigos informados pelo usuário.
 */
@Service
@Transactional(readOnly = true)
public class ReferenciaCatalogo {

    /**
     * @param codigos               subclasses (7 dígitos) que casam com os prefixos
     * @param prefixosSemCatalogo   prefixos curtos que não casaram com nada (catálogo não carregado)
     */
    public record ExpansaoCnae(List<String> codigos, List<String> prefixosSemCatalogo) {
    }

    private final JdbcClient jdbc;

    public ReferenciaCatalogo(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean cnaesCarregados() {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM cnae_subclasse)").query(Boolean.class).single();
    }

    public boolean municipiosCarregados() {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM municipio)").query(Boolean.class).single();
    }

    public ExpansaoCnae expandirCnaes(List<String> prefixos) {
        Set<String> codigos = new LinkedHashSet<>();
        List<String> semCatalogo = new ArrayList<>();
        for (String prefixo : prefixos) {
            List<String> achados = codigosDoPrefixo(prefixo);
            if (!achados.isEmpty()) {
                codigos.addAll(achados);
            } else if (prefixo.length() == 7) {
                codigos.add(prefixo);
            } else {
                semCatalogo.add(prefixo);
            }
        }
        return new ExpansaoCnae(List.copyOf(codigos), List.copyOf(semCatalogo));
    }

    /** Prefixos que não correspondem a nenhuma subclasse do catálogo. */
    public List<String> prefixosSemCorrespondencia(List<String> prefixos) {
        List<String> desconhecidos = new ArrayList<>();
        for (String prefixo : prefixos) {
            if (codigosDoPrefixo(prefixo).isEmpty()) {
                desconhecidos.add(prefixo);
            }
        }
        return desconhecidos;
    }

    public Set<Integer> municipiosExistentes(Collection<Integer> codigos) {
        if (codigos.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jdbc.sql("SELECT codigo_ibge FROM municipio WHERE codigo_ibge IN (:c)")
                .param("c", codigos)
                .query(Integer.class).list());
    }

    private List<String> codigosDoPrefixo(String prefixo) {
        return jdbc.sql("SELECT codigo FROM cnae_subclasse WHERE codigo LIKE :p ORDER BY codigo")
                .param("p", prefixo + "%")
                .query(String.class).list();
    }
}