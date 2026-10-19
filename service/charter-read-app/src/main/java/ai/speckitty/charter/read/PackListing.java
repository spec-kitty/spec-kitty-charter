package ai.speckitty.charter.read;

import java.util.List;

/**
 * The packs of the offering in layer order, and the digest of the files they were loaded from.
 *
 * @param packs one summary per pack, in layer order
 * @param sourceFingerprint the digest of the input files, verified by {@link ConsistentLoad}; the inbound adapter
 *     reports it as a header, never in the body
 */
public record PackListing(List<PackSummary> packs, String sourceFingerprint) {
    /**
     * Copies {@code packs}.
     *
     * @param packs one summary per pack
     * @param sourceFingerprint the digest of the input files
     */
    public PackListing {
        packs = List.copyOf(packs);
    }
}
