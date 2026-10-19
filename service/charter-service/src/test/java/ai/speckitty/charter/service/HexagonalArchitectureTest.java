package ai.speckitty.charter.service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.RefusalCode;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
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
                .resideOutsideOfPackages(
                        "ai.speckitty.charter.domain..",
                        "ai.speckitty.charter.read..",
                        "java..",
                        // JSpecify annotations allowed: Decision Moment 01M589DHDJG0GQAFTWCJKJ60F1.
                        "org.jspecify.annotations..")
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

    /**
     * {@code internal_error} is the refusal of a failure no other code names, and only the inbound adapter, which
     * answers the failure, may name it: a use case or an outbound adapter that refused with it would say, in the
     * domain's own words, that it does not know what went wrong. The enum itself is exempt.
     */
    private static ArchRule onlyTheInboundAdapterNamesInternalError() {
        return noClasses()
                .that()
                .resideOutsideOfPackage("ai.speckitty.charter.rest..")
                .and()
                .doNotHaveFullyQualifiedName(RefusalCode.class.getName())
                .should()
                .accessField(RefusalCode.class, "INTERNAL_ERROR");
    }

    @Test
    void onlyTheInboundAdapterRefusesWithInternalError() {
        onlyTheInboundAdapterNamesInternalError().check(classes);
    }

    @Test
    void theInternalErrorRuleFailsOnAClassOutsideTheInboundAdapter() {
        JavaClasses outside =
                new ClassFileImporter().importPackages("ai.speckitty.charter.service.internalerrorfixture");
        assertThatThrownBy(() -> onlyTheInboundAdapterNamesInternalError().check(outside))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UsesInternalError");
    }

    /**
     * NullAway runs only on {@code @NullMarked} code (OnlyNullMarked), so every hand-written package must be
     * marked; only the generated contract package is outside NullAway by construction.
     */
    private static ArchRule everyHandWrittenPackageIsNullMarked() {
        return classes()
                .that()
                .resideOutsideOfPackage("ai.speckitty.charter.api..")
                .should(new ArchCondition<JavaClass>("reside in a package annotated @NullMarked") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!item.getPackage().isAnnotatedWith("org.jspecify.annotations.NullMarked")) {
                            events.add(SimpleConditionEvent.violated(
                                    item, item.getName() + " resides in a package that is not @NullMarked"));
                        }
                    }
                });
    }

    @Test
    void everyHandWrittenMainPackageIsNullMarked() {
        everyHandWrittenPackageIsNullMarked().check(classes);
    }

    @Test
    void thePackageRuleFailsOnAPackageWithoutNullMarked() {
        JavaClasses unmarked = new ClassFileImporter().importPackages("ai.speckitty.charter.service.unmarkedfixture");
        assertThatThrownBy(() -> everyHandWrittenPackageIsNullMarked().check(unmarked))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "ai.speckitty.charter.service.unmarkedfixture.UnmarkedFixture resides in a package that is not"
                                + " @NullMarked");
    }

    /**
     * A test seam never lives in main: no main class depends on a class of a {@code ..bdd..} package, or on a class
     * whose name ends in {@code Drifting} or {@code Scenario}. The seam that makes a read see a file change is test
     * code, so the code it tests cannot have been written to cooperate with it.
     */
    private static ArchRule noTestSeamInMain() {
        return noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..bdd..")
                .orShould()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Drifting")
                .orShould()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Scenario");
    }

    @Test
    void noMainClassDependsOnATestSeam() {
        noTestSeamInMain().check(classes);
    }

    @Test
    void theTestSeamRuleFailsOnAClassThatDependsOnOne() {
        JavaClasses seamUser = new ClassFileImporter().importPackages("ai.speckitty.charter.service.seamfixture");
        assertThatThrownBy(() -> noTestSeamInMain().check(seamUser))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UsesSeam")
                .hasMessageContaining("ScenarioPacks");
    }
}
