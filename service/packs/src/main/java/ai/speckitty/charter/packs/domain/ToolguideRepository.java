package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.Optional;

/** The port through which a pack's toolguides are stored and fetched one at a time, whatever the storage. */
public interface ToolguideRepository {

    /**
     * Upserts a toolguide: stores it, creating it when its identity is unknown and replacing it when known.
     *
     * @param toolguide the toolguide to store
     */
    void save(Toolguide toolguide);

    /**
     * Fetches one toolguide.
     *
     * @param pack the pack that owns the toolguide
     * @param toolguide the toolguide's identity
     * @return the toolguide, or empty when the pack has none with that identity
     */
    Optional<Toolguide> load(PackIdentifier pack, ToolguideIdentifier toolguide);
}
