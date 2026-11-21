package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.MutabilityCheck.Operation;
import ai.speckitty.charter.packs.domain.MutabilityCheck.Write;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The whole-pack registration's edge diff (ADR-0045 decision 5, ruling G1): a tension and a structural relation on one
 * pair are two edges of the diff, and the gate judges a changed tension on each side.
 */
class PackRegistrationEdgeDiffTest {

    private static final Urn A = new Urn(NodeKind.DIRECTIVE, "A");
    private static final Urn B = new Urn(NodeKind.DIRECTIVE, "B");
    private static final Relationship REQUIRES = Relationship.of(A, B, RelationType.REQUIRES);
    private static final Relationship TENSION = Relationship.of(A, B, RelationType.TENSION);

    private static List<Relationship> changed(List<Relationship> upload, List<Relationship> before) {
        return PackRegistrationService.changedEdges(
                PackRegistrationService.keyed(upload), PackRegistrationService.keyed(before));
    }

    @Test
    void aShardHoldingARequiresAndATensionOnOnePairDiffsThemAsSeparateEdges() {
        assertThat(changed(List.of(REQUIRES, TENSION), List.of(REQUIRES))).containsExactly(TENSION);
        assertThat(changed(List.of(REQUIRES, TENSION), List.of(TENSION))).containsExactly(REQUIRES);
        assertThat(changed(List.of(TENSION), List.of(REQUIRES))).containsExactly(TENSION, REQUIRES);
        assertThat(changed(List.of(REQUIRES, TENSION), List.of())).containsExactly(REQUIRES, TENSION);
        // Positive control: the same shard diffs as nothing.
        assertThat(changed(List.of(REQUIRES, TENSION), List.of(TENSION, REQUIRES)))
                .isEmpty();
    }

    @Test
    void aChangedTensionIsJudgedOnEachSideAndAStructuralEdgeOnItsSourceOnly() {
        assertThat(PackRegistrationService.writesOn(TENSION))
                .containsExactly(new Write(Operation.RELATIONSHIP, A), new Write(Operation.RELATIONSHIP, B));
        assertThat(PackRegistrationService.writesOn(REQUIRES)).containsExactly(new Write(Operation.RELATIONSHIP, A));
    }
}
