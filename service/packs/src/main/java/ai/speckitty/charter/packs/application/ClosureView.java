package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.InForceRequest;
import ai.speckitty.charter.packs.domain.InForceResolution;
import ai.speckitty.charter.packs.domain.InForceStates;
import ai.speckitty.charter.packs.domain.LayerMerge;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.RequiredFloor;
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
        private @Nullable MergedView built;

        /**
         * The inputs of a closure's merge.
         *
         * @param packs the composed closure, in closure order
         * @param facts the facts each pack was read with, by configured identifier
         */
        public MergeInputs(List<PackUnderResolution> packs, Map<String, PackCompositionFacts> facts) {
            this.packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
            this.facts = Map.copyOf(Objects.requireNonNull(facts, "facts"));
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

        synchronized MergedView merged(PerPackPolicy policy, EdgeIndex edges) {
            if (built == null) {
                MergedClosure merged = LayerMerge.merge(packs, facts, policy);
                built = new MergedView(merged, edges.merged(merged), bindingsOf(merged));
            }
            return built;
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
     * The action context over the merged closure (FR-015, FR-019, rule E16): ADR-0018's five-step walk from the held step
     * {@code action:<missionType>/<action>} over the artifacts in force, with their enhancers, {@code depth} counting
     * {@code suggests} hops.
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
        return new ActionContextWalk(merged().edges()).walk(missionType, action, depth, effective(states));
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
