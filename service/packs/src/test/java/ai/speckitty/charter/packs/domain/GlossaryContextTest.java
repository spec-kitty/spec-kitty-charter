package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The glossary context entity (ADR-0049 decisions 1 to 3): each rule is refused on the same valid fixture, and {@link
 * #positiveControlTheValidFixtureBuilds} is the positive control that the fixture itself builds.
 */
@DisplayName("Glossary context entity")
class GlossaryContextTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack");

    static GlossaryTerm term(String id) {
        return new GlossaryTerm(
                new TermIdentifier(id),
                id,
                "Definition of " + id,
                List.of(),
                List.of(),
                List.of(),
                TermStatus.ACTIVE,
                List.of());
    }

    /** The valid fixture: one term, every required field. */
    private static GlossaryContext.Builder valid(String id) {
        return GlossaryContext.builder()
                .id(new GlossaryContextIdentifier(id))
                .packId(PACK)
                .name("Spec Kitty core")
                .description("The core vocabulary.")
                .term(term("mission"));
    }

    @Test
    @DisplayName("a context builds, keeps its terms in order and is addressed glossary_context:<id>")
    void buildsAndKeepsOrder() {
        GlossaryContext context = valid("spec-kitty-core")
                .tags(List.of("core", "workflow"))
                .appliesTo(new AppliesTo(List.of("src/**"), List.of("java"), List.of()))
                .term(term("lane"))
                .build();

        assertThat(context.kind()).isEqualTo(ArtifactKind.GLOSSARY);
        assertThat(context.id()).isEqualTo(new GlossaryContextIdentifier("spec-kitty-core"));
        assertThat(context.packId()).isEqualTo(PACK);
        assertThat(context.localId()).isEqualTo("spec-kitty-core");
        assertThat(context.label()).isEqualTo("Spec Kitty core");
        assertThat(context.name()).isEqualTo("Spec Kitty core");
        assertThat(context.urn().kind()).isEqualTo(NodeKind.GLOSSARY_CONTEXT);
        assertThat(context.urn()).hasToString("glossary_context:spec-kitty-core");
        assertThat(context.schemaVersion()).contains("1.0");
        assertThat(context.description()).contains("The core vocabulary.");
        assertThat(context.tags()).containsExactly("core", "workflow");
        assertThat(context.appliesTo().paths()).containsExactly("src/**");
        assertThat(context.appliesTo().languages()).containsExactly("java");
        assertThat(context.terms()).extracting(t -> t.id().value()).containsExactly("mission", "lane");
        assertThat(context.derivedRelationships())
                .as("no term names another, so the context derives nothing")
                .isEmpty();
        assertThat(context.writtenReferences()).isEmpty();
        assertThat(context).hasToString("GlossaryContext[spec-kitty-core of pack pack]");
    }

    private static GlossaryTerm seeing(String id, String... seeAlso) {
        return new GlossaryTerm(
                new TermIdentifier(id),
                id,
                "Definition of " + id,
                List.of(),
                List.of(),
                List.of(),
                TermStatus.ACTIVE,
                Stream.of(seeAlso).map(PackQualifiedReference::parse).toList());
    }

    @Test
    @DisplayName("each distinct see_also entry derives one vocabulary relationship from the context, in term order")
    void itsTermsSeeAlsoDeriveVocabularyRelationships() {
        GlossaryContext context = GlossaryContext.builder()
                .id(new GlossaryContextIdentifier("core"))
                .packId(PACK)
                .name("Core")
                .description("d")
                .term(seeing("lane", "term:core/mission", "base::term:g/lane"))
                .term(seeing("mission", "term:core/lane", "term:core/mission"))
                .term(seeing("wp", "base::term:g/lane"))
                .build();

        assertThat(context.derivedRelationships())
                .extracting(
                        edge -> edge.from().toString(),
                        Relationship::type,
                        edge -> edge.toReference().toString())
                .containsExactly(
                        tuple("glossary_context:core", RelationType.VOCABULARY, "term:core/mission"),
                        tuple("glossary_context:core", RelationType.VOCABULARY, "base::term:g/lane"),
                        tuple("glossary_context:core", RelationType.VOCABULARY, "term:core/lane"));
        assertThat(context.derivedRelationships())
                .allSatisfy(edge -> assertThat(edge.fromPack()).isEmpty());
        assertThat(context.writtenReferences())
                .extracting(PackQualifiedReference::toString)
                .as("the compose's reference check reads what it writes")
                .containsExactly("term:core/mission", "base::term:g/lane", "term:core/lane");
    }

    @Test
    @DisplayName("a context that states no tags and no applies_to holds empty ones")
    void absentListsAreEmpty() {
        GlossaryContext context = valid("g").build();

        assertThat(context.tags()).isEmpty();
        assertThat(context.appliesTo()).isEqualTo(AppliesTo.NOWHERE_IN_PARTICULAR);
        assertThat(context.appliesTo().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("a term is found by its id; an id the context does not hold finds none")
    void aTermIsFoundByItsId() {
        GlossaryContext context = valid("g").term(term("lane")).build();

        assertThat(context.term(new TermIdentifier("lane"))).contains(term("lane"));
        assertThat(context.term(new TermIdentifier("ghost"))).isEmpty();
    }

    @Test
    @DisplayName("it holds a term of an id it holds, and no term of an id it lacks or one outside the term grammar")
    void itHoldsTheTermsItHoldsByTheirIds() {
        GlossaryContext context = valid("g").term(term("lane")).build();

        assertThat(context.holdsTerm("lane")).isTrue();
        assertThat(context.holdsTerm("ghost")).isFalse();
        assertThat(context.holdsTerm("Lane"))
                .as("outside the grammar, not a fault")
                .isFalse();
        assertThat(context.holdsTerm("")).isFalse();
    }

    @Test
    @DisplayName("the lists are copies: a change to the builder's list does not reach the context")
    void theListsAreImmutable() {
        java.util.ArrayList<String> tags = new java.util.ArrayList<>(List.of("core"));
        GlossaryContext context = valid("g").tags(tags).build();
        tags.add("late");

        assertThat(context.tags()).containsExactly("core");
        assertThatThrownBy(() -> context.terms().add(term("x"))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("a context with no terms is refused; the same context with one term builds")
    void noTermsIsRefused() {
        assertThatThrownBy(() -> GlossaryContext.builder()
                        .id(new GlossaryContextIdentifier("g"))
                        .packId(PACK)
                        .name("G")
                        .description("D")
                        .build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("at least one term");
        assertThat(valid("g").build().terms()).hasSize(1);
    }

    @Test
    @DisplayName("a repeated term id is refused, naming the context and every repeated id")
    void aRepeatedTermIdIsRefused() {
        assertThatThrownBy(() -> valid("g")
                        .term(term("zeta"))
                        .term(term("mission"))
                        .term(term("zeta"))
                        .build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("unique within glossary context g")
                .hasMessageContaining("[mission, zeta]");
        assertThat(valid("g").term(term("zeta")).build().terms()).hasSize(2);
    }

    @Test
    @DisplayName("two terms that share a name but not an id are two terms (ADR-0049 decision 12)")
    void aSharedNameIsNoConflict() {
        GlossaryTerm other = new GlossaryTerm(
                new TermIdentifier("mission-run"),
                "mission",
                "Another sense.",
                List.of(),
                List.of(),
                List.of(),
                TermStatus.DRAFT,
                List.of());

        assertThat(valid("g").term(other).build().terms()).hasSize(2);
    }

    @Test
    @DisplayName("positive control: the valid fixture every refusal below starts from builds")
    void positiveControlTheValidFixtureBuilds() {
        GlossaryContext context = valid("g").build();

        assertThat(context.name()).isEqualTo("Spec Kitty core");
        assertThat(context.description()).contains("The core vocabulary.");
        assertThat(context.tags()).isEmpty();
        assertThat(context.terms()).containsExactly(term("mission"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("a blank name is refused (positive control: positiveControlTheValidFixtureBuilds)")
    void aBlankNameIsRefused(String name) {
        assertThatThrownBy(() -> valid("g").name(name).build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("a name that is not blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n"})
    @DisplayName("a blank description is refused (positive control: positiveControlTheValidFixtureBuilds)")
    void aBlankDescriptionIsRefused(String description) {
        assertThatThrownBy(() -> valid("g").description(description).build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("a description that is not blank");
    }

    @Test
    @DisplayName("a blank tag is refused; the same tags without it build")
    void aBlankTagIsRefused() {
        assertThatThrownBy(() -> valid("g").tags(List.of("core", " ")).build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("tags are not blank");
        assertThat(valid("g").tags(List.of("core")).build().tags()).containsExactly("core");
    }

    @Test
    @DisplayName("a repeated tag is refused; the same tags named once build")
    void aRepeatedTagIsRefused() {
        assertThatThrownBy(() ->
                        valid("g").tags(List.of("core", "workflow", "core")).build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("each tag once");
        assertThat(valid("g").tags(List.of("core", "workflow")).build().tags()).hasSize(2);
    }

    @Test
    @DisplayName("a missing identity, pack, name or description is a programming error")
    void missingRequiredFieldsThrow() {
        assertThatThrownBy(() -> valid("g").name(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name");
        assertThatThrownBy(() -> valid("g").description(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("description");
        assertThatThrownBy(() -> valid("g").id(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id");
        assertThatThrownBy(() -> valid("g").packId(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("packId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Core", "a--b", "4-eyes", "core-", "", "core_terms"})
    @DisplayName("a context id off the letter-first slug grammar is refused; spec-kitty-core is accepted")
    void aContextIdOffTheGrammarIsRefused(String id) {
        assertThatThrownBy(() -> new GlossaryContextIdentifier(id))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("glossary context identifier");
        assertThat(new GlossaryContextIdentifier("spec-kitty-core").value()).isEqualTo("spec-kitty-core");
    }

    @Test
    @DisplayName("two contexts are equal by identity, whatever their terms")
    void identityEquality() {
        GlossaryContext one = valid("g").build();
        GlossaryContext other = valid("g").term(term("aggregate")).build();

        assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
        assertThat(one.equals(one)).as("reflexive").isTrue();
        assertThat(one).isNotEqualTo(valid("h").build());
        assertThat(one).isNotEqualTo("g");
    }
}
