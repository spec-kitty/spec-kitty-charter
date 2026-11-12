package ai.speckitty.charter.packs.infra.archive;

import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.ARCHIVE_UNREADABLE;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.BODY_TOO_LARGE;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.COMPRESSION_METHOD;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.ENCRYPTED;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.ENTRY_TYPE;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.EXPANDED_TOO_LARGE;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.HEADER_TOO_LARGE;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.PATH_ABSOLUTE;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.PATH_BACKSLASH;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.PATH_CHARACTER;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.PATH_COLLISION;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.PATH_DOT_SEGMENT;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.PATH_DRIVE_LETTER;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.PATH_EMPTY_SEGMENT;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.PATH_NUL;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.PATH_TOO_LONG;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.PATH_TRAVERSAL;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.TOO_MANY_ENTRIES;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.ZIP_COUNT_MISMATCH;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.ZIP_EXTRA_FIELD;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.ZIP_EXTRA_FIELD_TOO_LARGE;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.ZIP_NAME_ENCODING;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.ZIP_NAME_MISMATCH;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.ZIP_OVERLAP;
import static ai.speckitty.charter.packs.application.ArchiveViolation.Code.ZIP_SIZE_MISMATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.application.ArchiveFormat;
import ai.speckitty.charter.packs.application.ArchiveRefusal;
import ai.speckitty.charter.packs.application.ArchiveViolation;
import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import ai.speckitty.charter.packs.application.StagedPack;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * NFR-005: a corpus of crafted archives, each built by code (never a committed binary), each refused with its expected
 * violation code and offending path, so an extractor that refuses everything, or refuses for the wrong reason, fails.
 * Beside each shape a benign archive of the same shape must extract to the expected tree. The bomb rows also hold the
 * decompressed bytes read to NFR-004's bound.
 */
class HostileArchiveCorpusTest {

    private static final long MIB = 1024L * 1024L;
    private static final long READ_BOUND = 32 * MIB + 64 * 1024;
    private static final int MINIMUM_CORPUS = 22;

    @TempDir
    Path work;

    /** One crafted archive and what the extractor must say about it. */
    record Hostile(String id, ArchiveFormat format, Supplier<byte[]> archive, List<ArchiveViolation> expected) {
        @Override
        public String toString() {
            return id;
        }
    }

    private static ArchiveViolation v(Code code, String path) {
        return new ArchiveViolation(code, path);
    }

    private static Hostile tar(String id, Supplier<byte[]> archive, ArchiveViolation... expected) {
        return new Hostile(id, ArchiveFormat.TAR_GZ, archive, List.of(expected));
    }

    private static Hostile zip(String id, Supplier<byte[]> archive, ArchiveViolation... expected) {
        return new Hostile(id, ArchiveFormat.ZIP, archive, List.of(expected));
    }

    private static Supplier<byte[]> tarOf(java.util.function.Consumer<RawTar> fill) {
        return () -> {
            RawTar tar = new RawTar();
            fill.accept(tar);
            return tar.gz();
        };
    }

    private static Supplier<byte[]> zipOf(java.util.function.Consumer<RawZip> fill) {
        return () -> {
            RawZip zip = new RawZip();
            fill.accept(zip);
            return zip.build();
        };
    }

    static Stream<Hostile> hostileTars() {
        return Stream.of(tarTraversalAndSpecialEntries(), tarMalformedAndCollidingNames(), tarHeaderLimitsAndBombs())
                .flatMap(rows -> rows);
    }

