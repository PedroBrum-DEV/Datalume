/** Importação mensal dos arquivos de dados abertos do CNPJ (download, carga em massa e controle de lotes). */
@ApplicationModule(displayName = "Importacao", allowedDependencies = {"referencia", "estabelecimento", "compartilhado"})
package br.com.datalume.importacao;

import org.springframework.modulith.ApplicationModule;