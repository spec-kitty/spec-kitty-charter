package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Directive entity")
class DirectiveEntityTest {

    private static final PackIdentifier PACK = new PackIdentifier("01PACK");

    private static Directive.Builder valid(String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Title of " + id)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED);
    }

    @Test
    @DisplayName("the builder yields a valid entity exposing the common spine")
    void builderBuildsValidEntity() {
        Directive directive = valid("DIRECTIVE_001").scope("all code").build();

        assertThat(directive.kind()).isEqualTo(ArtifactKind.DIRECTIVE);
        assertThat(directive.localId()).isEqualTo("DIRECTIVE_001");
        assertThat(directive.label()).isEqualTo("Title of DIRECTIVE_001");
        assertThat(directive.description()).contains("Intent of DIRECTIVE_001");
        assertThat(directive.scope()).contains("all code");
    }

    @Test
    @DisplayName("lenient adherence demands an explicit allowance; with one it passes")
    void lenientAdherenceNeedsAllowance() {
        Directive.Builder lenient = valid("DIRECTIVE_002").enforcement(Enforcement.LENIENT_ADHERENCE);

        assertThatThrownBy(lenient::build).isInstanceOf(ConstraintBreachException.class);
        assertThat(lenient.explicitAllowance("spikes may skip it").build().explicitAllowances())
                .containsExactly("spikes may skip it");
    }

    @Test
    @DisplayName("same identity with different fields is equal")
    void identityEquality() {
        Directive one = valid("DIRECTIVE_004").title("one").build();
        Directive other = valid("DIRECTIVE_004").title("other").build();

        assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
        assertThat(one).isNotEqualTo(valid("DIRECTIVE_005").title("one").build());
    }

    @Test
    @DisplayName("a directive derives no relationship: its procedures are reached through graph edges (ADR-0064)")
    void aDirectiveDerivesNoRelationship() {
        Directive directive = valid("DIRECTIVE_006").build();

        assertThat(directive.derivedRelationships()).isEmpty();
    }
}
