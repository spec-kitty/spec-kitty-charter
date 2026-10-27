package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Glossary pack entity")
class GlossaryPackTest {

    private static final PackIdentifier PACK = new PackIdentifier("01PACK");

    private static GlossaryTerm term(String surface) {
        return new GlossaryTerm(
                surface,
                "Definition of " + surface,
                0.9,
                "active",
                List.of(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of());
    }

    private static GlossaryPack.Builder valid(String id) {
        return GlossaryPack.builder()
                .id(new GlossaryPackIdentifier(id))
                .packId(PACK)
                .provenance("org")
                .term(term("event"));
    }

    @Test
    @DisplayName("a glossary pack builds, keeps its terms in order and is addressed as glossary_pack:<id>")
    void buildsAndKeepsOrder() {
        GlossaryPack glossary = valid("evd-glossary")
                .description(Optional.of("Vocabulary."))
                .term(term("aggregate"))
                .build();

        assertThat(glossary.kind()).isEqualTo(ArtifactKind.GLOSSARY_PACK);
        assertThat(glossary.localId()).isEqualTo("evd-glossary");
        assertThat(glossary.label()).isEqualTo("evd-glossary");
        assertThat(glossary.urn().toString()).isEqualTo("glossary_pack:evd-glossary");
        assertThat(glossary.schemaVersion()).isEmpty();
        assertThat(glossary.description()).contains("Vocabulary.");
        assertThat(glossary.terms()).extracting(GlossaryTerm::surface).containsExactly("event", "aggregate");
        assertThat(glossary.relationships()).isEmpty();
    }

    @Test
    @DisplayName("a glossary pack with no terms is refused")
    void noTermsIsRefused() {
        assertThatThrownBy(() -> GlossaryPack.builder()
                        .id(new GlossaryPackIdentifier("g"))
                        .packId(PACK)
                        .provenance("org")
                        .build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("at least one term");
    }

    @Test
    @DisplayName("a repeated surface is refused, naming the surface")
    void aRepeatedSurfaceIsRefused() {
        assertThatThrownBy(() -> valid("g")
                        .term(term("zeta"))
                        .term(term("event"))
                        .term(term("zeta"))
                        .build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("glossary pack g")
                .hasMessageContaining("[event, zeta]");
    }

    @Test
    @DisplayName("a blank surface or status, a blank id and a non-finite confidence are refused, each for its reason")
    void blanksAreRefused() {
        assertThatThrownBy(() -> term(" "))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("a surface that is not blank");
        assertThatThrownBy(() -> new GlossaryTerm(
                        "s", "d", 0.9, "", List.of(), Optional.empty(), List.of(), List.of(), List.of()))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("a status that is not blank");
        assertThatThrownBy(() -> new GlossaryTerm(
                        "s", "d", Double.NaN, "active", List.of(), Optional.empty(), List.of(), List.of(), List.of()))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("finite number");
        assertThatThrownBy(() -> new GlossaryPackIdentifier(" "))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("an empty provenance is kept: the schema and core accept it, so the domain does not refuse it")
    void anEmptyProvenanceIsKept() {
        assertThat(valid("g").provenance("").build().provenance()).isEmpty();
    }

    @Test
    @DisplayName("two glossary packs are equal by identity, whatever their terms")
    void identityEquality() {
        GlossaryPack one = valid("g").build();
        GlossaryPack other = valid("g").term(term("aggregate")).build();

        assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
        assertThat(one).isNotEqualTo(valid("h").build());
    }
}
