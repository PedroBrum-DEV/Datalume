/**
 * Código transversal (erros, request-id, utilitários). Módulo aberto: todos os módulos
 * podem usá-lo. Evite colocar regra de negócio aqui.
 */
@ApplicationModule(displayName = "Compartilhado", type = ApplicationModule.Type.OPEN)
package br.com.datalume.compartilhado;

import org.springframework.modulith.ApplicationModule;
