package ai.speckitty.charter.activations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The node reference of the contract: an authored kind and an id, ordered by kind token and id. */
class NodeRefTest {

    @Test
    void aStepIsANodeOfKindActionWhoseIdHoldsTheSlash() {
        Optional<NodeRef> step = NodeRef.of(Urn.action("software-dev", "plan"));

        assertThat(step).contains(new NodeRef(NodeKind.ACTION, "software-dev/plan"));
        assertThat(step.orElseThrow().urn()).isEqualTo("action:software-dev/plan");
    }

    @Test
    void anArtifactIsTheNodeOfItsKind() {
        assertThat(NodeRef.of(new ArtifactRef(ArtifactKind.TACTIC, "tdd")))
                .isEqualTo(new NodeRef(NodeKind.TACTIC, "tdd"));
    }

    @Test
    void aGuardAndALifecycleAreNoNodeOfAReference() {
        Urn guard = new Urn(NodeKind.GUARD, "subtasks-complete");

        assertThat(NodeRef.of(guard)).isEmpty();
        assertThat(NodeRef.of(new Urn(NodeKind.LIFECYCLE, "work-package"))).isEmpty();
        assertThatThrownBy(() -> new NodeRef(NodeKind.GUARD, "subtasks-complete"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nodesAreOrderedByKindTokenThenIdWhateverTheOrderTheyWereGivenIn() {
        NodeRef action = new NodeRef(NodeKind.ACTION, "m/s");
        NodeRef directiveB = new NodeRef(NodeKind.DIRECTIVE, "B");
        NodeRef directiveA = new NodeRef(NodeKind.DIRECTIVE, "A");

        assertThat(List.of(directiveB, action, directiveA).stream().sorted().toList())
                .containsExactly(action, directiveA, directiveB);
        assertThat(List.of(directiveA, directiveB, action).stream().sorted().toList())
                .containsExactly(action, directiveA, directiveB);
    }
}
