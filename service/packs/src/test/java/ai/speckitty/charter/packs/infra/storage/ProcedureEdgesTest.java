package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackEdge;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackInspector.Scope;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.Procedure;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A pack's relationships are the edges of its graph documents, and no artifact derives one from a field (ADR-0064).
 * The fixture pack holds a tactic and a procedure that both relate to the same directive, and one root graph shard that
 * holds those two edges: the edge port reads them back in document order, while every loaded artifact derives none, so
 * the edges can only come from the graph document.
 */
@DisplayName("A loaded pack's edges come from its graph document; no artifact derives one (ADR-0064)")
class ProcedureEdgesTest {

    private static final PackIdentifier PACK = new PackIdentifier("01M60H9BPR0CEDVREEDGES000A");

    @Test
    @DisplayName("the edge port answers the graph document's edges in order, and every artifact derives none")
    void theEdgesAreTheGraphDocumentsAndNoArtifactDerivesOne(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("procedure/edges-pack", host);
        PackLocation location = new PackLocation("edges-pack", PackLevel.ORG, pack);
        DomainRepositoryService repository =
                new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy());

        Pack loaded = repository.load(PACK);

        assertThat(loaded.violations()).as("every fixture file loads").isEmpty();
        assertThat(loaded.artifacts(ArtifactKind.DIRECTIVE))
                .as("the edge target exists")
                .hasSize(1);
        assertThat(repository.edges(new PackIdentifier("edges-pack")))
                .containsExactly(
                        new PackEdge("tactic:land-with-review", "directive:REVIEW_FIRST", RelationType.SUGGESTS),
                        new PackEdge("procedure:land-a-change", "directive:REVIEW_FIRST", RelationType.REQUIRES));
        assertThat(loaded.artifacts())
                .allSatisfy(artifact -> assertThat(artifact.derivedRelationships())
                        .as(artifact.urn().toString())
                        .isEmpty());
    }

    @Test
    @DisplayName("a procedure whose id is not its file stem is refused by the load and by validate mode alike")
    void aProcedureWhoseIdIsNotItsFileStemIsRefused(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("procedure/misnamed-pack", host);
        PackLocation location = new PackLocation("misnamed-pack", PackLevel.ORG, pack);

        Pack loaded = new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy())
                .load(new PackIdentifier("misnamed-pack"));

        assertThat(loaded.artifacts(ArtifactKind.PROCEDURE, Procedure.class))
                .extracting(procedure -> procedure.id().value())
                .containsExactly("land-a-change");
        assertThat(loaded.violations())
                .containsExactly(new PackElementViolation(
                        "ship-it.procedure.yaml", "schema", "procedure id 'land-it' is not its file stem 'ship-it'"));

        PackContents validated = new FileSystemPackInspector()
                .inspect(new PackRoot("misnamed-pack", Optional.of(PackLevel.ORG), pack.toString()), Scope.FULL);
        assertThat(validated.unreadableFiles()).extracting(FileRead::file).containsExactly("ship-it.procedure.yaml");
    }
}
