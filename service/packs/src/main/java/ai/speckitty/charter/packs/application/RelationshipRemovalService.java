package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ImpactCheck;
import ai.speckitty.charter.packs.domain.MissionStepLinkRule;
import ai.speckitty.charter.packs.domain.MutabilityCheck;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.SubstepDelegationRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
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
    private final MutabilityCheck gate;
    private final ImpactCheck impact;

    /**
     * A service over its ports.
     *
     * @param packs loads the pack
     * @param store locks and swaps
     * @param gate the mutability check judged before the edge goes
     * @param impact the impact check
     */
    @Autowired
    public RelationshipRemovalService(
            PackRepository packs, PackRemovalStore store, MutabilityCheck gate, ImpactCheck impact) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.store = Objects.requireNonNull(store, "store");
        this.gate = Objects.requireNonNull(gate, "gate");
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
     *     {@code relationship_not_registered} (409); {@code validation_failed} with the violation
     *     {@value MissionStepLinkRule#CODE} at {@code /to} when the edge is the last that links a mission type's step
     *     (422); a composition code or a relation code of the impact check (422)
     * @throws ai.speckitty.charter.kernel.transport.PreconditionException {@code MISSING} (428) or {@code STALE} (412)
     * @throws RegistryWriteLock.RegistryBusyException when the registry lock was not free in time (503)
     */
    public PackRevision remove(
            PackIdentifier pack, PackQualifiedReference from, PackQualifiedReference to, @Nullable String ifMatch) {
        return remove(pack, from, to, Optional.empty(), ifMatch, RevisionTrail.NONE);
    }

    /**
     * Removes the edge of the pair and tells the trail which revision it found and which it left.
     *
     * @param pack the pack's identity
     * @param from one end
     * @param to the other end
     * @param ifMatch the {@code If-Match} precondition, required
     * @param trail told the stored revision once the {@code If-Match} check has read it, and once more when the removal
     *     changed the pack; a refusal never tells it a move
     * @return the revision after the removal
     * @throws CharterRefusal as for {@link #remove(PackIdentifier, PackQualifiedReference, PackQualifiedReference,
     *     String)}
     * @throws ai.speckitty.charter.kernel.transport.PreconditionException {@code MISSING} (428) or {@code STALE} (412)
     * @throws RegistryWriteLock.RegistryBusyException when the registry lock was not free in time (503)
     */
    public PackRevision remove(
            PackIdentifier pack,
            PackQualifiedReference from,
            PackQualifiedReference to,
            @Nullable String ifMatch,
            RevisionTrail trail) {
        return remove(pack, from, to, Optional.empty(), ifMatch, trail);
    }

    /**
     * Removes the registered edge of the pair of one relation, or of every pair class when {@code relation} is empty
     * (ADR-0045 decision 5), and tells the trail which revision it found and which it left. Given a relation, the pair
     * is judged by the edges of that relation only: no registered edge of it is {@code relationship_not_found}, and an
     * author-written or derived edge of it is {@code relationship_not_registered}. Without one, every registered edge of
     * the pair goes, and an author-written or derived edge of the pair is {@code relationship_not_registered}, whose
     * detail names the relation parameter when a registered edge stands beside it.
     *
     * @param pack the pack's identity
     * @param from one end
     * @param to the other end
     * @param relation the relation of the edge to remove; empty for every edge of the pair
     * @param ifMatch the {@code If-Match} precondition, required
     * @param trail told the revisions found and left
     * @return the revision after the removal
     * @throws CharterRefusal as for {@link #remove(PackIdentifier, PackQualifiedReference, PackQualifiedReference,
     *     String)}
     * @throws ai.speckitty.charter.kernel.transport.PreconditionException {@code MISSING} (428) or {@code STALE} (412)
     * @throws RegistryWriteLock.RegistryBusyException when the registry lock was not free in time (503)
     */
    public PackRevision remove(
            PackIdentifier pack,
            PackQualifiedReference from,
            PackQualifiedReference to,
            Optional<RelationType> relation,
            @Nullable String ifMatch,
            RevisionTrail trail) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(trail, "trail");
        return store.exclusively(pack, () -> removeUnderLocks(pack, from, to, relation, ifMatch, trail));
    }

    private PackRevision removeUnderLocks(
            PackIdentifier pack,
            PackQualifiedReference from,
            PackQualifiedReference to,
            Optional<RelationType> relation,
            @Nullable String ifMatch,
            RevisionTrail trail) {
        Pack current = packs.load(pack);
        PackRevision before = RemovalPrecondition.require(store, pack, ifMatch, trail);
        Predicate<Relationship> asked = edge ->
                names(edge, from, to) && relation.map(edge.type()::equals).orElse(true);
        List<Relationship> registered = store.registeredRelationships(pack);
        List<Relationship> named = registered.stream().filter(asked).toList();
        boolean outside = heldOutsideTheShard(current, store.authored(pack).edges(), asked);
        if (named.isEmpty() && !outside) {
            throw new CharterRefusal(
                    RefusalCode.RELATIONSHIP_NOT_FOUND,
                    "Pack " + pack.value() + " holds no "
                            + relation.map(type -> type.token() + " ").orElse("") + "relationship between " + from
                            + " and " + to + ".");
        }
        if (outside) {
            throw new CharterRefusal(
                    RefusalCode.RELATIONSHIP_NOT_REGISTERED,
                    "The " + relation.map(type -> type.token() + " ").orElse("") + "relationship between " + from
                            + " and " + to + " is not only in registered.graph.yaml: register the whole pack to change"
                            + " it"
                            + (relation.isEmpty() && !named.isEmpty()
                                    ? ", or name the registered edge's relation in the relation parameter."
                                    : "."));
        }
        // The edge goes as a RELATIONSHIP write on its source, as a whole-pack replace that drops it is judged; a
        // tension is judged on each side (ADR-0045 decision 1).
        PackRefusals.require(gate.judge(new MutabilityCheck.Live(
                current,
                named.stream()
                        .flatMap(edge -> PackRegistrationService.writesOn(edge).stream())
                        .toList())));
        List<Relationship> retained = registered.stream().filter(asked.negate()).toList();
        refuseUnlinkingAStep(pack, current.descriptor().name(), named, retained);
        refuseUnscopingACandidate(pack, current, named, retained);
        PackRefusals.noticesOrRefuse(impact.judge(new ImpactCheck.Write(
                pack, facts -> WriteFacts.withRegisteredEdges(facts, registered, retained), Optional.empty())));
        PackRevision after = store.remove(pack, PackRemovalStore.Removal.ofRelationship(retained));
        trail.moved(before, after);
        return after;
    }

    /**
     * Refuses the removal of a mission type's own step link that no other document of the pack still holds (operator
     * ruling DM-01M7B1YQR5ZKQPDW6DX64KPNRN): the step would be left without its edge, and every read of the pack would
     * refuse it. A step goes with its link by registering the whole pack. The refusal is {@code validation_failed}
     * with one violation {@value MissionStepLinkRule#CODE} at {@code /to}.
     */
    private void refuseUnlinkingAStep(
            PackIdentifier pack, String packName, List<Relationship> removed, List<Relationship> retained) {
        // The authored edges count too: a link the author wrote in another spelling (the source written with the pack's
        // own name) still links the step when the registered copy goes.
        List<Relationship> kept = new ArrayList<>(store.authored(pack).edges());
        kept.addAll(retained);
        for (Relationship edge : removed) {
            boolean ownLink = MissionStepLinkRule.isOwnStepLink(packName, edge.from(), edge);
            boolean stillLinked = kept.stream()
                    .anyMatch(other -> MissionStepLinkRule.isOwnStepLink(packName, edge.from(), other)
                            && other.to().equals(edge.to()));
            if (ownLink && !stillLinked) {
                String reason =
                        "the step " + edge.to() + " of " + edge.from() + " would have no requires edge from its type";
                throw CharterRefusal.withViolations(
                        RefusalCode.VALIDATION_FAILED,
                        "The relationship removal is refused: " + reason + ".",
                        List.of(new RefusalViolation("/to", MissionStepLinkRule.CODE, reason)));
            }
        }
    }

    /**
     * Refuses the removal of the last {@code scope} edge from a step's action to a delegation candidate of one of its
     * substeps (FR-005): the candidate would be left without its edge, and every read of the pack would refuse it. A
     * substep goes with its edge by registering the whole pack. The refusal is {@code validation_failed} with one
     * violation {@value SubstepDelegationRule#UNSCOPED} at {@code /to}.
     */
    private void refuseUnscopingACandidate(
            PackIdentifier pack, Pack current, List<Relationship> removed, List<Relationship> retained) {
        String packName = current.descriptor().name();
        List<Relationship> scopes = removed.stream()
                .filter(edge -> edge.type() == RelationType.SCOPE && edge.from().kind() == NodeKind.ACTION)
                .filter(edge -> edge.fromPack().map(packName::equals).orElse(true))
                .toList();
        if (scopes.isEmpty()) {
            return;
        }
        List<Relationship> kept = new ArrayList<>(store.authored(pack).edges());
        kept.addAll(retained);
        Set<String> needed = SubstepDelegationRule.neededScopes(packName, store.missionTypes(pack));
        for (Relationship edge : scopes) {
            String key = SubstepDelegationRule.scopeKey(packName, edge.from(), edge.toReference());
            boolean stillScoped = kept.stream()
                    .filter(other -> other.type() == RelationType.SCOPE
                            && other.fromPack().map(packName::equals).orElse(true))
                    .anyMatch(other -> SubstepDelegationRule.scopeKey(packName, other.from(), other.toReference())
                            .equals(key));
            if (needed.contains(key) && !stillScoped) {
                String reason = "a substep of " + edge.from() + " delegates to " + edge.toReference()
                        + ", which would have no scope edge from the step's action";
                throw CharterRefusal.withViolations(
                        RefusalCode.VALIDATION_FAILED,
                        "The relationship removal is refused: " + reason + ".",
                        List.of(new RefusalViolation("/to", SubstepDelegationRule.UNSCOPED, reason)));
            }
        }
    }

    /**
     * Whether an edge joins the pair the request names, in either orientation. A reference that names a pack matches
     * only an end that names the same pack; one that names none matches by the kernel's comparison, the URN alone. An
     * end that is no URN, or a pair that joins one URN to itself with no qualifier, names no edge.
     */
    private static boolean names(Relationship edge, PackQualifiedReference from, PackQualifiedReference to) {
        if (!from.qualified() && !to.qualified() && from.asUrn().equals(to.asUrn())) {
            return false;
        }
        return (endNames(edge.fromReference(), from) && endNames(edge.toReference(), to))
                || (endNames(edge.fromReference(), to) && endNames(edge.toReference(), from));
    }

    private static boolean endNames(PackQualifiedReference end, PackQualifiedReference requested) {
        Optional<Urn> urn = requested.asUrn();
        return urn.isPresent()
                && urn.equals(end.asUrn())
                && (requested.packName().isEmpty() || requested.packName().equals(end.packName()));
    }

    /** Whether an author-written graph document, or a relation a component derives, holds an edge the request asks for. */
    private static boolean heldOutsideTheShard(
            Pack current, List<Relationship> authored, Predicate<Relationship> asked) {
        return authored.stream().anyMatch(asked)
                || current.artifacts().stream()
                        .flatMap(artifact -> artifact.derivedRelationships().stream())
                        .anyMatch(asked);
    }
}
