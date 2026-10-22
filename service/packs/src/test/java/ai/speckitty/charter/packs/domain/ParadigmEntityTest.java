package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.DrgEdge;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Relation;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Paradigm entity")
class ParadigmEntityTest {

    private static final PackIdentifier PACK = new PackIdentifier("01PACK");

    private static Paradigm.Builder valid(String id) {
        return Paradigm.builder()
                .id(new ParadigmIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .name("Name of " + id)
                .summary("Summary of " + id);
    }

    @Test
    @DisplayName("a blank summary is rejected while the same fixture with a summary builds")
    void blankSummaryRejected() {
        assertThat(valid("ddd").build().summary()).isEqualTo("Summary of ddd");

        assertThatThrownBy(() -> valid("ddd").summary(" ").build()).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a blank directive ref is rejected while a named one builds")
    void blankDirectiveRefRejected() {
        assertThat(valid("ddd").directiveRef("DIRECTIVE_003").build().directiveRefs())
                .containsExactly("DIRECTIVE_003");

        assertThatThrownBy(() -> valid("ddd").directiveRef("").build()).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("edges: REQUIRES a directive per directive_ref, SUGGESTS per typed reference")
    void edgesFollowTheContract() {
        Paradigm paradigm = valid("ddd")
                .directiveRef("DIRECTIVE_003")
                .reference(new ParadigmReference("tactic", "tdd-red-green"))
                .build();
        Urn self = new Urn(NodeKind.PARADIGM, "ddd");

        assertThat(paradigm.kind()).isEqualTo(ArtifactKind.PARADIGM);
        assertThat(paradigm.label()).isEqualTo("Name of ddd");
        assertThat(paradigm.description()).isEqualTo(Optional.of("Summary of ddd"));
        assertThat(paradigm.edges())
                .containsExactly(
                        new DrgEdge(
                                self,
                                new Urn(NodeKind.TACTIC, "tdd-red-green"),
                                Relation.SUGGESTS,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty()),
                        DrgEdge.of(self, new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_003"), Relation.REQUIRES));
    }

    @Test
    @DisplayName("equality is by identity, not by other fields")
    void identityEquality() {
        Paradigm one = valid("ddd").build();

        assertThat(one)
                .isEqualTo(valid("ddd").name("Other").build())
                .hasSameHashCodeAs(valid("ddd").build());
        assertThat(one).isNotEqualTo(valid("tdd").build());
    }
}
