package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link PackAssembly} builds the aggregate from what the store read and judges nothing (#554, T023): it places each
 * artifact at the file it was read from, and the files it is told to withhold it leaves out without a fault of its own.
 */
@DisplayName("PackAssembly builds the aggregate from the read and judges nothing (#554, T023)")
class PackAssemblyTest {

    private static final String TYPE_FILE = "missions/software-dev/software-dev.mission-type.yaml";
    private static final String ENHANCEMENT_FILE =
            "missions/software-dev/enhancements/plan-risk-notes.step-enhancement.yaml";

    @Test
    @DisplayName("every artifact the read holds is in the aggregate, at the file it was read from, with no violation")
    void theAggregateHoldsEveryArtifactAtItsFile(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        FilesystemPackStore.PackRead read = read(pack);
        Map<Urn, String> places = new HashMap<>();

        Pack built = PackAssembly.assembly(id(read), read, places).build();

        assertThat(built.violations()).isEmpty();
        assertThat(built.artifacts()).isNotEmpty();
        assertThat(places).hasSameSizeAs(built.artifacts());
        for (CharterArtifact artifact : built.artifacts()) {
            assertThat(places.get(artifact.urn()))
                    .as("%s is placed at the file it was read from", artifact.urn())
                    .isNotNull()
                    .satisfies(file -> assertThat(pack.resolve(file)).isRegularFile());
        }
        assertThat(places).containsEntry(new Urn(NodeKind.MISSION_TYPE, "software-dev"), TYPE_FILE);
    }

    @Test
    @DisplayName("a withheld step enhancement and mission type are left out, and the assembly records no fault for"
            + " them: which files they are is the rules' judgement, not the assembly's")
    void theWithheldFilesAreLeftOutWithoutAFault(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        FilesystemPackStore.PackRead read = read(pack);
        Map<Urn, String> places = new HashMap<>();

        Pack built = PackAssembly.assembly(id(read), read, places, Set.of(ENHANCEMENT_FILE), Set.of(TYPE_FILE))
                .build();

        assertThat(built.violations()).isEmpty();
        assertThat(places.values()).doesNotContain(ENHANCEMENT_FILE, TYPE_FILE);
        assertThat(built.artifacts(ArtifactKind.MISSION_TYPE, CharterArtifact.class))
                .isEmpty();
        assertThat(built.artifacts())
                .hasSize(PackAssembly.assembly(id(read), read, new HashMap<>())
                                .build()
                                .artifacts()
                                .size()
                        - 2);
    }

    private static FilesystemPackStore.PackRead read(Path pack) {
        return new FilesystemPackStore(pack.getParent(), new YamlSerializationStrategy()).read(PackDirectory.of(pack));
    }

    private static PackIdentifier id(FilesystemPackStore.PackRead read) {
        return new PackIdentifier(read.descriptor().packId());
    }
}
