package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.Collections;
import java.util.List;
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
    @DisplayName("a paradigm is a charter artifact that derives no relationship: its edges are graph edges (ADR-0064)")
    void spineAndNoDerivedRelationship() {
        Paradigm paradigm = valid("ddd").build();

        assertThat(paradigm.kind()).isEqualTo(ArtifactKind.PARADIGM);
        assertThat(paradigm.label()).isEqualTo("Name of ddd");
        assertThat(paradigm.description()).isEqualTo(Optional.of("Summary of ddd"));
        assertThat(paradigm.derivedRelationships()).isEmpty();
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

    // ---- principles (ADR-0067) ------------------------------------------------------------------------------

    private static Principle principle(String name) {
        return new Principle(name, "Why " + name + " matters.");
    }

    @Test
    @DisplayName("principles are optional and keep their authored order")
    void principlesOptionalAndOrdered() {
        assertThat(valid("ddd").build().principles()).isEmpty();

        Paradigm paradigm = valid("ddd")
                .principles(List.of(principle("Isolated"), principle("Fast"), principle("Deterministic")))
                .build();

        assertThat(paradigm.principles())
                .extracting(Principle::name)
                .containsExactly("Isolated", "Fast", "Deterministic");
    }

    @Test
    @DisplayName("the list has no count cap: a long list builds (ADR-0067 decision 4)")
    void noCountCap() {
        List<Principle> many = java.util.stream.IntStream.rangeClosed(1, 40)
                .mapToObj(i -> principle("P" + i))
                .toList();

        assertThat(valid("ddd").principles(many).build().principles()).hasSize(40);
    }

    @Test
    @DisplayName("a principle name repeated ignoring case and whitespace is refused; distinct names build")
    void uniqueNames() {
        assertThat(valid("ddd")
                        .principles(List.of(principle("Fast"), principle("Isolated")))
                        .build())
                .isNotNull();

        assertThatThrownBy(() -> valid("ddd")
                        .principles(List.of(principle("Fast"), principle(" fast ")))
                        .build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("unique");
        assertThatThrownBy(() -> valid("ddd")
                        .principles(List.of(principle("Fast"), principle("fast\u00A0")))
                        .build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("unique");
    }

    @Test
    @DisplayName("a blank or overlong name or description is refused; the caps themselves build")
    void lengthsAndBlanks() {
        String name64 = String.join("", Collections.nCopies(64, "n"));
        String desc255 = String.join("", Collections.nCopies(255, "d"));
        assertThat(valid("ddd")
                        .principles(List.of(new Principle(name64, desc255)))
                        .build())
                .isNotNull();
        assertThat(valid("ddd")
                        .principles(List.of(new Principle("\uD83D\uDE00".repeat(40), "d")))
                        .build())
                .as("the caps count code points, as the schema's maxLength does: 40 emoji are 80 chars")
                .isNotNull();

        for (Principle bad : List.of(
                new Principle(" ", "d"),
                new Principle("\u00A0", "d"),
                new Principle("n", " "),
                new Principle(name64 + "n", "d"),
                new Principle("n", desc255 + "d"))) {
            assertThatThrownBy(() -> valid("ddd").principles(List.of(bad)).build())
                    .isInstanceOf(ConstraintBreachException.class)
                    .hasMessageContaining("A principle needs a name");
        }
    }
}
