package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A reader written over a stream (as every test double is) still extracts an archive that is a file, by reading the
 * file as a stream: the file form is an optimisation an adapter may override, never a requirement of the port.
 */
class PackArchiveReaderTest {

    @TempDir
    Path work;

    @Test
    void aReaderThatTakesAStreamIsGivenTheBytesOfTheFile() throws Exception {
        Path archive = work.resolve("archive");
        Files.write(archive, new byte[] {1, 2, 3});
        List<byte[]> seen = new ArrayList<>();
        PackArchiveReader reader = (InputStream body, ArchiveFormat format, Path into) -> {
            try {
                seen.add(body.readAllBytes());
            } catch (IOException unreadable) {
                throw new IllegalStateException(unreadable);
            }
            return new StagedPack(into);
        };

        StagedPack staged = reader.extract(archive, ArchiveFormat.TAR_GZ, work);

        assertThat(seen).singleElement().isEqualTo(new byte[] {1, 2, 3});
        assertThat(staged.directory()).isEqualTo(work);
    }
}
