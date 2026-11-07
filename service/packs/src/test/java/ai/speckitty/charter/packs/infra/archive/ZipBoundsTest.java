package ai.speckitty.charter.packs.infra.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ai.speckitty.charter.packs.application.ArchiveFormat;
import ai.speckitty.charter.packs.application.ArchiveRefusal;
import ai.speckitty.charter.packs.application.ArchiveViolation;
import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * NFR-004 for zips: the central directory is walked, from the end record, before {@link ZipFile} is opened, and a
 * zip that fails the walk never reaches the library. The tests replace the opener to count its calls.
 */
class ZipBoundsTest {

    private static final int CENTRAL_FIXED = 46;

    @TempDir
    Path work;

    private Path staging;
    private final AtomicInteger opened = new AtomicInteger();

    @BeforeEach
    void stagingRoot() throws IOException {
        staging = Files.createDirectory(work.resolve("staging"));
    }

    private ZipReader.ZipOpener counting() {
        return channel -> {
            opened.incrementAndGet();
            return ZipReader.openLibrary(channel);
        };
    }

    private ArchiveRefusal refuse(byte[] zip) {
        Throwable thrown = catchThrowable(() -> new BoundedArchiveExtractor(ArchiveLimits.DEFAULTS, counting())
                .extract(new ByteArrayInputStream(zip), ArchiveFormat.ZIP, staging));
        assertThat(thrown).isInstanceOf(ArchiveRefusal.class);
        return (ArchiveRefusal) thrown;
    }

    private static RawZip withHeaders(int count) {
        RawZip zip = new RawZip();
        IntStream.range(0, count).forEach(i -> zip.file("pack/f" + i + ".txt", ""));
        return zip;
    }

    @Test
    void aZipWhoseEndRecordSaysOneEntryButHoldsMoreThanTheLimitIsRefusedWithoutOpeningTheLibrary() {
        RawZip zip = withHeaders(10_001);
        zip.declaredCount(1);

        ArchiveRefusal refusal = refuse(zip.build());

        assertThat(refusal.violations()).containsExactly(new ArchiveViolation(Code.TOO_MANY_ENTRIES, ""));
        assertThat(opened).hasValue(0);
    }

    @Test
    void aZipWhoseEndRecordClaimsMoreThanTheLimitIsRefusedWithoutOpeningTheLibrary() {
        RawZip zip = withHeaders(1);
        zip.declaredCount(10_001);

        assertThat(refuse(zip.build()).violations())
                .containsExactly(new ArchiveViolation(Code.TOO_MANY_ENTRIES, ""));
        assertThat(opened).hasValue(0);
    }

    @Test
    void aCountThatDiffersFromTheHeadersIsRefusedEitherWayWithoutOpeningTheLibrary() {
        RawZip over = withHeaders(3);
        over.declaredCount(2);
        RawZip under = withHeaders(2);
        under.declaredCount(3);

        assertThat(refuse(over.build()).violations())
                .containsExactly(new ArchiveViolation(Code.ZIP_COUNT_MISMATCH, ""));
        assertThat(refuse(under.build()).violations())
                .containsExactly(new ArchiveViolation(Code.ZIP_COUNT_MISMATCH, ""));
        assertThat(opened).hasValue(0);
    }

    @Test
    void aZip64CountThatDiffersFromTheHeadersIsRefused() {
        RawZip zip = withHeaders(2);
        zip.zip64Count(5);

        assertThat(refuse(zip.build()).violations())
                .containsExactly(new ArchiveViolation(Code.ZIP_COUNT_MISMATCH, ""));
        assertThat(opened).hasValue(0);
    }

    @Test
    void aCentralHeaderWithMoreThan1024BytesOfExtraFieldsIsRefusedWithoutOpeningTheLibrary() {
        RawZip zip = new RawZip();
        zip.file("pack/a.txt", "x").extra(extra(1_025));

        assertThat(refuse(zip.build()).violations())
                .containsExactly(new ArchiveViolation(Code.ZIP_EXTRA_FIELD_TOO_LARGE, "pack/a.txt"));
        assertThat(opened).hasValue(0);
    }

    @Test
    void aLocalHeaderWithMoreThan1024BytesOfExtraFieldsIsRefused() {
        RawZip zip = new RawZip();
        RawZip.Entry entry = zip.file("pack/a.txt", "x");
        entry.localExtra = extra(1_025);

        assertThat(refuse(zip.build()).violations())
                .containsExactly(new ArchiveViolation(Code.ZIP_EXTRA_FIELD_TOO_LARGE, "pack/a.txt"));
    }

    @Test
    void exactly1024BytesOfExtraFieldsAreAcceptedAndTheLibraryIsOpenedOnce() throws Exception {
        RawZip zip = new RawZip();
        zip.file("pack/a.txt", "x").extra(extra(1_024));

        new BoundedArchiveExtractor(ArchiveLimits.DEFAULTS, counting())
                .extract(new ByteArrayInputStream(zip.build()), ArchiveFormat.ZIP, staging);

        assertThat(opened).hasValue(1);
    }

