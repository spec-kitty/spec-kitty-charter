package ai.speckitty.charter.packs.infra.archive;

import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Reads a gzip-compressed tar (IC-03). The JDK's {@link GZIPInputStream} feeds a {@link TarArchiveInputStream} created
 * directly (no factory, strict header parsing); a counting bound sits on the decompressed stream, so a bomb stops at
 * 32 MiB + 64 KiB read. An entry's type is judged from its raw link flag against an allowlist, and its name is checked
 * after PAX and GNU long-name headers have been applied ({@code getName()}).
 */
final class TarGzReader {

    private static final int RECORD_SIZE = 512;
    private static final int BLOCK_SIZE = 20 * RECORD_SIZE;
    private static final byte FLAG_FILE = '0';
    private static final byte FLAG_OLD_FILE = 0;
    private static final byte FLAG_DIRECTORY = '5';

    private final ArchiveLimits limits;

    TarGzReader(ArchiveLimits limits) {
        this.limits = limits;
    }

    /** Reads the archive into the tree; the body is read once and not closed. */
    void read(InputStream body, PackTree tree) throws IOException {
        LimitedInputStream compressed = new LimitedInputStream(body, limits.maxBodyBytes(), Code.BODY_TOO_LARGE, true);
        try (GZIPInputStream gzip = new GZIPInputStream(compressed)) {
            LimitedInputStream plain =
                    new LimitedInputStream(gzip, limits.maxDecompressedRead(), Code.EXPANDED_TOO_LARGE, false);
            try {
                readEntries(plain, tree);
            } finally {
                tree.recordDecompressed(plain.count());
            }
        }
    }

    private void readEntries(InputStream plain, PackTree tree) throws IOException {
        try (TarArchiveInputStream tar = new TarArchiveInputStream(plain, BLOCK_SIZE, RECORD_SIZE, "UTF-8", false)) {
            int count = 0;
            for (TarArchiveEntry entry = tar.getNextEntry(); entry != null; entry = tar.getNextEntry()) {
                if (++count > limits.maxEntries()) {
                    throw new ArchiveLimitException(Code.TOO_MANY_ENTRIES, "");
                }
                accept(entry, tar, tree);
            }
        }
    }

    private static void accept(TarArchiveEntry entry, InputStream data, PackTree tree) throws IOException {
        String name = entry.getName();
        if (!isFileOrDirectory(entry)) {
            tree.violation(Code.ENTRY_TYPE, name);
            return;
        }
        List<String> segments = tree.admit(name, entry.isDirectory());
        if (segments == null || segments.isEmpty() || !tree.clean()) {
            return;
        }
        if (entry.isDirectory()) {
            tree.makeDirectory(segments);
            return;
        }
        tree.reserve(name, entry.getSize());
        tree.writeFile(segments, data);
    }

    private static boolean isFileOrDirectory(TarArchiveEntry entry) {
        byte flag = entry.getLinkFlag();
        boolean allowed = flag == FLAG_FILE || flag == FLAG_OLD_FILE || flag == FLAG_DIRECTORY;
        return allowed && !entry.isSparse();
    }
}
