package ai.speckitty.charter.packs.infra.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ai.speckitty.charter.packs.application.ArchiveFormat;
import ai.speckitty.charter.packs.application.ArchiveRefusal;
import ai.speckitty.charter.packs.application.ArchiveViolation;
import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import ai.speckitty.charter.packs.application.StagedPack;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The extractor's limits, the shapes it accepts, and the facts about its output that hold for any archive. */
class BoundedArchiveExtractorTest {

    @TempDir
    Path work;

    private Path staging;

    @BeforeEach
    void stagingRoot() throws IOException {
        staging = Files.createDirectory(work.resolve("staging"));
    }

    private ArchiveRefusal refused(ArchiveLimits limits, byte[] bytes, ArchiveFormat format) {
        Throwable thrown = catchThrowable(
                () -> new BoundedArchiveExtractor(limits).extract(new ByteArrayInputStream(bytes), format, staging));
        assertThat(thrown).isInstanceOf(ArchiveRefusal.class);
        return (ArchiveRefusal) thrown;
    }

    private StagedPack extract(byte[] bytes, ArchiveFormat format) throws ArchiveRefusal {
        return new BoundedArchiveExtractor().extract(new ByteArrayInputStream(bytes), format, staging);
    }

    @Test
    void theDefaultLimitsAreTheContractsOwn() {
        ArchiveLimits limits = ArchiveLimits.DEFAULTS;

        assertThat(limits.maxBodyBytes()).isEqualTo(32L * 1024 * 1024);
        assertThat(limits.maxExpandedBytes()).isEqualTo(32L * 1024 * 1024);
        assertThat(limits.maxEntries()).isEqualTo(10_000);
        assertThat(limits.maxDecompressedRead()).isEqualTo(32L * 1024 * 1024 + 64 * 1024);
        assertThat(limits.maxPathBytes()).isEqualTo(4_096);
    }

    @Test
    void aBodyOverTheBodyLimitIsRefusedAsTooLargeInBothFormats() {
        ArchiveLimits small = new ArchiveLimits(2_000, 1_000_000, 100, 64 * 1024, 4_096);
        byte[] noise = new byte[8_000];
        new java.util.Random(3).nextBytes(noise);
        byte[] tar = RawTar.gzip(new RawTar().entry("pack/a.bin", (byte) '0', noise, "").tar());
        byte[] zip = zipOf("pack/a.bin", noise);

        assertThat(refused(small, tar, ArchiveFormat.TAR_GZ).violations())
                .containsExactly(new ArchiveViolation(Code.BODY_TOO_LARGE, ""));
        assertThat(refused(small, zip, ArchiveFormat.ZIP).violations())
                .containsExactly(new ArchiveViolation(Code.BODY_TOO_LARGE, ""));
        assertThat(refused(small, zip, ArchiveFormat.ZIP).bodyTooLarge()).isTrue();
    }

    @Test
    void aBodyExactlyAtTheLimitIsNotRefusedForItsSize() throws Exception {
        byte[] zip = zipOf("a.txt", "x".getBytes(StandardCharsets.UTF_8));
        ArchiveLimits exact = new ArchiveLimits(zip.length, 1_000, 10, 1_000, 4_096);

        StagedPack staged = new BoundedArchiveExtractor(exact)
                .extract(new ByteArrayInputStream(zip), ArchiveFormat.ZIP, staging);

        assertThat(ArchiveFixtures.tree(staged.directory())).containsEntry("a.txt", "x");
    }

    @Test
    void tarReadingStopsWithinTheSlackOfTheExpandedLimit() {
        ArchiveLimits limits = new ArchiveLimits(1_000_000, 1_000, 10_000, 100, 4_096);
        RawTar tar = new RawTar();
        IntStream.range(0, 5_000).forEach(i -> tar.special("pack/l" + i, '2', "x"));

        ArchiveRefusal refusal = refused(limits, tar.gz(), ArchiveFormat.TAR_GZ);

        assertThat(refusal.violations()).extracting(ArchiveViolation::code).contains(Code.EXPANDED_TOO_LARGE);
        assertThat(refusal.decompressedBytes()).isLessThanOrEqualTo(1_100);
    }

