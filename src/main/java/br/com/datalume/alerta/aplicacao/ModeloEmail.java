package br.com.datalume.alerta.aplicacao;

import br.com.datalume.alerta.dominio.RepositorioAlerta.ItemEmail;
import br.com.datalume.alerta.dominio.RepositorioAlerta.ResultadoEmail;
import java.util.List;

/**
 * Monta o resumo por e-mail (texto e HTML). Só traz CNPJ, nome e a descrição da mudança:
 * nunca e-mail ou telefone das empresas.
 */
final class ModeloEmail {

    record Conteudo(String assunto, String texto, String html) {
    }

    private ModeloEmail() {
    }

    static Conteudo montar(String nomeUsuario, String referencia, List<ResultadoEmail> resultados,
            List<ItemEmail> itens, int maxItens, String linkPainel, String linkCancelamento) {
        long total = resultados.stream().mapToLong(ResultadoEmail::total).sum();
        String ref = rotuloReferencia(referencia);
        String assunto = "Datalume: " + total + (total == 1 ? " mudança" : " mudanças") + " nos seus nichos (" + ref + ")";
        long restantes = Math.max(total - itens.size(), 0);

        StringBuilder t = new StringBuilder();
        t.append("Olá, ").append(primeiroNome(nomeUsuario)).append("!\n\n");
        t.append("Resumo das mudanças nos seus nichos monitorados, referência ").append(ref).append(":\n\n");
        for (ResultadoEmail r : resultados) {
            t.append("• ").append(r.nichoNome()).append(": ").append(r.total()).append(" (")
                    .append(detalhe(r)).append(")\n");
        }
        t.append("\n");
        String nichoAtual = null;
        for (ItemEmail i : itens) {
            if (!i.nichoNome().equals(nichoAtual)) {
                nichoAtual = i.nichoNome();
                t.append(nichoAtual).append("\n");
            }
            t.append("  - ").append(formatarCnpj(i.cnpj())).append("  ").append(nomeOuTraco(i.nome()))
                    .append(" — ").append(i.descricao()).append("\n");
        }
        if (restantes > 0) {
            t.append("\n… e mais ").append(restantes).append(" mudanças. Veja todas em ").append(linkPainel).append("\n");
        }
        t.append("\nPara parar de receber estes e-mails: ").append(linkCancelamento).append("\n");

        StringBuilder h = new StringBuilder();
        h.append("<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#1f2933\">");
        h.append("<p>Olá, ").append(esc(primeiroNome(nomeUsuario))).append("!</p>");
        h.append("<p>Resumo das mudanças nos seus nichos monitorados, referência <b>").append(esc(ref)).append("</b>:</p><ul>");
        for (ResultadoEmail r : resultados) {
            h.append("<li><b>").append(esc(r.nichoNome())).append("</b>: ").append(r.total()).append(" (")
                    .append(esc(detalhe(r))).append(")</li>");
        }
        h.append("</ul>");
        nichoAtual = null;
        boolean listaAberta = false;
        for (ItemEmail i : itens) {
            if (!i.nichoNome().equals(nichoAtual)) {
                if (listaAberta) {
                    h.append("</ul>");
                }
                nichoAtual = i.nichoNome();
                h.append("<p style=\"margin-bottom:2px\"><b>").append(esc(nichoAtual)).append("</b></p><ul>");
                listaAberta = true;
            }
            h.append("<li>").append(esc(formatarCnpj(i.cnpj()))).append(" ").append(esc(nomeOuTraco(i.nome())))
                    .append(" — ").append(esc(i.descricao())).append("</li>");
        }
        if (listaAberta) {
            h.append("</ul>");
        }
        if (restantes > 0) {
            h.append("<p>… e mais ").append(restantes).append(" mudanças. <a href=\"").append(esc(linkPainel))
                    .append("\">Ver todas</a></p>");
        }
        h.append("<p style=\"color:#6b7280;font-size:12px\">Você recebe este resumo porque monitora nichos no Datalume. ")
                .append("<a href=\"").append(esc(linkCancelamento)).append("\">Parar de receber</a>.</p></div>");
        return new Conteudo(assunto, t.toString(), h.toString());
    }

    private static String detalhe(ResultadoEmail r) {
        StringBuilder sb = new StringBuilder();
        parte(sb, r.totalAberturas(), "aberturas");
        parte(sb, r.totalBaixas(), "baixas");
        parte(sb, r.totalSituacao(), "mudanças de situação");
        parte(sb, r.totalEndereco(), "mudanças de endereço");
        parte(sb, r.totalCnae(), "mudanças de CNAE");
        return sb.toString();
    }

    private static void parte(StringBuilder sb, long n, String rotulo) {
        if (n > 0) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(n).append(' ').append(rotulo);
        }
    }

    static String rotuloReferencia(String referencia) {
        return referencia != null && referencia.matches("\\d{4}-\\d{2}")
                ? referencia.substring(5) + "/" + referencia.substring(0, 4) : referencia;
    }

    static String formatarCnpj(String c) {
        if (c == null || c.length() != 14) {
            return c;
        }
        return c.substring(0, 2) + "." + c.substring(2, 5) + "." + c.substring(5, 8) + "/" + c.substring(8, 12)
                + "-" + c.substring(12);
    }

    private static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return "tudo bem";
        }
        return nome.trim().split("\\s+")[0];
    }

    private static String nomeOuTraco(String nome) {
        return nome == null || nome.isBlank() ? "(sem nome)" : nome;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}