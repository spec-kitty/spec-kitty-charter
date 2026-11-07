package ai.speckitty.charter.packs.infra.archive;

import java.io.IOException;
import java.nio.channels.FileChannel;

/** Stub: the central-directory walk is not written yet. */
final class ZipCentralDirectory {

    ZipCentralDirectory(FileChannel channel, ArchiveLimits limits) {}

    void verify() throws IOException {}

    int headersRead() {
        return 0;
    }
}
