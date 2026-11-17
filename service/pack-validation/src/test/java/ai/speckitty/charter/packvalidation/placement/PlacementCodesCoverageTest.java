package ai.speckitty.charter.packvalidation.placement;

import static ai.speckitty.charter.packvalidation.placement.Placements.FRAGMENT;
import static ai.speckitty.charter.packvalidation.placement.Placements.NO_FRAGMENT;
import static ai.speckitty.charter.packvalidation.placement.Placements.declaring;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** What the placement rules state, in the order validate mode composes them: the level rules, then the fragment. */
    private static List<Reason> judge(Placement placement) {
        List<Reason> reasons = new ArrayList<>(LevelRules.RULES.apply(placement));
        reasons.addAll(OrgFragmentRules.RULES.apply(placement));
        return reasons;
    }

    static List<ReasonCode> codes() {
        return List.copyOf(PlacementCodes.ALL);
    }

    @Test
    @DisplayName("every code PlacementCodes builds has a fixture")
    void everyCodeHasAFixture() {
        assertThat(FIXTURES.keySet()).containsExactlyInAnyOrderElementsOf(PlacementCodes.ALL);
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
