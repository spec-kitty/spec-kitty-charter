package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Registers a styleguide into a pack (FR-013), the styleguide write half of the US2 replicated slice, a faithful copy
 * of {@link DirectiveRegistrationService}'s write discipline. Every write is <strong>functional</strong> (ADR-0026):
 * there is no CRUD, no {@code DELETE}/{@code PATCH}; a registration goes <em>load &rarr; mutate &rarr; revalidate the
 * whole {@link Pack} &rarr; commit atomically</em>, so the aggregate invariants (belongs-to-pack, no-duplicate-id)
 * gate the write, and a refusal changes zero stored files (NFR-002, the {@link PackWriteStore}'s stage-and-swap
 * enforces it).
 *
 * <p>Unlike a directive, a styleguide mints no relationships: its references are bare citation strings (ADR-0024), so
 * there is no body-carried relationships door here. The standalone relationship endpoint is unchanged and owned by the
 * directive slice (WP05); the commit leaves the pack's relationship shard untouched. A cross-authored edge is
 * registered through {@code PUT /packs/{id}/relationships}, not through this service.
 */
@Service
public final class StyleguideRegistrationService {

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
    public StyleguideRegistrationService(PackRepository packs, PackWriteStore writeStore, WritePolicyGate policy) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.writeStore = Objects.requireNonNull(writeStore, "writeStore");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * The outcome of a registration: the new revision (the {@code ETag}), whether it created rather than replaced, and
     * the registered styleguide's URN (the {@code Location} target of a create).
     *
     * @param revision the revision of the committed state, for the {@code ETag}
     * @param created {@code true} when the write created a net-new styleguide, {@code false} when it replaced one
     * @param target the registered styleguide's URN
     */
    public record Registered(PackRevision revision, boolean created, Urn target) {}

    /**
     * Registers a styleguide into the pack (POST create or PUT create-or-replace).
     *
     * @param pack the pack to register into
     * @param styleguide the assembled styleguide (already domain-valid)
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
    public Registered registerStyleguide(
            PackIdentifier pack, Styleguide styleguide, boolean replace, @Nullable String ifMatch) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(styleguide, "styleguide");
        Pack current = load(pack);
        precondition(pack, ifMatch);
        boolean exists = current.styleguides().stream().anyMatch(s -> s.id().equals(styleguide.id()));
        if (!replace && exists) {
            throw new CharterRefusal(
                    RefusalCode.REGISTRATION_CONFLICT,
                    "Pack " + pack.value() + " already has a styleguide "
                            + styleguide.id().value() + ".");
        }
        policy.authorize(
                current,
                exists ? WritePolicyGate.Operation.REPLACE : WritePolicyGate.Operation.REGISTER,
                styleguide.urn());
        Pack mutated = revalidated(current, styleguide);
        PackRevision revision = writeStore.commit(pack, mutated, null); // relationships unchanged
        return new Registered(revision, !exists, styleguide.urn());
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
     * Rebuilds the whole aggregate with {@code styleguide} added (or replacing the one of the same id) and
     * revalidates it: {@link Pack.Builder#build()} re-runs the aggregate invariants (belongs-to-pack, no-duplicate-id
     * = FR-008), so a breach is a validation refusal (422) rather than a 500, and the write never reaches the commit.
     */
    private static Pack revalidated(Pack current, Styleguide styleguide) {
        Pack.Builder builder = Pack.builder().id(current.id()).descriptor(current.descriptor());
        current.tactics().forEach(builder::tactic);
        current.directives().forEach(builder::directive);
        current.styleguides().stream()
                .filter(existing -> !existing.id().equals(styleguide.id()))
                .forEach(builder::styleguide);
        builder.styleguide(styleguide);
        current.toolguides().forEach(builder::toolguide);
        current.paradigms().forEach(builder::paradigm);
        current.violations().forEach(builder::violation);
        try {
            return builder.build();
        } catch (ConstraintBreachException breach) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(breach.getMessage()), breach);
        }
    }
}
