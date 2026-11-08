package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import ai.speckitty.charter.packs.application.RegistrationJobs.Stager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The stager of an uploaded archive: it extracts the file the request body was spooled to into the job's staging
 * directory, through the bounded extractor (IC-03), outside every lock. An archive the extractor refuses is a
 * {@code 422 validation_failed} that names each entry and the rule it broke, whatever the rule: a body that expands past
 * its bound is a {@code 422} too, the {@code 413} being the synchronous answer to a body that is too large to take.
 */
public final class ArchiveStaging {

    private static final String EXTRACTED = "extract";

    private ArchiveStaging() {}

    /**
     * A stager for an archive that is already on disk.
     *
     * @param reader the extractor
     * @param format the archive's format
     * @param archive the spooled body
     * @return the stager
     */
    public static Stager of(PackArchiveReader reader, ArchiveFormat format, Path archive) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(archive, "archive");
        return directory -> {
            Path into = OwnerOnly.directories(directory.resolve(EXTRACTED));
            try (InputStream body = Files.newInputStream(archive)) {
                return new Staged(reader.extract(body, format, into), Map.of());
            } catch (ArchiveRefusal refused) {
                throw refusalOf(refused);
            } catch (IOException unreadable) {
                throw new IllegalStateException("cannot read the spooled archive", unreadable);
            }
        };
    }

    /**
     * The {@code 422 validation_failed} an archive the extractor refuses ends a registration with, naming each entry and
     * the rule it broke. A source that walks a tree rather than an archive (git) answers a tree the writer refuses the same
     * way.
     *
     * @param refused what the extractor or the tree writer found
     * @return the refusal
     */
    public static CharterRefusal refusalOf(ArchiveRefusal refused) {
        List<RefusalViolation> violations = refused.violations().stream()
                .map(violation -> new RefusalViolation(
                        "",
                        violation.code().wire(),
                        violation.path().isEmpty()
                                ? "The archive is refused: " + violation.code().wire() + "."
                                : "The archive entry " + violation.path() + " is refused: "
                                        + violation.code().wire() + "."))
                .toList();
        String more = refused.omitted() > 0 ? " " + refused.omitted() + " more are not listed." : "";
        return CharterRefusal.withViolations(
                RefusalCode.VALIDATION_FAILED,
                "The archive is refused. " + refused.getMessage() + "." + more,
                violations);
    }
}