    private static Stream<Hostile> tarTraversalAndSpecialEntries() {
        return Stream.of(
                tar("tar zip-slip", tarOf(t -> t.file("../evil", "x")), v(PATH_TRAVERSAL, "../evil")),
                tar(
                        "tar zip-slip in the middle",
                        tarOf(t -> t.file("pack/../../evil", "x")),
                        v(PATH_TRAVERSAL, "pack/../../evil")),
                tar("tar absolute path", tarOf(t -> t.file("/etc/passwd", "x")), v(PATH_ABSOLUTE, "/etc/passwd")),
                tar("tar symlink", tarOf(t -> t.special("pack/link", '2', "/etc/passwd")), v(ENTRY_TYPE, "pack/link")),
                tar(
                        "tar hardlink to a target outside the pack",
                        tarOf(t -> t.special("pack/hard", '1', "../../../etc/shadow")),
                        v(ENTRY_TYPE, "pack/hard")),
                tar(
                        "tar symlink chain",
                        tarOf(t -> t.special("a", '2', "b")
                                .special("b", '2', "/etc")
                                .file("a/passwd", "x")),
                        v(ENTRY_TYPE, "a"),
                        v(ENTRY_TYPE, "b")),
                tar("tar character device", tarOf(t -> t.special("pack/null", '3', "")), v(ENTRY_TYPE, "pack/null")),
                tar("tar FIFO", tarOf(t -> t.special("pack/pipe", '6', "")), v(ENTRY_TYPE, "pack/pipe")),
                tar("tar sparse file", tarOf(t -> t.special("pack/sparse", 'S', "")), v(ENTRY_TYPE, "pack/sparse")),
                tar("tar contiguous file flag", tarOf(t -> t.special("pack/c", '7', "")), v(ENTRY_TYPE, "pack/c")),
                tar("tar GNU dump directory flag", tarOf(t -> t.special("pack/d", 'D', "")), v(ENTRY_TYPE, "pack/d")),
                tar(
                        "tar PAX path override",
                        tarOf(t -> t.paxPath("../../evil").file("ok.txt", "x")),
                        v(PATH_TRAVERSAL, "../../evil")));
    }

    private static Stream<Hostile> tarMalformedAndCollidingNames() {
        return Stream.of(
                tar(
                        "tar GNU long name with ..",
                        tarOf(t -> t.gnuLongName(LONG_DOTDOT).file("short", "x")),
                        v(PATH_TRAVERSAL, LONG_DOTDOT)),
                tar(
                        "tar duplicate after case folding",
                        tarOf(t -> t.file("pack/A.yaml", "1").file("pack/a.yaml", "2")),
                        v(PATH_COLLISION, "pack/a.yaml")),
                tar(
                        "tar the same file twice",
                        tarOf(t -> t.file("pack/a.yaml", "1").file("pack/a.yaml", "2")),
                        v(PATH_COLLISION, "pack/a.yaml")),
                tar(
                        "tar a file where a directory is",
                        tarOf(t -> t.file("pack/a/b.yaml", "1").file("pack/a", "2")),
                        v(PATH_COLLISION, "pack/a")),
                tar("tar empty segment", tarOf(t -> t.file("pack//a", "x")), v(PATH_EMPTY_SEGMENT, "pack//a")),
                tar("tar backslash", tarOf(t -> t.file("pack\\evil", "x")), v(PATH_BACKSLASH, "pack\\evil")),
                tar("tar drive letter", tarOf(t -> t.file("C:/evil", "x")), v(PATH_DRIVE_LETTER, "C:/evil")),
                tar("tar dot segment", tarOf(t -> t.file("pack/./a", "x")), v(PATH_DOT_SEGMENT, "pack/./a")),
                tar(
                        "tar non-ASCII name",
                        tarOf(t -> t.file("pack/caf\u00e9.txt", "x")),
                        v(PATH_CHARACTER, "pack/caf\u00e9.txt")),
                tar(
                        "tar control character in a name",
                        tarOf(t -> t.file("pack/a\nb", "x")),
                        v(PATH_CHARACTER, "pack/a\\u000ab")),
                tar(
                        "tar NUL in a PAX path",
                        tarOf(t -> t.paxPath("pack/a\0b").file("ok.txt", "x")),
                        v(PATH_NUL, "pack/a\\u0000b")),
                tar(
                        "tar path over 4096 bytes",
                        tarOf(t -> t.paxPath("a/".repeat(2_100)).file("ok.txt", "x")),
                        v(PATH_TOO_LONG, "a/".repeat(128))));
    }

