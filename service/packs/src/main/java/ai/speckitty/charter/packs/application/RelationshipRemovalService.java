package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The {@code DELETE} of one registered relationship of a pack (IC-06, FR-006; decisions DM-01M6TQ62HXYF362GAFS2BZ3PGF and
 * DM-01M6TR1KDAJ64GMJD6AYNZKT59). It removes the edge of an unordered pair <em>only</em> from
 * {@code registered.graph.yaml}, the shard {@code PUT /packs/{packId}/relationships} writes, and never rewrites a file the
 * author wrote: a pair the author wrote into a source shard or {@code drg/fragment.yaml}, one a component derives (a
 * guard's {@code implemented_by}, ADR-0064 D7) and one held both there and in the registered shard are
 * {@code 409 relationship_not_registered}, and nothing changes, since a {@code 204} that left the edge in force would
 * mislead. It runs under the registry write lock and then the pack's own, and is strict as every removal is: a result that
 * leaves a closure the pack joins unable to compose, or breaking a relation rule, is refused {@code 422}
 * (DM-01M6TQ67C9ZTNGZB9CY28JS54F).
 */
@Service
public final class RelationshipRemovalService {

    private final PackRepository packs;
    private final PackRemovalStore store;
    private final RegistrationImpact impact;

    /**
     * A service over its ports.
     *
     * @param packs loads the pack
     * @param store locks and swaps
     * @param impact the impact check
     */
    @Autowired
    public RelationshipRemovalService(PackRepository packs, PackRemovalStore store, RegistrationImpact impact) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.store = Objects.requireNonNull(store, "store");
        this.impact = Objects.requireNonNull(impact, "impact");
    }

    /**
     * Removes the registered edge of an unordered pair.
     *
     * @param pack the pack's identity
     * @param from one end of the pair
     * @param to the other end
     * @param ifMatch the {@code If-Match} precondition, required
     * @return the revision after the removal
     * @throws CharterRefusal {@code pack_not_found} or {@code relationship_not_found} (404);
     *     {@code relationship_not_registered} (409); a composition code or a relation code of the impact check (422)
     * @throws ai.speckitty.charter.kernel.transport.PreconditionException {@code MISSING} (428) or {@code STALE} (412)
     * @throws RegistryWriteLock.RegistryBusyException when the registry lock was not free in time (503)
     */
    public PackRevision remove(
            PackIdentifier pack, PackQualifiedReference from, PackQualifiedReference to, @Nullable String ifMatch) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return store.exclusively(pack, () -> removeUnderLocks(pack, from, to, ifMatch));
    }

    private PackRevision removeUnderLocks(
            PackIdentifier pack, PackQualifiedReference from, PackQualifiedReference to, @Nullable String ifMatch) {
        Pack current = packs.load(pack);
        RemovalPrecondition.require(store, pack, ifMatch);
        Optional<Relationship> pair = pairOf(from, to);
        List<Relationship> registered = store.registeredRelationships(pack);
        boolean inRegisteredShard = pair.isPresent() && registered.stream().anyMatch(pair.get()::equals);
        boolean elsewhere = pair.isPresent() && heldOutsideTheShard(pack, current, pair.get());
        if (!inRegisteredShard && !elsewhere) {
            throw new CharterRefusal(
                    RefusalCode.RELATIONSHIP_NOT_FOUND,
                    "Pack " + pack.value() + " holds no relationship between " + from + " and " + to + ".");
        }
        if (elsewhere) {
            throw new CharterRefusal(
                    RefusalCode.RELATIONSHIP_NOT_REGISTERED,
                    "The relationship between " + from + " and " + to
                            + " is not only in registered.graph.yaml: register the whole pack to change it.");
        }
        List<Relationship> retained =
                registered.stream().filter(edge -> !pair.get().equals(edge)).toList();
        impact.check(pack, facts -> WriteFacts.withRegisteredEdges(facts, registered, retained));
        return store.remove(pack, PackRemovalStore.Removal.ofRelationship(retained));
    }

    /** The unordered pair, as the kernel compares edges; empty for an end that is no URN, or a pair that joins one to itself. */
    private static Optional<Relationship> pairOf(PackQualifiedReference from, PackQualifiedReference to) {
        Optional<Urn> first = from.asUrn();
        Optional<Urn> second = to.asUrn();
        if (first.isEmpty() || second.isEmpty() || first.get().equals(second.get())) {
            return Optional.empty();
        }
        return Optional.of(Relationship.of(first.get(), second.get(), RelationType.SUGGESTS));
    }

    /** Whether an author-written graph document, or a relation a component derives, holds the pair. */
    private boolean heldOutsideTheShard(PackIdentifier pack, Pack current, Relationship pair) {
        boolean authored = store.authored(pack).edges().stream().anyMatch(pair::equals);
        return authored
                || current.artifacts().stream()
                        .flatMap(artifact -> artifact.derivedRelationships().stream())
                        .anyMatch(pair::equals);
    }
}
