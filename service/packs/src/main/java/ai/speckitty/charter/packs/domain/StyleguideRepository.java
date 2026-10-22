package ai.speckitty.charter.packs.domain;

import java.util.Optional;

/** The port through which a pack's styleguides are stored and fetched one at a time, whatever the storage. */
public interface StyleguideRepository {

    /**
     * Upserts a styleguide: stores it, creating it when its identity is unknown and replacing it when known.
     *
     * @param styleguide the styleguide to store
     */
    void save(Styleguide styleguide);

    /**
     * Fetches one styleguide.
     *
     * @param pack the pack that owns the styleguide
     * @param styleguide the styleguide's identity
     * @return the styleguide, or empty when the pack has none with that identity
     */
    Optional<Styleguide> load(PackIdentifier pack, StyleguideIdentifier styleguide);
}
