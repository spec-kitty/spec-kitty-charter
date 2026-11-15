package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.EndpointRule;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.domain.MissionStepLinkRule;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.RelationshipEndpointRule;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final RelationshipTargetCheck targets;

    /**
     * A service over the injected ports.
     *
     * @param packs the port that loads the pack aggregate
     * @param writeStore the write-path seam that commits atomically and holds the relationship shard
     * @param policy the mutability gate consulted before a write (permissive by default; WP06 supplies ADR-0012)
     * @param targets the check that the compose can resolve a relationship's {@code to}, made before anything is written
     */
    @Autowired
    public RelationshipRegistrationService(
            PackRepository packs, PackWriteStore writeStore, WritePolicyGate policy, RelationshipTargetCheck targets) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.writeStore = Objects.requireNonNull(writeStore, "writeStore");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.targets = Objects.requireNonNull(targets, "targets");
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
     * @param draft the relationship to register, not yet bound; its {@code from} must resolve within the pack, and its
     *     {@code to} in the compose of the pack when it is pack-qualified or held by another pack
     * @param ifMatch the {@code If-Match} precondition; a sub-resource write <strong>requires</strong> one
     * @return the registration outcome; {@link Registered#created()} is {@code true} when the pair was not registered
     *     before (in either orientation)
     * @throws CharterRefusal {@link RefusalCode#PACK_NOT_FOUND} (404) when the pack is unknown,
     *     {@link RefusalCode#REGISTRATION_CONFLICT} (409) when {@code from} does not resolve within the pack,
     *     {@link RefusalCode#VALIDATION_FAILED} (422) for a self-loop, then, in this order:
     *     {@link RefusalCode#PROMPT_FRAGMENT_INVALID} (422 here) when the edge would give a held step enhancement an
     *     {@code overrides} edge or a second {@code enhances} edge, or replace its one {@code enhances} edge,
     *     {@link RefusalCode#VALIDATION_FAILED} (422) with the violation
     *     {@value EndpointRule#OVERRIDES_SOURCE_NOT_ACTIVATABLE} at
     *     {@code /from} when the edge is an {@code overrides} edge whose source kind is not activatable (FR-014), and
     *     {@link RefusalCode#VALIDATION_FAILED} (422) with the violation {@value RelationshipEndpointRule#CODE} at
     *     {@code /type} when the relation does not allow the endpoint kinds (ADR-0064 decision 13), then
     *     {@link RefusalCode#VALIDATION_FAILED} (422) with the violation {@value MissionStepLinkRule#CODE} at
     *     {@code /to} when a {@code requires} edge from a mission type of the pack names an action that is no step of
     *     it; after them the
     *     compose's {@link RefusalCode#PACK_REFERENCE_PACK_MISSING}, {@link RefusalCode#PACK_REFERENCE_UNQUALIFIED} or
     *     {@link RefusalCode#PACK_REFERENCE_ARTIFACT_MISSING} (422 here) when {@code to} cannot resolve, a composition
     *     refusal (500) when the configured packs do not compose, or the policy's
     *     {@link RefusalCode#GOVERNANCE_POLICY_VIOLATION} (409)
     * @throws PreconditionException {@link PreconditionException.Kind#MISSING} (428) when no precondition was supplied,
     *     {@link PreconditionException.Kind#STALE} (412) when {@code ifMatch} is stale
     */
    public Registered registerRelationship(PackIdentifier pack, RelationshipDraft draft, @Nullable String ifMatch) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(draft, "draft");
        // Held from load to swap, so the precondition, the merge over the current shard and the commit see one
        // state: a concurrent writer cannot slip between them (a lost update).
        return writeStore.exclusively(pack, () -> {
            Pack current = packs.load(pack);
            precondition(pack, ifMatch);
            if (!urnsOf(current).contains(draft.from())) {
                throw new CharterRefusal(
                        RefusalCode.REGISTRATION_CONFLICT,
                        "Relationship from " + draft.from() + " does not resolve within pack " + pack.value() + ".");
            }
            if (PackPolicy.joinsItself(current.descriptor().name(), draft.fromReference(), draft.toReference())) {
                throw new CharterRefusal(
                        RefusalCode.VALIDATION_FAILED,
                        "A relationship cannot be a self-loop: " + draft.from() + " to " + draft.toReference()
                                + " in pack " + pack.value() + ".");
            }
            List<Relationship> existing = writeStore.registeredRelationships(pack);
            // Before anything is written: an edge that breaks the edge rules of a held step enhancement would make
            // every composing read of the pack refuse (R-REL), an edge the endpoint table refuses every read, and a
            // target the compose cannot resolve the next offering read.
            // The fault order (WP05): the step-enhancement rules, then the source rule of slice C, then the table.
            refuseStepEnhancementEdge(current, draft, existing);
            Relationship relationship = bound(draft);
            refuseStrayStepLink(pack, current.descriptor().name(), relationship, existing);
            targets.require(pack, relationship.toReference());
            policy.authorize(current, WritePolicyGate.Operation.RELATIONSHIP, relationship.from());
            boolean created = existing.stream().noneMatch(relationship::equals);
            List<Relationship> merged = RelationshipMerge.upsert(existing, relationship);
            PackRevision revision =
                    writeStore.commit(pack, ifMatch, PackWriteStore.Change.relationships(current, merged));
            return new Registered(revision, created, relationship.from());
        });
    }

    /**
     * Binds the draft through the kernel's endpoint rule, or refuses it {@code validation_failed} with the bridge's
     * fault: every composing read of the pack would refuse the edge, as the load does such an edge in a graph document.
     * An {@code overrides} edge whose source is not of an activatable kind (FR-014, operator ruling of 2026-11-12 on
     * #447) is the violation {@value EndpointRule#OVERRIDES_SOURCE_NOT_ACTIVATABLE} at {@code /from}; an edge whose
     * relation does not allow its endpoint kinds (ADR-0064 decision 13) is {@value RelationshipEndpointRule#CODE} at
     * {@code /type}.
     */
    private static Relationship bound(RelationshipDraft draft) {
        return switch (draft.bind()) {
            case EndpointRule.Bound bound -> bound.relationship();
            case EndpointRule.Fault fault
            when EndpointRule.OVERRIDES_SOURCE_NOT_ACTIVATABLE.equals(fault.code()) -> {
                String reason = fault.reason(
                        draft.from().toString(), draft.toReference().toString());
                throw CharterRefusal.withViolations(
                        RefusalCode.VALIDATION_FAILED,
                        "The relationship is refused: " + reason + ".",
                        List.of(new RefusalViolation("/from", fault.code(), reason)));
            }
            case EndpointRule.Fault fault ->
                throw CharterRefusal.withViolations(
                        RefusalCode.VALIDATION_FAILED,
                        "The relationship is refused: " + fault.message() + ".",
                        List.of(new RefusalViolation("/type", RelationshipEndpointRule.CODE, fault.message())));
        };
    }

    /**
     * Refuses a {@code requires} edge from a mission type of the pack to an action that is not one of its steps
     * (operator ruling DM-01M7B1YQR5ZKQPDW6DX64KPNRN): every composing read of the pack would refuse it. The type's
     * steps are the targets of its own step links, authored or registered, which the load matched to its file; a step
     * link is added with its step, by registering the whole pack. The refusal is {@code validation_failed} with one
     * violation {@value MissionStepLinkRule#CODE} at {@code /to}.
     */
    private void refuseStrayStepLink(
            PackIdentifier pack, String packName, Relationship relationship, List<Relationship> registered) {
        Urn from = relationship.from();
        if (relationship.type() != RelationType.REQUIRES
                || from.kind() != NodeKind.MISSION_TYPE
                || relationship.to().kind() != NodeKind.ACTION) {
            return;
        }
        List<Relationship> links = new ArrayList<>(writeStore.authoredRelationships(pack));
        links.addAll(registered);
        boolean ownStep = MissionStepLinkRule.isOwnStepLink(packName, from, relationship)
                && links.stream()
                        .anyMatch(link -> MissionStepLinkRule.isOwnStepLink(packName, from, link)
                                && link.to().equals(relationship.to()));
        if (!ownStep) {
            String reason = "the requires edge from " + from + " to " + relationship.toReference()
                    + " names no step of the type";
            throw CharterRefusal.withViolations(
                    RefusalCode.VALIDATION_FAILED,
                    "The relationship is refused: " + reason + ".",
                    List.of(new RefusalViolation("/to", MissionStepLinkRule.CODE, reason)));
        }
    }

    /**
     * Refuses an edge that would give a held step enhancement an {@code overrides} edge (an enhancement only adds), a
     * second {@code enhances} edge, or no {@code enhances} edge at all (R-REL, FR-006), before the endpoint table judges
     * its kinds, as the load's step-enhancement rules come first. The pack loaded, so each held enhancement has its one
     * {@code enhances} edge already, in a source document or in the registered shard; the shard is the only one of the
     * two this write can change.
     */
    private static void refuseStepEnhancementEdge(
            Pack current, RelationshipDraft draft, List<Relationship> registered) {
        Urn from = draft.from();
        if (isHeldStepEnhancement(current, from)) {
            if (draft.type() == RelationType.OVERRIDES) {
                throw refused(from, "an enhancement only adds, so it cannot be the source of an overrides edge");
            }
            boolean registeredAlready = registered.stream()
                    .anyMatch(edge ->
                            edge.type() == RelationType.ENHANCES && edge.from().equals(from) && samePair(edge, draft));
            if (draft.type() == RelationType.ENHANCES && !registeredAlready) {
                throw refused(from, "it has its enhances edge already and may have exactly one");
            }
        }
        for (Relationship edge : registered) {
            if (edge.type() == RelationType.ENHANCES
                    && isHeldStepEnhancement(current, edge.from())
                    && samePair(edge, draft)
                    && !(draft.type() == RelationType.ENHANCES && from.equals(edge.from()))) {
                throw refused(edge.from(), "the relationship would replace its enhances edge and leave it with none");
            }
        }
    }

    /** Whether the draft names the registered edge's unordered pair, the identity of a relationship (ADR-0027). */
    private static boolean samePair(Relationship edge, RelationshipDraft draft) {
        return (edge.from().equals(draft.from()) && edge.to().equals(draft.to()))
                || (edge.from().equals(draft.to()) && edge.to().equals(draft.from()));
    }

    private static boolean isHeldStepEnhancement(Pack pack, Urn urn) {
        return urn.kind() == NodeKind.STEP_ENHANCEMENT && urnsOf(pack).contains(urn);
    }

    private static CharterRefusal refused(Urn enhancement, String rule) {
        return new CharterRefusal(
                RefusalCode.PROMPT_FRAGMENT_INVALID,
                "The step enhancement " + enhancement + " is refused: " + rule + ".");
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
        return pack.artifacts().stream().map(CharterArtifact::urn).collect(Collectors.toUnmodifiableSet());
    }
}
