package ai.speckitty.charter.packs.infra.archive;

import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.archivers.zip.ZipShort;
import org.jspecify.annotations.Nullable;

/**
 * Reads a zip (IC-03). The body is spooled to an owner-only file in staging; the entry count is read from the
 * end-of-central-directory record before any entry; {@link ZipFile} is built with its builder and without the Unicode
 * extra fields. Every entry is inspected before any is written: encrypted entries, methods other than STORED and
 * DEFLATED, Unicode extra fields, non-ASCII names without the EFS flag, a local name that differs from the central
 * one, overlapping data and Unix link, device, FIFO and socket modes are refused. A zip with no Unix mode carries no
 * link flag, so its entries are plain files.
 */
final class ZipReader {

    static final String SPOOL_NAME = "upload.zip.spool";
    private static final int UNIX_TYPE_MASK = 0170000;
    private static final int UNIX_FILE = 0100000;
    private static final int UNIX_DIRECTORY = 040000;
    private static final ZipShort UNICODE_PATH = new ZipShort(0x7075);
    private static final ZipShort UNICODE_COMMENT = new ZipShort(0x6375);

    private final ArchiveLimits limits;

    ZipReader(ArchiveLimits limits) {
        this.limits = limits;
    }

    /** One entry that passed inspection and will be written. */
    private record Planned(ZipArchiveEntry entry, String name, List<String> segments) {}

    /** The bytes an entry occupies in the file, from its local header to the end of its data. */
    private record Span(long start, long end, String name) {}

    /** Reads the archive into the tree; the body is read once and not closed. */
    void read(InputStream body, PackTree tree, Path stagingRoot) throws IOException {
        Path spool = stagingRoot.resolve(SPOOL_NAME);
        try {
            spool(body, spool);
            try (FileChannel channel = FileChannel.open(spool, StandardOpenOption.READ)) {
                readSpooled(channel, tree);
            }
        } finally {
            Files.deleteIfExists(spool);
        }
    }

    private void spool(InputStream body, Path spool) throws IOException {
        LimitedInputStream bounded = new LimitedInputStream(body, limits.maxBodyBytes(), Code.BODY_TOO_LARGE, true);
        java.io.OutputStream out = ArchiveIo.createFile(spool);
        try {
            ArchiveIo.copy(bounded, out);
        } finally {
            ArchiveIo.close(out);
        }
    }

    private void readSpooled(FileChannel channel, PackTree tree) throws IOException {
        if (ZipEndRecord.entryCount(channel) > limits.maxEntries()) {
            throw new ArchiveLimitException(Code.TOO_MANY_ENTRIES, "");
        }
        try (ZipFile zip = ZipFile.builder()
                .setSeekableByteChannel(channel)
                .setUseUnicodeExtraFields(false)
                .get()) {
            List<Planned> planned = inspect(zip, channel, tree);
            if (tree.clean()) {
                for (Planned p : planned) {
                    write(zip, p, tree);
                }
            }
        }
    }

    private List<Planned> inspect(ZipFile zip, FileChannel channel, PackTree tree) throws IOException {
        List<Planned> planned = new ArrayList<>();
        List<Span> spans = new ArrayList<>();
        int count = 0;
        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        while (entries.hasMoreElements()) {
            if (++count > limits.maxEntries()) {
                throw new ArchiveLimitException(Code.TOO_MANY_ENTRIES, "");
            }
            inspectEntry(entries.nextElement(), channel, tree, planned, spans);
        }
        reportOverlaps(spans, tree);
        return planned;
    }

    private void inspectEntry(
            ZipArchiveEntry entry, FileChannel channel, PackTree tree, List<Planned> planned, List<Span> spans)
            throws IOException {
        String name = new String(entry.getRawName(), StandardCharsets.UTF_8);
        ZipLocalHeader local = ZipLocalHeader.read(channel, entry.getLocalHeaderOffset());
        if (local == null) {
            tree.violation(Code.ARCHIVE_UNREADABLE, name);
            return;
        }
        spans.add(new Span(entry.getLocalHeaderOffset(), local.dataStart() + entry.getCompressedSize(), name));
        Code refused = entryViolation(entry, local);
        if (refused != null) {
            tree.violation(refused, name);
            return;
        }
        List<String> segments = tree.admit(name, entry.isDirectory());
        if (segments == null || segments.isEmpty()) {
            return;
        }
        if (!entry.isDirectory()) {
            tree.reserve(name, entry.getSize());
        }
        planned.add(new Planned(entry, name, segments));
    }

    private static @Nullable Code entryViolation(ZipArchiveEntry entry, ZipLocalHeader local) {
        if (entry.getGeneralPurposeBit().usesEncryption()) {
            return Code.ENCRYPTED;
        }
        int method = entry.getMethod();
        if (method != ZipArchiveEntry.STORED && method != ZipArchiveEntry.DEFLATED) {
            return Code.COMPRESSION_METHOD;
        }
        if (entry.getExtraField(UNICODE_PATH) != null || entry.getExtraField(UNICODE_COMMENT) != null) {
            return Code.ZIP_EXTRA_FIELD;
        }
        if (!isAscii(entry.getRawName()) && !entry.getGeneralPurposeBit().usesUTF8ForNames()) {
            return Code.ZIP_NAME_ENCODING;
        }
        if (!local.nameEquals(entry.getRawName())) {
            return Code.ZIP_NAME_MISMATCH;
        }
        return isFileOrDirectory(entry.getUnixMode()) ? null : Code.ENTRY_TYPE;
    }

    private static boolean isFileOrDirectory(int unixMode) {
        int type = unixMode & UNIX_TYPE_MASK;
        return type == 0 || type == UNIX_FILE || type == UNIX_DIRECTORY;
    }

    private static boolean isAscii(byte[] name) {
        for (byte b : name) {
            if (b < 0) {
                return false;
            }
        }
        return true;
    }

    private static void reportOverlaps(List<Span> spans, PackTree tree) {
        List<Span> ordered = new ArrayList<>(spans);
        ordered.sort(Comparator.comparingLong(Span::start));
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i).start() < ordered.get(i - 1).end()) {
                tree.violation(Code.ZIP_OVERLAP, ordered.get(i).name());
            }
        }
    }

    private void write(ZipFile zip, Planned planned, PackTree tree) throws IOException {
        if (planned.entry().isDirectory()) {
            tree.makeDirectory(planned.segments());
            return;
        }
        long budget = tree.remainingBudget();
        try (InputStream in = zip.getInputStream(planned.entry())) {
            LimitedInputStream bounded = new LimitedInputStream(in, budget, Code.EXPANDED_TOO_LARGE, true);
            long n = copy(planned, bounded, tree);
            tree.recordDecompressed(n);
            if (n != planned.entry().getSize()) {
                tree.violation(Code.ZIP_SIZE_MISMATCH, planned.name());
            }
        }
    }

    private static long copy(Planned planned, LimitedInputStream bounded, PackTree tree) throws IOException {
        try {
            return tree.writeFile(planned.segments(), bounded);
        } catch (ArchiveLimitException e) {
            tree.recordDecompressed(bounded.count());
            throw e.at(planned.name());
        }
    }
}
