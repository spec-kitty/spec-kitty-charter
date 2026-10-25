package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Registers a paradigm into a pack (FR-014), the write half of the US2 reference slice, a faithful copy of
 * {@link DirectiveRegistrationService} kept independent per-kind (plan D5). Every write is <strong>functional</strong>
 * (ADR-0026): there is no CRUD, no {@code DELETE}/{@code PATCH}; a registration goes <em>load &rarr; mutate &rarr;
 * revalidate the whole {@link Pack} &rarr; commit atomically</em>, so the aggregate invariants (belongs-to-pack,
 * no-duplicate-id) gate the write, and a refusal changes zero stored files (NFR-002, the {@link PackWriteStore}'s
 * stage-and-swap enforces it).
 *
 * <h2>Dual-write ownership (analysis finding I1 / plan IC-05)</h2>
 *
 * A paradigm body may carry relationships (its directive refs minted as {@code requires} and its typed references
 * minted as {@code suggests}). The ownership rule is the one the directive slice proved:
 *
 * <blockquote><strong>A paradigm body may assert only relationships whose {@code from} is that paradigm.</strong> A
 * body relationship with any other {@code from} is refused {@link RefusalCode#VALIDATION_FAILED} (422). A
 * cross-authored edge — one whose {@code from} is a different component — must be registered through the standalone
 * relationship endpoint ({@code PUT /packs/{id}/relationships}).</blockquote>
 *
 * Both doors upsert the <em>same</em> pack relationship shard, keyed by the unordered {@code {from, to}} pair,
 * last-write-wins, so they converge: re-registering a pair (in either orientation, via either door) replaces it. The
 * standalone endpoint lives on {@link RelationshipRegistrationService}; both reuse the one package-private
 * {@code RelationshipMerge} so the shard stays single-sourced.
 */
@Service
public final class ParadigmRegistrationService {

    private final PackRepository packs;
    private final PackWriteStore writeStore;
    private final WritePolicyGate policy;

    /**
     * A service over the injected ports.
     *
     * @param packs the port that loads the pack aggregate to mutate and revalidate
     * @param writeStore the write-path seam that commits the aggregate atomically and holds the relationship shard
     * @param policy the mutability gate consulted before a write (permissive by default; WP06 supplies ADR-0012)
     */
    @Autowired
    public ParadigmRegistrationService(PackRepository packs, PackWriteStore writeStore, WritePolicyGate policy) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.writeStore = Objects.requireNonNull(writeStore, "writeStore");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * The outcome of a registration: the new revision (the {@code ETag}), whether it created rather than replaced, and
     * the registered paradigm's URN (the {@code Location} target of a create).
     *
     * @param revision the revision of the committed state, for the {@code ETag}
     * @param created {@code true} when the write created a net-new paradigm, {@code false} when it replaced one
     * @param target the registered paradigm's URN
     */
    public record Registered(PackRevision revision, boolean created, Urn target) {}

    /**
     * Registers a paradigm and its body-carried relationships into the pack (POST create or PUT create-or-replace).
     *
     * @param pack the pack to register into
     * @param paradigm the assembled paradigm (already domain-valid)
     * @param bodyRelationships the relationships carried in the body; each {@code from} must be the paradigm's URN
     * @param replace {@code true} for PUT (idempotent create-or-replace), {@code false} for POST (create; a duplicate
     *     id is a conflict)
     * @param ifMatch the {@code If-Match} precondition, or {@code null} when none was supplied (optional for an
     *     artifact write)
     * @return the registration outcome
     * @throws CharterRefusal {@link RefusalCode#PACK_NOT_FOUND} (404) when the pack is unknown,
     *     {@link RefusalCode#REGISTRATION_CONFLICT} (409) when a POST names an existing id,
     *     {@link RefusalCode#VALIDATION_FAILED} (422) when a body relationship breaks the ownership rule or the
     *     mutated aggregate fails revalidation, or the policy's {@link RefusalCode#GOVERNANCE_POLICY_VIOLATION} (409)
     * @throws PreconditionException {@link PreconditionException.Kind#STALE} (412) when {@code ifMatch} is stale
     */
    public Registered registerParadigm(
            PackIdentifier pack,
            Paradigm paradigm,
            List<Relationship> bodyRelationships,
            boolean replace,
            @Nullable String ifMatch) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(paradigm, "paradigm");
        Objects.requireNonNull(bodyRelationships, "bodyRelationships");
        Pack current = load(pack);
        precondition(pack, ifMatch);
        boolean exists = current.paradigms().stream().anyMatch(p -> p.id().equals(paradigm.id()));
        if (!replace && exists) {
            throw new CharterRefusal(
                    RefusalCode.REGISTRATION_CONFLICT,
                    "Pack " + pack.value() + " already has a paradigm "
                            + paradigm.id().value() + ".");
        }
        RelationshipMerge.ownBodyRelationships("paradigm", paradigm.urn(), bodyRelationships);
        policy.authorize(
                current,
                exists ? WritePolicyGate.Operation.REPLACE : WritePolicyGate.Operation.REGISTER,
                paradigm.urn());
        Pack mutated = revalidated(current, paradigm);
        List<Relationship> merged = bodyRelationships.isEmpty()
                ? null // the body asserts no edge: leave the relationship shard untouched
                : RelationshipMerge.mergeAll(writeStore.registeredRelationships(pack), bodyRelationships);
        PackRevision revision = writeStore.commit(pack, mutated, merged);
        return new Registered(revision, !exists, paradigm.urn());
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private Pack load(PackIdentifier pack) {
        try {
            return packs.load(pack);
        } catch (CharterRefusal refused) {
            if (refused.code() != RefusalCode.PACK_UNREADABLE) {
                throw refused;
            }
            throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.", refused);
        }
    }

    private void precondition(PackIdentifier pack, @Nullable String ifMatch) {
        if (ifMatch == null) {
            return;
        }
        if (!PackRevision.matches(ifMatch, writeStore.currentRevision(pack))) {
            throw new PreconditionException(
                    PreconditionException.Kind.STALE, "The If-Match precondition is stale; read the pack again.");
        }
    }

    /**
     * Rebuilds the whole aggregate with {@code paradigm} added (or replacing the one of the same id) and revalidates
     * it: {@link Pack.Builder#build()} re-runs the aggregate invariants (belongs-to-pack, no-duplicate-id), so a
     * breach is a validation refusal (422) rather than a 500, and the write never reaches the commit.
     */
    private static Pack revalidated(Pack current, Paradigm paradigm) {
        Pack.Builder builder = Pack.builder().id(current.id()).descriptor(current.descriptor());
        current.tactics().forEach(builder::tactic);
        current.directives().forEach(builder::directive);
        current.styleguides().forEach(builder::styleguide);
        current.toolguides().forEach(builder::toolguide);
        current.paradigms().stream()
                .filter(existing -> !existing.id().equals(paradigm.id()))
                .forEach(builder::paradigm);
        builder.paradigm(paradigm);
        current.violations().forEach(builder::violation);
        try {
            return builder.build();
        } catch (ConstraintBreachException breach) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(breach.getMessage()), breach);
        }
    }
}