    @Test
    void anEntryThatFillsTheExpandedLimitExactlyIsAccepted() throws Exception {
        ArchiveLimits limits = new ArchiveLimits(1_000_000, 1_000, 10, 64 * 1024, 4_096);
        byte[] tar = new RawTar().entry("a.bin", (byte) '0', new byte[1_000], "").gz();

        StagedPack staged =
                new BoundedArchiveExtractor(limits).extract(new ByteArrayInputStream(tar), ArchiveFormat.TAR_GZ, staging);

        assertThat(Files.size(staged.directory().resolve("a.bin"))).isEqualTo(1_000);
    }

    @Test
    void oneByteOverTheExpandedLimitIsRefused() {
        ArchiveLimits limits = new ArchiveLimits(1_000_000, 1_000, 10, 64 * 1024, 4_096);
        byte[] tar = new RawTar().entry("a.bin", (byte) '0', new byte[1_001], "").gz();

        assertThat(refused(limits, tar, ArchiveFormat.TAR_GZ).violations())
                .extracting(ArchiveViolation::code)
                .containsExactly(Code.EXPANDED_TOO_LARGE);
    }

    @Test
    void theEntryLimitCountsEveryEntryOfEitherKind() {
        ArchiveLimits limits = new ArchiveLimits(1_000_000, 1_000_000, 3, 64 * 1024, 4_096);
        byte[] four = new RawTar().dir("a/").file("a/1", "x").file("a/2", "x").file("a/3", "x").gz();
        byte[] three = new RawTar().dir("a/").file("a/1", "x").file("a/2", "x").gz();

        assertThat(refused(limits, four, ArchiveFormat.TAR_GZ).violations())
                .extracting(ArchiveViolation::code)
                .containsExactly(Code.TOO_MANY_ENTRIES);
        assertThat(catchThrowable(() -> new BoundedArchiveExtractor(limits)
                        .extract(new ByteArrayInputStream(three), ArchiveFormat.TAR_GZ, staging)))
                .isNull();
    }

    @Test
    void theZipEntryCountIsReadFromTheEndRecordBeforeAnyEntryIsTouched() {
        RawZip zip = new RawZip();
        zip.file("pack/a.txt", "x").localName("pack/other.txt");
        zip.declaredCount(10_001);

        ArchiveRefusal refusal = refused(ArchiveLimits.DEFAULTS, zip.build(), ArchiveFormat.ZIP);

        assertThat(refusal.violations()).containsExactly(new ArchiveViolation(Code.TOO_MANY_ENTRIES, ""));
        assertThat(refusal.decompressedBytes()).isZero();
    }

    @Test
    void aZipWithNoUnixModeIsWrittenAsAPlainFile() throws Exception {
        RawZip zip = new RawZip();
        zip.file("link", "../../etc/passwd");

        StagedPack staged = extract(zip.build(), ArchiveFormat.ZIP);

        assertThat(Files.isSymbolicLink(staged.directory().resolve("link"))).isFalse();
        assertThat(Files.isRegularFile(staged.directory().resolve("link"))).isTrue();
    }

    @Test
    void filesAreOwnerReadWriteOnlyAndDirectoriesOwnerOnly() throws Exception {
        byte[] tar = new RawTar().dir("p/").dir("p/sub/").file("p/sub/a.txt", "a").file("p/b.txt", "b").gz();

        StagedPack staged = extract(tar, ArchiveFormat.TAR_GZ);

        ArchiveFixtures.assertOwnerOnly(staged.directory());
        ArchiveFixtures.assertOwnerOnly(staged.directory().getParent());
    }

    @Test
    void theExtractorWritesOnlyInsideTheStagingRootAndLeavesNoSpoolFile() throws Exception {
        RawZip zip = new RawZip();
        zip.file("pack.yaml", "name: p\n");

        StagedPack staged = extract(zip.build(), ArchiveFormat.ZIP);

        assertThat(staged.directory().toRealPath()).startsWith(staging.toRealPath());
        assertThat(ArchiveFixtures.listing(staging)).hasSize(1);
        assertThat(ArchiveFixtures.listing(work)).containsExactly("staging");
    }

    @Test
    void aLeadingDirectoryIsNotStrippedWhenAFileSitsBesideIt() throws Exception {
        byte[] tar = new RawTar().file("pack.yaml", "a").file("sub/b.txt", "b").gz();

        StagedPack staged = extract(tar, ArchiveFormat.TAR_GZ);

        assertThat(ArchiveFixtures.tree(staged.directory()).keySet()).containsExactly("pack.yaml", "sub/", "sub/b.txt");
    }

