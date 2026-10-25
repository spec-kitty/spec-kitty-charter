package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SC-003: the four doctrine kinds are one uniform {@link CharterArtifact} family. Each kind is built once and read
 * only through the spine, so a kind that drifts from the contract (wrong label source, wrong description source,
 * wrong URN kind, wrong edge shape) fails here. Every shape assertion is anchored to the values the fixture was
 * built from, so none can pass vacuously.
 */
@DisplayName("CharterArtifact family")
class CharterArtifactFamilyTest {

    private static final PackIdentifier PACK = new PackIdentifier("01FAMILYPACK");

    private static final Directive DIRECTIVE = Directive.builder()
            .id(new DirectiveIdentifier("DIRECTIVE_700"))
            .packId(PACK)
            .schemaVersion("1.0")
            .title("Directive title")
            .intent("Directive intent")
            .enforcement(Enforcement.REQUIRED)
            .procedure("release")
            .reference(new DirectiveReference("tactic", "tdd", Optional.of("always"), Optional.of("why")))
            .build();

    private static final Styleguide STYLEGUIDE = Styleguide.builder()
            .id(new StyleguideIdentifier("family-style"))
            .packId(PACK)
            .schemaVersion("1.0")
            .title("Styleguide title")
            .scope(StyleguideScope.CODE)
            .principle("be small")
            .reference("cited-elsewhere")
            .build();

    private static final Paradigm PARADIGM = Paradigm.builder()
            .id(new ParadigmIdentifier("family-paradigm"))
            .packId(PACK)
            .schemaVersion("1.0")
            .name("Paradigm name")
            .summary("Paradigm summary")
            .directiveRef("DIRECTIVE_700")
            .reference(new ParadigmReference("tactic", "tdd"))
            .build();

    private static final Tactic TACTIC = Tactic.builder()
            .id(new TacticIdentifier("family-tactic"))
            .packId(PACK)
            .schemaVersion("1.0")
            .name("Tactic name")
            .purpose("Tactic purpose")
            .step(new TacticStep("Only step", Optional.empty(), List.of(), List.of()))
            .reference(new TacticReference("ref", "directive", "DIRECTIVE_700", "when ready"))
            .build();

    private static final List<CharterArtifact> FAMILY = List.of(DIRECTIVE, STYLEGUIDE, PARADIGM, TACTIC);

    @Test
    @DisplayName("every kind is a CharterArtifact of its own kind, in its pack, at schema 1.0")
    void everyKindIsOneUniformArtifact() {
        assertThat(FAMILY)
                .extracting(CharterArtifact::kind)
                .containsExactly(
                        ArtifactKind.DIRECTIVE, ArtifactKind.STYLEGUIDE, ArtifactKind.PARADIGM, ArtifactKind.TACTIC);
        assertThat(FAMILY).extracting(CharterArtifact::packId).containsOnly(PACK);
        assertThat(FAMILY).extracting(CharterArtifact::schemaVersion).containsOnly("1.0");
        assertThat(FAMILY)
                .extracting(CharterArtifact::localId)
                .containsExactly("DIRECTIVE_700", "family-style", "family-paradigm", "family-tactic");
    }

    @Test
    @DisplayName("label is the title for directive and styleguide, the name for paradigm and tactic")
    void labelComesFromTheKindsHumanName() {
        assertThat(FAMILY)
                .extracting(CharterArtifact::label)
                .containsExactly("Directive title", "Styleguide title", "Paradigm name", "Tactic name");
    }

    @Test
    @DisplayName("description is intent, none, summary, purpose; the styleguide has none")
    void descriptionComesFromTheKindsSummaryField() {
        assertThat(DIRECTIVE.description()).contains("Directive intent");
        assertThat(STYLEGUIDE.description()).isEmpty();
        assertThat(PARADIGM.description()).contains("Paradigm summary");
        assertThat(TACTIC.description()).contains("Tactic purpose");
    }

    @Test
    @DisplayName("the URN is <kind>:<id> with the kind's own token")
    void urnIsKindColonId() {
        assertThat(FAMILY)
                .extracting(artifact -> artifact.urn().toString())
                .containsExactly(
                        "directive:DIRECTIVE_700",
                        "styleguide:family-style",
                        "paradigm:family-paradigm",
                        "tactic:family-tactic");
    }

    @Test
    @DisplayName(
            "relationships follow the contract: the styleguide mints none, the others a suggests per typed ref and a requires per bare ref")
    void relationshipsFollowTheContract() {
        assertThat(STYLEGUIDE.relationships()).isEmpty();

        assertThat(DIRECTIVE.relationships())
                .extracting(Relationship::from, Relationship::to, Relationship::type)
                .containsExactly(
                        tuple(DIRECTIVE.urn(), new Urn(NodeKind.TACTIC, "tdd"), RelationType.SUGGESTS),
                        tuple(DIRECTIVE.urn(), new Urn(NodeKind.PROCEDURE, "release"), RelationType.REQUIRES));
        assertThat(DIRECTIVE.relationships().get(0).when()).contains("always");
        assertThat(DIRECTIVE.relationships().get(0).rationale()).contains("why");

        assertThat(PARADIGM.relationships())
                .extracting(Relationship::from, Relationship::to, Relationship::type)
                .containsExactly(
                        tuple(PARADIGM.urn(), new Urn(NodeKind.TACTIC, "tdd"), RelationType.SUGGESTS),
                        tuple(PARADIGM.urn(), new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_700"), RelationType.REQUIRES));

        assertThat(TACTIC.relationships())
                .extracting(Relationship::from, Relationship::to, Relationship::type)
                .containsExactly(
                        tuple(TACTIC.urn(), new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_700"), RelationType.SUGGESTS));
        assertThat(TACTIC.relationships().get(0).when()).contains("when ready");
    }
}
