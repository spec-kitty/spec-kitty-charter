package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import ai.speckitty.charter.packs.infra.storage.rules.TreeRead;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The glossary_pack kind is replaced outright by the glossary kind (ADR-0049, FR-005, C-001): a pack file in the old
 * shape is refused by name, with the reason, never left unclassified and silently ignored. One canonical example pack
 * plus one old-shape file: the classification, validate mode, the store's load and the registration's staged load all
 * refuse the file, and the same pack without it is the positive control. The old shape appears here only because this
 * is a refusal test.
 */
@DisplayName("A pack file in the replaced glossary_pack shape is refused, not ignored (ADR-0049)")
class GlossaryOldShapeRefusalTest {

    private static final String FILE = "glossary_packs/x.glossary-pack.yaml";
    private static final String REASON =
            "the glossary_pack shape is replaced by glossaries/<id>.glossary.yaml (ADR-0049)";

    /** Core's old shape, as the example pack held it before ADR-0049. */
    private static final String OLD_SHAPE = """
            id: x
            provenance: built-in
            terms:
            - surface: lane
              definition: A sequence of work packages.
              confidence: 0.9
              status: active
            """;

    private Path pack;

    @BeforeEach
    void theBuiltInExamplePack(@TempDir Path host) {
        pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
    }

    private String name() {
        return String.valueOf(pack.getFileName());
    }

    private void plantTheOldShape() throws IOException {
        Files.createDirectories(pack.resolve(FILE).getParent());
        Files.writeString(pack.resolve(FILE), OLD_SHAPE, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the file kinds classify the old file as retired, with the reason, wherever the old rule read one")
    void theClassificationIsRetired() {
        for (String file : new String[] {FILE, "x.glossary-pack.yaml", "a/b/x.glossary-pack.yaml"}) {
            assertThat(PackFileKind.bundled().classify("qa/" + file))
                    .as(file)
                    .isEqualTo(new PackFileKind.Retired("**/*.glossary-pack.yaml", REASON));
        }
        assertThat(PackFileKind.bundled().classify("qa/glossaries/x.glossary.yaml"))
                .as("the new file kind is mapped")
                .isInstanceOf(PackFileKind.Mapped.class);
    }

    @Nested
    @DisplayName("control: the example pack in the new shape")
    class TheControl {

        @Test
        @DisplayName("loads with no violation, holds its glossary context, and registers")
        void theNewShapeLoadsAndRegisters() {
            WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

            WholePackStore.LoadedPack loaded = store.load(pack);

            assertThat(loaded.pack().violations()).isEmpty();
            assertThat(loaded.pack().artifacts(ArtifactKind.GLOSSARY))
                    .extracting(artifact -> artifact.localId())
                    .containsExactly("spec-kitty-core");
            assertThat(store.loadStaged(new StagedPack(pack)).pack().artifacts(ArtifactKind.GLOSSARY))
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("the old-shape file")
    class TheOldShape {

        @BeforeEach
        void planted() throws IOException {
            plantTheOldShape();
        }

        @Test
        @DisplayName("validate mode: an artifact_schema_error saying the file is retired and why")
        void validateModeRefusesIt() {
            TreeRead contents = new FileSystemPackInspector()
                    .readTree(new PackRoot(name(), Optional.of(PackLevel.BUILT_IN), pack.toString()));

            assertThat(contents.readFaults())
                    .extracting(fault -> fault.location().file())
                    .containsExactly(FILE);
            assertThat(contents.readFaults().stream().map(PackFinding::of))
                    .singleElement()
                    .satisfies(finding -> {
                        assertThat(finding.code()).isEqualTo(StorageCodes.RETIRED);
                        assertThat(finding.file()).isEqualTo(FILE);
                        assertThat(finding.detail()).isEqualTo(FILE + ": " + REASON);
                    });
        }

        @Test
        @DisplayName("the store's load: a retired violation naming the file and the reason, and no glossary from it")
        void theLoadRefusesIt() {
            WholePackStore.LoadedPack loaded = new WholePackStore(new YamlSerializationStrategy()).load(pack);

            assertThat(loaded.pack().violations())
                    .extracting(violation ->
                            violation.file() + " " + violation.kind().value() + " " + violation.reason())
                    .containsExactly(FILE + " retired " + REASON);
            assertThat(loaded.pack().artifacts(ArtifactKind.GLOSSARY))
                    .extracting(artifact -> artifact.localId())
                    .containsExactly("spec-kitty-core");
        }

        @Test
        @DisplayName("registration: 422 validation_failed holding the retired violation, naming the file")
        void registrationRefusesIt() {
            WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

            assertThatThrownBy(() -> store.loadStaged(new StagedPack(pack)))
                    .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                        assertThat(refusal.violations())
                                .containsExactly(new RefusalViolation(FILE, "retired", FILE + ": " + REASON));
                    });
        }

        @Test
        @DisplayName("the digest takes the file in its retired tail, and the census names it retired")
        void theDigestAndTheCensusCoverIt() {
            assertThat(PackFileSet.listing(pack).retired())
                    .containsOnlyKeys(FILE)
                    .containsEntry(FILE, REASON);
            assertThat(WholePackCensus.of(pack))
                    .containsEntry(FILE, WholePackCensus.RETIRED + "**/*.glossary-pack.yaml");
        }
    }
}
