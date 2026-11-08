package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.infra.storage.FileStagingArea;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The stager of an uploaded archive hands the extractor the spooled file itself, not a second stream of it (#418, A6). */
class ArchiveStagingTest {

    @TempDir
    Path work;

    @Test
    void theExtractorIsGivenTheFileTheBodyWasSpooledTo() throws Exception {
        FileStagingArea staging = new FileStagingArea(work.resolve("staging"));
        Path job = staging.createJobDirectory("01J0000000000000000000000A");
        Path archive = Files.write(job.resolve("archive"), new byte[] {1, 2, 3});
        List<Path> files = new ArrayList<>();
        PackArchiveReader reader = new PackArchiveReader() {
            @Override
            public StagedPack extract(InputStream body, ArchiveFormat format, Path into) {
                throw new AssertionError("the stream form must not be used for a file");
            }

            @Override
            public StagedPack extract(Path file, ArchiveFormat format, Path into) {
                files.add(file);
                return new StagedPack(into);
            }
        };

        RegistrationJobs.Staged staged =
                ArchiveStaging.of(staging, reader, ArchiveFormat.ZIP, archive).stage(job);

        assertThat(files).containsExactly(archive);
        assertThat(staged.pack().directory()).isEqualTo(job.resolve("extract"));
    }

    @Test
    void anUnreadableArchiveIsTheServicesFailureNotTheClients() throws IOException {
        FileStagingArea staging = new FileStagingArea(work.resolve("staging"));
        Path job = staging.createJobDirectory("01J0000000000000000000000B");
        PackArchiveReader reader = (InputStream body, ArchiveFormat format, Path into) -> new StagedPack(into);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> ArchiveStaging.of(staging, reader, ArchiveFormat.ZIP, job.resolve("missing"))
                                .stage(job))
                .isInstanceOf(IllegalStateException.class);
    }
}
