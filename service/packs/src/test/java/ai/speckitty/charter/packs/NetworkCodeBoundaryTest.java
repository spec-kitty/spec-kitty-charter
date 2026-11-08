package ai.speckitty.charter.packs;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
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
 * <p>PROCESSES_START_ONLY_IN_THE_GIT_ADAPTER (WP18, NFR-011) is the same kind of rule for {@link ProcessBuilder} and
 * {@link Process}: only {@code packs.infra.fetch.git} names them.
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

    /**
     * The system git is a host binary run as a subprocess (C-008, IC-05): the code that starts a process is one package,
     * where the threat model of running it (fixed argv, scrubbed environment, deadline) is kept. A second place that
     * names {@link ProcessBuilder} or {@link Process} would be a process started outside it.
     */
    static final ArchRule PROCESSES_START_ONLY_IN_THE_GIT_ADAPTER = noClasses()
            .that(resideOutsideOfPackages(ROOT + ".infra.fetch.git.."))
            .should()
            .dependOnClassesThat(equivalentTo(ProcessBuilder.class).or(equivalentTo(Process.class)))
            .because("only packs.infra.fetch.git runs a host binary, under the threat model of IC-05");

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
    void onlyTheGitAdapterNamesAProcess() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        assertThatCode(() -> PROCESSES_START_ONLY_IN_THE_GIT_ADAPTER.check(production))
                .doesNotThrowAnyException();
    }

    @Test
    void theProcessRuleFailsOnAClassOutsideTheGitPackageThatStartsAProcess() {
        JavaClasses fixtures = new ClassFileImporter().importPackages(ROOT + ".application.badfixture");

        assertThatThrownBy(() -> PROCESSES_START_ONLY_IN_THE_GIT_ADAPTER.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ApplicationStartsProcess")
                .hasMessageContaining("java.lang.ProcessBuilder");
    }

    @Test
    void theProcessRulePassesAClassInTheGitPackage() {
        JavaClasses fixtures = new ClassFileImporter().importPackages(ROOT + ".infra.fetch.git.goodfixture");

        assertThat(fixtures).isNotEmpty();
        assertThatCode(() -> PROCESSES_START_ONLY_IN_THE_GIT_ADAPTER.check(fixtures))
                .doesNotThrowAnyException();
    }

    @Test
    void thePortItselfWhichNamesUriPassesTheRule() {
        JavaClasses port =
                new ClassFileImporter().importClasses(ai.speckitty.charter.packs.application.GuideFetcher.class);

        assertThatCode(() -> NETWORK_CODE_STAYS_IN_INFRA.check(port)).doesNotThrowAnyException();
    }
}
