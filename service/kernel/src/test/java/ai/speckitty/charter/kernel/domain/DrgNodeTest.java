package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DrgNodeTest {

    private static final Urn A = new Urn(NodeKind.ANTI_PATTERN, "a");

    @Test
    void aNodeWithOnlyALabelHasNoProvenanceTagsOrBodyPath() {
        DrgNode node = new DrgNode(A, Optional.of("A"));

        assertThat(node.label()).contains("A");
        assertThat(node.provenance()).isEmpty();
        assertThat(node.tags()).isEmpty();
        assertThat(node.bodyPath()).isEmpty();
    }

    @Test
    void aNodeKeepsEveryDeclaredFieldAndItsTagsInWrittenOrder() {
        DrgNode node =
                new DrgNode(A, Optional.of("A"), Optional.of("imported"), List.of("b", "a", "b"), Optional.of("a.md"));

        assertThat(node.provenance()).contains("imported");
        assertThat(node.tags()).containsExactly("b", "a", "b");
        assertThat(node.bodyPath()).contains("a.md");
    }

    @Test
    void tagsAreACopySoTheCallersListCannotChangeTheNode() {
        List<String> tags = new ArrayList<>(List.of("x"));
        DrgNode node = new DrgNode(A, Optional.empty(), Optional.empty(), tags, Optional.empty());

        tags.add("y");

        assertThat(node.tags()).containsExactly("x");
    }

    @Test
    void tagOrderIsPartOfTheNodesValue() {
        DrgNode ba = new DrgNode(A, Optional.empty(), Optional.empty(), List.of("b", "a"), Optional.empty());
        DrgNode ab = new DrgNode(A, Optional.empty(), Optional.empty(), List.of("a", "b"), Optional.empty());

        assertThat(ba).isNotEqualTo(ab);
        assertThat(ba)
                .isEqualTo(new DrgNode(A, Optional.empty(), Optional.empty(), List.of("b", "a"), Optional.empty()));
    }

    @Test
    void provenanceAndBodyPathArePartOfTheNodesValue() {
        DrgNode plain = new DrgNode(A, Optional.empty());

        assertThat(plain).isNotEqualTo(new DrgNode(A, Optional.empty(), Optional.of("p"), List.of(), Optional.empty()));
        assertThat(plain).isNotEqualTo(new DrgNode(A, Optional.empty(), Optional.empty(), List.of(), Optional.of("x")));
        assertThat(plain).isEqualTo(new DrgNode(A, Optional.empty(), Optional.empty(), List.of(), Optional.empty()));
    }
}
