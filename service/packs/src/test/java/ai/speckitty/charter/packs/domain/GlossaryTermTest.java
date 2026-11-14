package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A term of a glossary context (ADR-0049 decisions 3 and 4): each rule is refused beside the valid fixture, which builds
 * as its positive control.
 */
@DisplayName("Glossary term")
class GlossaryTermTest {

    private static final Urn LANE = Urn.parse("term:spec-kitty-core/lane").orElseThrow();

    private static GlossaryTerm term(String name, String definition, List<Urn> seeAlso) {
        return new GlossaryTerm(
                new TermIdentifier("mission"),
                name,
                definition,
                List.of("Run a mission."),
                List.of("Mission"),
                List.of("project", "feature"),
                TermStatus.ACTIVE,
                seeAlso);
    }

    @Test
    @DisplayName("a term keeps every field as given, its lists in order")
    void keepsItsFields() {
        GlossaryTerm term = term("mission", "A unit of work.", List.of(LANE));

        assertThat(term.id().value()).isEqualTo("mission");
        assertThat(term.name()).isEqualTo("mission");
        assertThat(term.definition()).isEqualTo("A unit of work.");
        assertThat(term.examples()).containsExactly("Run a mission.");
        assertThat(term.aliases()).containsExactly("Mission");
        assertThat(term.avoid()).containsExactly("project", "feature");
        assertThat(term.status()).isEqualTo(TermStatus.ACTIVE);
        assertThat(term.seeAlso()).containsExactly(LANE);
    }

    @Test
    @DisplayName("a term's address is term:<context-id>/<term-id>, a URN of the node kind term")
    void itsAddressNamesItsContext() {
        Urn urn = term("mission", "d", List.of()).urn(new GlossaryContextIdentifier("spec-kitty-core"));

        assertThat(urn.kind()).isEqualTo(NodeKind.TERM);
        assertThat(urn).hasToString("term:spec-kitty-core/mission");
        assertThat(Urn.parse(urn.toString())).contains(urn);
        assertThat(GlossaryTerm.termUrn(new GlossaryContextIdentifier("qa"), new TermIdentifier("lane")))
                .hasToString("term:qa/lane");
    }

    @Test
    @DisplayName("the lists are copies of what was given")
    void theListsAreCopies() {
        List<Urn> seeAlso = new ArrayList<>(List.of(LANE));
        GlossaryTerm term = term("mission", "d", seeAlso);
        seeAlso.clear();

        assertThat(term.seeAlso()).containsExactly(LANE);
        assertThatThrownBy(() -> term.avoid().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> term.examples().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> term.aliases().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("a blank name is refused; the fixture's name builds")
    void aBlankNameIsRefused(String name) {
        assertThatThrownBy(() -> term(name, "d", List.of()))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("a name that is not blank");
        assertThat(term("mission", "d", List.of()).name()).isEqualTo("mission");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n"})
    @DisplayName("a blank definition is refused; the fixture's definition builds")
    void aBlankDefinitionIsRefused(String definition) {
        assertThatThrownBy(() -> term("mission", definition, List.of()))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("a definition that is not blank");
        assertThat(term("mission", "d", List.of()).definition()).isEqualTo("d");
    }

    @Test
    @DisplayName("a see_also entry that is not a term's URN is refused; a term URN is accepted")
    void aSeeAlsoThatIsNoTermIsRefused() {
        Urn context = Urn.parse("glossary_context:spec-kitty-core").orElseThrow();

        assertThatThrownBy(() -> term("mission", "d", List.of(LANE, context)))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("see_also names terms only");
        assertThat(term("mission", "d", List.of(LANE)).seeAlso()).containsExactly(LANE);
    }

    @Test
    @DisplayName("a missing id, name, definition or status is a programming error")
    void missingRequiredFieldsThrow() {
        TermIdentifier id = new TermIdentifier("mission");
        assertThatThrownBy(() ->
                        new GlossaryTerm(null, "n", "d", List.of(), List.of(), List.of(), TermStatus.ACTIVE, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id");
        assertThatThrownBy(() ->
                        new GlossaryTerm(id, null, "d", List.of(), List.of(), List.of(), TermStatus.ACTIVE, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name");
        assertThatThrownBy(() ->
                        new GlossaryTerm(id, "n", null, List.of(), List.of(), List.of(), TermStatus.ACTIVE, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("definition");
        assertThatThrownBy(() -> new GlossaryTerm(id, "n", "d", List.of(), List.of(), List.of(), null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mission", "a--b", "4-eyes", "lane-", "", " ", "a/b", "terms_x"})
    @DisplayName("a term id off the letter-first slug grammar is refused; mission is accepted")
    void aTermIdOffTheGrammarIsRefused(String id) {
        assertThatThrownBy(() -> new TermIdentifier(id))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("term identifier");
        assertThat(new TermIdentifier("mission").value()).isEqualTo("mission");
    }

    @Test
    @DisplayName("a term id of 64 characters is accepted, one of 65 refused")
    void aTermIdIsAtMostSixtyFourCharacters() {
        assertThat(new TermIdentifier("a".repeat(64)).value()).hasSize(64);
        assertThatThrownBy(() -> new TermIdentifier("a".repeat(65))).isInstanceOf(ConstraintBreachException.class);
    }

    @ParameterizedTest
    @EnumSource(TermStatus.class)
    @DisplayName("each status reads back from its token")
    void eachStatusReadsBackFromItsToken(TermStatus status) {
        assertThat(TermStatus.fromToken(status.token())).isEqualTo(status);
    }

    @Test
    @DisplayName("the statuses are draft, active and deprecated; any other token is refused")
    void theStatusesAreTheThreeOfAdr0053() {
        assertThat(TermStatus.values()).extracting(TermStatus::token).containsExactly("draft", "active", "deprecated");
        assertThatThrownBy(() -> TermStatus.fromToken("Active"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown term status 'Active'");
    }

    @Test
    @DisplayName("applies_to copies its lists, and states nothing only when every list is empty")
    void appliesToCopiesItsLists() {
        List<String> paths = new ArrayList<>(List.of("src/**"));
        AppliesTo appliesTo = new AppliesTo(paths, List.of(), List.of());
        paths.clear();

        assertThat(appliesTo.paths()).containsExactly("src/**");
        assertThat(appliesTo.isEmpty()).isFalse();
        assertThat(new AppliesTo(List.of(), List.of("java"), List.of()).isEmpty())
                .isFalse();
        assertThat(new AppliesTo(List.of(), List.of(), List.of("spring")).isEmpty())
                .isFalse();
        assertThat(new AppliesTo(List.of(), List.of(), List.of()).isEmpty()).isTrue();
    }
}
