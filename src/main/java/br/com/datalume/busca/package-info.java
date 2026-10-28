/** Busca por nicho (CNAE) e região: pesquisa, facetas, nichos salvos por conta e exportação. */
@ApplicationModule(displayName = "Busca",
        allowedDependencies = {"estabelecimento", "referencia", "conta", "compartilhado"})
package br.com.datalume.busca;

import org.springframework.modulith.ApplicationModule;