    private static Stream<Hostile> tarHeaderLimitsAndBombs() {
        return Stream.of(
                tar(
                        "tar PAX x header over 64 KiB",
                        tarOf(t ->
                                t.pax(Map.of("comment", "a".repeat(65 * 1024))).file("ok.txt", "x")),
                        v(HEADER_TOO_LARGE, "")),
                tar(
                        "tar PAX global header over 64 KiB",
                        tarOf(t -> t.entry("GlobalHead/0", (byte) 'g', new byte[64 * 1024 + 1], "")
                                .file("ok.txt", "x")),
                        v(HEADER_TOO_LARGE, "")),
                tar(
                        "tar GNU long name header over 64 KiB",
                        tarOf(t -> t.gnuLongName("pack/" + "a".repeat(70_000)).file("short", "x")),
                        v(HEADER_TOO_LARGE, "")),
                tar(
                        "tar GNU long link header over 64 KiB",
                        tarOf(t -> t.entry("././@LongLink", (byte) 'K', new byte[64 * 1024 + 1], "")
                                .file("short", "x")),
                        v(HEADER_TOO_LARGE, "")),
                tar(
                        "tar PAX path starting with a slash",
                        tarOf(t -> t.paxPath("/abs/evil").file("ignored", "x")),
                        v(PATH_ABSOLUTE, "/abs/evil")),
                tar(
                        "tar PAX global path starting with a slash",
                        tarOf(t -> t.globalPaxPath("/abs/evil").file("ignored", "x")),
                        v(PATH_ABSOLUTE, "/abs/evil")),
                tar(
                        "tar GNU long name starting with a slash",
                        tarOf(t -> t.gnuLongName("/abs/evil").file("short", "x")),
                        v(PATH_ABSOLUTE, "/abs/evil")),
                tar(
                        "tar bomb, one entry",
                        () -> RawTar.gzBombOfZeros("pack/big.bin", 64 * MIB),
                        v(EXPANDED_TOO_LARGE, "pack/big.bin")),
                tar(
                        "tar bomb, many entries",
                        HostileArchiveCorpusTest::manyMebibyteEntries,
                        v(EXPANDED_TOO_LARGE, "pack/f32.bin")),
                tar("tar with 10,001 entries", HostileArchiveCorpusTest::tenThousandAndOne, v(TOO_MANY_ENTRIES, "")),
                tar(
                        "tar truncated gzip",
                        () -> truncated(new RawTar().file("pack/a", "x").gz()),
                        v(ARCHIVE_UNREADABLE, "")),
                tar(
                        "tar not gzip",
                        () -> "not an archive at all".getBytes(StandardCharsets.UTF_8),
                        v(ARCHIVE_UNREADABLE, "")));
    }

    private static final String LONG_DOTDOT = "pack/" + "d/".repeat(60) + "../x";

    static Stream<Hostile> hostileZips() {
        return Stream.of(
                        zipTraversalAndEntryKinds(),
                        zipEntryCountsAndNames(),
                        zipBombsAndStructure(),
                        zipPathFormsAndLimits())
                .flatMap(rows -> rows);
    }

    private static Stream<Hostile> zipTraversalAndEntryKinds() {
        return Stream.of(
                zip("zip zip-slip", zipOf(z -> z.file("../evil.txt", "x")), v(PATH_TRAVERSAL, "../evil.txt")),
                zip("zip absolute path", zipOf(z -> z.file("/etc/passwd", "x")), v(PATH_ABSOLUTE, "/etc/passwd")),
                zip(
                        "zip symlink",
                        zipOf(z -> z.file("pack/link", "/etc/passwd").unixMode(0120777)),
                        v(ENTRY_TYPE, "pack/link")),
                zip(
                        "zip character device",
                        zipOf(z -> z.file("pack/null", "").unixMode(020666)),
                        v(ENTRY_TYPE, "pack/null")),
                zip("zip FIFO", zipOf(z -> z.file("pack/pipe", "").unixMode(010644)), v(ENTRY_TYPE, "pack/pipe")),
                zip("zip socket", zipOf(z -> z.file("pack/sock", "").unixMode(0140755)), v(ENTRY_TYPE, "pack/sock")),
                zip(
                        "zip encrypted entry",
                        zipOf(z -> z.file("pack/secret.txt", "x").flags(RawZip.FLAG_ENCRYPTED)),
                        v(ENCRYPTED, "pack/secret.txt")),
                zip(
                        "zip bzip2 method",
                        zipOf(z -> z.file("pack/a.txt", "x").method(12)),
                        v(COMPRESSION_METHOD, "pack/a.txt")));
    }

