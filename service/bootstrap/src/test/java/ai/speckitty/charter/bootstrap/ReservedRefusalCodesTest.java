package ai.speckitty.charter.bootstrap;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Enforces that two refusal codes are raised by nothing: {@code governance_policy_violation}, reserved for the
 * mutability policy, and {@code unresolved_edge_endpoint}, no longer raised since an edge endpoint that is not a URN
 * is a schema error (#114, R6). The contract scenario "governance_policy_violation is reserved" pins the first
 * declaration, and {@code x-refusal} says {@code raised: false} for both; this test pins the other half, that
 * nothing in the main code names either constant except the enum that declares it and the title mapping of
 * {@code RefusalHandler}.
 *
 * <p>The exclusion is by name pattern, not by simple name: javac compiles the {@code switch} over the enum in
 * {@code RefusalHandler.title()} into a synthetic class {@code RefusalHandler$1} (a {@code $SwitchMap} table) that
 * reads the field, and the enum's own constants initializer is part of the enum class.
 *
 * <p>The mutability-policy epic #55 (ADR-0012, steps #57 to #61) raises {@code governance_policy_violation}, and
 * drops that rule when it does.
 */
class ReservedRefusalCodesTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ai.speckitty.charter");

    private static ArchRule nothingRaises(String constant) {
        return noClasses()
                .that()
                .haveNameNotMatching("ai\\.speckitty\\.charter\\.kernel\\.domain\\.RefusalCode(\\$.*)?")
                .and()
                .haveNameNotMatching("ai\\.speckitty\\.charter\\.packs\\.infra\\.api\\.RefusalHandler(\\$.*)?")
                .should()
                .accessField(RefusalCode.class, constant);
    }

    @Test
    void noMainClassButTheEnumAndTheTitleMappingNamesGovernancePolicyViolation() {
        nothingRaises("GOVERNANCE_POLICY_VIOLATION").check(MAIN);
    }

    @Test
    void noMainClassButTheEnumAndTheTitleMappingNamesUnresolvedEdgeEndpoint() {
        nothingRaises("UNRESOLVED_EDGE_ENDPOINT").check(MAIN);
    }

    @Test
    void theRuleBitesOnAClassThatNamesTheConstant() {
        // A guard that matches nothing passes forever: this class names a refusal code, so the rule must fail on it.
        JavaClasses probe = new ClassFileImporter().importClasses(NamesAReservedCode.class);
        assertThatThrownBy(() -> nothingRaises("UNRESOLVED_EDGE_ENDPOINT").check(probe))
                .isInstanceOf(AssertionError.class);
    }

    /** Names {@code unresolved_edge_endpoint} the way a raising class would, for the rule to find. */
    static final class NamesAReservedCode {
        RefusalCode code() {
            return RefusalCode.UNRESOLVED_EDGE_ENDPOINT;
        }
    }
}
