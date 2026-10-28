package br.com.datalume.busca.web;

import br.com.datalume.busca.dominio.Nicho;
import br.com.datalume.estabelecimento.CriteriosBusca;
import br.com.datalume.estabelecimento.EstabelecimentoPesquisa;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Parâmetros de query das buscas. Listas aceitam vírgula ou repetição (cnae=8630,4635 ou cnae=8630&amp;cnae=4635).
 * {@code situacao} ausente = só ativas; {@code situacao=todas} = todas as situações.
 */
public record ParametrosBusca(
        List<String> cnae,
        Boolean secundarios,
        List<String> uf,
        List<Integer> municipio,
        List<String> situacao,
        List<String> porte,
        Boolean matriz,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate abertoDe,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate abertoAte,
        Boolean comEmail,
        Boolean comTelefone,
        String nome,
        Boolean contatos,
        Integer limite,
        String cursor) {

    /** Critérios completos informados na própria requisição (busca livre). */
    public CriteriosBusca criterios() {
        return new CriteriosBusca(cnae, Boolean.TRUE.equals(secundarios), uf, municipio, situacoes(), porte,
                matriz, abertoDe, abertoAte, Boolean.TRUE.equals(comEmail), Boolean.TRUE.equals(comTelefone), nome);
    }

    /** CNAEs, região e "secundários" vêm do nicho; o restante dos filtros vem da requisição. */
    public CriteriosBusca paraNicho(Nicho nicho) {
        return new CriteriosBusca(nicho.cnaes(), nicho.incluirSecundarios(), nicho.ufs(), nicho.municipios(),
                situacoes(), porte, matriz, abertoDe, abertoAte, Boolean.TRUE.equals(comEmail),
                Boolean.TRUE.equals(comTelefone), nome);
    }

    public int limiteOuPadrao() {
        return limite == null ? EstabelecimentoPesquisa.PAGINA_PADRAO : limite;
    }

    public boolean pedeContatos() {
        return Boolean.TRUE.equals(contatos);
    }

    private List<String> situacoes() {
        if (situacao == null || situacao.isEmpty()) {
            return null; // padrão: somente ativas
        }
        boolean todas = situacao.stream().anyMatch(s -> s != null && s.trim().equalsIgnoreCase("todas"));
        return todas ? List.of() : situacao;
    }
}