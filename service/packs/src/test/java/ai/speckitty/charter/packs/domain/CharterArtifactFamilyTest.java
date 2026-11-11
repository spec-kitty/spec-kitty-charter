package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
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
            .build();

    private static final Styleguide STYLEGUIDE = Styleguide.builder()
            .id(new StyleguideIdentifier("family-style"))
            .packId(PACK)
            .schemaVersion("1.0")
            .title("Styleguide title")
            .scope(StyleguideScope.CODE)
            .principle("be small", "be small, in one sentence.")
            .build();

    private static final Paradigm PARADIGM = Paradigm.builder()
            .id(new ParadigmIdentifier("family-paradigm"))
            .packId(PACK)
            .schemaVersion("1.0")
            .name("Paradigm name")
            .summary("Paradigm summary")
            .build();

    private static final Tactic TACTIC = Tactic.builder()
            .id(new TacticIdentifier("family-tactic"))
            .packId(PACK)
            .schemaVersion("1.0")
            .name("Tactic name")
            .purpose("Tactic purpose")
            .step(new TacticStep("Only step", Optional.empty(), List.of()))
            .build();

    private static final List<CharterArtifact> FAMILY = List.of(DIRECTIVE, STYLEGUIDE, PARADIGM, TACTIC);

    private static final Asset ASSET = Asset.builder()
            .id(new AssetIdentifier("family-asset"))
            .packId(PACK)
            .mime("text/markdown")
            .path("family/asset.md")
            .title("Asset title")
            .build();

    private static final Template TEMPLATE = Template.builder()
            .id(new TemplateIdentifier("writing/family-template.md"))
            .packId(PACK)
            .content(Payload.of(new byte[] {'#'}))
            .build();

    /** The payload-bearing kinds: no schema version, no description, no relationships. */
    private static final List<CharterArtifact> PAYLOAD_KINDS = List.of(ASSET, TEMPLATE);

    @Test
    @DisplayName("every kind is a CharterArtifact of its own kind, in its pack, at schema 1.0")
    void everyKindIsOneUniformArtifact() {
        assertThat(FAMILY)
                .extracting(CharterArtifact::kind)
                .containsExactly(
                        ArtifactKind.DIRECTIVE, ArtifactKind.STYLEGUIDE, ArtifactKind.PARADIGM, ArtifactKind.TACTIC);
        assertThat(FAMILY).extracting(CharterArtifact::packId).containsOnly(PACK);
        assertThat(FAMILY).extracting(CharterArtifact::schemaVersion).containsOnly(Optional.of("1.0"));
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
    @DisplayName("no member of the family derives a relationship: relationships are graph edges (ADR-0064)")
    void noMemberDerivesARelationship() {
        assertThat(FAMILY)
                .allSatisfy(artifact -> assertThat(artifact.derivedRelationships())
                        .as(artifact.urn().toString())
                        .isEmpty());
        assertThat(FAMILY)
                .allSatisfy(artifact -> assertThat(artifact.writtenReferences())
                        .as(artifact.urn().toString())
                        .isEmpty());
    }

    @Test
    @DisplayName(
            "asset and template join the family through the same spine, with no schema version, description or edges")
    void payloadKindsAreUniformArtifacts() {
        assertThat(PAYLOAD_KINDS)
                .extracting(CharterArtifact::kind)
                .containsExactly(ArtifactKind.ASSET, ArtifactKind.TEMPLATE);
        assertThat(PAYLOAD_KINDS).extracting(CharterArtifact::packId).containsOnly(PACK);
        assertThat(PAYLOAD_KINDS).extracting(CharterArtifact::schemaVersion).containsOnly(Optional.empty());
        assertThat(PAYLOAD_KINDS).extracting(CharterArtifact::description).containsOnly(Optional.empty());
        assertThat(PAYLOAD_KINDS)
                .allSatisfy(
                        artifact -> assertThat(artifact.derivedRelationships()).isEmpty());
        assertThat(PAYLOAD_KINDS)
                .extracting(CharterArtifact::label)
                .containsExactly("Asset title", "family-template.md");
        assertThat(PAYLOAD_KINDS)
                .extracting(artifact -> artifact.urn().toString())
                .containsExactly("asset:family-asset", "template:writing/family-template.md");
    }
}
