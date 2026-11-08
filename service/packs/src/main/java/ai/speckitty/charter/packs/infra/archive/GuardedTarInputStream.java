package ai.speckitty.charter.packs.infra.archive;

import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarUtils;
import org.jspecify.annotations.Nullable;

/**
 * A {@link TarArchiveInputStream} that judges the tar's meta headers before the library parses them (NFR-004, the
 * operator's ruling DM-01M6TZRAA7JQR2H5WN4SKNXGQ8). The library keeps every PAX record, and every byte of a GNU long
 * name, in memory, and the decompressed-byte bound does not stop that: a 28 MiB PAX header of three million records
 * costs about 384 MB of heap. Every header record the library reads passes through {@link #readRecord()}, so a PAX
 * extended ({@code x}, {@code X}), PAX global ({@code g}) or GNU long-name or long-link ({@code L}, {@code K}) header
 * whose declared size is over 64 KiB is refused {@code header_too_large} on its raw 512-byte block, before the
 * library has seen the body: the library's next read is never made. A header within the cap is buffered, as far as
 * its body is read by the library, so a PAX {@code path} or a GNU long name that begins with {@code /} can be refused
 * {@code path_absolute}: the library would otherwise make it relative without a word.
 */
final class GuardedTarInputStream extends TarArchiveInputStream {

    /** The most bytes a PAX or GNU long-name header may declare. */
    static final int MAX_META_BYTES = 64 * 1024;

    private static final int RECORD_SIZE = 512;
    private static final int BLOCK_SIZE = 20 * RECORD_SIZE;
    private static final int SIZE_AT = 124;
    private static final int SIZE_LENGTH = 12;
    private static final int TYPE_AT = 156;
    private static final byte LONG_NAME = 'L';
    private static final byte LONG_LINK = 'K';

    private final MetaBody body;

    private GuardedTarInputStream(MetaBody body) {
        super(body, BLOCK_SIZE, RECORD_SIZE, "UTF-8", false);
        this.body = body;
    }

    /** A guarded reader of the plain (decompressed) tar stream. */
    static GuardedTarInputStream open(InputStream plain) {
        return new GuardedTarInputStream(new MetaBody(plain));
    }

    @Override
    protected byte @Nullable [] readRecord() throws IOException {
        byte[] record = super.readRecord();
        if (record != null) {
            body.header(record);
        }
        return record;
    }

    /** The stream under the library: it keeps the body of a meta header, which is at most 64 KiB, for inspection. */
    private static final class MetaBody extends FilterInputStream {

        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        private final byte[] one = new byte[1];
        private byte kind;
        private int pending;

        MetaBody(InputStream in) {
            super(in);
        }

        /** Judges a header record just read, before the library parses it. */
        void header(byte[] record) throws IOException {
            captured.reset();
            pending = 0;
            byte type = record[TYPE_AT];
            if (!isMeta(type)) {
                return;
            }
            long size = declaredSize(record);
            if (size < 0 || size > MAX_META_BYTES) {
                throw new ArchiveLimitException(Code.HEADER_TOO_LARGE, "");
            }
            if (type != LONG_LINK) {
                kind = type;
                pending = (int) size;
            }
        }

        private static boolean isMeta(byte type) {
            return type == 'x' || type == 'X' || type == 'g' || type == LONG_NAME || type == LONG_LINK;
        }

        /** The size a header declares; an unparsable field is the library's to refuse, so it counts as zero here. */
        private static long declaredSize(byte[] record) {
            try {
                return TarUtils.parseOctalOrBinary(record, SIZE_AT, SIZE_LENGTH);
            } catch (IllegalArgumentException e) {
                return 0;
            }
        }

        @Override
        public int read() throws IOException {
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            if (n > 0 && pending > 0) {
                absorb(b, off, n);
            }
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            if (pending == 0) {
                return in.skip(n);
            }
            byte[] scratch = new byte[(int) Math.min(n, 4096)];
            int read = read(scratch, 0, scratch.length);
            return Math.max(read, 0);
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        private void absorb(byte[] b, int off, int n) throws IOException {
            int take = Math.min(pending, n);
            captured.write(b, off, take);
            pending -= take;
            if (pending == 0) {
                refuseAbsolutePath();
            }
        }

        private void refuseAbsolutePath() throws IOException {
            byte[] data = captured.toByteArray();
            String path = kind == LONG_NAME ? longName(data) : PaxRecords.path(data);
            if (path != null && path.startsWith("/")) {
                throw new ArchiveLimitException(Code.PATH_ABSOLUTE, path);
            }
        }

        private static @Nullable String longName(byte[] data) {
            int end = 0;
            while (end < data.length && data[end] != 0) {
                end++;
            }
            return new String(data, 0, end, StandardCharsets.UTF_8);
        }
    }
}
