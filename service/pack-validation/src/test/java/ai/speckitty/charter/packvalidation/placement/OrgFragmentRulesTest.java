package ai.speckitty.charter.packvalidation.placement;

import static ai.speckitty.charter.packvalidation.placement.Placements.BROKEN_FRAGMENT;
import static ai.speckitty.charter.packvalidation.placement.Placements.FRAGMENT;
import static ai.speckitty.charter.packvalidation.placement.Placements.NO_DESCRIPTOR;
import static ai.speckitty.charter.packvalidation.placement.Placements.NO_FRAGMENT;
import static ai.speckitty.charter.packvalidation.placement.Placements.declaring;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** An org pack is its fragment (#554, S2, R2 row 37), over hand-written placements. */
class OrgFragmentRulesTest {

    private static final Optional<PackLevel> ORG = Optional.of(PackLevel.ORG);

    private static final Reason MISSING = new Reason(
            "org_fragment_missing",
            Evaluation.FAIL,
            PlacementCodes.ORG_FRAGMENT_MISSING,
            Location.in("pack.yaml"),
            "org pack qa has no drg/fragment.yaml");

    @Test
    void anOrgPackWithNoFragmentIsRefused() {
        assertThat(OrgFragmentRules.RULES.apply(Placements.of(ORG, declaring(ORG), NO_FRAGMENT)))
                .containsExactly(MISSING);
        assertThat(OrgFragmentRules.RULES.apply(Placements.of(ORG, NO_DESCRIPTOR, NO_FRAGMENT)))
                .as("the configured level decides, whatever the descriptor")
                .containsExactly(MISSING);
    }

    @Test
    void aBarePathIsAnOrgPackWhenItsDescriptorDeclaresOrg() {
        assertThat(OrgFragmentRules.RULES.apply(Placements.of(Optional.empty(), declaring(ORG), NO_FRAGMENT)))
                .containsExactly(MISSING);
        assertThat(OrgFragmentRules.RULES.apply(
                        Placements.of(Optional.empty(), declaring(Optional.of(PackLevel.PROJECT)), NO_FRAGMENT)))
                .isEmpty();
        assertThat(OrgFragmentRules.RULES.apply(
                        Placements.of(Optional.empty(), declaring(Optional.empty()), NO_FRAGMENT)))
                .isEmpty();
        assertThat(OrgFragmentRules.RULES.apply(Placements.of(Optional.empty(), NO_DESCRIPTOR, NO_FRAGMENT)))
                .isEmpty();
    }

    @Test
    void aPackAtAnotherLevelAPresentFragmentOrAnUnreadableOneAnswersNothing() {
        assertThat(OrgFragmentRules.RULES.apply(
                        Placements.of(Optional.of(PackLevel.PROJECT), declaring(ORG), NO_FRAGMENT)))
                .as("the configured level wins over the declared one")
                .isEmpty();
        assertThat(OrgFragmentRules.RULES.apply(Placements.of(ORG, declaring(ORG), FRAGMENT)))
                .isEmpty();
        assertThat(OrgFragmentRules.RULES.apply(Placements.of(ORG, declaring(ORG), BROKEN_FRAGMENT)))
                .as("an unreadable fragment is not a missing one: its own finding speaks")
                .isEmpty();
    }
}
