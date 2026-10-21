package ai.speckitty.charter.packs.domain;

import java.util.Optional;

/** The port through which a pack's tactics are stored and fetched one at a time, whatever the storage. */
public interface TacticRepository {

    /**
     * Stores a tactic, replacing the existing tactic of the same identity in its pack.
     *
     * @param tactic the tactic to store
     */
    void save(Tactic tactic);

    /**
     * Fetches one tactic.
     *
     * @param pack the pack that owns the tactic
     * @param tactic the tactic's identity
     * @return the tactic, or empty when the pack has none with that identity
     */
    Optional<Tactic> load(PackIdentifier pack, TacticIdentifier tactic);
}
