package ai.speckitty.charter.packvalidation.placement;

import static ai.speckitty.charter.packvalidation.placement.Placements.FRAGMENT;
import static ai.speckitty.charter.packvalidation.placement.Placements.NO_FRAGMENT;
import static ai.speckitty.charter.packvalidation.placement.Placements.declaring;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every placement code is exercised (#554, NFR-005, T055), beside its subject (ADR-0062): each {@link PlacementCodes}
 * constant has a placement the placement rules refuse or warn with it, and a twin they do not. {@code :packs}'
 * {@code PackCodesCoverageTest} does the same for the domain's, the storage edge's and the {@code CLOSURE} codes.
 */
@DisplayName("every placement code has a failing and a passing placement (#554, NFR-005)")
class PlacementCodesCoverageTest {

    private static final Optional<PackLevel> ORG = Optional.of(PackLevel.ORG);

    /** A placement that states the code, and its twin that does not. */
    record Fixture(Placement failing, Placement passing) {}

    private static final Map<ReasonCode, Fixture> FIXTURES = Map.of(
            PlacementCodes.PACK_LEVEL_MISSING,
            new Fixture(
                    Placements.of(ORG, declaring(Optional.empty()), FRAGMENT),
                    Placements.of(ORG, declaring(ORG), FRAGMENT)),
            PlacementCodes.PACK_LEVEL_MISMATCH,
            new Fixture(
                    Placements.of(Optional.of(PackLevel.PROJECT), declaring(ORG), FRAGMENT),
                    Placements.of(Optional.of(PackLevel.PROJECT), declaring(Optional.of(PackLevel.PROJECT)), FRAGMENT)),
            PlacementCodes.ORG_FRAGMENT_MISSING,
            new Fixture(Placements.of(ORG, declaring(ORG), NO_FRAGMENT), Placements.of(ORG, declaring(ORG), FRAGMENT)));

    /**
     * The codes no placement states, each with the test that fires it and proves its twin does not: the pack-set
     * check's {@code pack_level_missing} violation, which needs a set with no level, not a placement (#478, WP03 review
     * M3). Each entry names a real test method, so the list cannot rot.
     */
    private static final Map<ReasonCode, String> STATED_ELSEWHERE = Map.of(
            PlacementCodes.PACK_LEVEL_MISSING_VIOLATION,
            "ai.speckitty.charter.packvalidation.set.PackSetCheckTest$Skipped#anUnknownLevelSkipsClosure");

    /** What the placement rules state, in the order validate mode composes them: the level rules, then the fragment. */
    private static List<Reason> judge(Placement placement) {
        List<Reason> reasons = new ArrayList<>(LevelRules.RULES.apply(placement));
        reasons.addAll(OrgFragmentRules.RULES.apply(placement));
        return reasons;
    }

    static List<ReasonCode> codes() {
        return PlacementCodes.ALL.stream()
                .filter(code -> !STATED_ELSEWHERE.containsKey(code))
                .toList();
    }

    @Test
    @DisplayName("every code stated outside the placement rules names a test method that exists")
    void everyCodeStatedElsewhereNamesARealTest() throws ReflectiveOperationException {
        for (String test : STATED_ELSEWHERE.values()) {
            String[] parts = test.split("#", 2);
            Class<?> owner = Class.forName(parts[0]);
            assertThat(java.util.Arrays.stream(owner.getDeclaredMethods()).map(java.lang.reflect.Method::getName))
                    .as(test)
                    .contains(parts[1]);
        }
    }

    @Test
    @DisplayName("every ReasonCode constant PlacementCodes declares has a fixture, and is in PlacementCodes.ALL")
    void everyCodeHasAFixture() {
        Set<ReasonCode> declared = new LinkedHashSet<>();
        for (Field field : PlacementCodes.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers)
                    && Modifier.isStatic(modifiers)
                    && Modifier.isFinal(modifiers)
                    && field.getType() == ReasonCode.class) {
                try {
                    declared.add((ReasonCode) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(field.getName(), e);
                }
            }
        }

        Set<ReasonCode> covered = new LinkedHashSet<>(FIXTURES.keySet());
        covered.addAll(STATED_ELSEWHERE.keySet());
        assertThat(covered).containsExactlyInAnyOrderElementsOf(declared);
        assertThat(FIXTURES.keySet()).doesNotContainAnyElementsOf(STATED_ELSEWHERE.keySet());
        assertThat(PlacementCodes.ALL).containsExactlyInAnyOrderElementsOf(declared);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codes")
    @DisplayName("each placement code is stated by its failing placement, and not by its twin")
    void eachCodeHasAFailingAndAPassingPlacement(ReasonCode code) {
        Fixture fixture = FIXTURES.get(code);
        assertThat(fixture).as("no placement stands for %s", code).isNotNull();

        assertThat(judge(fixture.failing()))
                .extracting(Reason::code)
                .as("the failing placement")
                .contains(code);
        assertThat(judge(fixture.passing()))
                .extracting(Reason::code)
                .as("the passing twin")
                .doesNotContain(code);
    }
}
