package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Registers a toolguide into a pack (FR-015, US3), the write half of the toolguide slice. It copies
 * {@link DirectiveRegistrationService}'s discipline: every write is <strong>functional</strong> (ADR-0026) — there is
 * no CRUD, no {@code DELETE}/{@code PATCH}; a registration goes <em>load &rarr; mutate &rarr; revalidate the whole
 * {@link Pack} &rarr; commit atomically</em>, so the aggregate invariants (belongs-to-pack, no-duplicate-id = FR-008)
 * gate the write, and a refusal changes zero stored files (NFR-002, the {@link PackWriteStore}'s stage-and-swap
 * enforces it).
 *
 * <p>Unlike a directive, a toolguide originates no relationships ({@link Toolguide#relationships()} is empty), so the
 * registration body carries none: there is no body-relationship door and no dual-write ownership rule to enforce here.
 * The commit leaves the pack's standalone relationship shard untouched, so a toolguide write never erases a
 * cross-authored edge another door registered.
 */
@Service
public final class ToolguideRegistrationService {

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
    public ToolguideRegistrationService(PackRepository packs, PackWriteStore writeStore, WritePolicyGate policy) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.writeStore = Objects.requireNonNull(writeStore, "writeStore");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * The outcome of a registration: the new revision (the {@code ETag}), whether it created rather than replaced, and
     * the registered toolguide's URN (the {@code Location} target of a create).
     *
     * @param revision the revision of the committed state, for the {@code ETag}
     * @param created {@code true} when the write created a net-new toolguide, {@code false} when it replaced one
     * @param target the registered toolguide's URN
     */
    public record Registered(PackRevision revision, boolean created, Urn target) {}

    /**
     * Registers a toolguide into the pack (POST create or PUT create-or-replace).
     *
     * @param pack the pack to register into
     * @param toolguide the assembled toolguide (already domain-valid)
     * @param replace {@code true} for PUT (idempotent create-or-replace), {@code false} for POST (create; a duplicate
     *     id is a conflict)
     * @param ifMatch the {@code If-Match} precondition, or {@code null} when none was supplied (optional for an
     *     artifact write)
     * @return the registration outcome
     * @throws CharterRefusal {@link RefusalCode#PACK_NOT_FOUND} (404) when the pack is unknown,
     *     {@link RefusalCode#REGISTRATION_CONFLICT} (409) when a POST names an existing id,
     *     {@link RefusalCode#VALIDATION_FAILED} (422) when the mutated aggregate fails revalidation, or the policy's
     *     {@link RefusalCode#GOVERNANCE_POLICY_VIOLATION} (409)
     * @throws PreconditionException {@link PreconditionException.Kind#STALE} (412) when {@code ifMatch} is stale
     */
    public Registered registerToolguide(
            PackIdentifier pack, Toolguide toolguide, boolean replace, @Nullable String ifMatch) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(toolguide, "toolguide");
        // Held from load to swap, so the precondition, the merge over the current shard and the commit see one
        // state: a concurrent writer cannot slip between them (a lost update).
        return writeStore.exclusively(pack, () -> {
            Pack current = packs.load(pack);
            precondition(pack, ifMatch);
            boolean exists = current.toolguides().stream().anyMatch(t -> t.id().equals(toolguide.id()));
            if (!replace && exists) {
                throw new CharterRefusal(
                        RefusalCode.REGISTRATION_CONFLICT,
                        "Pack " + pack.value() + " already has a toolguide "
                                + toolguide.id().value() + ".");
            }
            policy.authorize(
                    current,
                    exists ? WritePolicyGate.Operation.REPLACE : WritePolicyGate.Operation.REGISTER,
                    toolguide.urn());
            Pack mutated = revalidated(current, toolguide);
            PackRevision revision =
                    writeStore.commit(pack, ifMatch, PackWriteStore.Change.artifact(mutated, toolguide));
            return new Registered(revision, !exists, toolguide.urn());
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
     * Rebuilds the whole aggregate with {@code toolguide} added (or replacing the one of the same id) and revalidates
     * it: {@link Pack#with} re-runs the aggregate invariants (belongs-to-pack, no-duplicate-id = FR-008),
     * so a breach is a validation refusal (422) rather than a 500, and the write never reaches the commit.
     */
    private static Pack revalidated(Pack current, Toolguide toolguide) {
        try {
            return current.with(toolguide);
        } catch (ConstraintBreachException breach) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(breach.getMessage()), breach);
        }
    }
}
