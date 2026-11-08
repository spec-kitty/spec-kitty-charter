package ai.speckitty.charter.packs.application;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The port of the staging directory a registration works in (#418, A8): the root, an owner-only directory for one job,
 * an owner-only directory inside it, and the removal of a job's directory. The application ring names this interface and
 * never touches the file system for the lifecycle of what it stages; the adapter is {@code packs.infra.storage}'s.
 *
 * <p>Everything it creates is the owner's alone (plan, security squad 8): a staged upload is untrusted input of one
 * client, and the process may share a host or a volume with others.
 */
public interface StagingArea {

    /**
     * The staging root: the directory every job directory lies in, which the sweep works in.
     *
     * @return the root, absolute and normalised
     */
    Path root();

    /**
     * Creates the directory of one job, {@code <root>/<id>}, and the root and every parent that is missing, each with mode
     * {@code 0700}.
     *
     * @param id the registration id, a ULID
     * @return the directory
     * @throws IOException when it cannot be made
     */
    Path createJobDirectory(String id) throws IOException;

    /**
     * Creates a directory, and any parent that is missing, with mode {@code 0700}; one that exists is left as it is.
     *
     * @param directory the directory, inside a job's directory
     * @return the directory
     * @throws IOException when it cannot be made
     */
    Path createDirectory(Path directory) throws IOException;

    /**
     * Removes a job's directory and everything in it, links as links (a link is removed and never followed). A directory
     * that is not there is not an error, and one that cannot be removed is logged and left to the sweep.
     *
     * @param directory a job's directory, inside the root
     * @throws IllegalArgumentException when the directory is the root or lies outside it
     */
    void delete(Path directory);
}
