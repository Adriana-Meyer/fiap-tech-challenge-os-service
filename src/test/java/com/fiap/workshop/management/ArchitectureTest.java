package com.fiap.workshop.management;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Fails the build when a layer depends on one it must not know about
 * (domain <- application <- infrastructure / interfaces).
 */
@AnalyzeClasses(packages = "com.fiap.workshop.management", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.fiap.workshop.management";

    @ArchTest
    static final ArchRule layers_respect_the_dependency_direction = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy(ROOT + ".domain..")
            .layer("Application").definedBy(ROOT + ".application..")
            .layer("Infrastructure").definedBy(ROOT + ".infrastructure..")
            .layer("Interfaces").definedBy(ROOT + ".interfaces..")
            .whereLayer("Interfaces").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces", "Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Interfaces");

    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses().that().resideInAPackage(ROOT + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "com.fasterxml..", "io.jsonwebtoken..");

    @ArchTest
    static final ArchRule controllers_depend_on_ports_not_use_cases = noClasses()
            .that().resideInAPackage(ROOT + ".interfaces..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".application.usecase..");
}
