package ai.speckitty.charter.packs.infra.storage.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.ClassifiedFile;
import ai.speckitty.charter.packs.domain.NotHeldFile;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.PlantedPacks;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The tree census (#554, S3, FR-016): its invariants, and what it holds over the planted packs, through the domain port
 * whose readout carries it (S6). WP11's narrowed files (a nested shard, a {@code charter.yaml}) are not held, a retired
 * file is never on the not-held list, and an excluded file is listed with its rule both as classified and as not held.
 */
@DisplayName("TreeCensus: the one owner of held (#554, S3)")
class TreeCensusTest {

    private static final FileSystemPackInspector READER = new FileSystemPackInspector();

    @Test
    @DisplayName("the census copies its lists, and a manifest that is a regular file is present")
    void theCensusHoldsItsInvariants() {
        List<NotHeldFile> notHeld = new ArrayList<>(List.of(NotHeldFile.unmodelled("notes.txt")));
        TreeCensus census = new TreeCensus(
                List.of(), notHeld, TreeCensus.Manifest.ABSENT, TreeCensus.ReservedShard.ABSENT, List.of(), List.of());
        notHeld.clear();

        assertThat(census.notHeld()).containsExactly(NotHeldFile.unmodelled("notes.txt"));
        assertThat(TreeCensus.empty().files()).isEmpty();
        assertThatThrownBy(() -> new TreeCensus.Manifest(false, true, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new TreeCensus.Entries(new ArrayList<>(List.of("tactic:a at a.tactic.yaml")), List.of())
                        .constituents())
                .containsExactly("tactic:a at a.tactic.yaml");
    }

    @Test
    @DisplayName("over every planted pack, no file is both classified as mapped and not held, and none is listed twice")
    void overThePlantedPacksTheHeldSetIsOneAccount(@TempDir Path host) throws IOException {
        int plants = 0;
        for (String plant : PlantedPacks.names()) {
            if (!Files.isDirectory(PlantedPacks.plant(plant).resolve("files"))) {
                continue;
            }
            Path pack = PlantedPacks.copyOf(plant, Files.createDirectories(host.resolve(String.valueOf(plants++))));
            PackReadout read = READER.read(new PackRoot("qa", Optional.of(PackLevel.ORG), pack.toString()));

            List<String> notHeld =
                    read.notHeld().stream().map(NotHeldFile::path).toList();
            assertThat(notHeld)
                    .as("%s: listed once, in path order", plant)
                    .doesNotHaveDuplicates()
                    .isSorted();
            assertThat(read.files())
                    .as("%s: a mapped file is held, so it is never also not held", plant)
                    .filteredOn(file -> file.kind().isPresent())
                    .extracting(ClassifiedFile::file)
                    .noneMatch(notHeld::contains);
        }
        assertThat(plants).isGreaterThan(50);
    }

    @Test
    @DisplayName("WP11's narrowed files are not held; a retired file is not listed; an excluded file is classified too")
    void theNarrowedRetiredAndExcludedFiles(@TempDir Path host) throws IOException {
        Path pack = PlantedPacks.copyOf("control-a-clean-org-pack", host);
        write(pack, "drg/tactic.graph.yaml", "schema_version: '1.0'\nnodes: []\nedges: []\n");
        write(pack, "project/charter.yaml", "catalog: {}\n");
        write(pack, "PROVENANCE.md", "# Provenance\n");
        write(pack, "docs/notes.md", "# Notes\n");

        PackReadout read = READER.read(new PackRoot("qa", Optional.of(PackLevel.ORG), pack.toString()));

        assertThat(read.notHeld())
                .containsExactly(
                        NotHeldFile.excluded(
                                "docs/notes.md", read.notHeld().getFirst().reason()),
                        NotHeldFile.unmodelled("drg/tactic.graph.yaml"),
                        NotHeldFile.unmodelled("project/charter.yaml"));
        assertThat(read.notHeld().getFirst().kind()).isEqualTo(NotHeldFile.Kind.EXCLUDED);
        assertThat(read.files()).extracting(ClassifiedFile::file).contains("docs/notes.md");
        // The verdict warns of each file not held (WP07): the retired file is its one violation.
        assertThat(read.rationale().violations())
                .as("the retired file is a read fault, never not held")
                .extracting(fault -> fault.location().file())
                .containsExactly("PROVENANCE.md");
    }

    private static void write(Path pack, String relative, String text) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
