package br.com.datalume.monitor.aplicacao;

import br.com.datalume.estabelecimento.EstabelecimentoMudancas.MudancaDetalhada;
import br.com.datalume.estabelecimento.Situacoes;

/** Texto legível de uma mudança e sua direção em relação ao nicho (entrou, saiu ou continua nele). */
public final class DescricaoMudanca {

    public enum Direcao { ENTROU, SAIU, NO_NICHO }

    private DescricaoMudanca() {
    }

    public static Direcao direcao(MudancaDetalhada m) {
        if (!m.casaAntes() && m.casaDepois()) {
            return Direcao.ENTROU;
        }
        if (m.casaAntes() && !m.casaDepois()) {
            return Direcao.SAIU;
        }
        return Direcao.NO_NICHO;
    }

    public static String descrever(MudancaDetalhada m, Direcao direcao) {
        String base = switch (m.tipo()) {
            case ABERTURA -> "Nova empresa (início de atividade recente)";
            case BAIXA -> "Empresa baixada";
            case SITUACAO -> "Situação cadastral: " + Situacoes.descricao(m.situacaoAntes()) + " → "
                    + Situacoes.descricao(m.situacaoDepois());
            case MUDANCA_ENDERECO -> "Mudança de endereço" + trecho(m);
            case MUDANCA_CNAE -> "CNAE principal: " + valor(m.cnaeAntes()) + " → " + valor(m.cnaeDepois());
        };
        return switch (direcao) {
            case SAIU -> base + " (saiu do nicho)";
            case ENTROU -> m.tipo() == br.com.datalume.estabelecimento.TipoMudanca.ABERTURA ? base
                    : base + " (entrou no nicho)";
            case NO_NICHO -> base;
        };
    }

    private static String trecho(MudancaDetalhada m) {
        String antes = local(m.municipioAntes(), m.ufAntes());
        String depois = local(m.municipioDepois(), m.ufDepois());
        return antes.equals(depois) ? "" : ": " + antes + " → " + depois;
    }

    private static String local(String municipio, String uf) {
        String m = municipio == null || municipio.isBlank() ? "?" : municipio;
        return uf == null || uf.isBlank() ? m : m + "/" + uf;
    }

    private static String valor(String v) {
        return v == null ? "?" : v;
    }
}