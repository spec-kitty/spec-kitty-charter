package ai.speckitty.charter.deployable;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Enforces that {@code unresolved_edge_endpoint} is raised by nothing: it is no longer raised since an edge endpoint
 * that is not a URN is a schema error (#114, R6). {@code x-refusal} says {@code raised: false} for it; this test pins
 * the other half, that nothing in the main code names the constant except the enum that declares it and the title
 * mapping of {@code RefusalHandler}.
 *
 * <p>The exclusion is by name pattern, not by simple name: javac compiles the {@code switch} over the enum in
 * {@code RefusalHandler.title()} into a synthetic class {@code RefusalHandler$1} (a {@code $SwitchMap} table) that
 * reads the field, and the enum's own constants initializer is part of the enum class.
 *
 * <p>The six codes reserved for the activation set (#125, ADR-0020 D9, D10) are pinned the same way: the activation
 * operations answer 501 until they are built, so nothing raises them, and their {@code x-refusal} says
 * {@code raised: false}. #291, which builds the writes, deletes their rules as it raises each code.
 *
 * <p>The companion {@code governance_policy_violation} pin is gone: the mutability-policy epic #55 (ADR-0012, steps
 * #57 to #61) landed as the {@code @Primary PackMutabilityPolicy}, which names and raises the code, and this test's
 * contract was always to drop that rule once a main class raises it.
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
    void noMainClassButTheEnumAndTheTitleMappingNamesUnresolvedEdgeEndpoint() {
        nothingRaises("UNRESOLVED_EDGE_ENDPOINT").check(MAIN);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ACTIVATION_NOT_FOUND",
                "ARTIFACT_NOT_FOUND",
                "PRESET_NOT_FOUND",
                "ARTIFACT_REQUIRED",
                "ARTIFACT_LOCKED",
                "ACTIVATION_INVALID"
            })
    void noMainClassButTheEnumAndTheMappingsNamesAnActivationCode(String constant) {
        nothingRaises(constant).check(MAIN);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ACTIVATION_NOT_FOUND",
                "ARTIFACT_NOT_FOUND",
                "PRESET_NOT_FOUND",
                "ARTIFACT_REQUIRED",
                "ARTIFACT_LOCKED",
                "ACTIVATION_INVALID"
            })
    void theRuleBitesOnAClassThatNamesAnActivationCode(String constant) {
        JavaClasses probe = new ClassFileImporter().importClasses(NamesTheActivationCodes.class);
        assertThatThrownBy(() -> nothingRaises(constant).check(probe)).isInstanceOf(AssertionError.class);
    }

    /** Names each code reserved for the activation set, for the rule to find. */
    static final class NamesTheActivationCodes {
        RefusalCode[] codes() {
            return new RefusalCode[] {
                RefusalCode.ACTIVATION_NOT_FOUND,
                RefusalCode.ARTIFACT_NOT_FOUND,
                RefusalCode.PRESET_NOT_FOUND,
                RefusalCode.ARTIFACT_REQUIRED,
                RefusalCode.ARTIFACT_LOCKED,
                RefusalCode.ACTIVATION_INVALID
            };
        }
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
