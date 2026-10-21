package ai.speckitty.charter.packs.domain;

/** Port: reads one pack root as facts. Implementations live in outbound adapters. */
public interface PackInspector {

    /** How much of a pack to read. */
    enum Scope {
        /** {@code pack.yaml}, the root shards and {@code drg/fragment.yaml}: what a load needs. */
        LOAD_PATH,
        /** Every file of the pack: what a validation needs. */
        FULL
    }

    /**
     * Reads one pack root. It never throws on file content, only on I/O failure: a file that is absent,
     * unparseable or invalid is reported in the result.
     *
     * @param root the pack to read
     * @param scope how much of it to read
     * @return the facts; the descriptor, shards and fragment for {@link Scope#LOAD_PATH}, everything for
     *     {@link Scope#FULL}
     */
    PackContents inspect(PackRoot root, Scope scope);
}
