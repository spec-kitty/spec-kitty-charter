package ai.speckitty.charter.packs.infra.archive;

import ai.speckitty.charter.packs.application.ArchiveViolation.Code;
import java.io.IOException;
import java.io.Serial;

/** A limit was reached while the archive was being read; it ends the extraction and becomes one violation. */
final class ArchiveLimitException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Code code;
    private final String path;

    ArchiveLimitException(Code code, String path) {
        super(code.wire());
        this.code = code;
        this.path = path;
    }

    Code code() {
        return code;
    }

    String path() {
        return path;
    }

    /** The same limit, attributed to an entry. */
    ArchiveLimitException at(String entryPath) {
        return new ArchiveLimitException(code, entryPath);
    }
}
