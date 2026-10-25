package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-003: a component's {@code relationships()} are the ones it is the <em>effective origin</em> of — every
 * relationship whose {@code from} is itself, plus every {@link ai.speckitty.charter.kernel.domain.Directionality#BIDIRECTIONAL}
 * relationship whose {@code to} is itself; a purely directed relationship with the component as {@code to} is not
 * returned, so a walk never climbs back up a {@code requires}. The interim relation mapping is kept: typed references
 * mint {@code suggests}, bare references mint {@code requires}, and a styleguide mints none. Every assertion is
 * anchored to a same-fixture positive control, so none can pass vacuously.
 */
@DisplayName("Artifact relationships")
class ArtifactRelationshipsTest {

    private static final PackIdentifier PACK = new PackIdentifier("01RELPACK");

    @Test
    @DisplayName("a directive mints a suggests to each typed ref, then a requires to each procedure, all from itself")
    void directiveMintsSuggestsThenRequiresFromItself() {
        Directive directive = Directive.builder()
                .id(new DirectiveIdentifier("DIRECTIVE_010"))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Title")
                .intent("Intent")
                .enforcement(Enforcement.REQUIRED)
                .reference(new DirectiveReference("tactic", "tdd", Optional.of("when green"), Optional.of("why")))
                .procedure("release")
                .build();

        Urn self = directive.urn();

        assertThat(directive.relationships())
                .extracting(Relationship::from, Relationship::to, Relationship::type)
                .containsExactly(
                        tuple(self, new Urn(NodeKind.TACTIC, "tdd"), RelationType.SUGGESTS),
                        tuple(self, new Urn(NodeKind.PROCEDURE, "release"), RelationType.REQUIRES));
        // the authored `when` of the typed ref round-trips onto the suggests relationship
        assertThat(directive.relationships().get(0).when()).contains("when green");
        assertThat(directive.relationships().get(0).rationale()).contains("why");
    }

    @Test
    @DisplayName("a paradigm's directive refs mint a requires to directive:<id>, its typed refs a suggests")
    void paradigmMintsRequiresToDirectiveAndSuggestsToTyped() {
        Paradigm paradigm = Paradigm.builder()
                .id(new ParadigmIdentifier("tdd"))
                .packId(PACK)
                .schemaVersion("1.0")
                .name("Name")
                .summary("Summary")
                .reference(new ParadigmReference("tactic", "red-green"))
                .directiveRef("DIRECTIVE_010")
                .build();

        Urn self = paradigm.urn();

        assertThat(paradigm.relationships())
                .extracting(Relationship::from, Relationship::to, Relationship::type)
                .containsExactly(
                        tuple(self, new Urn(NodeKind.TACTIC, "red-green"), RelationType.SUGGESTS),
                        tuple(self, new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_010"), RelationType.REQUIRES));
    }

    @Test
    @DisplayName("a styleguide mints no relationships: its references are bare citations in the body")
    void styleguideMintsNoRelationships() {
        Styleguide styleguide = Styleguide.builder()
                .id(new StyleguideIdentifier("java-style"))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Java style")
                .scope(StyleguideScope.CODE)
                .principle("be small")
                .reference("Clean Code ch.3")
                .build();

        assertThat(styleguide.relationships()).isEmpty();
    }

    @Test
    @DisplayName(
            "effective origin returns from-self and a bidirectional-to-self relationship, but never a directed-to-self one")
    void effectiveOriginKeepsFromSelfAndBidirectionalToSelfButNotDirectedToSelf() {
        Urn self = new Urn(NodeKind.DIRECTIVE, "self");
        Urn otherA = new Urn(NodeKind.TACTIC, "a");
        Urn otherB = new Urn(NodeKind.TACTIC, "b");
        Urn otherC = new Urn(NodeKind.TACTIC, "c");

        Relationship fromSelf = Relationship.of(self, otherC, RelationType.SUGGESTS);
        // IMPACTS is the one BIDIRECTIONAL relation; authored otherA -> self, self is still an effective origin.
        Relationship bidirectionalToSelf = Relationship.of(otherA, self, RelationType.IMPACTS);
        // REQUIRES is DIRECTED; authored otherB -> self, self must NOT climb back up it.
        Relationship directedToSelf = Relationship.of(otherB, self, RelationType.REQUIRES);

        List<Relationship> effective =
                ArtifactEdges.effectiveOrigin(self, List.of(fromSelf, bidirectionalToSelf, directedToSelf));

        assertThat(effective).containsExactlyInAnyOrder(fromSelf, bidirectionalToSelf);
        assertThat(effective).doesNotContain(directedToSelf);
    }
}
