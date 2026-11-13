package ai.speckitty.charter.profiles.application;

import static ai.speckitty.charter.profiles.application.ProfileFixtures.active;
import static ai.speckitty.charter.profiles.application.ProfileFixtures.effectiveRead;
import static ai.speckitty.charter.profiles.application.ProfileFixtures.effectiveRequires;
import static ai.speckitty.charter.profiles.application.ProfileFixtures.urn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The values of the activation-scoped profile read: what they refuse, what they copy and which entries they link. */
class EffectiveProfileReadTest {

    @Test
    void anEntryIsLinkedOnlyWhenItsKindHasAPackScopedRead() {
        EffectiveProfileRead read = effectiveRead();

        assertThat(read.linksArtifact(effectiveRequires("directive:D", "main"))).isTrue();
        assertThat(read.linksArtifact(effectiveRequires("styleguide:S", "main")))
                .isTrue();
        assertThat(read.linksArtifact(effectiveRequires("procedure:Q", "main"))).isFalse();
        assertThat(read.linksArtifact(effectiveRequires("glossary_pack:G", "main")))
                .isFalse();
    }

    @Test
    void anEntryIsLinkedWhateverItsStateIs() {
        EffectiveProfileRead.Facts inactive = new EffectiveProfileRead.Facts(
                urn("directive:D"),
                new PackIdentifier("main"),
                PackLevel.ORG,
                "inactive",
                false,
                "open",
                "not_selected",
                List.of(),
                Optional.empty());

        assertThat(effectiveRead()
                        .linksArtifact(new EffectiveProfileRead.Entry(
                                inactive, GovernanceEntry.Step.SUGGESTS, urn("agent_profile:P"), 1, EdgeWords.NONE)))
                .isTrue();
    }

    @Test
    void anEntryHasTheRelationOfItsStep() {
        assertThat(effectiveRequires("directive:D", "main").relation()).isEqualTo(RelationType.REQUIRES);
        assertThat(new EffectiveProfileRead.Entry(
                                active("directive:D", "main"),
                                GovernanceEntry.Step.SUGGESTS,
                                urn("agent_profile:P"),
                                1,
                                EdgeWords.NONE)
                        .relation())
                .isEqualTo(RelationType.SUGGESTS);
    }

    @Test
    void anEntryIsAtLeastOneHopFromTheProfile() {
        assertThatThrownBy(() -> new EffectiveProfileRead.Entry(
                        active("directive:D", "main"),
                        GovernanceEntry.Step.REQUIRES,
                        urn("agent_profile:P"),
                        0,
                        EdgeWords.NONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one hop");
    }

    @Test
    void theListsAreCopiedSoALaterChangeOfTheCallersListIsNotTheReads() {
        List<EffectiveProfileRead.Entry> entries = new ArrayList<>(List.of(effectiveRequires("directive:D", "main")));
        List<ArtifactKind> kinds = new ArrayList<>(List.of(ArtifactKind.DIRECTIVE));
        EffectiveProfileRead read = new EffectiveProfileRead(
                ProfileFixtures.leanProfile("main", "P"),
                "a-1",
                "r",
                "g",
                "s",
                active("agent_profile:P", "main"),
                entries,
                kinds);

        entries.clear();
        kinds.clear();

        assertThat(read.governance()).hasSize(1);
        assertThat(read.linkableKinds()).containsExactly(ArtifactKind.DIRECTIVE);
    }

    @Test
    void everyFieldIsRequired() {
        EffectiveProfileRead ok = effectiveRead();
        assertThatThrownBy(() -> new EffectiveProfileRead(null, "a", "r", "g", "s", ok.winner(), List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        new EffectiveProfileRead(ok.profile(), null, "r", "g", "s", ok.winner(), List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        new EffectiveProfileRead(ok.profile(), "a", null, "g", "s", ok.winner(), List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        new EffectiveProfileRead(ok.profile(), "a", "r", null, "s", ok.winner(), List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        new EffectiveProfileRead(ok.profile(), "a", "r", "g", null, ok.winner(), List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EffectiveProfileRead(ok.profile(), "a", "r", "g", "s", null, List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> new EffectiveProfileRead(ok.profile(), "a", "r", "g", "s", ok.winner(), null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> new EffectiveProfileRead(ok.profile(), "a", "r", "g", "s", ok.winner(), List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void theFactsOfAnArtifactAreRequired() {
        List<ai.speckitty.charter.kernel.domain.Urn> none = List.of();
        PackIdentifier pack = new PackIdentifier("main");
        assertThatThrownBy(() -> new EffectiveProfileRead.Facts(
                        null, pack, PackLevel.ORG, "active", false, "open", "selected", none, Optional.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EffectiveProfileRead.Facts(
                        urn("directive:D"),
                        null,
                        PackLevel.ORG,
                        "active",
                        false,
                        "open",
                        "selected",
                        none,
                        Optional.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EffectiveProfileRead.Facts(
                        urn("directive:D"), pack, null, "active", false, "open", "selected", none, Optional.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EffectiveProfileRead.Facts(
                        urn("directive:D"),
                        pack,
                        PackLevel.ORG,
                        null,
                        false,
                        "open",
                        "selected",
                        none,
                        Optional.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EffectiveProfileRead.Facts(
                        urn("directive:D"),
                        pack,
                        PackLevel.ORG,
                        "active",
                        false,
                        null,
                        "selected",
                        none,
                        Optional.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EffectiveProfileRead.Facts(
                        urn("directive:D"), pack, PackLevel.ORG, "active", false, "open", null, none, Optional.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EffectiveProfileRead.Facts(
                        urn("directive:D"),
                        pack,
                        PackLevel.ORG,
                        "active",
                        false,
                        "open",
                        "selected",
                        null,
                        Optional.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EffectiveProfileRead.Facts(
                        urn("directive:D"), pack, PackLevel.ORG, "active", false, "open", "selected", none, null))
                .isInstanceOf(NullPointerException.class);
    }
}