    @Test
    void aRuntimeExceptionFromTheLibraryIsAnUnreadableArchiveNotAServerError() {
        ZipReader.ZipOpener failing = channel -> {
            throw new IllegalStateException("library bug");
        };
        byte[] zip = withHeaders(1).build();

        Throwable thrown = catchThrowable(() -> new BoundedArchiveExtractor(ArchiveLimits.DEFAULTS, failing)
                .extract(new ByteArrayInputStream(zip), ArchiveFormat.ZIP, staging));

        assertThat(thrown).isInstanceOf(ArchiveRefusal.class);
        assertThat(((ArchiveRefusal) thrown).violations())
                .containsExactly(new ArchiveViolation(Code.ARCHIVE_UNREADABLE, ""));
        assertThat(ArchiveFixtures.listing(staging)).isEmpty();
    }

    @Test
    void aHostFailureIsNotMistakenForAnUnreadableArchive() {
        ZipReader.ZipOpener failing = channel -> {
            throw new UncheckedIOException(new IOException("disk"));
        };
        byte[] zip = withHeaders(1).build();

        assertThat(catchThrowable(() -> new BoundedArchiveExtractor(ArchiveLimits.DEFAULTS, failing)
                        .extract(new ByteArrayInputStream(zip), ArchiveFormat.ZIP, staging)))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void theWalkStopsAtTheFirstHeaderPastTheLimitWhateverTheFileHolds() throws Exception {
        Path file = work.resolve("many.zip");
        writeCentralDirectoryOf(file, 600_000, 1);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ZipCentralDirectory walk = new ZipCentralDirectory(channel, ArchiveLimits.DEFAULTS);

            Throwable thrown = catchThrowable(walk::verify);

            assertThat(thrown).isInstanceOf(ArchiveLimitException.class);
            assertThat(((ArchiveLimitException) thrown).code()).isEqualTo(Code.TOO_MANY_ENTRIES);
            assertThat(walk.headersRead()).isEqualTo(10_001);
        }
    }

    @Test
    void theWalkOfAnHonestDirectoryReadsEveryHeaderOnce() throws Exception {
        Path file = work.resolve("honest.zip");
        writeCentralDirectoryOf(file, 7, 7);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ZipCentralDirectory walk = new ZipCentralDirectory(channel, ArchiveLimits.DEFAULTS);

            walk.verify();

            assertThat(walk.headersRead()).isEqualTo(7);
        }
    }

    @Test
    void aSpoolFileThatAlreadyExistsInTheStagingRootIsNotDeleted() throws Exception {
        Path foreign = staging.resolve(ZipReader.SPOOL_NAME);
        Files.writeString(foreign, "belongs to the caller");
        byte[] zip = withHeaders(1).build();

        Throwable thrown = catchThrowable(() -> new BoundedArchiveExtractor()
                .extract(new ByteArrayInputStream(zip), ArchiveFormat.ZIP, staging));

        assertThat(thrown).isInstanceOf(UncheckedIOException.class);
        assertThat(Files.readString(foreign)).isEqualTo("belongs to the caller");
    }

    @Test
    void theRemainingEntriesAreNotWrittenAfterASizeMismatch() throws Exception {
        RawZip zip = new RawZip();
        zip.file("pack/a.txt", "x".repeat(100)).deflated().declaredSize(5);
        zip.file("pack/b.txt", "second");
        PackTree tree = PackTree.create(staging, ArchiveLimits.DEFAULTS);

        new ZipReader(ArchiveLimits.DEFAULTS).read(new ByteArrayInputStream(zip.build()), tree, staging);

        assertThat(tree.clean()).isFalse();
        assertThat(ArchiveFixtures.tree(staging.resolve(PackTree.DIRECTORY_NAME)).keySet())
                .containsExactly("pack/", "pack/a.txt");
    }

    /** One unknown-id extra record padded to {@code total} bytes, which the library accepts. */
    private static byte[] extra(int total) {
        ByteBuffer b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0xCAFE).putShort((short) (total - 4));
        return b.array();
    }

    /** A file holding {@code headers} one-byte-named central headers and an end record that declares {@code count}. */
    private static void writeCentralDirectoryOf(Path file, int headers, int count) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(CENTRAL_FIXED + 1).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(0x02014b50).position(28);
        header.putShort((short) 1).position(CENTRAL_FIXED);
        header.put("a".getBytes(StandardCharsets.US_ASCII));
        long size = (long) headers * (CENTRAL_FIXED + 1);
        try (FileChannel out = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            for (int i = 0; i < headers; i++) {
                header.rewind();
                out.write(header);
            }
            ByteBuffer end = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
            end.putInt(0x06054b50).putShort((short) 0).putShort((short) 0);
            end.putShort((short) count).putShort((short) count).putInt((int) size).putInt(0).putShort((short) 0);
            end.rewind();
            out.write(end);
        }
    }
}
