package ai.speckitty.charter.read;

import ai.speckitty.charter.domain.CharterPack;
import ai.speckitty.charter.domain.CharterPackRepository;
import ai.speckitty.charter.domain.PackDescriptor;
import java.util.List;
import java.util.Objects;

/** The packs of the offering in layer order. */
public final class PackListingQuery {

    private final CharterPackRepository packs;

    public PackListingQuery(CharterPackRepository packs) {
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    public List<PackSummary> answer() {
        return packs.load().packs().stream().map(PackListingQuery::summarize).toList();
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
