package br.com.datalume;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Garante as fronteiras entre módulos (sem ciclos e respeitando allowedDependencies). */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(DatalumeApplication.class);

    @Test
    void estruturaModularEhValida() {
        modules.verify();
    }

    @Test
    void geraDocumentacaoDosModulos() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
