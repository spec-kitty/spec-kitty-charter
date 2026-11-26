package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.packs.domain.PackReading;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.GoldenSubjects;
import ai.speckitty.charter.packs.examples.GoldenSubjects.Subject;
import ai.speckitty.charter.packs.infra.storage.rules.StorageRulesets;
import ai.speckitty.charter.packs.infra.storage.rules.TreeRead;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The one reader as the domain port (#554, S6, T039): {@link FileSystemPackInspector} implements {@link PackReading},
 * and its readout carries the storage edge's verdict of the one read ({@link StorageRulesets#verdict}), the census's
 * files and its files not held, over one walk of the tree.
 */
@DisplayName("FileSystemPackInspector as PackReading: the readout is the verdict of one read (#554, S6)")
class PackReadingTest {

    private static final FileSystemPackInspector READER = new FileSystemPackInspector();

    static List<Subject> subjects() {
        return GoldenSubjects.subjects();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subjects")
    @DisplayName("over every golden pack, the readout is the verdict, the census's files and its files not held")
    void theReadoutIsTheVerdictOfTheOneRead(Subject subject, @TempDir Path host) throws IOException {
        assumeLinks(subject, host);
        Path pack = GoldenSubjects.copy(subject, Files.createDirectories(host.resolve("pack")));
        PackRoot root = new PackRoot(subject.name(), Optional.empty(), pack.toString());
        PackReading reading = READER;

        ReadCounters.Measured<PackReadout> read = ReadCounters.measured(() -> reading.read(root));
        PackReadout readout = read.value();
        long walked = read.read().walks();
        TreeRead tree = READER.readTree(root);

        assertThat(walked).as("one walk of the tree").isOne();
        assertThat(readout.rationale()).isEqualTo(StorageRulesets.verdict(tree));
        assertThat(readout.notHeld()).isEqualTo(tree.census().notHeld());
        assertThat(readout.files()).isEqualTo(tree.census().files());
        assertThat(readout.contents().descriptor()).isEqualTo(tree.contents().descriptor());
    }

    /** A plant that needs symbolic links is skipped where the file system cannot make one, unless CI requires them. */
    private static void assumeLinks(Subject subject, Path host) throws IOException {
        if (!subject.plantsLinks()) {
            return;
        }
        Path target = Files.createTempFile(host, "probe", ".tmp");
        Path link = host.resolve("probe.lnk");
        boolean links;
        try {
            Files.createSymbolicLink(link, target);
            links = true;
        } catch (UnsupportedOperationException | FileSystemException noLinks) {
            links = false;
        } finally {
            Files.deleteIfExists(link);
            Files.delete(target);
        }
        assumeTrue(links || Boolean.getBoolean("charter.requireSymlinks"), "the file system cannot create links");
    }
}
