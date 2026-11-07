package ai.speckitty.charter.packs.infra.archive;

import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import java.io.IOException;
import java.io.InputStream;

/**
 * Counts what is read through it and refuses to deliver more than its limit (NFR-004). It never closes the stream it
 * wraps. In the strict form a read at the limit fails without touching the source, so the source is never read past the
 * limit; in the probing form it reads one byte beyond to tell "exactly the limit" from "over it".
 */
final class LimitedInputStream extends InputStream {

    private final InputStream source;
    private final long limit;
    private final Code code;
    private final boolean probe;
    private long count;

    LimitedInputStream(InputStream source, long limit, Code code, boolean probe) {
        this.source = source;
        this.limit = limit;
        this.code = code;
        this.probe = probe;
    }

    /** The bytes read through this stream so far. */
    long count() {
        return count;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n < 0 ? -1 : one[0] & 0xff;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        long room = limit - count;
        if (room <= 0 && !probe) {
            throw new ArchiveLimitException(code, "");
        }
        int ask = (int) Math.min(length, probe ? room + 1 : room);
        int n = source.read(buffer, offset, ask);
        if (n > 0) {
            count += n;
        }
        if (count > limit) {
            throw new ArchiveLimitException(code, "");
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        byte[] sink = new byte[(int) Math.min(Math.max(n, 0), 8192)];
        int read = read(sink, 0, sink.length);
        return Math.max(read, 0);
    }

    @Override
    public int available() throws IOException {
        return source.available();
    }

    @Override
    public void close() {
        // The caller owns the source.
    }
}