    @Test
    void aLeadingDirectoryIsNotStrippedWhenTwoDirectoriesShareTheTop() throws Exception {
        byte[] tar = new RawTar().file("one/a.txt", "a").file("two/b.txt", "b").gz();

        StagedPack staged = extract(tar, ArchiveFormat.TAR_GZ);

        assertThat(ArchiveFixtures.tree(staged.directory()).keySet())
                .containsExactly("one/", "one/a.txt", "two/", "two/b.txt");
    }

    @Test
    void onlyOneLeadingDirectoryIsStripped() throws Exception {
        byte[] tar = new RawTar().file("outer/inner/a.txt", "a").gz();

        StagedPack staged = extract(tar, ArchiveFormat.TAR_GZ);

        assertThat(ArchiveFixtures.tree(staged.directory()).keySet()).containsExactly("inner/", "inner/a.txt");
    }

    @Test
    void aRefusalListsAtMostOneHundredViolationsAndCountsTheRest() {
        RawTar tar = new RawTar();
        IntStream.range(0, 150).forEach(i -> tar.special("pack/l" + i, '2', "x"));

        ArchiveRefusal refusal = refused(ArchiveLimits.DEFAULTS, tar.gz(), ArchiveFormat.TAR_GZ);

        assertThat(refusal.violations()).hasSize(100);
        assertThat(refusal.omitted()).isEqualTo(50);
        assertThat(refusal.violations().get(0).path()).isEqualTo("pack/l0");
        assertThat(refusal.getMessage()).contains("149 more");
    }

    @Test
    void everyPathAValueCarriesIsEscapedAndCutTo256Bytes() {
        byte[] tar = new RawTar().paxPath("pack/" + "é".repeat(300) + "\n").file("ok", "x").gz();

        ArchiveViolation violation = refused(ArchiveLimits.DEFAULTS, tar, ArchiveFormat.TAR_GZ)
                .violations()
                .get(0);

        assertThat(violation.path().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(256);
        assertThat(violation.path()).doesNotContain("\n");
        assertThat(new ArchiveViolation(Code.PATH_CHARACTER, "a\u0007b\r\u0085c").path())
                .isEqualTo("a\\u0007b\\u000d\\u0085c");
    }

    @Test
    void aValueIsCutOnACharacterBoundaryNotInsideOne() {
        String path = "€".repeat(100);

        String cut = new ArchiveViolation(Code.PATH_CHARACTER, path).path();

        assertThat(cut.getBytes(StandardCharsets.UTF_8)).hasSize(255);
        assertThat(cut).isEqualTo("€".repeat(85));
    }

    @Test
    void theBodyIsReadButNeverClosed() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream body = new FilterInputStream(new ByteArrayInputStream(new RawTar().file("a", "b").gz())) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };

        new BoundedArchiveExtractor().extract(body, ArchiveFormat.TAR_GZ, staging);

        assertThat(closed).isFalse();
    }

    @Test
    void aSecondExtractionIntoAnotherRootIsIndependent() throws Exception {
        byte[] tar = new RawTar().file("a.txt", "a").gz();
        Path other = Files.createDirectory(work.resolve("other"));

        StagedPack first = extract(tar, ArchiveFormat.TAR_GZ);
        StagedPack second = new BoundedArchiveExtractor().extract(new ByteArrayInputStream(tar), ArchiveFormat.TAR_GZ, other);

        assertThat(first.directory()).isNotEqualTo(second.directory());
        assertThat(ArchiveFixtures.tree(second.directory())).containsEntry("a.txt", "a");
    }

    @Test
    void aZipWithUnixAndZip64LikeExtraFieldsIsRead() throws Exception {
        byte[] unixExtra = {0x55, 0x78, 0x04, 0x00, 0x01, 0x02, 0x03, 0x04};
        RawZip zip = new RawZip();
        zip.file("a.txt", "a").extra(unixExtra).unixMode(0100644);

        StagedPack staged = extract(zip.build(), ArchiveFormat.ZIP);

        assertThat(ArchiveFixtures.tree(staged.directory())).containsEntry("a.txt", "a");
    }

    @Test
    void theExtractorNamesNoViolationForAnEmptyArchive() throws Exception {
        StagedPack staged = extract(new RawTar().gz(), ArchiveFormat.TAR_GZ);

        assertThat(ArchiveFixtures.tree(staged.directory())).isEmpty();
        List<ArchiveViolation> none = List.of();
        assertThat(none).isEmpty();
    }

    private static byte[] zipOf(String name, byte[] content) {
        RawZip zip = new RawZip();
        zip.bytes(name, content);
        return zip.build();
    }
}
