package ai.speckitty.charter.packs.domain;

import java.util.Optional;

/** The port through which a pack's directives are stored and fetched one at a time, whatever the storage. */
public interface DirectiveRepository {

    /**
     * Upserts a directive: stores it, creating it when its identity is unknown and replacing it when known.
     *
     * @param directive the directive to store
     */
    void save(Directive directive);

    /**
     * Fetches one directive.
     *
     * @param pack the pack that owns the directive
     * @param directive the directive's identity
     * @return the directive, or empty when the pack has none with that identity
     */
    Optional<Directive> load(PackIdentifier pack, DirectiveIdentifier directive);
}
