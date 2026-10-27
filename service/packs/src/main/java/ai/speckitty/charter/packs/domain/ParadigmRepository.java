package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.Optional;

/** The port through which a pack's paradigms are stored and fetched one at a time, whatever the storage. */
public interface ParadigmRepository {

    /**
     * Upserts a paradigm: stores it, creating it when its identity is unknown and replacing it when known.
     *
     * @param paradigm the paradigm to store
     */
    void save(Paradigm paradigm);

    /**
     * Fetches one paradigm.
     *
     * @param pack the pack that owns the paradigm
     * @param paradigm the paradigm's identity
     * @return the paradigm, or empty when the pack has none with that identity
     */
    Optional<Paradigm> load(PackIdentifier pack, ParadigmIdentifier paradigm);
}
