package ai.speckitty.charter.read;

import ai.speckitty.charter.domain.CharterPack;
import ai.speckitty.charter.domain.PackDescriptor;
import java.util.Objects;

/** The packs of the offering in layer order. */
public final class PackListingQuery {

    private final ConsistentLoad load;

    /**
     * A query over the offering {@code packs} loads.
     *
     * @param load the consistent load; the offering is loaded afresh for every answer
     */
    public PackListingQuery(ConsistentLoad load) {
        this.load = Objects.requireNonNull(load, "load");
    }

    /**
     * Every loaded pack, summarized.
     *
     * @return one summary per pack, in layer order, with the digest of the files they were loaded from
     * @throws ai.speckitty.charter.domain.CharterRefusal the repository's refusal when the offering cannot be loaded,
     *     or {@code STALE_INPUT} when the packs changed while the read was answered
     */
    public PackListing answer() {
        ConsistentLoad.Loaded loaded = load.load();
        return new PackListing(
                loaded.offering().packs().stream()
                        .map(PackListingQuery::summarize)
                        .toList(),
                loaded.sourceFingerprint());
    }

    private static PackSummary summarize(CharterPack pack) {
        return new PackSummary(
                pack.name(),
                pack.level(),
                pack.descriptor().map(PackDescriptor::packId),
                pack.descriptor().map(PackDescriptor::packVersion),
                pack.descriptor().flatMap(PackDescriptor::parentPack),
                pack.graph().nodes().size(),
                pack.graph().edges().size(),
                pack.diagnostics());
    }
}
