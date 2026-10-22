package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.DrgEdge;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Relation;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CharterArtifactSpineTest {

    private static final class Sample implements CharterArtifact {
        @Override
        public ArtifactKind kind() {
            return ArtifactKind.PARADIGM;
        }

        @Override
        public PackIdentifier packId() {
            return new PackIdentifier("pack");
        }

        @Override
        public String localId() {
            return "domain-driven-design";
        }

        @Override
        public String schemaVersion() {
            return "1.0";
        }

        @Override
        public String label() {
            return "Domain-driven design";
        }

        @Override
        public Optional<String> description() {
            return Optional.empty();
        }

        @Override
        public List<DrgEdge> edges() {
            return List.of();
        }
    }

    @Test
    void urnIsDerivedFromKindAndLocalId() {
        assertThat(new Sample().urn()).isEqualTo(new Urn(NodeKind.PARADIGM, "domain-driven-design"));
    }

    @Test
    void enforcementTokensRoundTripIncludingTheHyphenatedOne() {
        for (Enforcement value : Enforcement.values()) {
            assertThat(Enforcement.fromToken(value.token())).contains(value);
        }
        assertThat(Enforcement.LENIENT_ADHERENCE.token()).isEqualTo("lenient-adherence");
        assertThat(Enforcement.fromToken("lenient_adherence")).isEmpty();
    }

    @Test
    void edgesUseTheInterimRelationsAndTargetKinds() {
        Urn owner = new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_001");

        List<DrgEdge> edges = ArtifactEdges.build(
                owner,
                List.of(new ArtifactEdges.TypedRef("tactic", "tdd-red-green", Optional.of("always"), Optional.of("why"))),
                NodeKind.PROCEDURE,
                List.of("release"));
        List<DrgEdge> paradigmEdges = ArtifactEdges.build(
                new Urn(NodeKind.PARADIGM, "ddd"), List.of(), NodeKind.DIRECTIVE, List.of("DIRECTIVE_003"));

        assertThat(edges)
                .containsExactly(
                        new DrgEdge(
                                owner,
                                new Urn(NodeKind.TACTIC, "tdd-red-green"),
                                Relation.SUGGESTS,
                                Optional.empty(),
                                Optional.of("always"),
                                Optional.of("why")),
                        DrgEdge.of(owner, new Urn(NodeKind.PROCEDURE, "release"), Relation.REQUIRES));
        assertThat(paradigmEdges)
                .containsExactly(DrgEdge.of(
                        new Urn(NodeKind.PARADIGM, "ddd"), new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_003"), Relation.REQUIRES));
    }
}
