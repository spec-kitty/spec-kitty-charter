package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Pack aggregate")
class PackAggregateTest {

    private static final PackIdentifier ID = new PackIdentifier("01PACK");
    private static final PackDescriptor DESCRIPTOR =
            new PackDescriptor("01PACK", "1.0.0", Optional.empty(), "demo", Optional.empty(), Optional.empty());

    private static Tactic tactic(String id, PackIdentifier owner) {
        return Tactic.builder()
                .id(new TacticIdentifier(id))
                .packId(owner)
                .schemaVersion("1.0")
                .name("n")
                .step(new TacticStep("s", Optional.empty(), List.of(), List.of()))
                .build();
    }

    private static Toolguide toolguide(String id, PackIdentifier owner) {
        return Toolguide.builder()
                .id(new ToolguideIdentifier(id))
                .packId(owner)
                .schemaVersion("1.0")
                .tool("t")
                .title("T")
                .guide(Guide.of("How to use the tool."))
                .summary("s")
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
    @DisplayName("violations are carried by the aggregate and do not affect its tactics or identity")
    void violationsLiveOnTheAggregateOnly() {
        var violation = new PackElementViolation("broken.tactic.yaml", "schema", "missing steps");
        Pack clean = Pack.builder().id(ID).descriptor(DESCRIPTOR).build();
        Pack partial = Pack.builder()
                .id(ID)
                .descriptor(DESCRIPTOR)
                .violation(violation)
                .build();

        assertThat(clean.violations()).isEmpty();
        assertThat(partial.violations()).containsExactly(violation);
        assertThat(partial.tactics()).isEmpty();
        assertThat(partial).isEqualTo(clean);
        assertThatThrownBy(() -> partial.violations().add(violation)).isInstanceOf(UnsupportedOperationException.class);
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

    @Test
    @DisplayName("a pack holding toolguides validates and exposes them as an immutable list (FR-015)")
    void toolguidesAreHeldAndImmutable() {
        Pack pack = Pack.builder()
                .id(ID)
                .descriptor(DESCRIPTOR)
                .toolguide(toolguide("tg-1", ID))
                .toolguide(toolguide("tg-2", ID))
                .build();

        assertThat(pack.toolguides()).extracting(tg -> tg.id().value()).containsExactly("tg-1", "tg-2");
        assertThatThrownBy(() -> pack.toolguides().add(toolguide("tg-3", ID)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("a toolguide owned by another pack is rejected; one of this pack is accepted (FR-008)")
    void foreignToolguideIsRejectedWithAValidControl() {
        assertThatCode(() -> Pack.builder()
                        .id(ID)
                        .descriptor(DESCRIPTOR)
                        .toolguide(toolguide("tg-1", ID))
                        .build())
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> Pack.builder()
                        .id(ID)
                        .descriptor(DESCRIPTOR)
                        .toolguide(toolguide("tg-1", new PackIdentifier("other")))
                        .build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("Every toolguide of a pack must belong to that pack.");
    }

    @Test
    @DisplayName("two toolguides with the same identifier are rejected; distinct ids are accepted (FR-008)")
    void duplicateToolguideIdIsRejectedWithAValidControl() {
        assertThatCode(() -> Pack.builder()
                        .id(ID)
                        .descriptor(DESCRIPTOR)
                        .toolguide(toolguide("tg-1", ID))
                        .toolguide(toolguide("tg-2", ID))
                        .build())
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> Pack.builder()
                        .id(ID)
                        .descriptor(DESCRIPTOR)
                        .toolguide(toolguide("tg-1", ID))
                        .toolguide(toolguide("tg-1", ID))
                        .build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("must not hold two toolguides with the same identifier");
    }
}
