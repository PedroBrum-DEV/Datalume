/** Monitoramento de mudanças (abertura, baixa, mudança de endereço/CNAE). */
@ApplicationModule(displayName = "Monitor", allowedDependencies = {"estabelecimento", "busca", "conta", "compartilhado"})
package br.com.datalume.monitor;

import org.springframework.modulith.ApplicationModule;
