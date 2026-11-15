package ai.speckitty.charter.packs.infra.storage;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * The whole-pack gate's only dependency on storage: read a pack directory into the domain, write it into an empty
 * directory, read the written pack back, and say per source file what happened. The gate compares the files; this seam
 * only moves them.
 *
 * <p>Today's adapter is {@link DomainRoundTrip}, built from the per-kind repository calls. The whole-pack load and
 * write on the {@code Pack} aggregate (#189) is meant to replace it behind this interface, leaving the comparator, the
 * report and the ratchet untouched.
 */
interface WholePackRoundTrip {

    /**
     * The gate kinds this adapter models, as the census names them (for example {@code directive},
     * {@code pack-descriptor}, {@code graph-shard}). A file of any other kind is not this adapter's to answer.
     *
     * @return the modelled gate kinds
     */
    Set<String> modelledKinds();

    /**
     * Reads {@code source}, writes it into {@code target}, and reads {@code target} back.
     *
     * @param source the pack directory to read; never written
     * @param target an empty or missing directory that becomes the written pack
     * @param kindOf the gate kind of every pack-relative source path, from the census
     * @return one outcome per source path whose kind is modelled
     */
    Map<String, Outcome> roundTrip(Path source, Path target, Map<String, String> kindOf);

    /** What happened to one source file. */
    sealed interface Outcome permits Written, Refused {}

    /**
     * The file was read, written and read back.
     *
     * @param writtenPath the target-relative path of the written file
     */
    record Written(String writtenPath) implements Outcome {}

    /**
     * The file was refused on read, on write or on read-back.
     *
     * @param reason why, naming the field when the reader did
     */
    record Refused(String reason) implements Outcome {}
}
