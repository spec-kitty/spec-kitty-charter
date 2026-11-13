package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.StepBearer;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.EnhancementCompose;
import ai.speckitty.charter.packs.domain.InForceRequest;
import ai.speckitty.charter.packs.domain.InForceResolution;
import ai.speckitty.charter.packs.domain.InForceStates;
import ai.speckitty.charter.packs.domain.LayerMerge;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.PackCompanionFiles;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.RequiredFloor;
import ai.speckitty.charter.packs.domain.SlotCompose;
import ai.speckitty.charter.packs.domain.StepHolding;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * What one verified read of a composition closure answers (NFR-007, FR-009, FR-023): a read-only value an adapter maps
 * into its own snapshot, so no caller beyond it holds packs types. Every field comes from the same
 * {@link ConsistentRead}, so they all name the inputs {@link #fingerprint()} digests.
 *
 * @param packs the closure's packs in the offering's shape, in dependency order, each with its resolved edges
 * @param heldIds the ids each pack of the closure holds, per artifact kind, mission types included (one kind model,
 *     IC-20)
 * @param holderOf each held URN with the descriptor {@code pack_id} of the first pack, in the closure's order, that holds
 *     it: the pack a bare reference from outside the holders resolves to
 * @param floor the must-have items of the closure's governing presets, with the requiring packs. It is built from
 *     {@link #heldPresets()}, so it follows the copy of a preset that wins (ADR-0053 d6): the must-have items of a
 *     copy that loses require nothing. It holds held ids only: a must-have item no pack of the closure holds is in
 *     its {@link RequiredFloor#unheld()} report instead (A10)
 * @param policy the mutability policies of the closure's packs, by descriptor {@code pack_id}, each scoped to what
 *     the pack and the packs it builds on hold (A15), so a lock never reaches an unrelated pack's artifact
 * @param closurePackIds the closure's descriptor {@code pack_id}s, in closure order
 * @param heldPresets the closure's presets, each id once with its holding pack. When two packs of the closure hold one
 *     preset id, the last in merge order wins, the rule ADR-0053 d6 applies to layer overrides; {@link #presets()} is
 *     a view by id over this list
 * @param parents each closure pack's {@code parent_pack}, by descriptor {@code pack_id}; a pack without one is absent
 * @param fingerprint the verified digest of the inputs every field was read from
 * @param edges the typed edges of the closure's packs, indexed from the facts the same read holds; the reach answers
 *     from it with no further read
 * @param mergeInputs what {@link #merged()} builds the layer merge from: the composed packs and the facts the same read
 *     holds
 * @param closureFingerprint the verified digest of the packs IN the closure alone, from the same read: the closure's
 *     packs, their configured ids and levels in closure order, and the files of those packs. A change to a configured
 *     pack outside the closure leaves it as it is, where {@link #fingerprint()} (the digest of every configured pack)
 *     changes. It is what the effective reads' entity tags cover
 */
public record ClosureView(
        List<OfferingReadService.OfferingEntry> packs,
        Map<ArtifactKind, Set<String>> heldIds,
        Map<Urn, String> holderOf,
        RequiredFloor floor,
        PerPackPolicy policy,
        List<String> closurePackIds,
        List<HeldPreset> heldPresets,
        Map<String, String> parents,
        String fingerprint,
        EdgeIndex edges,
        MergeInputs mergeInputs,
        String closureFingerprint) {

    /**
     * What the layer merge is built from: the composed closure and the facts the verified read holds, with the merge
     * kept once it is built. The merge never reads again (NFR-007); it is built on the first {@link #merged()} and not
     * before, so a read that never asks for it, {@code /offering} among them, never runs it. The write cascade asks for it
     * through {@link #reach} (FR-038).
     */
    public static final class MergeInputs {

        private static final MergeInputs NONE = new MergeInputs(List.of(), Map.of());

        private final List<PackUnderResolution> packs;
        private final Map<String, PackCompositionFacts> facts;
        private final WinnerSource winners;
        private @Nullable MergedView built;

        /**
         * The inputs of a closure's merge, with no way to read the entity of a winner.
         *
         * @param packs the composed closure, in closure order
         * @param facts the facts each pack was read with, by configured identifier
         */
        public MergeInputs(List<PackUnderResolution> packs, Map<String, PackCompositionFacts> facts) {
            this(packs, facts, WinnerSource.none());
        }

        /**
         * The inputs of a closure's merge.
         *
         * @param packs the composed closure, in closure order
         * @param facts the facts each pack was read with, by configured identifier
         * @param winners where the entity of a winner is read from, when {@link #effectiveArtifact} asks
         */
        public MergeInputs(
                List<PackUnderResolution> packs, Map<String, PackCompositionFacts> facts, WinnerSource winners) {
            this.packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
            this.facts = Map.copyOf(Objects.requireNonNull(facts, "facts"));
            this.winners = Objects.requireNonNull(winners, "winners");
        }

        WinnerSource winners() {
            return winners;
        }

        /** The composed closure, in closure order. */
        List<PackUnderResolution> packs() {
            return packs;
        }

        /** The facts each pack was read with, by configured identifier. */
        Map<String, PackCompositionFacts> facts() {
            return facts;
        }

        /**
         * The inputs of a view built without the closure's packs: it merges nothing.
         *
         * @return the empty inputs
         */
        public static MergeInputs none() {
            return NONE;
        }

        /** The union floor (A1): the merged closure's when it was built, else computed with no override judged. */
        synchronized RequiredFloor floor() {
            return built != null ? built.closure().floor() : LayerMerge.floorOf(packs, facts);
        }

        /**
         * The layer merge, built once. Two closure-level refusals come out of it, both {@code 500 pack_unreadable}
         * whatever the activation: a layer override the overridden artifact's level forbids (C21, from
         * {@link LayerMerge}), and then an enhancement of a step whose mission type is locked along the closure without
         * the enhancing pack's own policy (C22 and C25 as amended, OD-2 (A)), the read side of the
         * {@code override_not_permitted} the registration impact check answers.
         */
        synchronized MergedView merged(PerPackPolicy policy, EdgeIndex edges) {
            if (built == null) {
                MergedClosure merged = LayerMerge.merge(packs, facts, policy);
                refuseLockedStepEnhanced(policy);
                built = new MergedView(merged, edges.merged(merged), bindingsOf(merged));
            }
            return built;
        }

        private void refuseLockedStepEnhanced(PerPackPolicy policy) {
            EnhancementCompose.lockedTargets(
                            packs,
                            pack -> facts.getOrDefault(pack.configured().id().value(), PackCompositionFacts.NONE)
                                    .edges(),
                            policy)
                    .stream()
                    .findFirst()
                    .ifPresent(locked -> {
                        throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, locked.detail(), locked.packs());
                    });
        }

        /**
         * The mission type each winner is bound to, as the pack that holds the version in force declares it: an artifact
         * a later level replaced is bound as the replacing version is, which may be to no mission type.
         */
        private Map<Urn, Urn> bindingsOf(MergedClosure merged) {
            Map<Urn, Urn> bindings = new LinkedHashMap<>();
            merged.winners()
                    .forEach((urn, winner) -> Optional.ofNullable(
                                    facts.get(winner.packId().value()))
                            .map(held -> held.bindings().get(urn.toString()))
                            .flatMap(Urn::parse)
                            .ifPresent(missionType -> bindings.put(urn, missionType)));
            return bindings;
        }
    }

    /**
     * Where the entity of a winning artifact is read from: the one read the merge does not hold, because the facts it
     * is built from name the artifacts and never carry them. The view that {@link ClosureQueries} builds checks every
     * read against the closure digest it was verified at and refuses {@code stale_input} when the packs moved.
     */
    @FunctionalInterface
    public interface WinnerSource {

        /**
         * Reads one artifact of a pack.
         *
         * @param pack the configured pack that holds the version in force
         * @param kind the artifact's kind
         * @param id the artifact's id
         * @return the entity; empty when the pack holds no readable artifact of that kind and id
         */
        Optional<CharterArtifact> read(PackIdentifier pack, ArtifactKind kind, String id);

        /**
         * Reads the stored prompt companion of a step in one pack, as {@link PackCompanionFiles#stepFiles} gives it: the
         * bytes as stored, never a re-rendering. A source that cannot read the stored step files answers a wiring fault
         * ({@link IllegalStateException}).
         *
         * @param pack the configured pack at the merged winner's place of the step
         * @param step the step, {@code action:M/s}
         * @return the prompt's bytes; empty when that pack's mission type gives the step no prompt
         */
        default Optional<Payload> stepPrompt(PackIdentifier pack, Urn step) {
            throw new IllegalStateException("this view was built without the stored step files and cannot read the"
                    + " prompt of " + step + " in pack " + pack.value());
        }

        /**
         * A source for a view built without the closure's packs: it has nothing to read from, so a read is a wiring fault
         * ({@link IllegalStateException}), never an unreadable pack.
         *
         * @return the source that refuses every read
         */
        static WinnerSource none() {
            return (pack, kind, id) -> {
                throw new IllegalStateException(
                        "this view was built without a pack store and cannot read the winner of " + kind + " " + id);
            };
        }
    }

    /**
     * The pack that holds an artifact, as the reads name it.
     *
     * @param packId the configured identifier of the pack
     * @param level the level the pack is configured at
     */
    public record Holder(PackIdentifier packId, PackLevel level) {

        /**
         * Checks the fields.
         *
         * @param packId the holding pack's configured identifier
         * @param level the holding pack's level
         */
        public Holder {
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(level, "level");
        }
    }

    /**
     * An artifact as the winning copy of an activation's closure: the entity, the pack that holds it and, for an agent
     * profile, its governance.
     *
     * @param artifact the winning entity, as the winner's pack stores it (a kernel {@link CharterArtifact}: a caller
     *     narrows it to its kind)
     * @param holder the pack that holds the version in force: its configured identifier and level
     * @param governance the profile's governance under the activation; empty for every kind but {@code agent_profile}
     */
    public record EffectiveArtifactView(
            CharterArtifact artifact, Holder holder, Optional<ProfileGovernance.Governance> governance) {

        /**
         * Checks the fields.
         *
         * @param artifact the winning entity
         * @param holder the holding pack
         * @param governance the profile's governance
         */
        public EffectiveArtifactView {
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(holder, "holder");
            Objects.requireNonNull(governance, "governance");
        }
    }

    /**
     * The closure as the layer merge sees it: the version of each artifact in force, and the typed edges re-homed
     * through the winners (FR-009, FR-010).
     *
     * @param closure the winners, the layer overrides, the languages and the merged presets
     * @param edges the closure's edges with no edge from a losing place and every edge to one ending at the winner
     * @param bindings the mission type each winner that is bound to one is bound to (A7 Q1): a step contract to its
     *     mission
     */
    public record MergedView(MergedClosure closure, EdgeIndex edges, Map<Urn, Urn> bindings) {

        /**
         * Checks the fields.
         *
         * @param closure the layer merge
         * @param edges the merged edges
         * @param bindings the mission type each bound winner is bound to
         */
        public MergedView {
            Objects.requireNonNull(closure, "closure");
            Objects.requireNonNull(edges, "edges");
            bindings = Map.copyOf(Objects.requireNonNull(bindings, "bindings"));
        }

        /**
         * A merged view whose winners are bound to no mission type.
         *
         * @param closure the layer merge
         * @param edges the merged edges
         */
        public MergedView(MergedClosure closure, EdgeIndex edges) {
            this(closure, edges, Map.of());
        }

        /**
         * The forward reach over the merged edges ({@link Reachability#reach}).
         *
         * @param request what to walk
         * @return what the walk reached, stopped at, left behind a stop and passed through
         */
        public ReachResult reach(ReachRequest request) {
            return Reachability.reach(edges, request);
        }

        /**
         * The {@code overrides} and {@code enhances} edges among the winners, as the in-force resolution reads them
         * (FR-013 to FR-016): from the merged index, so an edge a losing version declared is not among them. The binding
         * of a step enhancer is derived here, from the merged edges and before an edge to a non-winner is dropped (OD-6,
         * rule E6 as amended): a {@code step_enhancement} with exactly one {@code enhances} target, an {@code action:},
         * is bound to the mission type of the step when the closure holds the step ({@link StepHolding}), and the edge is
         * not counted; when it does not, the edge is kept though its target is no winner, so the enhancer is pruned for
         * its target. Every other shape keeps rule E6 as built.
         *
         * @return each overrider and enhancer with the winning artifacts it names, and the bindings
         */
        InForceResolution.Edges resolutionEdges() {
            Map<Urn, Set<Urn>> overrides = new LinkedHashMap<>();
            Map<Urn, Set<Urn>> enhances = new LinkedHashMap<>();
            Map<Urn, Urn> derived = new LinkedHashMap<>(bindings);
            closure.winners().forEach((urn, winner) -> {
                List<Relationship> written = edges.inPack(new PackIdentifier(winner.descriptorPackId()))
                        .resolve(urn)
                        .map(CharterArtifact::derivedRelationships)
                        .orElse(List.of());
                written.stream()
                        .filter(edge -> edge.type() == RelationType.OVERRIDES)
                        .filter(edge -> closure.winners().containsKey(edge.to()))
                        .forEach(edge -> overrides
                                .computeIfAbsent(urn, ignored -> new LinkedHashSet<>())
                                .add(edge.to()));
                enhancements(urn, written, enhances, derived);
            });
            return new InForceResolution.Edges(overrides, enhances, derived);
        }

        /** Files the {@code enhances} edges of one winner, or the binding that stands for them (OD-6). */
        private void enhancements(
                Urn source, List<Relationship> written, Map<Urn, Set<Urn>> enhances, Map<Urn, Urn> derived) {
            Set<Urn> targets = new LinkedHashSet<>();
            written.stream()
                    .filter(edge -> edge.type() == RelationType.ENHANCES)
                    .forEach(edge -> targets.add(edge.to()));
            Optional<Urn> step = StepHolding.soleActionTarget(source, targets);
            if (step.isPresent() && edges.holdsStep(step.get())) {
                StepHolding.missionTypeOf(step.get()).ifPresent(missionType -> derived.put(source, missionType));
            } else if (step.isPresent()) {
                enhances.put(source, Set.of(step.get()));
            } else {
                targets.stream()
                        .filter(closure.winners()::containsKey)
                        .forEach(target -> enhances.computeIfAbsent(source, ignored -> new LinkedHashSet<>())
                                .add(target));
            }
        }
    }

    /**
     * A preset of the closure with the pack that holds it.
     *
     * @param preset the preset
     * @param descriptorPackId the descriptor {@code pack_id} of the holding pack
     */
    public record HeldPreset(Preset preset, String descriptorPackId) {

        /**
         * Checks the fields.
         *
         * @param preset the preset
         * @param descriptorPackId the holding pack
         */
        public HeldPreset {
            Objects.requireNonNull(preset, "preset");
            Objects.requireNonNull(descriptorPackId, "descriptorPackId");
        }
    }

    /**
     * Checks and copies every field.
     *
     * @param packs the closure's packs
     * @param heldIds the held ids per kind
     * @param holderOf the holder of each held URN
     * @param floor the required floor
     * @param policy the per-pack policies
     * @param closurePackIds the closure's {@code pack_id}s
     * @param heldPresets the presets, each id once
     * @param parents the parent of each pack that has one
     * @param fingerprint the verified digest
     * @param edges the closure's typed edges
     * @param mergeInputs what the layer merge is built from
     * @param closureFingerprint the digest of the closure's own packs
     */
    public ClosureView {
        packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
        heldIds = copyOfHeld(Objects.requireNonNull(heldIds, "heldIds"));
        holderOf = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(holderOf, "holderOf")));
        Objects.requireNonNull(floor, "floor");
        Objects.requireNonNull(policy, "policy");
        closurePackIds = List.copyOf(Objects.requireNonNull(closurePackIds, "closurePackIds"));
        heldPresets = List.copyOf(Objects.requireNonNull(heldPresets, "heldPresets"));
        parents = Map.copyOf(Objects.requireNonNull(parents, "parents"));
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(mergeInputs, "mergeInputs");
        Objects.requireNonNull(closureFingerprint, "closureFingerprint");
    }

    /**
     * A view whose closure digest is the whole source digest: for a view built without a scoped one.
     *
     * @param packs the closure's packs
     * @param heldIds the held ids per kind
     * @param holderOf the holder of each held URN
     * @param floor the required floor
     * @param policy the per-pack policies
     * @param closurePackIds the closure's {@code pack_id}s
     * @param heldPresets the presets, each id once
     * @param parents the parent of each pack that has one
     * @param fingerprint the verified digest
     * @param edges the closure's typed edges
     * @param mergeInputs what the layer merge is built from
     */
    public ClosureView(
            List<OfferingReadService.OfferingEntry> packs,
            Map<ArtifactKind, Set<String>> heldIds,
            Map<Urn, String> holderOf,
            RequiredFloor floor,
            PerPackPolicy policy,
            List<String> closurePackIds,
            List<HeldPreset> heldPresets,
            Map<String, String> parents,
            String fingerprint,
            EdgeIndex edges,
            MergeInputs mergeInputs) {
        this(
                packs,
                heldIds,
                holderOf,
                floor,
                policy,
                closurePackIds,
                heldPresets,
                parents,
                fingerprint,
                edges,
                mergeInputs,
                fingerprint);
    }

    /**
     * A view whose layer merge is not built from the closure's packs: {@link #merged()} merges nothing.
     *
     * @param packs the closure's packs
     * @param heldIds the held ids per kind
     * @param holderOf the holder of each held URN
     * @param floor the required floor
     * @param policy the per-pack policies
     * @param closurePackIds the closure's {@code pack_id}s
     * @param heldPresets the presets, each id once
     * @param parents the parent of each pack that has one
     * @param fingerprint the verified digest
     * @param edges the closure's typed edges
     */
    public ClosureView(
            List<OfferingReadService.OfferingEntry> packs,
            Map<ArtifactKind, Set<String>> heldIds,
            Map<Urn, String> holderOf,
            RequiredFloor floor,
            PerPackPolicy policy,
            List<String> closurePackIds,
            List<HeldPreset> heldPresets,
            Map<String, String> parents,
            String fingerprint,
            EdgeIndex edges) {
        this(
                packs,
                heldIds,
                holderOf,
                floor,
                policy,
                closurePackIds,
                heldPresets,
                parents,
                fingerprint,
                edges,
                MergeInputs.none());
    }

    /**
     * A view whose closure writes no typed edge.
     *
     * @param packs the closure's packs
     * @param heldIds the held ids per kind
     * @param holderOf the holder of each held URN
     * @param floor the required floor
     * @param policy the per-pack policies
     * @param closurePackIds the closure's {@code pack_id}s
     * @param heldPresets the presets, each id once
     * @param parents the parent of each pack that has one
     * @param fingerprint the verified digest
     */
    public ClosureView(
            List<OfferingReadService.OfferingEntry> packs,
            Map<ArtifactKind, Set<String>> heldIds,
            Map<Urn, String> holderOf,
            RequiredFloor floor,
            PerPackPolicy policy,
            List<String> closurePackIds,
            List<HeldPreset> heldPresets,
            Map<String, String> parents,
            String fingerprint) {
        this(
                packs,
                heldIds,
                holderOf,
                floor,
                policy,
                closurePackIds,
                heldPresets,
                parents,
                fingerprint,
                EdgeIndex.empty(),
                MergeInputs.none());
    }

    /**
     * The forward reach over the merged edges ({@link MergedView#reach}), so the write cascade never follows an edge of
     * an artifact a later level replaced (FR-038, rule E20). Where no layer override exists the merged and the unmerged
     * walks agree. It builds the layer merge on its first call, from the read this view holds.
     *
     * @param request what to walk
     * @return what the walk reached, stopped at, left behind a stop and passed through
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as {@link #merged()} does
     */
    public ReachResult reach(ReachRequest request) {
        return merged().reach(request);
    }

    /**
     * The must-have items of the copy of every governing preset in force at each level the closure holds, with the requiring packs (A1, rule
     * C31): the floor the effective reads answer. Unlike {@link #floor()}, which follows the copy of a preset that wins,
     * it is the union across levels. It judges no layer override, so it is answered for a closure whose {@link #merged()}
     * refuses.
     *
     * @return the union floor
     */
    public RequiredFloor mergedFloor() {
        return mergeInputs.floor();
    }

    /**
     * The closure under the layer merge (FR-009 to FR-012): the version of each artifact in force across levels, every
     * version a later level replaced, and the typed edges re-homed through the winners. It is built from the composed
     * packs and the facts this verified read holds, on the first call, and kept: nothing is read again (NFR-007).
     * {@link #holderOf()} stays the first holder in closure order, which the compose's reference checks use; the merged
     * view answers the winner.
     *
     * @return the merge of the closure
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} when a layer override
     *     breaks the owner's level (FR-011) or an {@code overrides} edge resolves to its own source (A12)
     */
    public MergedView merged() {
        return mergeInputs.merged(policy, edges);
    }

    /**
     * What is in force under a request, over the merged closure (FR-013 to FR-016).
     *
     * @param request what the document selects, denies and filters
     * @return one state per winning artifact and the rules not applied
     */
    public InForceStates inForce(InForceRequest request) {
        MergedView view = merged();
        return InForceResolution.resolve(view.closure(), view.resolutionEdges(), request);
    }

    /**
     * Refuses a request whose step enhancements in force name a slot the prompt in force of their step does not declare
     * (C38, FR-001; {@link SlotCompose}): an enhancement in force, its winning copy only, judged against the prompt at
     * the merged winner's place of its step. {@link #inForce} stays a pure resolution: a caller that composes asks this
     * explicitly, once the states are resolved (squad A3): the composing reads, the registration gate per stored
     * activation and the activation writes before they store. It reads nothing more than this view (NFR-007).
     *
     * @param states what {@link #inForce} answered for the request
     * @throws CharterRefusal with {@code PROMPT_SLOT_MISSING} for the first missing slot, by enhancement in the order the
     *     closure first holds them and then by fill, naming the enhancing pack and then the pack of the prompt in force;
     *     or as {@link #merged()} does
     */
    public void requireSlots(InForceStates states) {
        Objects.requireNonNull(states, "states");
        MergedView view = merged();
        Optional<SlotCompose.SlotViolation> first =
                SlotCompose.missing(view.closure(), mergeInputs.facts(), states).stream()
                        .findFirst();
        if (first.isEmpty()) {
            return;
        }
        Map<PackIdentifier, PackUnderResolution> byId = new LinkedHashMap<>();
        mergeInputs.packs().forEach(pack -> byId.putIfAbsent(pack.configured().id(), pack));
        SlotCompose.SlotViolation missing = first.get();
        throw new CharterRefusal(
                RefusalCode.PROMPT_SLOT_MISSING,
                missing.detail(pack -> Optional.ofNullable(byId.get(pack))
                        .map(held -> held.descriptor().name())
                        .orElse(pack.value())),
                missing.packs().stream()
                        .map(pack -> Optional.ofNullable(byId.get(pack))
                                .map(held -> new RefusalPack(
                                        held.descriptor().packId(),
                                        pack.value(),
                                        held.descriptor().name()))
                                .orElseGet(() -> new RefusalPack(pack.value(), pack.value(), pack.value())))
                        .toList());
    }

    /**
     * The effective prompt of a step over the merged closure (FR-003, FR-005; ADR-0066 decisions 4, 5 and 9; research
     * R8 as amended by operator ruling Q3, 2026-11-13): the stored prompt companion at the step's place
     * ({@link #stepPlace}, the rule the step read and the action context use), every slot it declares and, per slot, the
     * fragments of the winning copies of the step enhancements in force ({@link EffectivePromptQuery}). It judges nothing
     * itself: the caller has run {@link #requireAction}, the mission-type check and {@link #requireSlots} on the same
     * states. The versions and the prompt are read from the packs against the closure digest this view was verified at;
     * the slots and fragments come from the facts this view holds.
     *
     * @param missionType the mission type
     * @param action the action
     * @param states what {@link #inForce} answered for the request
     * @return the effective prompt; no byte, no slot and no pack for a step with no prompt companion
     * @throws CharterRefusal as {@link #merged()} does, with {@code PACK_UNREADABLE} when the place's pack cannot read
     *     the companion, and with {@code STALE_INPUT} when the packs changed since this view was verified
     */
    public EffectivePromptQuery.EffectivePrompt effectivePrompt(
            String missionType, String action, InForceStates states) {
        Objects.requireNonNull(missionType, "missionType");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(states, "states");
        Urn step = Urn.action(missionType, action);
        Map<PackIdentifier, String> names = new LinkedHashMap<>();
        mergeInputs
                .packs()
                .forEach(pack -> names.putIfAbsent(
                        pack.configured().id(), pack.descriptor().name()));
        return EffectivePromptQuery.answer(
                step,
                stepPlace(missionType, action),
                merged().closure(),
                mergeInputs.facts(),
                urn -> states.stateOf(urn)
                        .filter(InForceStates.ArtifactState::active)
                        .isPresent(),
                pack -> Optional.ofNullable(names.get(pack))
                        .orElseThrow(() -> new IllegalStateException(
                                "pack " + pack.value() + " wins in the merge but is not a pack of the closure")),
                pack -> mergeInputs.winners().stepPrompt(pack, step));
    }

    /**
     * The effective graph over the merged closure (FR-018, rule E15): the artifacts in force plus each held step whose
     * mission type is in force, the merged edges among them, and the layer overrides of those nodes. The node predicate
     * is built here from the in-force states, never handed in (decision {@code 01M7037ZNKJ6JTRKGD7G695ZV1}); it reads
     * nothing more than this view (NFR-007).
     *
     * @param states what {@link #inForce} answered for the request
     * @return the graph
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as {@link #merged()} does
     */
    public GraphProjection mergedGraph(InForceStates states) {
        MergedView view = merged();
        return GraphProjection.of(view.edges(), view.closure(), effective(states));
    }

    /**
     * The action context over the merged closure (FR-015, FR-019, rule E16 as amended by FR-008): the effective form of
     * ADR-0018's walk ({@link ActionContextWalk#walkEffective}) from the held step
     * {@code action:<missionType>/<action>} over the artifacts in force, with their enhancers, {@code depth} counting
     * {@code suggests} hops. Beside what the action's five steps reach, it lists under the ownership rule what the
     * mission type scopes and what no action and no mission type scopes, each entry naming its pass.
     *
     * @param missionType the mission type
     * @param action the action
     * @param depth the number of {@code suggests} hops, at least 0
     * @param states what {@link #inForce} answered for the request
     * @return what the action reaches; no entry, and {@code missionTypeInForce} false, for a mission type not in force
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code MISSION_TYPE_NOT_FOUND} or
     *     {@code ACTION_NOT_FOUND}, judged on the merged closure, or as {@link #merged()} does
     */
    public ActionContextWalk.Context actionContext(String missionType, String action, int depth, InForceStates states) {
        return new ActionContextWalk(merged().edges()).walkEffective(missionType, action, depth, effective(states));
    }

    /**
     * Judges the names of an action context on the merged closure and walks nothing: the same refusals as
     * {@link #actionContext}, without an in-force resolution.
     *
     * @param missionType the mission type
     * @param action the action
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code MISSION_TYPE_NOT_FOUND} or
     *     {@code ACTION_NOT_FOUND}, judged on the merged closure, or as {@link #merged()} does
     */
    public void requireAction(String missionType, String action) {
        new ActionContextWalk(merged().edges()).requireKnown(missionType, action);
    }

    /**
     * A profile's governance over the merged closure (FR-003, operator rulings on the walker rules): the profile walk
     * ({@link GovernanceWalk}) over the merged edges with the effective set as the predicate, as {@link #actionContext}
     * walks an action. The profile is the start and is exempt from the predicate. Each entry names the winner.
     *
     * @param profile the profile, {@code agent_profile:<id>}
     * @param depth the number of {@code suggests} hops, at least 0
     * @param states what {@link #inForce} answered for the request
     * @return the governance; no entry for a profile the merged closure does not hold
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as {@link #merged()} does
     * @throws IllegalArgumentException when {@code depth} is negative
     */
    public ProfileGovernance.Governance governance(Urn profile, int depth, InForceStates states) {
        MergedView view = merged();
        GovernanceWalk.Result walked = GovernanceWalk.over(view.edges()).walk(profile, depth, effective(states));
        return ProfileGovernance.Governance.of(walked, urn -> winnerOf(view, urn));
    }

    /**
     * A profile's governance over this closure, taken as one pack's composed {@code depends_on} closure (fold W5): the
     * profile walk over the merged edges with every node in force, plus the edges written to a target the edge's place
     * does not hold, which are listed with {@code held} false and never walked from. Each held entry names the merge
     * winner, the copy whose edges the walk followed, as {@link #governance} does (operator ruling, decision {@code
     * 01M72NRA73EZ02HYYMBQQVRJF3}). {@link ProfileGovernance#ofPack} builds the view and calls this inside the caller's
     * verified read.
     *
     * @param profile the profile, {@code agent_profile:<id>}
     * @param depth the number of {@code suggests} hops, at least 0
     * @return the governance
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as {@link #merged()} does
     * @throws IllegalArgumentException when {@code depth} is negative
     */
    public ProfileGovernance.Governance packGovernance(Urn profile, int depth) {
        MergedView view = merged();
        GovernanceWalk.Result walked = GovernanceWalk.overWritten(view.edges()).walk(profile, depth, urn -> true);
        return ProfileGovernance.Governance.of(walked, urn -> winnerOf(view, urn));
    }

    private static Optional<Holder> winnerOf(MergedView view, Urn urn) {
        return view.closure().winnerOf(urn).map(winner -> new Holder(winner.packId(), winner.level()));
    }

    /**
     * The winning copy of an artifact under an activation: the entity of the version in force across levels, the pack
     * that holds it and, for an agent profile, its governance ({@link #governance}). The entity is read from the
     * winner's pack, against the closure digest this view was verified at; the merge, the holder and the governance read
     * nothing.
     *
     * @param kind the artifact's kind
     * @param id the artifact's id
     * @param depth the number of {@code suggests} hops of a profile's governance, at least 0
     * @param states what {@link #inForce} answered for the request
     * @return the winner; empty when no pack of the closure holds the artifact
     * @throws CharterRefusal as {@link #merged()} does, with {@code PACK_UNREADABLE} when the winner's pack holds the
     *     artifact and cannot read it, and with {@code STALE_INPUT} when the packs changed since this view was verified
     */
    public Optional<EffectiveArtifactView> effectiveArtifact(
            ArtifactKind kind, String id, int depth, InForceStates states) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(states, "states");
        Optional<Urn> urn = urnOf(kind, id);
        MergedView view = merged();
        Optional<MergedClosure.Winner> winner = urn.flatMap(view.closure()::winnerOf);
        if (winner.isEmpty()) {
            return Optional.empty();
        }
        MergedClosure.Winner won = winner.get();
        CharterArtifact artifact = mergeInputs
                .winners()
                .read(won.packId(), kind, id)
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.PACK_UNREADABLE,
                        "pack `" + won.packId().value() + "` holds " + won.urn() + " and cannot read it"));
        Optional<ProfileGovernance.Governance> governance = kind == ArtifactKind.AGENT_PROFILE
                ? Optional.of(governance(won.urn(), depth, states))
                : Optional.empty();
        return Optional.of(new EffectiveArtifactView(artifact, new Holder(won.packId(), won.level()), governance));
    }

    /**
     * The place of a step over the merged closure (research R8, A7, DIV-243, review cycle 1 of WP09): the pack whose
     * version of the mission type the step is read from. It is the winner of {@code mission_type:<missionType>} when that
     * version declares the step, among its steps or its on-demand steps; else, when the closure holds the step
     * ({@link StepHolding}), the merged winner of {@code action:<missionType>/<action>} when its version of the mission
     * type declares the step, which keeps a step that only a version the winner replaced declares. One rule places the
     * step for the step read, the action context's links and a step contract's step link, so each names a step the step
     * read answers. The versions are read against the closure digest this view was verified at.
     *
     * @param missionType the mission type
     * @param action the step
     * @return the place's pack; empty when no version of the mission type the closure holds declares the step at either
     *     place
     * @throws CharterRefusal as {@link #merged()} does, with {@code PACK_UNREADABLE} when a winner's pack holds the
     *     mission type and cannot read it, and with {@code STALE_INPUT} when the packs changed since this view was
     *     verified
     */
    public Optional<PackIdentifier> stepPlace(String missionType, String action) {
        Objects.requireNonNull(missionType, "missionType");
        Objects.requireNonNull(action, "action");
        MergedView view = merged();
        Optional<Urn> type = urnOf(ArtifactKind.MISSION_TYPE, missionType);
        Optional<PackIdentifier> winner = type.flatMap(view.closure()::winnerOf).map(MergedClosure.Winner::packId);
        if (winner.filter(pack -> versionOf(pack, missionType)
                        .orElseThrow(() -> new CharterRefusal(
                                RefusalCode.PACK_UNREADABLE,
                                "pack `" + pack.value() + "` holds mission_type:" + missionType
                                        + " and cannot read it"))
                        .declaresStep(action))
                .isPresent()) {
            return winner;
        }
        Optional<Urn> step;
        try {
            step = Optional.of(Urn.action(missionType, action));
        } catch (IllegalArgumentException invalid) {
            step = Optional.empty();
        }
        return step.filter(view.edges()::holdsStep)
                .flatMap(view.closure()::winnerOf)
                .map(MergedClosure.Winner::packId)
                .filter(pack -> versionOf(pack, missionType)
                        .filter(version -> version.declaresStep(action))
                        .isPresent());
    }

    /**
     * A pack's version of the mission type, as a bearer of steps; empty when the pack holds no readable mission type of
     * that id. An artifact of the kind that bears no step is a bug of the store, never a refusal.
     */
    private Optional<StepBearer> versionOf(PackIdentifier pack, String missionType) {
        return mergeInputs
                .winners()
                .read(pack, ArtifactKind.MISSION_TYPE, missionType)
                .map(type -> {
                    if (type instanceof StepBearer bearer) {
                        return bearer;
                    }
                    throw new IllegalStateException("pack " + pack.value() + " holds mission_type:" + missionType
                            + " as a " + type.getClass().getName() + ", which declares no step");
                });
    }

    /**
     * The pack that holds the version of an artifact in force across the closure's levels, read from the merge alone:
     * what a rule over the closure, such as the fallback-profile rule (ADR-0068), loads the entity from.
     *
     * @param kind the artifact's kind
     * @param id the artifact's id
     * @return the winner's pack; empty when no pack of the closure holds the artifact
     */
    public Optional<PackIdentifier> winnerPackOf(ArtifactKind kind, String id) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        return urnOf(kind, id).flatMap(merged().closure()::winnerOf).map(MergedClosure.Winner::packId);
    }

    private static Optional<Urn> urnOf(ArtifactKind kind, String id) {
        try {
            return Optional.of(new Urn(NodeKind.of(kind), id));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    /**
     * The effective set as a predicate over URNs: the artifacts of an activatable kind in force. A step is none, and
     * neither is a template, an asset, a guard or a lifecycle, even when a lock puts it in force inside packs: the
     * effective reads never list them (decision {@code 01M702A71YF7WNDTG2AA89K8ZA}).
     */
    private static Predicate<Urn> effective(InForceStates states) {
        Objects.requireNonNull(states, "states");
        return urn ->
                urn.kind().artifactKind().filter(ArtifactKind::activatable).isPresent()
                        && states.stateOf(urn)
                                .filter(InForceStates.ArtifactState::active)
                                .isPresent();
    }

    /**
     * The closure's presets by id: a view over {@link #heldPresets()}, in its order.
     *
     * @return each preset id with the winning copy and its holding pack
     */
    public Map<String, HeldPreset> presets() {
        Map<String, HeldPreset> byId = new LinkedHashMap<>();
        heldPresets.forEach(held -> byId.put(held.preset().id(), held));
        return Collections.unmodifiableMap(byId);
    }

    /**
     * The {@code parent_pack} of a closure pack: the pack its descriptor extends.
     *
     * @param descriptorPackId the pack's descriptor {@code pack_id}
     * @return the parent's descriptor {@code pack_id}; empty for a pack that extends none
     */
    public Optional<String> parentOf(String descriptorPackId) {
        return Optional.ofNullable(parents.get(descriptorPackId));
    }

    /**
     * Whether a pack of the closure holds the artifact.
     *
     * @param kind the artifact's kind
     * @param id the artifact's id
     * @return {@code true} when the closure holds it
     */
    public boolean holds(ArtifactKind kind, String id) {
        return heldIds.getOrDefault(kind, Set.of()).contains(id);
    }

    /**
     * The level of a target along the closure, and the pack that set it ({@link PerPackPolicy#levelIn}).
     *
     * @param urn the target
     * @return the highest level the closure's packs declare, with the setting pack
     */
    public PerPackPolicy.Level lockOf(Urn urn) {
        return policy.levelIn(closurePackIds, urn);
    }

    /**
     * Whether a pack of the closure locks the target.
     *
     * @param urn the target
     * @return {@code true} when its level along the closure is {@link MutabilityLevel#LOCKED}
     */
    public boolean isLocked(Urn urn) {
        return lockOf(urn).level() == MutabilityLevel.LOCKED;
    }

    /**
     * Whether the closure requires the target: a governing preset requires it, or a pack locks it (ADR-0012 D7, locked
     * implies required).
     *
     * @param urn the target
     * @return {@code true} when it is on the floor or locked
     */
    public boolean isRequired(Urn urn) {
        return floor.isRequired(urn) || isLocked(urn);
    }

    private static Map<ArtifactKind, Set<String>> copyOfHeld(Map<ArtifactKind, Set<String>> held) {
        Map<ArtifactKind, Set<String>> copy = new EnumMap<>(ArtifactKind.class);
        held.forEach((kind, ids) -> copy.put(kind, Set.copyOf(ids)));
        return Collections.unmodifiableMap(copy);
    }
}
