package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Pack aggregate")
class PackAggregateTest {

    private static final PackIdentifier ID = new PackIdentifier("01PACK");
    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            "01PACK", "1.0.0", Optional.empty(), "demo", Optional.empty(), Optional.empty(), List.of());

    private static Tactic tactic(String id, PackIdentifier owner) {
        return Tactic.builder()
                .id(new TacticIdentifier(id))
                .packId(owner)
                .schemaVersion("1.0")
                .name("n")
                .step(new TacticStep("s", Optional.empty(), List.of(), List.of()))
                .build();
    }

    @Test
    @DisplayName("the builder composes descriptor, tactics and read violations")
    void builderComposesTheAggregate() {
        var violation = new PackElementViolation("broken.tactic.yaml", "schema", "missing steps");
        Pack pack = Pack.builder()
                .id(ID)
                .descriptor(DESCRIPTOR)
                .tactic(tactic("t-1", ID))
                .violation(violation)
                .build();

        assertThat(pack.id()).isEqualTo(ID);
        assertThat(pack.descriptor()).isEqualTo(DESCRIPTOR);
        assertThat(pack.tactics()).extracting(Tactic::id).containsExactly(new TacticIdentifier("t-1"));
        assertThat(pack.violations()).containsExactly(violation);
    }

    @Test
    @DisplayName("packs with the same identity are equal whatever they hold; different identity is not")
    void equalityIsByIdentity() {
        Pack bare = Pack.builder().id(ID).descriptor(DESCRIPTOR).build();
        Pack full = Pack.builder()
                .id(ID)
                .descriptor(DESCRIPTOR)
                .tactic(tactic("t", ID))
                .build();
        Pack other = Pack.builder()
                .id(new PackIdentifier("02OTHER"))
                .descriptor(DESCRIPTOR)
                .build();

        assertThat(bare).isEqualTo(full).hasSameHashCodeAs(full);
        assertThat(bare).isNotEqualTo(other);
    }

    @Test
    @DisplayName("a tactic owned by another pack is rejected")
    void foreignTacticIsRejected() {
        var builder = Pack.builder().id(ID).descriptor(DESCRIPTOR).tactic(tactic("t", new PackIdentifier("other")));

        assertThatThrownBy(builder::build)
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("Every tactic of a pack must belong to that pack.");
    }

    @Test
    @DisplayName("two tactics with the same identifier are rejected")
    void duplicateTacticIdIsRejected() {
        var builder = Pack.builder()
                .id(ID)
                .descriptor(DESCRIPTOR)
                .tactic(tactic("t", ID))
                .tactic(tactic("t", ID));

        assertThatThrownBy(builder::build)
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("must not hold two tactics with the same identifier");
    }
}
