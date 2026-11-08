package ai.speckitty.charter.packs.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The port that turns an uploaded pack archive into a staged pack directory (IC-03). The adapter lives in
 * {@code packs.infra.archive}; the application ring names only this interface.
 */
public interface PackArchiveReader {

    /**
     * Extracts the archive into a fresh directory inside the staging root, or refuses it.
     *
     * @param body the archive's bytes; read once and not closed
     * @param format the archive's format
     * @param stagingRoot an existing directory owned by the caller; the extractor writes only inside it
     * @return the staged pack
     * @throws ArchiveRefusal when the archive is over a limit or any entry breaks a path or type rule; nothing is left
     *     in the staging root
     */
    StagedPack extract(InputStream body, ArchiveFormat format, Path stagingRoot) throws ArchiveRefusal;

    /**
     * Extracts an archive that is already a file, as {@link #extract(InputStream, ArchiveFormat, Path)} does a stream. An
     * adapter overrides it to read the file where it lies instead of copying it (#418, A6: the upload's spool is not
     * spooled a second time); the default reads the file as a stream.
     *
     * @param archive the archive file, which is the caller's and is left as found
     * @param format the archive's format
     * @param stagingRoot an existing directory owned by the caller; the extractor writes only inside it
     * @return the staged pack
     * @throws ArchiveRefusal when the archive is over a limit or any entry breaks a path or type rule; nothing is left
     *     in the staging root
     * @throws IllegalStateException when the file cannot be read
     */
    default StagedPack extract(Path archive, ArchiveFormat format, Path stagingRoot) throws ArchiveRefusal {
        try (InputStream body = Files.newInputStream(archive)) {
            return extract(body, format, stagingRoot);
        } catch (IOException unreadable) {
            throw new IllegalStateException("cannot read the spooled archive", unreadable);
        }
    }
}
