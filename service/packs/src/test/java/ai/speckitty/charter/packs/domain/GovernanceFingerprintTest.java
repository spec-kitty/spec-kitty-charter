package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.DoctrineGraph;
import ai.speckitty.charter.kernel.domain.DrgEdge;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.Relation;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GovernanceFingerprintTest {

    private static final Urn A = new Urn(NodeKind.DIRECTIVE, "A");
    private static final Urn B = new Urn(NodeKind.TACTIC, "B");
    private static final Urn C = new Urn(NodeKind.TACTIC, "C");

    private static final List<DrgNode> NODES = List.of(
            new DrgNode(A, Optional.of("a")), new DrgNode(B, Optional.empty()), new DrgNode(C, Optional.empty()));
    private static final List<DrgEdge> EDGES =
            List.of(DrgEdge.of(A, B, Relation.SCOPE), DrgEdge.impacts(B, C, -0.5), DrgEdge.of(A, C, Relation.SCOPE));

    private static CharterOffering offering(String version, List<DrgNode> nodes, List<DrgEdge> edges) {
        PackDescriptor descriptor = new PackDescriptor(
                "01PACK", version, Optional.empty(), "b", Optional.of(PackLevel.BUILT_IN), Optional.empty(), List.of());
        return new CharterOffering(List.of(new CharterPack(
                "b", PackLevel.BUILT_IN, Optional.of(descriptor), new DoctrineGraph(nodes, edges), List.of())));
    }

    @Test
    void isASha256DigestThatIsStableAcrossLoads() {
        String fingerprint = offering("1.0.0", NODES, EDGES).governanceFingerprint();
        assertThat(fingerprint).matches("sha256:[0-9a-f]{64}");
        assertThat(offering("1.0.0", NODES, EDGES).governanceFingerprint()).isEqualTo(fingerprint);
    }

    @Test
    void doesNotDependOnTheOrderNodesAndEdgesAreDeclaredIn() {
        assertThat(offering("1.0.0", NODES.reversed(), EDGES.reversed()).governanceFingerprint())
                .isEqualTo(offering("1.0.0", NODES, EDGES).governanceFingerprint());
    }

    @Test
    void changesWithAnEdgeStrengthALabelOrAPackVersion() {
        String fingerprint = offering("1.0.0", NODES, EDGES).governanceFingerprint();
        assertThat(offering("1.0.1", NODES, EDGES).governanceFingerprint()).isNotEqualTo(fingerprint);
        assertThat(offering(
                                "1.0.0",
                                NODES,
                                List.of(
                                        DrgEdge.of(A, B, Relation.SCOPE),
                                        DrgEdge.impacts(B, C, -0.75),
                                        DrgEdge.of(A, C, Relation.SCOPE)))
                        .governanceFingerprint())
                .isNotEqualTo(fingerprint);
        assertThat(offering(
                                "1.0.0",
                                List.of(
                                        new DrgNode(A, Optional.of("")),
                                        new DrgNode(B, Optional.empty()),
                                        new DrgNode(C, Optional.empty())),
                                EDGES)
                        .governanceFingerprint())
                .as("an empty label is not an absent one")
                .isNotEqualTo(fingerprint);
    }
}