    private static Stream<Hostile> zipEntryCountsAndNames() {
        return Stream.of(
                zip(
                        "zip end record claims 10,001 entries",
                        zipOf(z -> {
                            z.file("pack/a.txt", "x");
                            z.declaredCount(10_001);
                        }),
                        v(TOO_MANY_ENTRIES, "")),
                zip(
                        "zip ZIP64 end record claims 10,001 entries",
                        zipOf(z -> {
                            z.file("pack/a.txt", "x");
                            z.zip64Count(10_001);
                        }),
                        v(TOO_MANY_ENTRIES, "")),
                zip(
                        "zip with 10,001 central entries",
                        HostileArchiveCorpusTest::zipTenThousandAndOne,
                        v(TOO_MANY_ENTRIES, "")),
                zip(
                        "zip end record claims fewer entries than the headers hold",
                        zipOf(z -> {
                            z.file("pack/a.txt", "x");
                            z.file("pack/b.txt", "x");
                            z.declaredCount(1);
                        }),
                        v(ZIP_COUNT_MISMATCH, "")),
                zip(
                        "zip end record claims more entries than the headers hold",
                        zipOf(z -> {
                            z.file("pack/a.txt", "x");
                            z.declaredCount(2);
                        }),
                        v(ZIP_COUNT_MISMATCH, "")),
                zip(
                        "zip with an honest count and over 1,024 bytes of central extra fields",
                        zipOf(z -> z.file("pack/a.txt", "x").extra(unknownExtra(1_025))),
                        v(ZIP_EXTRA_FIELD_TOO_LARGE, "pack/a.txt")),
                zip(
                        "zip local and central names differ",
                        zipOf(z -> z.file("pack/a.txt", "x").localName("pack/b.txt")),
                        v(ZIP_NAME_MISMATCH, "pack/a.txt")),
                zip(
                        "zip overlapping entries",
                        zipOf(z -> {
                            z.file("pack/a.txt", "aaaa");
                            z.file("pack/b.txt", "bbbb").declaredCompressed(1_000);
                            z.file("pack/c.txt", "c".repeat(2_000));
                        }),
                        v(ZIP_OVERLAP, "pack/c.txt")),
                zip(
                        "zip non-ASCII name without the EFS flag",
                        zipOf(z -> z.file("x", "x").rawName("pack/caf\u00e9.txt".getBytes(StandardCharsets.UTF_8))),
                        v(ZIP_NAME_ENCODING, "pack/caf\u00e9.txt")),
                zip(
                        "zip non-ASCII name with the EFS flag",
                        zipOf(z -> z.file("pack/caf\u00e9.txt", "x").flags(RawZip.FLAG_EFS)),
                        v(PATH_CHARACTER, "pack/caf\u00e9.txt")),
                zip(
                        "zip Unicode Path extra field carrying ../ under a benign name",
                        zipOf(z -> z.file("pack/ok.txt", "x").extra(RawZip.unicodePathExtra("pack/ok.txt", "../evil"))),
                        v(ZIP_EXTRA_FIELD, "pack/ok.txt")));
    }

