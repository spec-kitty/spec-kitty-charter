package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The staging area port (#418, A8): the directories a registration stages in are the owner's alone, whatever the
 * umask, and the delete never leaves the root and never follows a link.
 */
class FileStagingAreaTest {

    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rwx------");

    @TempDir
    Path host;

    @Test
    void theJobDirectoryAndEveryParentTheServiceHadToCreateAreOwnerOnly() throws IOException {
        Path root = host.resolve("spool").resolve("staging");
        FileStagingArea area = new FileStagingArea(root);

        Path job = area.createJobDirectory("01J0000000000000000000000A");

        assertThat(job).isEqualTo(root.resolve("01J0000000000000000000000A"));
        assertThat(Files.getPosixFilePermissions(job)).as("the job directory").isEqualTo(OWNER_ONLY);
        assertThat(Files.getPosixFilePermissions(root)).as("the root").isEqualTo(OWNER_ONLY);
        assertThat(Files.getPosixFilePermissions(root.getParent()))
                .as("a parent it created")
                .isEqualTo(OWNER_ONLY);
    }

    @Test
    void aDirectoryInsideAJobDirectoryIsOwnerOnlyToo() throws IOException {
        FileStagingArea area = new FileStagingArea(host.resolve("staging"));
        Path job = area.createJobDirectory("01J0000000000000000000000B");

        Path inside = area.createDirectory(job.resolve("extract"));

        assertThat(Files.getPosixFilePermissions(inside)).isEqualTo(OWNER_ONLY);
    }

    @Test
    void theRootIsTheDirectoryItWasGiven() {
        Path root = host.resolve("staging");

        assertThat(new FileStagingArea(root).root())
                .isEqualTo(root.toAbsolutePath().normalize());
    }

    @Test
    void deletingAJobDirectoryRemovesItAndEverythingInIt() throws IOException {
        FileStagingArea area = new FileStagingArea(host.resolve("staging"));
        Path job = area.createJobDirectory("01J0000000000000000000000C");
        Files.createDirectories(job.resolve("extract").resolve("deep"));
        Files.writeString(job.resolve("extract").resolve("deep").resolve("file"), "x");
        Files.writeString(job.resolve("archive"), "x");

        area.delete(job);

        assertThat(job).doesNotExist();
        assertThat(area.root()).exists();
    }

    @Test
    void deletingWhatIsNotThereIsQuietAndTouchesNothingElse() throws IOException {
        FileStagingArea area = new FileStagingArea(host.resolve("staging"));
        Path other = area.createJobDirectory("01J0000000000000000000000F");

        assertThatCode(() -> area.delete(area.root().resolve("01J0000000000000000000000D")))
                .doesNotThrowAnyException();

        assertThat(area.root()).isDirectory();
        assertThat(other).as("another job's directory").isDirectory();
    }

    @Test
    void aLinkInsideAJobDirectoryIsRemovedAndNeverFollowed() throws IOException {
        FileStagingArea area = new FileStagingArea(host.resolve("staging"));
        Path job = area.createJobDirectory("01J0000000000000000000000E");
        Path outside = Files.createDirectories(host.resolve("outside"));
        Files.writeString(outside.resolve("kept"), "x");
        Files.createSymbolicLink(job.resolve("link"), outside);

        area.delete(job);

        assertThat(job).doesNotExist();
        assertThat(outside.resolve("kept")).as("what the link pointed at").exists();
    }

    @Test
    void aDirectoryOutsideTheRootIsNeverDeleted() throws IOException {
        FileStagingArea area = new FileStagingArea(host.resolve("staging"));
        Path elsewhere = Files.createDirectories(host.resolve("elsewhere"));

        assertThatThrownBy(() -> area.delete(elsewhere)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> area.delete(area.root())).isInstanceOf(IllegalArgumentException.class);
        assertThat(elsewhere).exists();
    }
}
