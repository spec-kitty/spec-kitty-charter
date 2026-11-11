package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Optional;

/**
 * One pack's view of the components a graph walk visits: it resolves a bare URN to the component that pack holds, kind
 * neutral, so a walk is bounded by what it reaches rather than by pack size (NFR-001); the whole graph is never
 * materialised. A {@link ComposeResolver} hands out one per pack.
 *
 * <p>It returns {@link Optional#empty()} for a URN the pack does not hold, so a walk simply continues past it. It
 * <strong>never</strong> throws for an unresolvable URN.
 */
@FunctionalInterface
public interface ComponentResolver {

    /**
     * Resolves one component by its URN.
     *
     * @param urn the neighbour to fetch
     * @return the component when the pack holds it; empty otherwise
     */
    Optional<CharterArtifact> resolve(Urn urn);
}
