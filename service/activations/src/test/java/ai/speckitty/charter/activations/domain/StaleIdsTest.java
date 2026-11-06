package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.MISSION_TYPE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ADR-0059 amendment R5 (operator ruling of 2026-11-06): a write drops from the stored document every id its closure no
 * longer declares, in {@code selected}, {@code deactivated} and {@code missionTypes}, and names each one.
 */
class StaleIdsTest {

    private static final ClosureSnapshot CLOSURE = ClosureSnapshots.snapshot()
            .pack("main", "1.0.0")
            .held(DIRECTIVE, "D")
            .held(TACTIC, "t")
            .missionTypes("research")
            .build();

    @Test
    void everyIdTheClosureNoLongerDeclaresIsDroppedAndNamed() {
        ActivationDocument stored = ActivationDocument.builder()
                .packs(List.of(PackRef.pinned("main", "1.0.0")))
                .activatedKinds(List.of(DIRECTIVE))
                .missionTypes(List.of("research", "gone-type"))
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
        assertThat(dropped.document().missionTypes().orElseThrow()).containsExactly("research");
        assertThat(dropped.document().deactivated())
                .as("a deny list the drop empties is left out")
                .isEmpty();
        assertThat(dropped.document().packs()).isEqualTo(stored.packs());
        assertThat(dropped.document().activatedKinds()).isEqualTo(stored.activatedKinds());
    }

    @Test
    void anAllowListTheDropEmptiesStaysAsAnEmptyList() {
        ActivationDocument stored = ActivationDocument.builder()
                .selected(DIRECTIVE, List.of("GONE"))
                .build()
                .document();

        StaleIds.Dropped dropped = StaleIds.drop(stored, CLOSURE);

        assertThat(dropped.document().selected(DIRECTIVE))
                .hasValueSatisfying(ids -> assertThat(ids).isEmpty());
    }

    @Test
    void aDocumentTheClosureStillDeclaresIsLeftAsItIs() {
        ActivationDocument stored = ActivationDocument.builder()
                .missionTypes(List.of("research"))
                .selected(DIRECTIVE, List.of("D"))
                .deactivated(TACTIC, List.of("t"))
                .build()
                .document();

        StaleIds.Dropped dropped = StaleIds.drop(stored, CLOSURE);

        assertThat(dropped.dropped()).isEmpty();
        assertThat(dropped.document()).isEqualTo(stored);
    }
}
