package ai.speckitty.charter.packs;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.net.URI;
import org.junit.jupiter.api.Test;

/**
 * NETWORK_CODE_STAYS_IN_INFRA (WP08, NFR-004): no class in the pack domain or application ring depends on
 * {@code java.net} or {@code java.net.http}, so the only code that can open a connection is an {@code infra} adapter
 * (today {@code infra.fetch}). {@link URI} is the one exception, named explicitly: it is the {@code GuideFetcher}
 * port's parameter, a value that opens nothing.
 *
 * <p>The known-bad control follows {@code HexagonalArchitectureTest}: a test-only class in a {@code badfixture}
 * package inside the very package the rule selects, against which the unchanged production rule must fail.
 */
class NetworkCodeBoundaryTest {

    private static final String ROOT = "ai.speckitty.charter.packs";

    static final ArchRule NETWORK_CODE_STAYS_IN_INFRA = noClasses()
            .that()
            .resideInAnyPackage(ROOT + ".domain..", ROOT + ".application..")
            .should()
            .dependOnClassesThat(resideInAnyPackage("java.net..").and(not(equivalentTo(URI.class))))
            .because("only an infra adapter may reach the network; the port names java.net.URI alone");

    @Test
    void theInnerRingsNameNoNetworkTypeButUri() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        assertThatCode(() -> NETWORK_CODE_STAYS_IN_INFRA.check(production)).doesNotThrowAnyException();
    }

    @Test
    void theRuleFailsOnAnApplicationClassThatOpensASocket() {
        JavaClasses fixtures = new ClassFileImporter().importPackages(ROOT + ".application.badfixture");

        assertThatThrownBy(() -> NETWORK_CODE_STAYS_IN_INFRA.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ApplicationOpensSocket")
                .hasMessageContaining("java.net.Socket");
    }

    @Test
    void thePortItselfWhichNamesUriPassesTheRule() {
        JavaClasses port =
                new ClassFileImporter().importClasses(ai.speckitty.charter.packs.application.GuideFetcher.class);

        assertThatCode(() -> NETWORK_CODE_STAYS_IN_INFRA.check(port)).doesNotThrowAnyException();
    }
}
