package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.MISSION_TYPE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ADR-0059 amendment A5 (operator ruling of 2026-11-06): a write drops from the stored document every id its closure no
 * longer declares, in {@code selected} and {@code deactivated}, of every kind ({@code mission_type} included), and names
 * each one; and (B1) every version pin no pack of the closure holds, naming each pin as it was stored.
 */
class StaleIdsTest {

    private static final ClosureSnapshot CLOSURE = ClosureSnapshots.snapshot()
            .pack("main", "1.0.0")
            .held(DIRECTIVE, "D")
            .held(TACTIC, "t")
            .held(MISSION_TYPE, "research")
            .build();

    @Test
    void everyIdTheClosureNoLongerDeclaresIsDroppedAndNamed() {
        ActivationDocument stored = ActivationDocument.builder()
                .packs(List.of(PackRef.pinned("main", "1.0.0")))
                .languages(List.of("java"))
                .selected(MISSION_TYPE, List.of("research", "gone-type"))
                .selected(DIRECTIVE, List.of("D", "GONE"))
                .deactivated(TACTIC, List.of("old"))
                .build()
                .document();

        StaleIds.Dropped dropped = StaleIds.drop(stored, CLOSURE);

        assertThat(dropped.dropped())
                .containsExactly(
                        new ArtifactRef(DIRECTIVE, "GONE"),
                        new ArtifactRef(MISSION_TYPE, "gone-type"),
                        new ArtifactRef(TACTIC, "old"));
        assertThat(dropped.document().selected(DIRECTIVE).orElseThrow()).containsExactly("D");
        assertThat(dropped.document().selected(MISSION_TYPE).orElseThrow()).containsExactly("research");
        assertThat(dropped.document().deactivated())
                .as("a deny list the drop empties is left out")
                .isEmpty();
        assertThat(dropped.document().packs()).isEqualTo(stored.packs());
        assertThat(dropped.document().languages()).isEqualTo(stored.languages());
    }

    @Test
    void anAllowListTheDropEmptiesIsLeftOutAsNoneOfTheKind() {
        ActivationDocument stored = ActivationDocument.builder()
                .selected(DIRECTIVE, List.of("GONE"))
                .build()
                .document();

        StaleIds.Dropped dropped = StaleIds.drop(stored, CLOSURE);

        assertThat(dropped.document().selected(DIRECTIVE)).isEmpty();
        assertThat(dropped.document())
                .isEqualTo(ActivationDocument.builder().build().document());
    }

    @Test
    void aDocumentTheClosureStillDeclaresIsLeftAsItIs() {
        ActivationDocument stored = ActivationDocument.builder()
                .selected(MISSION_TYPE, List.of("research"))
                .selected(DIRECTIVE, List.of("D"))
                .deactivated(TACTIC, List.of("t"))
                .build()
                .document();

        StaleIds.Dropped dropped = StaleIds.drop(stored, CLOSURE);

        assertThat(dropped.dropped()).isEmpty();
        assertThat(dropped.document()).isEqualTo(stored);
    }

    @Test
    void b1APinNoPackOfTheClosureHoldsIsDroppedAndNamedAsItWasStored() {
        ClosureSnapshot closure = ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .pack("org", "2.0.0")
                .pack("team", "3.0.0")
                .held(DIRECTIVE, "D")
                .build();
        ActivationDocument stored = ActivationDocument.builder()
                .packs(List.of(PackRef.pinned("main", "0.9.0"), PackRef.pinned("org", "2.0.0"), PackRef.of("team")))
                .selected(DIRECTIVE, List.of("D"))
                .build()
                .document();

        StaleIds.Dropped dropped = StaleIds.drop(stored, closure);

        assertThat(dropped.droppedPins()).containsExactly(PackRef.pinned("main", "0.9.0"));
        assertThat(dropped.dropped()).isEmpty();
        assertThat(dropped.document().packs())
                .as("the pack stays, at the version the service holds; the other entries are as stored")
                .contains(List.of(PackRef.of("main"), PackRef.pinned("org", "2.0.0"), PackRef.of("team")));
        assertThat(dropped.document().selected()).isEqualTo(stored.selected());
    }

    @Test
    void aDocumentWhosePinsArePinnedToTheHeldVersionsDropsNoPin() {
        ActivationDocument stored = ActivationDocument.builder()
                .packs(List.of(PackRef.pinned("main", "1.0.0")))
                .build()
                .document();

        StaleIds.Dropped dropped = StaleIds.drop(stored, CLOSURE);

        assertThat(dropped.droppedPins()).isEmpty();
        assertThat(dropped.document()).isEqualTo(stored);
    }
}
