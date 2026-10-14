/**
 * Tabelas de referência: UFs, municípios (IBGE), conversão de município Receita -> IBGE e CNAEs.
 * API pública do módulo: {@link ReferenciaConsulta} e os records Uf, Municipio e Cnae.
 */
@ApplicationModule(displayName = "Referência", allowedDependencies = "compartilhado")
package br.com.datalume.referencia;

import org.springframework.modulith.ApplicationModule;