    private static Stream<Hostile> zipBombsAndStructure() {
        return Stream.of(
                zip(
                        "zip bomb with honest sizes",
                        zipOf(z -> z.add(RawZip.zeros("pack/big.bin", 40 * MIB))),
                        v(EXPANDED_TOO_LARGE, "pack/big.bin")),
                zip(
                        "zip bomb that lies about its size",
                        zipOf(z -> z.add(RawZip.zeros("pack/big.bin", 40 * MIB).declaredSize(10))),
                        v(EXPANDED_TOO_LARGE, "pack/big.bin")),
                zip(
                        "zip entry smaller than its declared size",
                        zipOf(z ->
                                z.file("pack/a.txt", "x".repeat(100)).deflated().declaredSize(5)),
                        v(ZIP_SIZE_MISMATCH, "pack/a.txt")),
                zip(
                        "zip duplicate after case folding",
                        zipOf(z -> {
                            z.file("pack/A.txt", "1");
                            z.file("pack/a.txt", "2");
                        }),
                        v(PATH_COLLISION, "pack/a.txt")),
                zip(
                        "zip the same file twice",
                        zipOf(z -> {
                            z.file("pack/a.txt", "1");
                            z.file("pack/a.txt", "2");
                        }),
                        v(PATH_COLLISION, "pack/a.txt")),
                zip(
                        "zip NUL in a name",
                        zipOf(z -> z.file("x", "x").rawName("pack/a\0b".getBytes(StandardCharsets.UTF_8))),
                        v(PATH_NUL, "pack/a\\u0000b")),
                zip(
                        "zip overlapping entries with the central directory in reverse order",
                        zipOf(z -> {
                            z.file("pack/a.txt", "aaaa");
                            z.file("pack/b.txt", "bbbb").declaredCompressed(1_000);
                            z.file("pack/c.txt", "c".repeat(2_000));
                            z.reverseCentral();
                        }),
                        v(ZIP_OVERLAP, "pack/c.txt")),
                zip(
                        "zip central offset past the end of the file",
                        zipOf(z -> z.file("pack/a.txt", "x").centralOffset(1_000_000)),
                        v(ARCHIVE_UNREADABLE, "")),
                zip(
                        "zip central offset not at a local header",
                        zipOf(z -> z.file("pack/a.txt", "x".repeat(100)).centralOffset(40)),
                        v(ARCHIVE_UNREADABLE, "pack/a.txt")));
    }

    private static Stream<Hostile> zipPathFormsAndLimits() {
        return Stream.of(
                zip("zip backslash", zipOf(z -> z.file("pack\\evil.txt", "x")), v(PATH_BACKSLASH, "pack\\evil.txt")),
                zip("zip drive letter", zipOf(z -> z.file("C:/evil.txt", "x")), v(PATH_DRIVE_LETTER, "C:/evil.txt")),
                zip("zip empty segment", zipOf(z -> z.file("pack//a.txt", "x")), v(PATH_EMPTY_SEGMENT, "pack//a.txt")),
                zip("zip dot segment", zipOf(z -> z.file("pack/./a.txt", "x")), v(PATH_DOT_SEGMENT, "pack/./a.txt")),
                zip(
                        "zip path over 4096 bytes",
                        zipOf(z -> z.file("a/".repeat(2_100), "x")),
                        v(PATH_TOO_LONG, "a/".repeat(128))),
                zip("zip body over 32 MiB", HostileArchiveCorpusTest::bigZipBody, v(BODY_TOO_LARGE, "")),
                zip(
                        "zip not a zip",
                        () -> "PK but not really".getBytes(StandardCharsets.UTF_8),
                        v(ARCHIVE_UNREADABLE, "")));
    }

    static Stream<Hostile> corpus() {
        return Stream.concat(hostileTars(), hostileZips());
    }

