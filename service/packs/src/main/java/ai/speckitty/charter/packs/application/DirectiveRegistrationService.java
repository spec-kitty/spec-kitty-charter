package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Registers a directive into a pack (FR-007/010/012), the write half of the US1 reference slice and the pattern
 * every later kind copies. Every write is <strong>functional</strong> (ADR-0026): there is no
 * CRUD, no {@code DELETE}/{@code PATCH}; a registration goes <em>load &rarr; mutate &rarr; revalidate the whole
 * {@link Pack} &rarr; commit atomically</em>, so the aggregate invariants (belongs-to-pack, no-duplicate-id) gate the
 * write, and a refusal changes zero stored files (NFR-002, the {@link PackWriteStore}'s stage-and-swap enforces it).
 *
 * <h2>One write path for an edge (ADR-0063)</h2>
 *
 * A registration body carries the directive's own fields only. A relationship is authored in the pack's graph documents
 * and written through {@link RelationshipRegistrationService#registerRelationship} ({@code PUT
 * /packs/{id}/relationships}), the only operation that writes one, so the registration never touches the relationship
 * shard.
 */
@Service
public final class DirectiveRegistrationService {

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
    public DirectiveRegistrationService(PackRepository packs, PackWriteStore writeStore, WritePolicyGate policy) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.writeStore = Objects.requireNonNull(writeStore, "writeStore");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * The outcome of a registration: the new revision (the {@code ETag}), whether it created rather than replaced, and
     * the registered component's URN (the {@code Location} target of a create).
     *
     * @param revision the revision of the committed state, for the {@code ETag}
     * @param created {@code true} when the write created a net-new component, {@code false} when it replaced one
     * @param target the registered component's URN
     */
    public record Registered(PackRevision revision, boolean created, Urn target) {}

    /**
     * Registers a directive into the pack (POST create or PUT create-or-replace).
     *
     * @param pack the pack to register into
     * @param directive the assembled directive (already domain-valid)
     * @param replace {@code true} for PUT (idempotent create-or-replace), {@code false} for POST (create; a duplicate
     *     id is a conflict)
     * @param ifMatch the {@code If-Match} precondition, or {@code null} when none was supplied (optional for an
     *     artifact write)
     * @return the registration outcome
     * @throws CharterRefusal {@link RefusalCode#PACK_NOT_FOUND} (404) when the pack is unknown,
     *     {@link RefusalCode#REGISTRATION_CONFLICT} (409) when a POST names an existing id,
     *     {@link RefusalCode#VALIDATION_FAILED} (422) when the mutated aggregate fails revalidation, or the policy's {@link RefusalCode#GOVERNANCE_POLICY_VIOLATION} (409)
     * @throws PreconditionException {@link PreconditionException.Kind#STALE} (412) when {@code ifMatch} is stale
     */
    public Registered registerDirective(
            PackIdentifier pack, Directive directive, boolean replace, @Nullable String ifMatch) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(directive, "directive");
        // Held from load to swap, so the precondition and the commit see one
        // state: a concurrent writer cannot slip between them (a lost update).
        return writeStore.exclusively(pack, () -> {
            Pack current = packs.load(pack);
            precondition(pack, ifMatch);
            boolean exists = current.directives().stream().anyMatch(d -> d.id().equals(directive.id()));
            if (!replace && exists) {
                throw new CharterRefusal(
                        RefusalCode.REGISTRATION_CONFLICT,
                        "Pack " + pack.value() + " already has a directive "
                                + directive.id().value() + ".");
            }
            policy.authorize(
                    current,
                    exists ? WritePolicyGate.Operation.REPLACE : WritePolicyGate.Operation.REGISTER,
                    directive.urn());
            Pack mutated = revalidated(current, directive);
            PackWriteStore.Change change = PackWriteStore.Change.artifact(mutated, directive);
            PackRevision revision = writeStore.commit(pack, ifMatch, change);
            return new Registered(revision, !exists, directive.urn());
        });
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

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
     * Rebuilds the whole aggregate with {@code directive} added (or replacing the one of the same id) and revalidates
     * it: {@link Pack#with} re-runs the aggregate invariants (belongs-to-pack, no-duplicate-id = FR-008),
     * so a breach is a validation refusal (422) rather than a 500, and the write never reaches the commit.
     */
    private static Pack revalidated(Pack current, Directive directive) {
        try {
            return current.with(directive);
        } catch (ConstraintBreachException breach) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(breach.getMessage()), breach);
        }
    }
}
