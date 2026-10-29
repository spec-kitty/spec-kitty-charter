package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.Procedure;
import ai.speckitty.charter.packs.domain.ProcedureReference;
import ai.speckitty.charter.packs.domain.Tactic;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A procedure's references are kept but never minted as relationships (research D3, #189). The fixture pack holds a
 * tactic and a procedure whose references point at the same directive in the same pack: the tactic is the positive
 * control that the store does mint a typed reference as {@code SUGGESTS}, so the procedure's empty relationships can
 * only come from the procedure not minting, not from a loader that mints nothing.
 */
@DisplayName("A loaded procedure keeps its references and mints no relationship; a loaded tactic mints its (#194)")
class ProcedureEdgesTest {

    private static final PackIdentifier PACK = new PackIdentifier("01M60H9BPROCEDUREEDGES000A");
    private static final Urn TACTIC = new Urn(NodeKind.of(ArtifactKind.TACTIC), "land-with-review");
    private static final Urn PROCEDURE = new Urn(NodeKind.of(ArtifactKind.PROCEDURE), "land-a-change");
    private static final Urn DIRECTIVE = new Urn(NodeKind.of(ArtifactKind.DIRECTIVE), "REVIEW_FIRST");

    @Test
    @DisplayName("in one pack, the tactic mints SUGGESTS to the directive while the procedure holds the same "
            + "reference and mints none")
    void theTacticMintsAndTheProcedureDoesNot(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("procedure/edges-pack", host);
        PackLocation location = new PackLocation("edges-pack", PackLevel.ORG, pack);

        Pack loaded = new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy()).load(PACK);

        assertThat(loaded.violations()).as("every fixture file loads").isEmpty();
        assertThat(loaded.artifacts(ArtifactKind.DIRECTIVE))
                .as("the reference target exists")
                .hasSize(1);

        // Positive control: the store mints the tactic's typed reference.
        Tactic tactic = loaded.artifacts(ArtifactKind.TACTIC, Tactic.class).getFirst();
        assertThat(tactic.relationships())
                .containsExactly(new Relationship(
                        TACTIC, DIRECTIVE, RelationType.SUGGESTS, Optional.of("Before landing."), Optional.empty()));

        // The procedure: same reference, kept on the entity, never minted.
        Procedure procedure =
                loaded.artifacts(ArtifactKind.PROCEDURE, Procedure.class).getFirst();
        assertThat(procedure.references())
                .containsExactly(new ProcedureReference(
                        ArtifactKind.DIRECTIVE, "REVIEW_FIRST", Optional.of("Before landing."), Optional.empty()));
        assertThat(procedure.relationships()).isEmpty();
        assertThat(loaded.artifacts().stream()
                        .flatMap(artifact -> artifact.relationships().stream())
                        .filter(relationship -> relationship.from().equals(PROCEDURE)
                                || relationship.to().equals(PROCEDURE)))
                .as("no artifact of the pack holds a relationship of the procedure")
                .isEmpty();
    }
}
