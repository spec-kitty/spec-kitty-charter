package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Pack aggregate keyed by kind (ADR-0037)")
class PackKindMapTest {

    private static final PackIdentifier ID = new PackIdentifier("01PACK");
    private static final PackDescriptor DESCRIPTOR =
            new PackDescriptor("01PACK", "1.0.0", Optional.empty(), "demo", Optional.empty(), Optional.empty());

    /** A kind the aggregate does not name anywhere: it must be held all the same. */
    private record Procedure(PackIdentifier packId, String localId) implements CharterArtifact {
        @Override
        public ArtifactKind kind() {
            return ArtifactKind.PROCEDURE;
        }

        @Override
        public Optional<String> schemaVersion() {
            return Optional.empty();
        }

        @Override
        public String label() {
            return localId;
        }

        @Override
        public Optional<String> description() {
            return Optional.empty();
        }

        @Override
        public List<Relationship> relationships() {
            return List.of();
        }
    }

    private static Tactic tactic(String id, String name) {
        return Tactic.builder()
                .id(new TacticIdentifier(id))
                .packId(ID)
                .schemaVersion("1.0")
                .name(name)
                .step(new TacticStep("s", Optional.empty(), List.of(), List.of()))
                .build();
    }

    private static Paradigm paradigm(String id) {
        return Paradigm.builder()
                .id(new ParadigmIdentifier(id))
                .packId(ID)
                .schemaVersion("1.0")
                .name("n")
                .summary("s")
                .build();
    }

    @Test
    @DisplayName("artifacts are grouped by kind, and a kind the aggregate never names is held like the others")
    void artifactsAreGroupedByKind() {
        Procedure procedure = new Procedure(ID, "landing");
        Pack pack = Pack.builder()
                .id(ID)
                .descriptor(DESCRIPTOR)
                .artifact(tactic("t-1", "n"))
                .artifact(procedure)
                .paradigm(paradigm("p-1"))
                .build();

        assertThat(pack.artifacts(ArtifactKind.PROCEDURE)).containsExactly(procedure);
        assertThat(pack.artifacts(ArtifactKind.TACTIC, Tactic.class))
                .extracting(Tactic::localId)
                .containsExactly("t-1");
        assertThat(pack.paradigms()).extracting(Paradigm::localId).containsExactly("p-1");
        assertThat(pack.artifacts(ArtifactKind.ASSET)).isEmpty();
        assertThat(pack.artifacts()).hasSize(3);
    }

    @Test
    @DisplayName("two kinds may share a local id; one kind may not")
    void uniquenessIsPerKind() {
        assertThat(Pack.builder()
                        .id(ID)
                        .descriptor(DESCRIPTOR)
                        .artifact(tactic("same", "n"))
                        .artifact(new Procedure(ID, "same"))
                        .build()
                        .artifacts())
                .hasSize(2);
        assertThatThrownBy(() -> Pack.builder()
                        .id(ID)
                        .descriptor(DESCRIPTOR)
                        .artifact(new Procedure(ID, "same"))
                        .artifact(new Procedure(ID, "same"))
                        .build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A pack must not hold two procedures with the same identifier.");
    }

    @Test
    @DisplayName("an artifact of any kind from another pack is refused")
    void foreignArtifactOfAnyKindIsRefused() {
        assertThatThrownBy(() -> Pack.builder()
                        .id(ID)
                        .descriptor(DESCRIPTOR)
                        .artifact(new Procedure(new PackIdentifier("02OTHER"), "x"))
                        .build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("Every procedure of a pack must belong to that pack.");
    }

    @Test
    @DisplayName("with adds an artifact and keeps every other kind and violation")
    void withAddsAndKeepsEverythingElse() {
        Procedure procedure = new Procedure(ID, "landing");
        var violation = new PackElementViolation("broken.tactic.yaml", "schema", "missing steps");
        Pack current = Pack.builder()
                .id(ID)
                .descriptor(DESCRIPTOR)
                .artifact(procedure)
                .paradigm(paradigm("p-1"))
                .violation(violation)
                .build();

        Pack mutated = current.with(tactic("t-1", "n"));

        assertThat(mutated.artifacts(ArtifactKind.PROCEDURE)).containsExactly(procedure);
        assertThat(mutated.paradigms()).hasSize(1);
        assertThat(mutated.tactics()).extracting(Tactic::localId).containsExactly("t-1");
        assertThat(mutated.violations()).containsExactly(violation);
        assertThat(current.tactics()).isEmpty();
    }

    @Test
    @DisplayName("with replaces the artifact of the same kind and id")
    void withReplacesTheSameKindAndId() {
        Pack current = Pack.builder()
                .id(ID)
                .descriptor(DESCRIPTOR)
                .artifact(tactic("t-1", "old"))
                .artifact(tactic("t-2", "kept"))
                .build();

        Pack mutated = current.with(tactic("t-1", "new"));

        assertThat(mutated.tactics()).extracting(Tactic::name).containsExactly("kept", "new");
    }

    @Test
    @DisplayName("with revalidates: a foreign artifact is refused")
    void withRevalidates() {
        Pack current = Pack.builder().id(ID).descriptor(DESCRIPTOR).build();

        assertThatThrownBy(() -> current.with(new Procedure(new PackIdentifier("02OTHER"), "x")))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("toBuilder holds everything the pack holds")
    void toBuilderHoldsEverything() {
        Pack current = Pack.builder()
                .id(ID)
                .descriptor(DESCRIPTOR)
                .artifact(new Procedure(ID, "landing"))
                .violation(new PackElementViolation("broken.tactic.yaml", "schema", "missing steps"))
                .build();

        Pack copy = current.toBuilder().build();

        assertThat(copy.artifacts()).isEqualTo(current.artifacts());
        assertThat(copy.violations()).isEqualTo(current.violations());
    }
}
