package ai.speckitty.charter.packvalidation.placement;

import static ai.speckitty.charter.packvalidation.placement.Placements.BROKEN_DESCRIPTOR;
import static ai.speckitty.charter.packvalidation.placement.Placements.FRAGMENT;
import static ai.speckitty.charter.packvalidation.placement.Placements.NO_DESCRIPTOR;
import static ai.speckitty.charter.packvalidation.placement.Placements.declaring;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.kernel.validation.Reason;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The level rules (#554, S2, R2 rows 35 and 36), over hand-written placements. */
class LevelRulesTest {

    private static final Optional<PackLevel> ORG = Optional.of(PackLevel.ORG);

    @Test
    void aConfiguredPackWithNoDescriptorIsWarnedThatTheConfiguredLevelIsUsed() {
        assertThat(LevelRules.RULES.apply(Placements.of(ORG, NO_DESCRIPTOR, FRAGMENT)))
                .containsExactly(new Reason(
                        "pack_level_missing",
                        new Origin("pack_level_missing"),
                        Evaluation.FAIL,
                        PlacementCodes.PACK_LEVEL_MISSING,
                        Location.in("pack.yaml"),
                        "org pack qa has no pack.yaml; the configured level org is used"));
    }

    @Test
    void aDescriptorThatDeclaresNoLevelIsWarned() {
        assertThat(LevelRules.RULES.apply(Placements.of(ORG, declaring(Optional.empty()), FRAGMENT)))
                .extracting(Reason::code, Reason::detail)
                .containsExactly(org.assertj.core.api.Assertions.tuple(
                        PlacementCodes.PACK_LEVEL_MISSING,
                        "pack.yaml of qa has no pack_level; the configured level org is used"));
    }

    @Test
    void aDeclaredLevelOtherThanTheConfiguredOneIsAMismatch() {
        assertThat(LevelRules.RULES.apply(Placements.of(Optional.of(PackLevel.PROJECT), declaring(ORG), FRAGMENT)))
                .extracting(Reason::code, Reason::detail, Reason::location)
                .containsExactly(org.assertj.core.api.Assertions.tuple(
                        PlacementCodes.PACK_LEVEL_MISMATCH,
                        "Pack 'qa' is configured as project but declares pack_level org",
                        Location.in("pack.yaml")));
    }

    @Test
    void theSameLevelAnUnreadableDescriptorAndABarePathAnswerNothing() {
        assertThat(LevelRules.RULES.apply(Placements.of(ORG, declaring(ORG), FRAGMENT)))
                .isEmpty();
        assertThat(LevelRules.RULES.apply(Placements.of(ORG, BROKEN_DESCRIPTOR, FRAGMENT)))
                .as("an unreadable descriptor speaks for itself")
                .isEmpty();
        assertThat(LevelRules.RULES.apply(Placements.of(Optional.empty(), NO_DESCRIPTOR, FRAGMENT)))
                .as("a bare path has no configured level to compare with")
                .isEmpty();
        assertThat(LevelRules.RULES.apply(
                        Placements.of(Optional.empty(), declaring(Optional.of(PackLevel.PROJECT)), FRAGMENT)))
                .isEmpty();
    }

    @Test
    void aPlacementNeedsAReadAndALevel() {
        assertThatThrownBy(() -> new Placement(null, ORG)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Placement(
                        Placements.of(ORG, NO_DESCRIPTOR, FRAGMENT).read(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
