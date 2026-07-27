package com.stockforecast.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

/**
 * Clean-architecture rules applied inside every module: dependencies point inwards
 * (api/infrastructure -> application -> domain) and the domain stays framework-free apart from JPA mapping
 * annotations.
 */
class LayeredArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.stockforecast");

    @Test
    void domainDoesNotDependOnOuterLayers() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..", "..api..")
                .check(CLASSES);
    }

    @Test
    void domainIsFreeOfSpringWebAndSerializationConcerns() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.servlet..", "tools.jackson..", "com.fasterxml.jackson..")
                .check(CLASSES);
    }

    @Test
    void applicationLayerDoesNotDependOnApiOrInfrastructure() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..")
                .check(CLASSES);
    }

    @Test
    void apiLayerDoesNotDependOnInfrastructure() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .check(CLASSES);
    }

    @Test
    void persistenceEntitiesNeverReachTheApiLayer() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .check(CLASSES);
    }

    @Test
    void controllersDoNotAccessRepositories() {
        noClasses().that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .check(CLASSES);
    }

    @Test
    void generalCodingRules() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(CLASSES);
        NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS.check(CLASSES);
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(CLASSES);
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(CLASSES);
    }
}
