package ai.speckitty.charter.deployable;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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
 * javac compiles the {@code switch} over the enum in {@code RefusalHandler} into a synthetic class
 * {@code RefusalHandler$1} (a {@code $SwitchMap} table) that reads the field, and the enum's own constants initializer
 * is part of the enum class. Each code marked {@code raised: true} must, the other way round, be named by some main
 * class outside those two; this says only that the code can raise it, not that a request of this version provokes it.
 *
 * <p>Two classes name unraised codes off the wire, and are allowed to name exactly those codes ({@link #OFF_WIRE}):
 * {@code WholePackStore}, the whole-pack load the round trip uses (ADR-0037), refuses a graph that breaks its schema
 * with {@code drg_schema_error}; and {@code PackFindingCode} projects its load-path findings onto refusal codes
 * through {@code PackFinding.toRefusal()}. No operation reaches either; {@link #offWireRules()} fail the day a main
 * class does, by a call or a method reference, so that the code's {@code x-refusal} entry is corrected with it.
 */
class ReservedRefusalCodesTest {

    private static final String REFUSAL_CODES = "contract/charter/schemas/CharterRefusalCode.yaml";

    private static final String WHOLE_PACK_STORE = WholePackStore.class.getName();
    private static final String PACK_FINDING = PackFinding.class.getName();
    private static final String PACK_FINDING_CODE = PackFindingCode.class.getName();

    private static final String ENUM = pattern(RefusalCode.class.getName());
    private static final String HANDLER_CLASS = "ai.speckitty.charter.packs.infra.api.RefusalHandler";
    private static final String HANDLER = pattern(HANDLER_CLASS);

    /** The methods of {@code RefusalHandler} that map every code to its status and title, raising none. */
    private static final Set<String> MAPPINGS = Set.of("statusOf", "title");

    /** The classes that may name an unraised code because no operation reaches them, and the codes each may name. */
    private static final Map<String, Set<String>> OFF_WIRE = Map.of(
            WHOLE_PACK_STORE, Set.of("DRG_SCHEMA_ERROR"),
            PACK_FINDING_CODE, Set.of("PACK_LEVEL_MISMATCH", "DRG_SCHEMA_ERROR"));

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ai.speckitty.charter");

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

    /** A call to, or a method reference of, {@code owner.method}: {@code callMethod} sees only the call. */
    private static DescribedPredicate<JavaAccess<?>> reaches(String owner, String method) {
        return DescribedPredicate.describe(
                owner + "." + method,
                access -> access.getTargetOwner().getName().equals(owner)
                        && access.getName().equals(method));
    }

    /**
     * The rules that keep the off-wire classes of {@link #OFF_WIRE} off the wire.
     *
     * @return one rule per way onto the wire: the whole-pack load, and the two projections of a finding to a refusal
     */
    static Stream<ArchRule> offWireRules() {
        String because =
                "the refusal it raises is pack_level_mismatch or drg_schema_error, which x-refusal marks raised: false";
        return Stream.of(
                noClasses()
                        .that()
                        .haveNameNotMatching(pattern(WHOLE_PACK_STORE))
                        .should()
                        .dependOnClassesThat()
                        .haveNameMatching(pattern(WHOLE_PACK_STORE))
                        .because(because),
                noClasses()
                        .that()
                        .haveNameNotMatching(pattern(PACK_FINDING))
                        .should()
                        .accessTargetWhere(reaches(PACK_FINDING, "toRefusal"))
                        .because(because),
                noClasses()
                        .that()
                        .haveNameNotMatching(pattern(PACK_FINDING))
                        .should()
                        .accessTargetWhere(reaches(PACK_FINDING_CODE, "refusal"))
                        .because(because));
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
        // raised; drg_schema_error is raised only off the wire (WholePackStore, PackFindingCode), so it is not.
        assertThat(raisedCodes()).contains("GOVERNANCE_POLICY_VIOLATION");
        assertThat(unraisedCodes()).contains("DRG_SCHEMA_ERROR");
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
        // and the synthetic switch-map classes are left out, not the whole handler.
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
    void theOffWireMethodsTheRulesNameExist() {
        // The rules name the two projections by string: a renamed method would make its rule match nothing.
        assertThat(MAIN.get(PACK_FINDING).getMethod("toRefusal").getName()).isEqualTo("toRefusal");
        assertThat(MAIN.get(PACK_FINDING_CODE).getMethod("refusal").getName()).isEqualTo("refusal");
    }

    /** Names every refusal code the way a raising class would, for the rule to find. */
    static final class NamesEveryCode {
        RefusalCode[] codes() {
            return new RefusalCode[] {
                RefusalCode.MISSION_TYPE_NOT_FOUND,
                RefusalCode.ACTION_NOT_FOUND,
                RefusalCode.PACK_NOT_FOUND,
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
                RefusalCode.ACTIVATION_INVALID
            };
        }
    }

    /**
     * Reaches each off-wire class the way an operation would: it holds the whole-pack load, and turns findings into
     * refusals by method reference, the stream shape a {@code callMethod} rule does not see.
     */
    static final class ReachesTheOffWireClasses {
        List<?> reach(WholePackStore store, List<PackFinding> findings, List<PackFindingCode> codes) {
            return List.of(
                    store,
                    findings.stream().map(PackFinding::toRefusal).toList(),
                    codes.stream().map(PackFindingCode::refusal).toList());
        }
    }
}
