package ai.speckitty.charter.service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The dependency rule of docs/architecture/hexagonal-service.md, as a gate. */
class HexagonalArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("ai.speckitty.charter");
    }

    @Test
    void dependenciesPointInward() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain")
                .definedBy("ai.speckitty.charter.domain..")
                .layer("ReadApp")
                .definedBy("ai.speckitty.charter.read..")
                .layer("Contract")
                .definedBy("ai.speckitty.charter.api..")
                .layer("RestIn")
                .definedBy("ai.speckitty.charter.rest..")
                .layer("YamlOut")
                .definedBy("ai.speckitty.charter.yaml..")
                .layer("Service")
                .definedBy("ai.speckitty.charter.service..")
                .whereLayer("Service")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("RestIn")
                .mayOnlyBeAccessedByLayers("Service")
                .whereLayer("YamlOut")
                .mayOnlyBeAccessedByLayers("Service")
                .whereLayer("Contract")
                .mayOnlyBeAccessedByLayers("RestIn")
                .whereLayer("ReadApp")
                .mayOnlyBeAccessedByLayers("RestIn", "Service")
                .whereLayer("Domain")
                .mayOnlyBeAccessedByLayers("ReadApp", "RestIn", "YamlOut", "Service")
                .check(classes);
    }

    @Test
    void theCoreIsPlainJava() {
        noClasses()
                .that()
                .resideInAnyPackage("ai.speckitty.charter.domain..", "ai.speckitty.charter.read..")
                .should()
                .dependOnClassesThat()
                .resideOutsideOfPackages("ai.speckitty.charter.domain..", "ai.speckitty.charter.read..", "java..")
                .check(classes);
    }

    @Test
    void transportTypesStayInTheInboundAdapter() {
        noClasses()
                .that()
                .resideOutsideOfPackages("ai.speckitty.charter.api..", "ai.speckitty.charter.rest..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("ai.speckitty.charter.api..")
                .check(classes);
    }
}
