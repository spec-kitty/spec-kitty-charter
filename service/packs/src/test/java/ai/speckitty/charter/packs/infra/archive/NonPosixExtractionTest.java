package ai.speckitty.charter.packs.infra.archive;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.application.ArchiveFormat;
import ai.speckitty.charter.packs.application.StagedPack;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * An upload is extracted on a file system without POSIX permissions (#510): on the windows-x64 native build every gzip
 * upload answered {@code 500} with {@code 'posix:permissions' not supported as initial attribute}. Jimfs, configured
 * like NTFS ({@code basic}, {@code owner}, {@code dos}, {@code acl}), refuses that attribute the same way; both formats
 * now extract, and every directory and file of the tree allows its owner alone.
 */
class NonPosixExtractionTest {

    private final FileSystem ntfsLike = Jimfs.newFileSystem(Configuration.windows().toBuilder()
            .setAttributeViews("basic", "owner", "dos", "acl")
            .build());

    @AfterEach
    void close() throws IOException {
        ntfsLike.close();
    }

    @Test
    void aGzipTarIsExtractedAndEveryPathOfTheTreeIsTheOwnersAlone() throws Exception {
        byte[] tar = new RawTar()
                .file("pack.yaml", "name: p\n")
                .file("doctrine/a.yaml", "a")
                .gz();

        StagedPack staged = extract(tar, ArchiveFormat.TAR_GZ);

        assertExtracted(staged.directory());
        assertOwnerOnly(staged.directory());
    }

    @Test
    void aZipIsExtractedAndEveryPathOfTheTreeIsTheOwnersAlone() throws Exception {
        RawZip zip = new RawZip();
        zip.file("pack.yaml", "name: p\n");
        zip.file("doctrine/a.yaml", "a");

        StagedPack staged = extract(zip.build(), ArchiveFormat.ZIP);

        assertExtracted(staged.directory());
        assertOwnerOnly(staged.directory());
    }

    private StagedPack extract(byte[] archive, ArchiveFormat format) throws Exception {
        Path staging = Files.createDirectories(ntfsLike.getPath("C:\\spool\\staging"));
        return new BoundedArchiveExtractor().extract(new ByteArrayInputStream(archive), format, staging);
    }

    private static void assertExtracted(Path root) throws IOException {
        assertThat(Files.readString(root.resolve("pack.yaml"))).isEqualTo("name: p\n");
        assertThat(Files.readString(root.resolve("doctrine").resolve("a.yaml"))).isEqualTo("a");
        try (Stream<Path> tree = Files.walk(root)) {
            assertThat(tree.count()).as("the root, doctrine, and two files").isEqualTo(4);
        }
    }

    private static void assertOwnerOnly(Path root) throws IOException {
        try (Stream<Path> tree = Files.walk(root)) {
            for (Path path : tree.toList()) {
                AclFileAttributeView view =
                        Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                List<AclEntry> acl = view.getAcl();
                assertThat(acl).as("the ACL of %s", path).hasSize(1);
                assertThat(acl.get(0).type()).isEqualTo(AclEntryType.ALLOW);
                assertThat(acl.get(0).principal()).isEqualTo(view.getOwner());
            }
        }
    }
}
