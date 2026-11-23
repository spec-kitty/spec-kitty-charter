package ai.speckitty.charter.deployable;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packvalidation.placement.PlacementCodes;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * Enforces that the {@code raised} flags of the contract's {@code x-refusal} map agree with what the main code names.
 * The codes come from the map in {@code CharterRefusalCode.yaml} itself, so the contract and this test cannot drift
 * apart (#293): a code a main class starts raising fails here until its entry says {@code raised: true}, and a code the
 * contract marks not raised is checked without editing this test.
 *
 * <p>For each code marked {@code raised: false}, no main class may name the constant except the enum that declares it
 * and the status and title mapping of {@code RefusalHandler}. The exclusion is by name pattern, not by simple name:
 * the status table is the nested class {@code RefusalHandler$StatusByCode}, whose initializer reads the fields (as a
 * {@code switch} over the enum compiles to a synthetic {@code RefusalHandler$1} that does), and the enum's own constants
 * initializer is part of the enum class. Each code marked {@code raised: true} must, the other way round, be named by some main
 * class outside those two; this says only that the code can raise it, not that a request of this version provokes it.
 *
 * <p>One class names an unraised code off the wire, and is allowed to name exactly that code ({@link #OFF_WIRE}):
 * {@code pack-validation}'s {@code PlacementCodes} builds the placement token {@code pack_level_mismatch} from the
 * refusal code's spelling, so the token has one source (#554, WP13). Validate mode is the validation edge, which the
 * service does not run (C-003), so no operation answers it. {@link #offWireRules()} fail the day a class outside
 * {@code pack-validation} names that class, so that the code's {@code x-refusal} entry is corrected with it.
 * {@code drg_schema_error} is raised by the amended definition of {@code raised} (a violation code of
 * {@code 422 validation_failed}, OD-3 (a)), so the whole-pack load that names it is no longer off the wire.
 */
class ReservedRefusalCodesTest {

    private static final String REFUSAL_CODES = "contract/charter/schemas/CharterRefusalCode.yaml";

    private static final String PLACEMENT_CODES = PlacementCodes.class.getName();

    /** The validation edge's packages: the service does not run them (C-003). */
    private static final String VALIDATION_EDGE = "ai.speckitty.charter.packvalidation..";

    private static final String ENUM = pattern(RefusalCode.class.getName());
    private static final String HANDLER_CLASS = "ai.speckitty.charter.web.RefusalHandler";
    private static final String HANDLER = pattern(HANDLER_CLASS);

    /** The methods of {@code RefusalHandler} that map every code to its status and title, raising none. */
    private static final Set<String> MAPPINGS = Set.of("statusOf", "title");

    /** The classes that may name an unraised code because no operation reaches them, and the codes each may name. */
    private static final Map<String, Set<String>> OFF_WIRE = Map.of(PLACEMENT_CODES, Set.of("PACK_LEVEL_MISMATCH"));

    private static final JavaClasses MAIN = ServiceClasses.main();

    /** A class and its nested and synthetic classes. */
    private static String pattern(String className) {
        return className.replace(".", "\\.").replace("$", "\\$") + "(\\$.*)?";
    }

    private static ArchRule nothingRaises(String constant) {
        String offWire = OFF_WIRE.entrySet().stream()
                .filter(entry -> entry.getValue().contains(constant))
                .map(entry -> pattern(entry.getKey()))
                .collect(Collectors.joining("|"));
        var rule = noClasses().that().haveNameNotMatching(ENUM).and().haveNameNotMatching(HANDLER);
        if (!offWire.isEmpty()) {
            rule = rule.and().haveNameNotMatching(offWire);
        }
        return rule.should().accessField(RefusalCode.class, constant);
    }

    /**
     * The rules that keep the off-wire classes of {@link #OFF_WIRE} off the wire.
     *
     * @return one rule per way onto the wire: a class outside the validation edge that names the placement codes
     */
    static Stream<ArchRule> offWireRules() {
        return Stream.of(noClasses()
                .that()
                .resideOutsideOfPackage(VALIDATION_EDGE)
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(PLACEMENT_CODES)
                .because("the placement token pack_level_mismatch is validate mode's, which x-refusal marks raised:"
                        + " false"));
    }

    /**
     * The constant of every code whose {@code x-refusal} entry says {@code raised: false}.
     *
     * @return the constant names, for example {@code UNRESOLVED_EDGE_ENDPOINT}
     */
    static List<String> unraisedCodes() {
        return codes(false);
    }

    /**
     * The constant of every code whose {@code x-refusal} entry says {@code raised: true}.
     *
     * @return the constant names, for example {@code PACK_UNREADABLE}
     */
    static List<String> raisedCodes() {
        return codes(true);
    }

    private static List<String> codes(boolean raised) {
        Map<String, RefusalCode> byToken =
                Arrays.stream(RefusalCode.values()).collect(Collectors.toMap(RefusalCode::token, Function.identity()));
        return refusals().entrySet().stream()
                .filter(entry -> Boolean.valueOf(raised).equals(((Map<?, ?>) entry.getValue()).get("raised")))
                .map(entry -> byToken.get(String.valueOf(entry.getKey())).name())
                .sorted()
                .toList();
    }

    private static Map<?, ?> refusals() {
        return (Map<?, ?>) schema().get("x-refusal");
    }

    private static Map<?, ?> schema() {
        try (var in = ReservedRefusalCodesTest.class.getClassLoader().getResourceAsStream(REFUSAL_CODES)) {
            assertThat(in).as(REFUSAL_CODES).isNotNull();
            return (Map<?, ?>) new Load(LoadSettings.builder().build())
                    .loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void theContractDeclaresABooleanRaisedFlagForEveryCodeOfTheEnum() {
        // A code missing from the map, or a raised flag that is not a boolean, would drop out of both lists unseen.
        assertThat(refusals().keySet().stream().map(String::valueOf).toList())
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(RefusalCode.values())
                        .map(RefusalCode::token)
                        .toList());
        assertThat(refusals().values())
                .allSatisfy(
                        entry -> assertThat(((Map<?, ?>) entry).get("raised")).isInstanceOf(Boolean.class));
        assertThat(unraisedCodes()).isNotEmpty();
        assertThat(raisedCodes()).isNotEmpty();
    }

    @Test
    void theContractMarksTheCodesTheWritesAndTheLoadRaiseAsDecided() {
        // #293: the registration writes raise governance_policy_violation (PackMutabilityPolicy, 409), so it is
        // raised; drg_schema_error is raised as a violation code of a refused registration (OD-3 (a)), so it is raised
        // too, and pack_level_mismatch, named only by validate mode's placement codes, is not.
        assertThat(raisedCodes()).contains("GOVERNANCE_POLICY_VIOLATION");
        assertThat(raisedCodes()).contains("DRG_SCHEMA_ERROR");
        // #125, #291, #360: the activation register, its per-artifact writes and the preset write raise all six of
        // the activation set's codes, and the select of a mission type raises mission_type_not_found. PR C built the
        // last two effective reads (IC-11), so getEffectiveActionContext raises action_not_found too.
        assertThat(raisedCodes())
                .contains(
                        "ACTIVATION_NOT_FOUND",
                        "ACTIVATION_INVALID",
                        "ARTIFACT_REQUIRED",
                        "ARTIFACT_LOCKED",
                        "ARTIFACT_NOT_FOUND",
                        "MISSION_TYPE_NOT_FOUND",
                        "PRESET_NOT_FOUND",
                        "ACTION_NOT_FOUND");
        assertThat(unraisedCodes()).doesNotContain("PRESET_NOT_FOUND", "ACTION_NOT_FOUND");
        // #438 (FR-006, SC-003): the compose check of a step enhancement in force (C38) raises prompt_slot_missing on
        // the composing reads, at registration, on the impact-checked writes and on the activation writes, so none of
        // ADR-0066's four codes is reserved any more.
        assertThat(raisedCodes())
                .contains(
                        "PROMPT_SLOT_MISSING",
                        "PROMPT_SLOT_MALFORMED",
                        "PROMPT_SLOT_NOT_EMPTY",
                        "PROMPT_FRAGMENT_INVALID");
        assertThat(unraisedCodes()).doesNotContain("PROMPT_SLOT_MISSING");
    }

    @ParameterizedTest
    @MethodSource("unraisedCodes")
    void noMainClassButTheEnumAndTheMappingsNamesAnUnraisedCode(String constant) {
        nothingRaises(constant).check(MAIN);
    }

    @ParameterizedTest
    @MethodSource("unraisedCodes")
    void theRuleBitesOnAClassThatNamesAnUnraisedCode(String constant) {
        // A guard that matches nothing passes forever: the probe names every code, so the rule must fail on it.
        JavaClasses probe = new ClassFileImporter().importClasses(NamesEveryCode.class);
        assertThatThrownBy(() -> nothingRaises(constant).check(probe)).isInstanceOf(AssertionError.class);
    }

    @Test
    void theProbeNamesEveryCode() {
        JavaClass probe = new ClassFileImporter().importClass(NamesEveryCode.class);
        Set<String> named = probe.getFieldAccessesFromSelf().stream()
                .filter(access -> access.getTargetOwner().isEquivalentTo(RefusalCode.class))
                .map(JavaFieldAccess::getName)
                .collect(Collectors.toSet());
        assertThat(named)
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(RefusalCode.values()).map(Enum::name).toList());
    }

    @Test
    void everyCodeMarkedRaisedIsNamedByAMainClassButTheEnumAndTheMappings() {
        // RefusalHandler raises internal_error itself (the refusal of an unexpected failure), so only its mappings
        // and its nested classes (the StatusByCode table) are left out, not the whole handler.
        Set<String> named = MAIN.stream()
                .flatMap(type -> type.getFieldAccessesFromSelf().stream())
                .filter(access -> access.getTargetOwner().isEquivalentTo(RefusalCode.class))
                .filter(access -> !mapping(access))
                .map(JavaFieldAccess::getName)
                .collect(Collectors.toSet());
        assertThat(named).containsAll(raisedCodes());
    }

    private static boolean mapping(JavaFieldAccess access) {
        String origin = access.getOriginOwner().getName();
        if (origin.matches(ENUM)) {
            return true;
        }
        if (!origin.matches(HANDLER)) {
            return false;
        }
        return !origin.equals(HANDLER_CLASS)
                || MAPPINGS.contains(access.getOrigin().getName());
    }

    @Test
    void theHandlerMappingsTheReverseCheckLeavesOutExist() {
        // A renamed mapping would count the handler's naming of every code as raising it, and pass vacuously.
        assertThat(MAIN.get(HANDLER_CLASS).getMethods().stream().map(method -> method.getName()))
                .containsAll(MAPPINGS);
    }

    @ParameterizedTest
    @MethodSource("offWireRules")
    void theOffWireRefusalsStayOffTheWire(ArchRule rule) {
        rule.check(MAIN);
    }

    @ParameterizedTest
    @MethodSource("offWireRules")
    void theOffWireRuleBitesOnAClassThatReachesAnOffWireClass(ArchRule rule) {
        JavaClasses probe = new ClassFileImporter().importClasses(ReachesTheOffWireClasses.class);
        assertThatThrownBy(() -> rule.check(probe)).isInstanceOf(AssertionError.class);
    }

    @Test
    void theOffWireClassTheRulesNameExists() {
        // The rules name the off-wire class by string: a renamed class would make its rule match nothing.
        assertThat(MAIN.get(PLACEMENT_CODES).getPackageName()).startsWith("ai.speckitty.charter.packvalidation");
        assertThat(MAIN.get(PLACEMENT_CODES).getFieldAccessesFromSelf().stream()
                        .filter(access -> access.getTargetOwner().isEquivalentTo(RefusalCode.class))
                        .map(JavaFieldAccess::getName))
                .as("it builds pack_level_mismatch from the refusal code's spelling")
                .containsExactly("PACK_LEVEL_MISMATCH");
    }

    /** Names every refusal code the way a raising class would, for the rule to find. */
    static final class NamesEveryCode {
        /** The first half of the codes; {@link #moreCodes()} names the rest. */
        RefusalCode[] codes() {
            return new RefusalCode[] {
                RefusalCode.MISSION_TYPE_NOT_FOUND,
                RefusalCode.ACTION_NOT_FOUND,
                RefusalCode.PACK_NOT_FOUND,
                RefusalCode.PACK_DOCUMENT_NOT_FOUND,
                RefusalCode.PACK_LEVEL_MISMATCH,
                RefusalCode.PACK_UNREADABLE,
                RefusalCode.DRG_SCHEMA_ERROR,
                RefusalCode.UNRESOLVED_EDGE_ENDPOINT,
                RefusalCode.INTERNAL_ERROR,
                RefusalCode.GOVERNANCE_POLICY_VIOLATION,
                RefusalCode.STALE_INPUT,
                RefusalCode.TACTIC_NOT_FOUND,
                RefusalCode.DIRECTIVE_NOT_FOUND,
                RefusalCode.STYLEGUIDE_NOT_FOUND,
                RefusalCode.PARADIGM_NOT_FOUND,
                RefusalCode.TOOLGUIDE_NOT_FOUND,
                RefusalCode.KIND_NOT_FOUND,
                RefusalCode.VALIDATION_FAILED,
                RefusalCode.REGISTRATION_CONFLICT,
                RefusalCode.ACTIVATION_NOT_FOUND,
                RefusalCode.ARTIFACT_NOT_FOUND,
                RefusalCode.PRESET_NOT_FOUND,
                RefusalCode.ARTIFACT_REQUIRED,
                RefusalCode.ARTIFACT_LOCKED,
                RefusalCode.ACTIVATION_INVALID,
                RefusalCode.PACK_DEPENDENCY_CYCLE,
                RefusalCode.PACK_DEPENDENCY_UNKNOWN,
                RefusalCode.PACK_DEPENDENCY_OUT_OF_RANGE,
                RefusalCode.PACK_PARENT_UNRESOLVED,
                RefusalCode.PACK_LEVEL_VIOLATION,
                RefusalCode.PACK_NAME_CONFLICT,
                RefusalCode.PACK_DUPLICATE_ID
            };
        }

        /** The rest of the codes, so each method stays within the method-length limit. */
        RefusalCode[] moreCodes() {
            return new RefusalCode[] {
                RefusalCode.PACK_SIBLING_CONFLICT,
                RefusalCode.PACK_REFERENCE_UNQUALIFIED,
                RefusalCode.PACK_REFERENCE_PACK_MISSING,
                RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING,
                RefusalCode.PACK_VERSION_CONFLICT,
                RefusalCode.ARTIFACT_REFERENCED,
                RefusalCode.RELATIONSHIP_NOT_FOUND,
                RefusalCode.RELATIONSHIP_NOT_REGISTERED,
                RefusalCode.REGISTRATION_NOT_FOUND,
                RefusalCode.PACK_SOURCE_REFUSED,
                RefusalCode.PACK_SOURCE_UNAVAILABLE,
                RefusalCode.OVERRIDE_NOT_PERMITTED,
                RefusalCode.ENHANCES_CYCLE,
                RefusalCode.CONFLICTING_RELATIONS,
                RefusalCode.SELF_OVERRIDE,
                RefusalCode.OVERRIDE_CYCLE,
                RefusalCode.REGISTRATION_TIMED_OUT,
                RefusalCode.PACK_STORAGE_FULL,
                RefusalCode.PROMPT_SLOT_MALFORMED,
                RefusalCode.PROMPT_SLOT_NOT_EMPTY,
                RefusalCode.PROMPT_FRAGMENT_INVALID,
                RefusalCode.PROMPT_SLOT_MISSING,
                RefusalCode.SKELETON_NOT_FOUND,
                RefusalCode.FALLBACK_PROFILE_UNKNOWN,
                RefusalCode.FALLBACK_PROFILE_NOT_LOCKED,
                RefusalCode.FALLBACK_PROFILE_NOT_ROLE_NEUTRAL,
                RefusalCode.FALLBACK_PROFILE_NOT_AGENT,
                RefusalCode.FALLBACK_PROFILE_MISSING,
                RefusalCode.FALLBACK_PROFILE_CONFLICT,
                RefusalCode.NO_PROFILE_MATCHED,
                RefusalCode.MISSION_TYPE_NOT_IN_FORCE,
                RefusalCode.ROLE_PIN_UNKNOWN,
                RefusalCode.ROLE_PIN_NOT_FITTING,
                RefusalCode.ROLE_PIN_CONFLICT,
                RefusalCode.SPECIALIZATION_CYCLE,
                RefusalCode.PROFILE_ENHANCEMENT_DROPS_FIELD,
                RefusalCode.ROUTING_CATALOG_NOT_IN_FORCE,
                RefusalCode.ROUTING_CATALOG_CONFLICT,
                RefusalCode.TASK_TYPE_NOT_FOUND,
                RefusalCode.PACK_HAS_DEPENDENTS,
                RefusalCode.PRESET_ITEM_NOT_HELD,
                RefusalCode.PRESET_ITEM_UNDECLARED
            };
        }
    }

    /** Reaches the off-wire placement token the way an operation outside the validation edge would. */
    static final class ReachesTheOffWireClasses {
        List<ReasonCode> reach() {
            return List.of(PlacementCodes.PACK_LEVEL_MISMATCH);
        }
    }
}
