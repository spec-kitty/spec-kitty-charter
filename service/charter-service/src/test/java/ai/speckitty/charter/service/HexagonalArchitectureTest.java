package ai.speckitty.charter.service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.Diagnostic;
import ai.speckitty.charter.domain.PackFindingCode;
import ai.speckitty.charter.domain.RefusalCode;
import com.tngtech.archunit.base.DescribedPredicate;
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

    private static DescribedPredicate<JavaClass> namedWithPrefix(String... prefixes) {
        return DescribedPredicate.describe(
                "named with one of the prefixes " + String.join(", ", prefixes),
                c -> java.util.Arrays.stream(prefixes).anyMatch(c.getName()::startsWith));
    }

    /**
     * Rule A: only the pack policy and the enums themselves name {@code Diagnostic.Code.PACK_LEVEL_MISSING}. The
     * prefix match covers the synthetic {@code $1} and {@code $SwitchMap} classes of a switch over the enum.
     */
    private static ArchRule onlyThePolicyNamesTheMissingDiagnosticRule() {
        return noClasses()
                .that(DescribedPredicate.not(namedWithPrefix(
                        "ai.speckitty.charter.domain.PackPolicy",
                        "ai.speckitty.charter.domain.PackFindingCode",
                        "ai.speckitty.charter.domain.Diagnostic")))
                .should()
                .accessField(Diagnostic.Code.class, "PACK_LEVEL_MISSING");
    }

    /**
     * Rule B: only the pack policy, the enums and the inbound adapter name {@code RefusalCode.PACK_LEVEL_MISMATCH};
     * {@code RefusalHandler$1} reads it with {@code getstatic}.
     */
    private static ArchRule onlyThePolicyNamesTheMismatchRefusalRule() {
        return noClasses()
                .that(DescribedPredicate.not(namedWithPrefix(
                        "ai.speckitty.charter.domain.PackPolicy",
                        "ai.speckitty.charter.domain.PackFindingCode",
                        "ai.speckitty.charter.domain.RefusalCode",
                        "ai.speckitty.charter.rest.")))
                .should()
                .accessField(RefusalCode.class, "PACK_LEVEL_MISMATCH");
    }

    /**
     * Rule C: only the pack policy and the finding enum name the pack-level finding codes, which closes the
     * {@code new PackFinding(...).toDiagnostic()} bypass.
     */
    private static ArchRule onlyThePolicyNamesThePackLevelFindingsRule() {
        return noClasses()
                .that(DescribedPredicate.not(namedWithPrefix(
                        "ai.speckitty.charter.domain.PackPolicy", "ai.speckitty.charter.domain.PackFindingCode")))
                .should()
                .accessField(PackFindingCode.class, "PACK_LEVEL_MISSING")
                .orShould()
                .accessField(PackFindingCode.class, "PACK_LEVEL_MISMATCH");
    }

    @Test
    void onlyThePolicyNamesThePackLevelFindings() {
        onlyThePolicyNamesThePackLevelFindingsRule().check(classes);
    }

    @Test
    void thePackLevelFindingRuleFailsOnAClassOutsideThePolicy() {
        JavaClasses outside = new ClassFileImporter().importPackages("ai.speckitty.charter.service.packlevelfixture");
        assertThatThrownBy(() -> onlyThePolicyNamesThePackLevelFindingsRule().check(outside))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BuildsFindingDirectly");
    }

    /**
     * Rule D: only the YAML adapter depends on {@code com.networknt}, so the validator never leaks into the
     * domain or the other adapters.
     */
    private static ArchRule onlyTheYamlAdapterUsesNetworkntRule() {
        return noClasses()
                .that()
                .resideOutsideOfPackage("ai.speckitty.charter.yaml..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.networknt..");
    }

    @Test
    void onlyThePolicyNamesTheMissingDiagnostic() {
        onlyThePolicyNamesTheMissingDiagnosticRule().check(classes);
    }

    @Test
    void onlyThePolicyNamesTheMismatchRefusal() {
        onlyThePolicyNamesTheMismatchRefusalRule().check(classes);
    }

    @Test
    void onlyTheYamlAdapterUsesNetworknt() {
        onlyTheYamlAdapterUsesNetworkntRule().check(classes);
    }

    // The rules check field access. Enum.valueOf("PACK_LEVEL_MISSING") or values() would get around them; no
    // production code does that today.
    @Test
    void theMissingDiagnosticRuleFailsOnAClassOutsideThePolicy() {
        JavaClasses outside = new ClassFileImporter().importPackages("ai.speckitty.charter.service.packlevelfixture");
        assertThatThrownBy(() -> onlyThePolicyNamesTheMissingDiagnosticRule().check(outside))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BuildsMissingDiagnostic");
    }

    @Test
    void theMismatchRefusalRuleFailsOnAClassOutsideThePolicy() {
        JavaClasses outside = new ClassFileImporter().importPackages("ai.speckitty.charter.service.packlevelfixture");
        assertThatThrownBy(() -> onlyThePolicyNamesTheMismatchRefusalRule().check(outside))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("RefusesWithMismatch");
    }

    // The stand-in exists only to prove that the networknt rule bites without the real dependency.
    @Test
    void theNetworkntRuleFailsOnAClassOutsideTheYamlAdapter() {
        JavaClasses outside = new ClassFileImporter()
                .importPackages("ai.speckitty.charter.service.networkntfixture", "com.networknt.fixture");
        assertThatThrownBy(() -> onlyTheYamlAdapterUsesNetworkntRule().check(outside))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UsesNetworkntStandIn");
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