    @Test
    void theCorpusHoldsAtLeastTwentyTwoArchivesOfBothFormats() {
        assertThat(corpus().count()).isGreaterThanOrEqualTo(MINIMUM_CORPUS);
        assertThat(hostileTars().count()).isGreaterThanOrEqualTo(MINIMUM_CORPUS / 2);
        assertThat(hostileZips().count()).isGreaterThanOrEqualTo(MINIMUM_CORPUS / 2);
        assertThat(corpus().map(Hostile::id)).doesNotHaveDuplicates();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void everyHostileArchiveIsRefusedWithItsViolationAndNothingIsLeftBehind(Hostile row) throws IOException {
        Path parent = Files.createDirectory(work.resolve("parent"));
        Path staging = Files.createDirectory(parent.resolve("staging"));
        List<String> parentBefore = ArchiveFixtures.listing(parent);

        ArchiveRefusal refusal = refuse(row, staging);

        assertThat(refusal.violations()).as("violations of '%s'", row.id()).containsExactlyElementsOf(row.expected());
        assertThat(refusal.decompressedBytes()).isLessThanOrEqualTo(READ_BOUND);
        assertThat(ArchiveFixtures.listing(staging))
                .as("staging after a refusal")
                .isEmpty();
        assertThat(ArchiveFixtures.listing(parent)).as("the staging parent").isEqualTo(parentBefore);
        assertThat(ArchiveFixtures.listing(work)).containsExactly("parent");
    }

    @Test
    void aHugeMetaHeaderIsRefusedBeforeItsBodyIsReadOrParsed() throws IOException {
        byte[] tar = new RawTar()
                .entry("PaxHeader/0", (byte) 'x', new byte[28 * (int) MIB], "")
                .file("ok.txt", "x")
                .gz();
        Path staging = Files.createDirectory(work.resolve("staging"));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() ->
                new BoundedArchiveExtractor().extract(new ByteArrayInputStream(tar), ArchiveFormat.TAR_GZ, staging));

        assertThat(thrown).isInstanceOf(ArchiveRefusal.class);
        ArchiveRefusal refusal = (ArchiveRefusal) thrown;
        assertThat(refusal.violations()).containsExactly(v(HEADER_TOO_LARGE, ""));
        assertThat(refusal.decompressedBytes()).isLessThanOrEqualTo(1_024);
    }

    @Test
    void aBodyOverTheLimitIsTheOneRefusalAnsweredAsTooLarge() throws IOException {
        Hostile row = hostileZips()
                .filter(h -> h.id().equals("zip body over 32 MiB"))
                .findFirst()
                .orElseThrow();
        Path staging = Files.createDirectory(work.resolve("staging"));

        assertThat(refuse(row, staging).bodyTooLarge()).isTrue();
        assertThat(refuse(
                                hostileZips()
                                        .filter(h -> h.id().equals("zip zip-slip"))
                                        .findFirst()
                                        .orElseThrow(),
                                staging)
                        .bodyTooLarge())
                .isFalse();
    }

    /** One benign archive and the tree it must extract to. */
    record Benign(String id, ArchiveFormat format, Supplier<byte[]> archive, Map<String, String> tree) {
        @Override
        public String toString() {
            return id;
        }
    }

    static Stream<Benign> benign() {
        Map<String, String> plain = Map.of("pack.yaml", "name: p\n");
        Map<String, String> nested = Map.of("agents/", "", "agents/a.yaml", "a: 1\n", "pack.yaml", "name: p\n");
        return Stream.of(benignTars(plain, nested), benignBoundaryCases(plain), benignZips(plain, nested))
                .flatMap(rows -> rows);
    }

    private static Stream<Benign> benignTars(Map<String, String> plain, Map<String, String> nested) {
        return Stream.of(
                new Benign(
                        "tar regular file", ArchiveFormat.TAR_GZ, tarOf(t -> t.file("pack.yaml", "name: p\n")), plain),
                new Benign(
                        "tar nested directory",
                        ArchiveFormat.TAR_GZ,
                        tarOf(t ->
                                t.file("pack.yaml", "name: p\n").dir("agents/").file("agents/a.yaml", "a: 1\n")),
                        nested),
                new Benign(
                        "tar ./ prefix and a root directory entry",
                        ArchiveFormat.TAR_GZ,
                        tarOf(t -> t.dir("./")
                                .file("./pack.yaml", "name: p\n")
                                .dir("./agents/")
                                .file("./agents/a.yaml", "a: 1\n")),
                        nested),
                new Benign(
                        "tar one shared leading directory",
                        ArchiveFormat.TAR_GZ,
                        tarOf(t -> t.dir("mypack/")
                                .file("mypack/pack.yaml", "name: p\n")
                                .dir("mypack/agents/")
                                .file("mypack/agents/a.yaml", "a: 1\n")),
                        nested),
                new Benign(
                        "tar file named like a link",
                        ArchiveFormat.TAR_GZ,
                        tarOf(t -> t.file("link", "not a link\n")),
                        Map.of("link", "not a link\n")),
                new Benign(
                        "tar PAX path to a safe name",
                        ArchiveFormat.TAR_GZ,
                        tarOf(t -> t.paxPath("pack.yaml").file("PaxHeader-name", "name: p\n")),
                        plain),
                new Benign(
                        "tar PAX header of exactly 64 KiB",
                        ArchiveFormat.TAR_GZ,
                        tarOf(t -> t.pax(Map.of("comment", "a".repeat(65_521))).file("pack.yaml", "name: p\n")),
                        plain));
    }

