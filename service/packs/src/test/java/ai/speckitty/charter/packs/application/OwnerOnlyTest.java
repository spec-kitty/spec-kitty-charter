package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The owner-only helper on file systems other than the host's (#510). Jimfs stands in for them: configured like NTFS
 * (the {@code basic}, {@code owner}, {@code dos} and {@code acl} views, no {@code posix}), it refuses a POSIX
 * attribute at creation exactly as Windows does, with an {@link UnsupportedOperationException}; configured with
 * {@code basic} alone, it can keep nothing from other users. The host's POSIX path keeps its own tests
 * ({@code FileStagingAreaTest}, {@code StagingPermissionsTest}, {@code BoundedArchiveExtractorTest}).
 */
class OwnerOnlyTest {

    private static final Configuration NTFS_LIKE = Configuration.windows().toBuilder()
            .setAttributeViews("basic", "owner", "dos", "acl")
            .build();

    private final FileSystem fileSystem = Jimfs.newFileSystem(NTFS_LIKE);

    @AfterEach
    void close() throws IOException {
        fileSystem.close();
    }

    @Nested
    class OnAFileSystemWithAclsButNoPosixPermissions {

        @Test
        void theFileSystemRefusesAPosixModeAtCreationAsWindowsDoes() {
            Path directory = fileSystem.getPath("C:\\staging");

            assertThatThrownBy(() -> Files.createDirectory(
                            directory,
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void everyDirectoryTheServiceCreatesIsTheOwnersAloneAndPassesThatOnToWhatItHolds() throws IOException {
            Path job = fileSystem.getPath("C:\\spool\\staging\\job-1");

            assertThat(OwnerOnly.directories(job)).isEqualTo(job);

            for (Path created : List.of(job, job.getParent(), job.getParent().getParent())) {
                assertThat(Files.isDirectory(created)).as("%s", created).isTrue();
                assertThat(acl(created)).as("the ACL of %s", created).containsExactly(ownerEntry(created, true));
            }
        }

        @Test
        void aDirectoryThatExistsIsLeftAsItIs() throws IOException {
            Path existing = Files.createDirectory(fileSystem.getPath("C:\\existing"));
            List<AclEntry> before = acl(existing);

            OwnerOnly.directories(existing.resolve("job"));

            assertThat(acl(existing)).isEqualTo(before);
            assertThat(acl(existing.resolve("job"))).containsExactly(ownerEntry(existing.resolve("job"), true));
        }

        @Test
        void aNewFileIsTheOwnersAloneBeforeItIsWritten() throws IOException {
            Path file = OwnerOnly.directories(fileSystem.getPath("C:\\job")).resolve("upload.tar.gz");

            try (OutputStream out = OwnerOnly.newFile(file)) {
                assertThat(acl(file)).containsExactly(ownerEntry(file, false));
                out.write("archive".getBytes(StandardCharsets.US_ASCII));
            }

            assertThat(Files.readString(file, StandardCharsets.US_ASCII)).isEqualTo("archive");
        }

        @Test
        void aNewFileNeverReplacesOneThatExists() throws IOException {
            Path file = Files.writeString(fileSystem.getPath("C:\\planted"), "theirs");

            assertThatThrownBy(() -> OwnerOnly.newFile(file)).isInstanceOf(FileAlreadyExistsException.class);
            assertThat(Files.readString(file)).isEqualTo("theirs");
        }

        @Test
        void oneDirectoryAndATemporaryOneAreTheOwnersAlone() throws IOException {
            Path parent = OwnerOnly.directories(fileSystem.getPath("C:\\work"));

            Path extracted = OwnerOnly.directory(parent.resolve("extract"));
            Path temporary = OwnerOnly.temporaryDirectory(parent, "git-");

            assertThat(acl(extracted)).containsExactly(ownerEntry(extracted, true));
            assertThat(temporary.getParent()).isEqualTo(parent);
            assertThat(temporary.getFileName().toString()).startsWith("git-");
            assertThat(acl(temporary)).containsExactly(ownerEntry(temporary, true));
        }

        @Test
        void oneDirectoryRefusesADirectoryThatExists() throws IOException {
            Path existing = Files.createDirectory(fileSystem.getPath("C:\\extract"));

            assertThatThrownBy(() -> OwnerOnly.directory(existing)).isInstanceOf(FileAlreadyExistsException.class);
        }

        @Test
        void aDirectoryAnotherUserPlantedAnEntryInBeforeItsAclWasSetIsRefused() throws IOException {
            // The window between creating a directory and setting its ACL, in a parent other users can write to.
            Path directory = Files.createDirectory(fileSystem.getPath("C:\\git-1"));
            Files.writeString(directory.resolve(".gitconfig"), "planted");

            assertThatThrownBy(() -> OwnerOnly.restrict(directory, true))
                    .isInstanceOf(FileSystemException.class)
                    .hasMessageContaining("held an entry before it was restricted");
        }

        @Test
        void aDirectoryWhoseAclCannotBeSetIsRemovedSoARetryDoesNotFindItUnrestricted() throws IOException {
            Path job = fileSystem.getPath("C:\\job-1");

            assertThatThrownBy(() -> OwnerOnly.directories(job, (path, directory) -> {
                        throw new IOException("the ACL cannot be set");
                    }))
                    .isInstanceOf(IOException.class);
            assertThat(job).doesNotExist();

            OwnerOnly.directories(job);

            assertThat(acl(job)).containsExactly(ownerEntry(job, true));
        }

        @Test
        void theStartUpCheckAcceptsIt() {
            assertThatCode(() -> OwnerOnly.requireSupported(fileSystem.getPath("C:\\spool")))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class OnAFileSystemWithNeitherPosixPermissionsNorAcls {

        private final FileSystem bare = Jimfs.newFileSystem(
                Configuration.unix().toBuilder().setAttributeViews("basic").build());

        @AfterEach
        void closeBare() throws IOException {
            bare.close();
        }

        @Test
        void theStartUpCheckRefusesItAndNamesTheDirectory() {
            Path staging = bare.getPath("/spool/staging");

            assertThatThrownBy(() -> OwnerOnly.requireSupported(staging))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("/spool/staging")
                    .hasMessageContaining("neither POSIX permissions nor ACLs");
        }

        @Test
        void nothingIsCreatedAndTheRefusalSaysWhy() {
            Path job = bare.getPath("/spool/staging/job-1");
            Path file = bare.getPath("/upload.tar.gz");

            assertThatThrownBy(() -> OwnerOnly.directories(job))
                    .isInstanceOf(OwnerOnly.UnsupportedFileSystemException.class)
                    .hasMessageContaining("neither POSIX permissions nor ACLs");
            assertThatThrownBy(() -> OwnerOnly.newFile(file))
                    .isInstanceOf(OwnerOnly.UnsupportedFileSystemException.class);
            assertThatThrownBy(() -> OwnerOnly.directory(job))
                    .isInstanceOf(OwnerOnly.UnsupportedFileSystemException.class);
            assertThatThrownBy(() -> OwnerOnly.temporaryDirectory(bare.getPath("/"), "git-"))
                    .isInstanceOf(OwnerOnly.UnsupportedFileSystemException.class);

            assertThat(Files.exists(bare.getPath("/spool"))).isFalse();
            assertThat(Files.exists(file)).isFalse();
        }
    }

    @Nested
    class OnAFileSystemWithPosixPermissions {

        private final FileSystem posix = Jimfs.newFileSystem(Configuration.unix().toBuilder()
                .setAttributeViews("basic", "owner", "posix")
                .build());

        @AfterEach
        void closePosix() throws IOException {
            posix.close();
        }

        @Test
        void directoriesAre0700AndFiles0600AtCreation() throws IOException {
            Path job = OwnerOnly.directories(posix.getPath("/spool/job"));
            Path extracted = OwnerOnly.directory(job.resolve("extract"));
            Path temporary = OwnerOnly.temporaryDirectory(job, "git-");
            Path file = job.resolve("upload.zip");
            OwnerOnly.newFile(file).close();

            for (Path directory : List.of(job, job.getParent(), extracted, temporary)) {
                assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)))
                        .as("%s", directory)
                        .isEqualTo("rwx------");
            }
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
                    .isEqualTo("rw-------");
        }
    }

    private static List<AclEntry> acl(Path path) throws IOException {
        return view(path).getAcl();
    }

    private static AclEntry ownerEntry(Path path, boolean directory) throws IOException {
        Set<AclEntryFlag> flags =
                directory ? EnumSet.of(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT) : Set.of();
        return AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(view(path).getOwner())
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .setFlags(flags)
                .build();
    }

    private static AclFileAttributeView view(Path path) {
        AclFileAttributeView view =
                Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        assertThat(view).as("the ACL view of %s", path).isNotNull();
        return view;
    }
}
