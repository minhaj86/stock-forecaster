package com.stockforecast.architecture;

import com.stockforecast.StockForecastApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces module boundaries: modules may only use each other's published API (top-level package), never their
 * internal sub-packages, and module dependencies must be acyclic.
 */
class ModularityTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(StockForecastApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        MODULES.verify();
    }

    @Test
    void expectedModulesAreDetected() {
        assertThat(MODULES.stream().map(module -> module.getIdentifier().toString()))
                .contains("identity", "user", "shared");
    }

    /**
     * Generates C4/PlantUML component diagrams and module canvases into target/spring-modulith-docs.
     */
    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
