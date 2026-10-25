package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphWalkerContractTest {

    private static final Urn A = new Urn(NodeKind.TACTIC, "a");
    private static final Urn B = new Urn(NodeKind.TACTIC, "b");
    private static final DrgNode NODE = new DrgNode(A, Optional.empty());
    private static final Relationship RELATIONSHIP = Relationship.of(A, B, RelationType.REQUIRES);

    @Test
    void descentHasExactlyContinueSkipStop() {
        assertThat(Descent.values()).containsExactly(Descent.CONTINUE, Descent.SKIP, Descent.STOP);
    }

    @Test
    void aWalkerCanBeInvokedWithANodeAndARelationshipAndReturnsItsChosenDescent() {
        GraphWalker walker = new GraphWalker() {
            @Override
            public Descent onNode(DrgNode node) {
                return node.equals(NODE) ? Descent.CONTINUE : Descent.STOP;
            }

            @Override
            public Descent onRelationship(Relationship relationship) {
                return relationship.equals(RELATIONSHIP) ? Descent.SKIP : Descent.STOP;
            }
        };

        assertThat(walker.onNode(NODE)).isEqualTo(Descent.CONTINUE);
        assertThat(walker.onRelationship(RELATIONSHIP)).isEqualTo(Descent.SKIP);
    }
}
