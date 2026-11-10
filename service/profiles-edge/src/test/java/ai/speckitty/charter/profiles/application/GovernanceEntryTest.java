package ai.speckitty.charter.profiles.application;

import static ai.speckitty.charter.profiles.application.ProfileFixtures.urn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GovernanceEntryTest {

    private static final Optional<PackIdentifier> HOLDER = Optional.of(new PackIdentifier("built-in"));

    private static EdgeWords words() {
        return new EdgeWords(Optional.of("when"), Optional.empty());
    }

    @Test
    void anEntryIsAtLeastOneHopFromTheProfile() {
        assertThatThrownBy(() -> new GovernanceEntry(
                        urn("directive:D"),
                        GovernanceEntry.Step.REQUIRES,
                        urn("agent_profile:P"),
                        0,
                        EdgeWords.NONE,
                        HOLDER,
                        true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new GovernanceEntry(
                                urn("directive:D"),
                                GovernanceEntry.Step.REQUIRES,
                                urn("agent_profile:P"),
                                1,
                                EdgeWords.NONE,
                                HOLDER,
                                true)
                        .hops())
                .isEqualTo(1);
    }

    @Test
    void anEntryOfAnyStepCarriesTheWordsOfItsEdgeAndAnEdgeMayStateNeither() {
        for (GovernanceEntry.Step step : GovernanceEntry.Step.values()) {
            assertThat(new GovernanceEntry(urn("directive:D"), step, urn("agent_profile:P"), 1, words(), HOLDER, true)
                            .edge())
                    .isEqualTo(words());
            assertThat(new GovernanceEntry(
                                    urn("directive:D"), step, urn("agent_profile:P"), 1, EdgeWords.NONE, HOLDER, true)
                            .edge()
                            .when())
                    .isEmpty();
        }
    }

    @Test
    void noWordsIsTheEdgeThatStatesNeither() {
        assertThat(EdgeWords.NONE).isEqualTo(new EdgeWords(Optional.empty(), Optional.empty()));
        assertThat(EdgeWords.NONE.reason()).isEmpty();
    }

    @Test
    void aHeldEntryNamesItsHolderAndAnEntryNotHeldNamesNone() {
        assertThatThrownBy(() -> new GovernanceEntry(
                        urn("directive:D"),
                        GovernanceEntry.Step.REQUIRES,
                        urn("agent_profile:P"),
                        1,
                        EdgeWords.NONE,
                        Optional.empty(),
                        true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GovernanceEntry(
                        urn("directive:D"),
                        GovernanceEntry.Step.REQUIRES,
                        urn("agent_profile:P"),
                        1,
                        EdgeWords.NONE,
                        HOLDER,
                        false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new GovernanceEntry(
                                urn("directive:D"),
                                GovernanceEntry.Step.REQUIRES,
                                urn("agent_profile:P"),
                                1,
                                EdgeWords.NONE,
                                Optional.empty(),
                                false)
                        .held())
                .isFalse();
    }

    @Test
    void aMissingFieldIsRefused() {
        assertThatThrownBy(() -> new GovernanceEntry(
                        null, GovernanceEntry.Step.REQUIRES, urn("agent_profile:P"), 1, EdgeWords.NONE, HOLDER, true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GovernanceEntry(
                        urn("directive:D"), null, urn("agent_profile:P"), 1, EdgeWords.NONE, HOLDER, true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GovernanceEntry(
                        urn("directive:D"), GovernanceEntry.Step.REQUIRES, null, 1, EdgeWords.NONE, HOLDER, true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GovernanceEntry(
                        urn("directive:D"),
                        GovernanceEntry.Step.REQUIRES,
                        urn("agent_profile:P"),
                        1,
                        null,
                        HOLDER,
                        true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GovernanceEntry(
                        urn("directive:D"),
                        GovernanceEntry.Step.REQUIRES,
                        urn("agent_profile:P"),
                        1,
                        EdgeWords.NONE,
                        null,
                        true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EdgeWords(null, Optional.empty())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EdgeWords(Optional.empty(), null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void eachStepHasItsWireTokenAndTheRelationOfItsEdges() {
        assertThat(Arrays.stream(GovernanceEntry.Step.values())
                        .map(step -> step.token() + "=" + step.relation().token()))
                .containsExactly(
                        "lineage=specializes_from",
                        "requires=requires",
                        "suggests=suggests",
                        "requires_of_suggested=requires",
                        "vocabulary=vocabulary");
    }

    @Test
    void theRelationOfAnEntryIsTheRelationOfItsStep() {
        GovernanceEntry entry = new GovernanceEntry(
                urn("agent_profile:Q"),
                GovernanceEntry.Step.LINEAGE,
                urn("agent_profile:P"),
                1,
                EdgeWords.NONE,
                HOLDER,
                true);

        assertThat(entry.relation()).isEqualTo(RelationType.SPECIALIZES_FROM);
    }
}
