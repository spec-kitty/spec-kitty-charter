package ai.speckitty.charter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CharterOfferingTest {

    private static final Urn A = new Urn(NodeKind.DIRECTIVE, "A");
    private static final Urn B = new Urn(NodeKind.TACTIC, "B");

    private static CharterPack pack(String name, PackLevel level, Urn node) {
        return new CharterPack(name, level, Optional.empty(),
                new DoctrineGraph(List.of(new DrgNode(node, Optional.empty())), List.of()), List.of());
    }

    @Test
    void ordersLayersBuiltInOrgProjectAndKeepsConfigurationOrderWithin() {
        CharterOffering offering = new CharterOffering(List.of(
                pack("p", PackLevel.PROJECT, A), pack("o2", PackLevel.ORG, A), pack("b", PackLevel.BUILT_IN, B),
                pack("o1", PackLevel.ORG, B)));
        assertThat(offering.packs()).extracting(CharterPack::name).containsExactly("b", "o2", "o1", "p");
    }

    @Test
    void mergeKeepsTheFirstDeclarationOfANode() {
        DoctrineGraph first = new DoctrineGraph(List.of(new DrgNode(A, Optional.of("first"))), List.of());
        DoctrineGraph second = new DoctrineGraph(List.of(new DrgNode(A, Optional.of("second")), new DrgNode(B, Optional.empty())),
                List.of(DrgEdge.of(A, B, Relation.SCOPE)));
        DoctrineGraph merged = first.merge(second);
        assertThat(merged.node(A).flatMap(DrgNode::label)).contains("first");
        assertThat(merged.outgoing(A, Relation.SCOPE)).hasSize(1);
        assertThat(merged.outgoing(A, Relation.REQUIRES)).isEmpty();
    }

    @Test
    void aDescriptorDeclaringAnotherLevelIsRefused() {
        PackDescriptor builtIn = new PackDescriptor("id", "1.0.0", Optional.empty(), "x", PackLevel.BUILT_IN);
        assertThatThrownBy(() -> new CharterPack("x", PackLevel.ORG, Optional.of(builtIn), DoctrineGraph.empty(), List.of()))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_LEVEL_MISMATCH));
    }
}
