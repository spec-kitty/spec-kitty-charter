package ai.speckitty.charter.profiles.application;

import static ai.speckitty.charter.profiles.application.ProfileFixtures.leanProfile;
import static ai.speckitty.charter.profiles.application.ProfileFixtures.read;
import static ai.speckitty.charter.profiles.application.ProfileFixtures.requires;
import static ai.speckitty.charter.profiles.application.ProfileFixtures.urn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProfileReadTest {

    @Test
    void anEntryLinksWhenItIsHeldAndItsKindIsServedByTheGenericRoute() {
        ProfileRead read = read();

        assertThat(read.linksArtifact(requires("directive:D", "built-in"))).isTrue();
        assertThat(read.linksArtifact(requires("styleguide:S", "built-in"))).isTrue();
    }

    @Test
    void anEntryOfAKindTheGenericRouteDoesNotServeHasNoLink() {
        ProfileRead read = read();

        assertThat(read.linksArtifact(requires("tactic:T", "built-in"))).isFalse();
        assertThat(read.linksArtifact(new GovernanceEntry(
                        urn("agent_profile:Q"),
                        GovernanceEntry.Step.LINEAGE,
                        urn("agent_profile:P"),
                        1,
                        EdgeWords.NONE,
                        Optional.of(new ai.speckitty.charter.kernel.domain.PackIdentifier("built-in")),
                        true)))
                .isFalse();
    }

    @Test
    void anEntryNoPackHoldsHasNoLinkWhateverItsKind() {
        ProfileRead read = read();

        assertThat(read.linksArtifact(new GovernanceEntry(
                        urn("directive:Gone"),
                        GovernanceEntry.Step.REQUIRES,
                        urn("agent_profile:P"),
                        1,
                        EdgeWords.NONE,
                        Optional.empty(),
                        false)))
                .isFalse();
    }

    @Test
    void anEntryOfAKindWithNoArtifactKindHasNoLink() {
        ProfileRead read = read();

        assertThat(read.linksArtifact(requires("action:software-dev/implement", "built-in")))
                .isFalse();
    }

    @Test
    void theListsAreCopiedAndTheFieldsAreRequired() {
        List<GovernanceEntry> entries = new ArrayList<>(List.of(requires("directive:D", "built-in")));
        List<ArtifactKind> kinds = new ArrayList<>(List.of(ArtifactKind.DIRECTIVE));
        ProfileRead read = new ProfileRead(leanProfile("p", "P"), "r", "f", entries, kinds);
        entries.clear();
        kinds.clear();

        assertThat(read.governance()).hasSize(1);
        assertThat(read.servedKinds()).containsExactly(ArtifactKind.DIRECTIVE);
        assertThatThrownBy(() -> new ProfileRead(null, "r", "f", List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProfileRead(leanProfile("p", "P"), null, "f", List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProfileRead(leanProfile("p", "P"), "r", null, List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProfileRead(leanProfile("p", "P"), "r", "f", null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProfileRead(leanProfile("p", "P"), "r", "f", List.of(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> read.linksArtifact(null)).isInstanceOf(NullPointerException.class);
    }
}
