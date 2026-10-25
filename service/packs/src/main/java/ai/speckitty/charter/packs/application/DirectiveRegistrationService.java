package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.CharterArtifact;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Registers a directive and a relationship into a pack (FR-007/009/010/012), the write half of the US1 reference
 * slice and the pattern every later kind copies. Every write is <strong>functional</strong> (ADR-0026): there is no
 * CRUD, no {@code DELETE}/{@code PATCH}; a registration goes <em>load &rarr; mutate &rarr; revalidate the whole
 * {@link Pack} &rarr; commit atomically</em>, so the aggregate invariants (belongs-to-pack, no-duplicate-id) gate the
 * write, and a refusal changes zero stored files (NFR-002, the {@link PackWriteStore}'s stage-and-swap enforces it).
 *
 * <h2>Dual-write ownership (analysis finding I1 / plan IC-05)</h2>
 *
 * A relationship can arrive two ways: inside an artifact-registration body ({@link #registerDirective}) or through the
 * standalone relationship endpoint ({@link #registerRelationship}). The chosen ownership rule is:
 *
 * <blockquote><strong>An artifact body may assert only relationships whose {@code from} is that artifact.</strong> A
 * body relationship with any other {@code from} is refused {@link RefusalCode#VALIDATION_FAILED} (422). A
 * cross-authored edge — one whose {@code from} is a different component — must be registered through
 * {@link #registerRelationship} ({@code PUT /packs/{id}/relationships}).</blockquote>
 *
 * Both doors upsert the <em>same</em> pack relationship shard, keyed by the unordered {@code {from, to}} pair,
 * last-write-wins, so they converge: re-registering a pair (in either orientation, via either door) replaces it. The
 * body door owns a component's own originating edges; the relationship door authors standalone or cross-authored
 * edges. {@code from} must resolve within the pack ({@link RefusalCode#REGISTRATION_CONFLICT}, 409, when it does not);
 * {@code to} may be external (graph endpoints need not be local).
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
     * Registers a directive and its body-carried relationships into the pack (POST create or PUT create-or-replace).
     *
     * @param pack the pack to register into
     * @param directive the assembled directive (already domain-valid)
     * @param bodyRelationships the relationships carried in the body; each {@code from} must be the directive's URN
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
    public Registered registerDirective(
            PackIdentifier pack,
            Directive directive,
            List<Relationship> bodyRelationships,
            boolean replace,
            @Nullable String ifMatch) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(directive, "directive");
        Objects.requireNonNull(bodyRelationships, "bodyRelationships");
        Pack current = load(pack);
        precondition(pack, ifMatch, false);
        boolean exists = current.directives().stream().anyMatch(d -> d.id().equals(directive.id()));
        if (!replace && exists) {
            throw new CharterRefusal(
                    RefusalCode.REGISTRATION_CONFLICT,
                    "Pack " + pack.value() + " already has a directive "
                            + directive.id().value() + ".");
        }
        ownBodyRelationships(directive.urn(), bodyRelationships);
        policy.authorize(
                current,
                exists ? WritePolicyGate.Operation.REPLACE : WritePolicyGate.Operation.REGISTER,
                directive.urn());
        Pack mutated = revalidated(current, directive);
        List<Relationship> merged = mergeAll(writeStore.registeredRelationships(pack), bodyRelationships);
        PackRevision revision = writeStore.commit(pack, mutated, merged);
        return new Registered(revision, !exists, directive.urn());
    }

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
     * @throws CharterRefusal {@link RefusalCode#PACK_NOT_FOUND} (404) when the pack is unknown, or
     *     {@link RefusalCode#REGISTRATION_CONFLICT} (409) when {@code from} does not resolve within the pack
     * @throws PreconditionException {@link PreconditionException.Kind#MISSING} (428) when no precondition was supplied,
     *     {@link PreconditionException.Kind#STALE} (412) when {@code ifMatch} is stale
     */
    public Registered registerRelationship(PackIdentifier pack, Relationship relationship, @Nullable String ifMatch) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(relationship, "relationship");
        Pack current = load(pack);
        precondition(pack, ifMatch, true);
        if (!urnsOf(current).contains(relationship.from())) {
            throw new CharterRefusal(
                    RefusalCode.REGISTRATION_CONFLICT,
                    "Relationship from " + relationship.from() + " does not resolve within pack " + pack.value() + ".");
        }
        policy.authorize(current, WritePolicyGate.Operation.RELATIONSHIP, relationship.from());
        List<Relationship> existing = writeStore.registeredRelationships(pack);
        boolean created = existing.stream().noneMatch(relationship::equals);
        List<Relationship> merged = upsert(existing, relationship);
        PackRevision revision = writeStore.commit(pack, current, merged);
        return new Registered(revision, created, relationship.from());
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

    /** Enforces the dual-write ownership rule: a body relationship must be authored from the directive itself. */
    private static void ownBodyRelationships(Urn owner, List<Relationship> bodyRelationships) {
        for (Relationship relationship : bodyRelationships) {
            if (!relationship.from().equals(owner)) {
                throw new CharterRefusal(
                        RefusalCode.VALIDATION_FAILED,
                        "A directive body may assert only relationships authored from "
                                + owner + ", but one is authored from " + relationship.from()
                                + "; register a cross-authored edge through PUT /relationships.");
            }
        }
    }

    private void precondition(PackIdentifier pack, @Nullable String ifMatch, boolean required) {
        if (ifMatch == null) {
            if (required) {
                throw new PreconditionException(
                        PreconditionException.Kind.MISSING,
                        "A relationship registration requires an If-Match precondition.");
            }
            return;
        }
        if (!PackRevision.matches(ifMatch, writeStore.currentRevision(pack))) {
            throw new PreconditionException(
                    PreconditionException.Kind.STALE, "The If-Match precondition is stale; read the pack again.");
        }
    }

    /**
     * Rebuilds the whole aggregate with {@code directive} added (or replacing the one of the same id) and revalidates
     * it: {@link Pack.Builder#build()} re-runs the aggregate invariants (belongs-to-pack, no-duplicate-id = FR-008),
     * so a breach is a validation refusal (422) rather than a 500, and the write never reaches the commit.
     */
    private static Pack revalidated(Pack current, Directive directive) {
        Pack.Builder builder = Pack.builder().id(current.id()).descriptor(current.descriptor());
        current.tactics().forEach(builder::tactic);
        current.directives().stream()
                .filter(existing -> !existing.id().equals(directive.id()))
                .forEach(builder::directive);
        builder.directive(directive);
        current.styleguides().forEach(builder::styleguide);
        current.toolguides().forEach(builder::toolguide);
        current.paradigms().forEach(builder::paradigm);
        current.violations().forEach(builder::violation);
        try {
            return builder.build();
        } catch (ConstraintBreachException breach) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(breach.getMessage()), breach);
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

    private static List<Relationship> mergeAll(List<Relationship> existing, List<Relationship> incoming) {
        List<Relationship> merged = new ArrayList<>(existing);
        for (Relationship relationship : incoming) {
            merged = new ArrayList<>(upsert(merged, relationship));
        }
        return List.copyOf(merged);
    }

    /**
     * Upserts {@code incoming} into {@code existing} on the unordered {@code {from, to}} pair, last-write-wins. It is
     * an explicit put, not a {@code Set.add}: {@link Relationship#equals(Object)} is pair-identity, so any
     * relationship already on the pair (in either orientation) is dropped and {@code incoming} appended. This mirrors
     * the storage shard's own upsert, kept here in pure application terms so the convergence is unit-testable.
     */
    private static List<Relationship> upsert(List<Relationship> existing, Relationship incoming) {
        List<Relationship> merged = new ArrayList<>(existing.size() + 1);
        for (Relationship relationship : existing) {
            if (!relationship.equals(incoming)) {
                merged.add(relationship);
            }
        }
        merged.add(incoming);
        return List.copyOf(merged);
    }
}
