package ai.speckitty.charter.packs.infra.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ai.speckitty.charter.packs.application.ArchiveFormat;
import ai.speckitty.charter.packs.application.ArchiveRefusal;
import ai.speckitty.charter.packs.application.ArchiveViolation;
import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import ai.speckitty.charter.packs.application.StagedPack;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An archive that is already a file (the upload's spool, the fetched archive) is read where it lies (#418, A6): a zip is
 * not copied to a second spool, so the per-job peak holds the archive once, the extracted tree and the commit copy. The
 * file is the caller's, and extraction leaves it as it found it.
 */
class ExtractFromFileTest {

    @TempDir
    Path work;

    private Path staging;
    private Path archive;

    @BeforeEach
    void rooms() throws IOException {
        staging = Files.createDirectory(work.resolve("staging"));
        archive = work.resolve("archive");
    }

    private byte[] zip() {
        RawZip zip = new RawZip();
        zip.file("pack/a.txt", "alpha");
        zip.file("pack/b.txt", "beta");
        return zip.build();
    }

    @Test
    void aZipFileIsReadWhereItLiesAndNoSecondSpoolIsMadeInTheStagingRoot() throws Exception {
        Files.write(archive, zip());
        List<List<String>> seenWhenTheLibraryOpened = new ArrayList<>();
        ZipReader.ZipOpener watching = channel -> {
            seenWhenTheLibraryOpened.add(ArchiveFixtures.listing(staging));
            return ZipReader.openLibrary(channel);
        };

        StagedPack staged = new BoundedArchiveExtractor(ArchiveLimits.DEFAULTS, watching)
                .extract(archive, ArchiveFormat.ZIP, staging);

        assertThat(seenWhenTheLibraryOpened)
                .as("the staging root, when the zip library opened the file: the tree only, no copy of the archive")
                .containsExactly(List.of(PackTree.DIRECTORY_NAME));
        assertThat(ArchiveFixtures.tree(staged.directory()))
                .containsEntry("a.txt", "alpha")
                .containsEntry("b.txt", "beta");
    }

    @Test
    void theArchiveFileIsLeftAsItWasFoundWhetherItIsAcceptedOrRefused() throws Exception {
        byte[] good = zip();
        Files.write(archive, good);
        new BoundedArchiveExtractor().extract(archive, ArchiveFormat.ZIP, staging);
        assertThat(Files.readAllBytes(archive)).as("after an accepted zip").isEqualTo(good);

        Path other = Files.createDirectory(work.resolve("other"));
        RawZip escaping = new RawZip();
        escaping.file("../escape", "x");
        byte[] hostile = escaping.build();
        Files.write(archive, hostile);
        Throwable refused =
                catchThrowable(() -> new BoundedArchiveExtractor().extract(archive, ArchiveFormat.ZIP, other));
        assertThat(refused).isInstanceOf(ArchiveRefusal.class);
        assertThat(Files.readAllBytes(archive)).as("after a refused zip").isEqualTo(hostile);
        assertThat(ArchiveFixtures.listing(other))
                .as("a refusal leaves nothing in the staging root")
                .isEmpty();
    }

    @Test
    void aZipFileOverTheBodyLimitIsRefusedAsTooLargeWithoutBeingRead() throws Exception {
        byte[] noise = new byte[8_000];
        new java.util.Random(3).nextBytes(noise);
        RawZip big = new RawZip();
        big.file("pack/a.bin", new String(noise, StandardCharsets.ISO_8859_1));
        Files.write(archive, big.build());
        ArchiveLimits small = new ArchiveLimits(2_000, 1_000_000, 100, 64 * 1024, 4_096);
        int[] opened = {0};
        ZipReader.ZipOpener counting = channel -> {
            opened[0]++;
            return ZipReader.openLibrary(channel);
        };

        Throwable thrown = catchThrowable(
                () -> new BoundedArchiveExtractor(small, counting).extract(archive, ArchiveFormat.ZIP, staging));

        assertThat(thrown).isInstanceOfSatisfying(ArchiveRefusal.class, refusal -> {
            assertThat(refusal.violations()).containsExactly(new ArchiveViolation(Code.BODY_TOO_LARGE, ""));
            assertThat(refusal.bodyTooLarge()).isTrue();
        });
        assertThat(opened[0]).isZero();
    }

    @Test
    void aGzipTarFileExtractsToTheSameTreeAsItsStream() throws Exception {
        byte[] tar = RawTar.gzip(new RawTar().file("pack/a.txt", "alpha").tar());
        Files.write(archive, tar);

        StagedPack staged = new BoundedArchiveExtractor().extract(archive, ArchiveFormat.TAR_GZ, staging);

        assertThat(ArchiveFixtures.tree(staged.directory())).containsEntry("a.txt", "alpha");
    }
}
