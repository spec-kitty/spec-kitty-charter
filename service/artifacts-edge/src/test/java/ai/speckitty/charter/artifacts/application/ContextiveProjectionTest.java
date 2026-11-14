package ai.speckitty.charter.artifacts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.artifacts.application.ContextiveProjection.Context;
import ai.speckitty.charter.artifacts.application.ContextiveProjection.Document;
import ai.speckitty.charter.artifacts.application.ContextiveProjection.Meta;
import ai.speckitty.charter.artifacts.application.ContextiveProjection.Term;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The Contextive document (ADR-0076 decision 6): each context in force by id, with its name, its description as the
 * domain vision statement and its paths, and its terms by term id, each with its name, definition, examples, aliases
 * and {@code meta}; an optional member the source lacks is empty.
 */
class ContextiveProjectionTest {

    private static final PackIdentifier CORE = new PackIdentifier("core");

    private static GlossaryView context(String id, List<String> paths) {
        return new GlossaryView(
                id,
                CORE,
                "1.0",
                "Name of " + id,
                "Where " + id + " applies.",
                List.of("tag"),
                paths,
                List.of("markdown"),
                List.of("spec-kitty"),
                List.of());
    }

    private static VocabularyTerm term(String contextId, String id, List<String> examples, List<String> aliases) {
        return new VocabularyTerm(
                "term:" + contextId + "/" + id,
                contextId,
                id,
                "Name of " + id,
                "The definition of " + id + ".",
                "deprecated",
                examples,
                aliases,
                List.of("avoided"),
                List.of("term:" + contextId + "/other"),
                CORE,
                PackLevel.BUILT_IN);
    }

    @Test
    void eachContextAndTermMapsItsFieldsAndTheTermsMetaNamesItsUrnAndStatus() {
        Document document = ContextiveProjection.of(
                List.of(term("core", "lane", List.of("A lane runs."), List.of("track", "swimlane"))),
                Map.of("core", context("core", List.of("kitty-specs/**", "docs/**"))));

        assertThat(document)
                .isEqualTo(new Document(List.of(new Context(
                        "Name of core",
                        "Where core applies.",
                        Optional.of(List.of("kitty-specs/**", "docs/**")),
                        List.of(new Term(
                                "Name of lane",
                                "The definition of lane.",
                                Optional.of(List.of("A lane runs.")),
                                Optional.of(List.of("track", "swimlane")),
                                new Meta("term:core/lane", "deprecated")))))));
    }

    @Test
    void anOptionalMemberTheSourceLacksIsOmitted() {
        Document document = ContextiveProjection.of(
                List.of(term("core", "lane", List.of(), List.of())), Map.of("core", context("core", List.of())));

        Context context = document.contexts().getFirst();
        assertThat(context.paths()).isEmpty();
        assertThat(context.terms().getFirst().examples()).isEmpty();
        assertThat(context.terms().getFirst().aliases()).isEmpty();
    }

    @Test
    void theContextsAreByIdAndTheirTermsByTermId() {
        Document document = ContextiveProjection.of(
                List.of(
                        term("zeta", "b", List.of(), List.of()),
                        term("alpha", "mission", List.of(), List.of()),
                        term("zeta", "a", List.of(), List.of()),
                        term("alpha", "lane", List.of(), List.of())),
                Map.of("zeta", context("zeta", List.of()), "alpha", context("alpha", List.of())));

        assertThat(document.contexts()).extracting(Context::name).containsExactly("Name of alpha", "Name of zeta");
        assertThat(document.contexts().getFirst().terms())
                .extracting(found -> found.meta().urn())
                .containsExactly("term:alpha/lane", "term:alpha/mission");
        assertThat(document.contexts().getLast().terms())
                .extracting(found -> found.meta().urn())
                .containsExactly("term:zeta/a", "term:zeta/b");
    }

    @Test
    void noTermsAndNoContextsIsAnEmptyDocument() {
        assertThat(ContextiveProjection.of(List.of(), Map.of())).isEqualTo(new Document(List.of()));
    }

    @Test
    void aTermOfAContextNotInForceIsABrokenInvariant() {
        assertThatThrownBy(() -> ContextiveProjection.of(
                        List.of(term("ghost", "lane", List.of(), List.of())),
                        Map.of("core", context("core", List.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The term term:ghost/lane names no context in force.");
    }
}
