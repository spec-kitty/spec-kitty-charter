package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Optional;

/**
 * A domain-owned port that resolves a DRG neighbour on demand (ADR-0025 Decision 4). It dispatches on
 * {@link Urn#kind()} to that kind's read path and fetches only that one component, so a walk is bounded by the walk
 * rather than by pack size (NFR-001); the whole graph is never materialised.
 *
 * <p>It returns {@link Optional#empty()} for a target whose kind is out of scope, unwired or external — a procedure,
 * an asset, an agent profile, and the like (research D6) — so a walk simply continues past it. It <strong>never</strong>
 * throws for an unresolvable kind; emptiness is the normal, expected answer for 44% of real edges that target
 * currently-unwired kinds.
 *
 * <p>Implementations are adapters that live outside the domain; the production composite is wired by the composition
 * root. The domain declares only the contract.
 */
@FunctionalInterface
public interface ComponentResolver {

    /**
     * Resolves one component by its URN.
     *
     * @param urn the neighbour to fetch
     * @return the component when its kind is in scope and the component exists; empty for an out-of-scope, unwired or
     *     external kind, or when no component of that kind has that id
     */
    Optional<CharterArtifact> resolve(Urn urn);
}
