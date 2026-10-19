package br.com.datalume.importacao.aplicacao;

import br.com.datalume.estabelecimento.EstabelecimentoCarga.Destino;

/** Arquivos da Receita que o Datalume importa, na ordem em que precisam ser processados. */
public enum TipoArquivo {
    MUNICIPIOS(null),
    NATUREZAS(Destino.NATUREZA_JURIDICA),
    MOTIVOS(Destino.MOTIVO_SITUACAO),
    /** Antes de EMPRESAS: só entram empresas que têm estabelecimento carregado. */
    ESTABELECIMENTOS(Destino.ESTABELECIMENTO),
    EMPRESAS(Destino.EMPRESA);

    private final Destino destino;

    TipoArquivo(Destino destino) {
        this.destino = destino;
    }

    /** Destino da carga em massa; nulo para MUNICIPIOS (tratado pelo módulo de referência). */
    public Destino destino() {
        return destino;
    }
}