    private static Stream<Benign> benignBoundaryCases(Map<String, String> plain) {
        return Stream.of(
                new Benign(
                        "zip with exactly 1,024 bytes of central extra fields",
                        ArchiveFormat.ZIP,
                        zipOf(z -> z.file("pack.yaml", "name: p\n").extra(unknownExtra(1_024))),
                        plain),
                new Benign(
                        "tar GNU long name that is safe",
                        ArchiveFormat.TAR_GZ,
                        tarOf(t -> t.gnuLongName("pack/" + "d/".repeat(60) + "x.txt")
                                .file("short", "x")),
                        longNameTree()),
                new Benign(
                        "tar names differing beyond case",
                        ArchiveFormat.TAR_GZ,
                        tarOf(t -> t.file("a.yaml", "1").file("b.yaml", "2")),
                        Map.of("a.yaml", "1", "b.yaml", "2")),
                new Benign(
                        "zip with a ZIP64 end record",
                        ArchiveFormat.ZIP,
                        zipOf(z -> {
                            z.file("pack.yaml", "name: p\n");
                            z.zip64Count(1);
                        }),
                        plain),
                new Benign(
                        "tar and an empty directory",
                        ArchiveFormat.TAR_GZ,
                        tarOf(t -> t.file("pack.yaml", "name: p\n").dir("empty/")),
                        Map.of("pack.yaml", "name: p\n", "empty/", "")),
                new Benign(
                        "zip and an empty directory",
                        ArchiveFormat.ZIP,
                        zipOf(z -> {
                            z.file("pack.yaml", "name: p\n");
                            z.dir("empty/");
                        }),
                        Map.of("pack.yaml", "name: p\n", "empty/", "")),
                new Benign("zip regular file", ArchiveFormat.ZIP, zipOf(z -> z.file("pack.yaml", "name: p\n")), plain));
    }

    private static Stream<Benign> benignZips(Map<String, String> plain, Map<String, String> nested) {
        return Stream.of(
                new Benign(
                        "zip nested directory, deflated",
                        ArchiveFormat.ZIP,
                        zipOf(z -> {
                            z.file("pack.yaml", "name: p\n").deflated();
                            z.dir("agents/");
                            z.file("agents/a.yaml", "a: 1\n").deflated();
                        }),
                        nested),
                new Benign(
                        "zip ./ prefix",
                        ArchiveFormat.ZIP,
                        zipOf(z -> {
                            z.file("./pack.yaml", "name: p\n");
                            z.file("./agents/a.yaml", "a: 1\n");
                        }),
                        Map.of("pack.yaml", "name: p\n", "agents/", "", "agents/a.yaml", "a: 1\n")),
                new Benign(
                        "zip one shared leading directory",
                        ArchiveFormat.ZIP,
                        zipOf(z -> {
                            z.dir("mypack/");
                            z.file("mypack/pack.yaml", "name: p\n");
                            z.dir("mypack/agents/");
                            z.file("mypack/agents/a.yaml", "a: 1\n");
                        }),
                        nested),
                new Benign(
                        "zip EFS-flagged ASCII name",
                        ArchiveFormat.ZIP,
                        zipOf(z -> z.file("pack.yaml", "name: p\n").flags(RawZip.FLAG_EFS)),
                        plain),
                new Benign(
                        "zip with Unix modes on files and directories",
                        ArchiveFormat.ZIP,
                        zipOf(z -> {
                            z.file("pack.yaml", "name: p\n").unixMode(0100644);
                            z.dir("agents/");
                            z.file("agents/a.yaml", "a: 1\n").unixMode(0100644);
                        }),
                        nested),
                new Benign(
                        "zip file named like a link and carrying no Unix mode",
                        ArchiveFormat.ZIP,
                        zipOf(z -> z.file("link", "not a link\n")),
                        Map.of("link", "not a link\n")));
    }

