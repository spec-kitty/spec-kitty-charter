package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.CharterArtifact;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Registers a standalone, cross-kind relationship edge into a pack (FR-009/012): the use case behind
 * {@code PUT /packs/{id}/relationships}. It is neutral to artifact kind; the per-kind services own the body-carried
 * door of the dual-write rule (an artifact body may assert only edges authored from itself) and this service owns the
 * other door, standalone or cross-authored edges. Both upsert the <em>same</em> pack relationship shard, keyed by the
 * unordered {@code {from, to}} pair, last-write-wins, through the one package-private {@code RelationshipMerge}.
 *
 * <p>The write is functional (ADR-0026): load &rarr; upsert into the relationship shard &rarr; commit the whole pack
 * atomically, so the revision advances and a refusal changes zero stored files (NFR-002).
 */
@Service
public final class RelationshipRegistrationService {

    private final PackRepository packs;
    private final PackWriteStore writeStore;
    private final WritePolicyGate policy;

    /**
     * A service over the injected ports.
     *
     * @param packs the port that loads the pack aggregate
     * @param writeStore the write-path seam that commits atomically and holds the relationship shard
     * @param policy the mutability gate consulted before a write (permissive by default; WP06 supplies ADR-0012)
     */
    @Autowired
    public RelationshipRegistrationService(PackRepository packs, PackWriteStore writeStore, WritePolicyGate policy) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.writeStore = Objects.requireNonNull(writeStore, "writeStore");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * The outcome of a relationship registration: the new revision (the {@code ETag}), whether the pair was net-new,
     * and the edge's {@code from} URN. Its own record, like each per-kind service's, so no kind's service type leaks
     * into this neutral one.
     *
     * @param revision the revision of the committed state, for the {@code ETag}
     * @param created {@code true} when the pair was not registered before (in either orientation)
     * @param target the registered edge's {@code from} URN
     */
    public record Registered(PackRevision revision, boolean created, Urn target) {}

    /**
     * Registers a standalone relationship edge, idempotent on the unordered {@code {from, to}} pair (FR-009). The
     * aggregate itself is unchanged; only the relationship shard is upserted and the whole pack is re-committed
     * atomically so the revision advances.
     *
     * @param pack the pack to register into
     * @param relationship the relationship to register; its {@code from} must resolve within the pack
     * @param ifMatch the {@code If-Match} precondition; a sub-resource write <strong>requires</strong> one
     * @return the registration outcome; {@link Registered#created()} is {@code true} when the pair was not registered
     *     before (in either orientation)
     * @throws CharterRefusal {@link RefusalCode#PACK_NOT_FOUND} (404) when the pack is unknown,
     *     {@link RefusalCode#REGISTRATION_CONFLICT} (409) when {@code from} does not resolve within the pack, or the
     *     policy's {@link RefusalCode#GOVERNANCE_POLICY_VIOLATION} (409)
     * @throws PreconditionException {@link PreconditionException.Kind#MISSING} (428) when no precondition was supplied,
     *     {@link PreconditionException.Kind#STALE} (412) when {@code ifMatch} is stale
     */
    public Registered registerRelationship(PackIdentifier pack, Relationship relationship, @Nullable String ifMatch) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(relationship, "relationship");
        // Held from load to swap, so the precondition, the merge over the current shard and the commit see one
        // state: a concurrent writer cannot slip between them (a lost update).
        return writeStore.exclusively(pack, () -> {
            Pack current = packs.load(pack);
            precondition(pack, ifMatch);
            if (!urnsOf(current).contains(relationship.from())) {
                throw new CharterRefusal(
                        RefusalCode.REGISTRATION_CONFLICT,
                        "Relationship from " + relationship.from() + " does not resolve within pack " + pack.value()
                                + ".");
            }
            policy.authorize(current, WritePolicyGate.Operation.RELATIONSHIP, relationship.from());
            List<Relationship> existing = writeStore.registeredRelationships(pack);
            boolean created = existing.stream().noneMatch(relationship::equals);
            List<Relationship> merged = RelationshipMerge.upsert(existing, relationship);
            PackRevision revision =
                    writeStore.commit(pack, ifMatch, PackWriteStore.Change.relationships(current, merged));
            return new Registered(revision, created, relationship.from());
        });
    }

    private void precondition(PackIdentifier pack, @Nullable String ifMatch) {
        if (ifMatch == null) {
            throw new PreconditionException(
                    PreconditionException.Kind.MISSING,
                    "A relationship registration requires an If-Match precondition.");
        }
        if (!PackRevision.matches(ifMatch, writeStore.currentRevision(pack))) {
            throw new PreconditionException(
                    PreconditionException.Kind.STALE, "The If-Match precondition is stale; read the pack again.");
        }
    }

    private static Set<Urn> urnsOf(Pack pack) {
        return Stream.of(
                        pack.tactics().stream(),
                        pack.directives().stream(),
                        pack.styleguides().stream(),
                        pack.toolguides().stream(),
                        pack.paradigms().stream())
                .flatMap(stream -> stream)
                .map(CharterArtifact::urn)
                .collect(Collectors.toUnmodifiableSet());
    }
}
