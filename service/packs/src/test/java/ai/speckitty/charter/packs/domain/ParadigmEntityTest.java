package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
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
    @DisplayName("relationships: suggests per typed reference, then requires a directive per directive_ref")
    void relationshipsFollowTheContract() {
        Paradigm paradigm = valid("ddd")
                .directiveRef("DIRECTIVE_003")
                .reference(new ParadigmReference("tactic", "tdd-red-green"))
                .build();
        Urn self = new Urn(NodeKind.PARADIGM, "ddd");

        assertThat(paradigm.kind()).isEqualTo(ArtifactKind.PARADIGM);
        assertThat(paradigm.label()).isEqualTo("Name of ddd");
        assertThat(paradigm.description()).isEqualTo(Optional.of("Summary of ddd"));
        assertThat(paradigm.relationships())
                .extracting(Relationship::from, Relationship::to, Relationship::type)
                .containsExactly(
                        tuple(self, new Urn(NodeKind.TACTIC, "tdd-red-green"), RelationType.SUGGESTS),
                        tuple(self, new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_003"), RelationType.REQUIRES));
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