    private static Map<String, String> longNameTree() {
        Map<String, String> tree = new java.util.TreeMap<>();
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            path.append("d/");
            tree.put(path.toString(), "");
        }
        tree.put(path + "x.txt", "x");
        return tree;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("benign")
    void aBenignArchiveOfTheSameShapeExtractsToTheExpectedTree(Benign row) throws Exception {
        Path parent = Files.createDirectory(work.resolve("parent"));
        Path staging = Files.createDirectory(parent.resolve("staging"));

        StagedPack staged = new BoundedArchiveExtractor()
                .extract(new ByteArrayInputStream(row.archive().get()), row.format(), staging);

        assertThat(ArchiveFixtures.tree(staged.directory()))
                .containsExactlyInAnyOrderEntriesOf(expectedWithParents(row.tree()));
        assertThat(staged.directory().toRealPath()).startsWith(staging.toRealPath());
        ArchiveFixtures.assertOwnerOnly(staged.directory());
        assertThat(ArchiveFixtures.listing(parent)).containsExactly("staging");
    }

    private static Map<String, String> expectedWithParents(Map<String, String> tree) {
        Map<String, String> all = new java.util.TreeMap<>(tree);
        for (String path : tree.keySet()) {
            for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1)) {
                all.putIfAbsent(path.substring(0, slash + 1), "");
            }
        }
        return all;
    }

    @Test
    void theBenignControlsCoverEveryShapeTheCorpusAttacks() {
        assertThat(benign().map(Benign::id))
                .contains(
                        "tar regular file",
                        "tar nested directory",
                        "tar ./ prefix and a root directory entry",
                        "tar one shared leading directory",
                        "zip EFS-flagged ASCII name");
        assertThat(benign().count()).isGreaterThanOrEqualTo(10);
    }

    private ArchiveRefusal refuse(Hostile row, Path staging) {
        byte[] bytes = row.archive().get();
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> new BoundedArchiveExtractor().extract(new ByteArrayInputStream(bytes), row.format(), staging));
        assertThat(thrown).as("'%s' must be refused", row.id()).isInstanceOf(ArchiveRefusal.class);
        return (ArchiveRefusal) thrown;
    }

    private static byte[] truncated(byte[] gz) {
        return Arrays.copyOf(gz, gz.length / 2);
    }

    private static byte[] manyMebibyteEntries() {
        RawTar tar = new RawTar();
        byte[] mebibyte = new byte[(int) MIB];
        IntStream.range(0, 40).forEach(i -> tar.entry("pack/f" + i + ".bin", (byte) '0', mebibyte, ""));
        return tar.gz();
    }

    private static byte[] tenThousandAndOne() {
        RawTar tar = new RawTar();
        IntStream.rangeClosed(0, 10_000).forEach(i -> tar.file("pack/f" + i + ".txt", ""));
        return tar.gz();
    }

    private static byte[] unknownExtra(int total) {
        return java.nio.ByteBuffer.allocate(total)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 0xCAFE)
                .putShort((short) (total - 4))
                .array();
    }

    private static byte[] zipTenThousandAndOne() {
        RawZip zip = new RawZip();
        IntStream.rangeClosed(0, 10_000).forEach(i -> zip.file("pack/f" + i + ".txt", ""));
        zip.declaredCount(1);
        return zip.build();
    }

    private static byte[] bigZipBody() {
        byte[] random = new byte[(int) (33 * MIB)];
        new Random(7).nextBytes(random);
        RawZip zip = new RawZip();
        zip.bytes("pack/big.bin", random);
        return zip.build();
    }

    @Test
    void anExtractorThatIsGivenAnUnusableStagingRootFailsLoudly() {
        assertThatThrownBy(() -> new BoundedArchiveExtractor()
                        .extract(
                                new ByteArrayInputStream(
                                        new RawTar().file("a", "b").gz()),
                                ArchiveFormat.TAR_GZ,
                                work.resolve("does-not-exist")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
