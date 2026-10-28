package br.com.datalume.estabelecimento;

import br.com.datalume.compartilhado.erro.RequisicaoInvalidaException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Critérios de pesquisa de estabelecimentos. Listas vazias = sem restrição, exceto {@code situacoes}:
 * {@code null} significa "somente ativas (02)" e lista vazia significa "todas as situações".
 *
 * @param cnaes              prefixos de CNAE (2 a 7 dígitos, pontuação ignorada)
 * @param incluirSecundarios também considera os CNAEs secundários
 * @param ufs                siglas
 * @param municipios         códigos IBGE
 * @param situacoes          códigos de situação cadastral (01, 02, 03, 04, 08)
 * @param portes             códigos de porte (00, 01, 03, 05)
 * @param nome               trecho do nome fantasia ou razão social (mínimo 3 caracteres)
 */
public record CriteriosBusca(
        List<String> cnaes,
        boolean incluirSecundarios,
        List<String> ufs,
        List<Integer> municipios,
        List<String> situacoes,
        List<String> portes,
        Boolean matriz,
        LocalDate abertoDe,
        LocalDate abertoAte,
        boolean comEmail,
        boolean comTelefone,
        String nome) {

    public CriteriosBusca {
        cnaes = normalizarCnaes(cnaes);
        ufs = normalizarUfs(ufs);
        municipios = municipios == null ? List.of()
                : List.copyOf(new LinkedHashSet<>(municipios.stream().filter(m -> m != null && m > 0).toList()));
        situacoes = situacoes == null ? List.of("02") : normalizarCodigos(situacoes, "situação");
        portes = portes == null ? List.of() : normalizarCodigos(portes, "porte");
        nome = normalizarNome(nome);
        if (abertoDe != null && abertoAte != null && abertoDe.isAfter(abertoAte)) {
            throw new RequisicaoInvalidaException("'abertoDe' não pode ser posterior a 'abertoAte'.");
        }
    }

    private static List<String> normalizarCnaes(List<String> valores) {
        Set<String> r = new LinkedHashSet<>();
        if (valores != null) {
            for (String v : valores) {
                String d = v == null ? "" : v.replaceAll("\\D", "");
                if (d.isEmpty()) {
                    continue;
                }
                if (!d.matches("\\d{2,7}")) {
                    throw new RequisicaoInvalidaException("CNAE inválido: " + v);
                }
                r.add(d);
            }
        }
        return List.copyOf(r);
    }

    private static List<String> normalizarUfs(List<String> valores) {
        Set<String> r = new LinkedHashSet<>();
        if (valores != null) {
            for (String v : valores) {
                String s = v == null ? "" : v.trim().toUpperCase(Locale.ROOT);
                if (s.isEmpty()) {
                    continue;
                }
                if (!s.matches("[A-Z]{2}")) {
                    throw new RequisicaoInvalidaException("UF inválida: " + v);
                }
                r.add(s);
            }
        }
        return List.copyOf(r);
    }

    private static List<String> normalizarCodigos(List<String> valores, String campo) {
        List<String> r = new ArrayList<>();
        for (String v : valores) {
            String s = v == null ? "" : v.trim();
            if (s.isEmpty()) {
                continue;
            }
            if (!s.matches("\\d{1,2}")) {
                throw new RequisicaoInvalidaException("Código de " + campo + " inválido: " + v);
            }
            String c = s.length() == 1 ? "0" + s : s;
            if (!r.contains(c)) {
                r.add(c);
            }
        }
        return List.copyOf(r);
    }

    private static String normalizarNome(String nome) {
        if (nome == null) {
            return null;
        }
        String s = nome.replaceAll("[%_\\\\]", " ").trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) {
            return null;
        }
        if (s.length() < 3) {
            throw new RequisicaoInvalidaException("O filtro 'nome' precisa ter ao menos 3 caracteres.");
        }
        return s;
    }

    /** Descrição para registro/auditoria, sem o texto digitado em 'nome' (pode conter dados pessoais). */
    public String descricao() {
        return "cnaes=" + String.join(",", cnaes)
                + "; secundarios=" + incluirSecundarios
                + "; ufs=" + String.join(",", ufs)
                + "; municipios=" + municipios.size()
                + "; situacoes=" + (situacoes.isEmpty() ? "todas" : String.join(",", situacoes))
                + "; portes=" + String.join(",", portes)
                + "; nome=" + (nome != null ? "sim" : "nao");
    }
}