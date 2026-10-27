package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.List;

/** The aggregating port that fetches a whole {@link Pack}: descriptor, readable tactics and read violations. */
public interface PackRepository {

    /**
     * Loads the pack. Partial-tolerant: an element that cannot be read is left out and reported through
     * {@link Pack#violations()} (the single owner of read violations), and the rest of the pack is still returned.
     *
     * @param pack the pack's identity
     * @return the pack aggregate
     */
    Pack load(PackIdentifier pack);

    /**
     * All readable tactics of a pack. Defaults to the aggregate's tactics; a storage edge may override it with a
     * point-wise read that does not assemble the whole aggregate.
     *
     * @param pack the pack's identity
     * @return the readable tactics, in a stable order
     */
    default List<Tactic> tacticsOf(PackIdentifier pack) {
        return load(pack).tactics();
    }
}
