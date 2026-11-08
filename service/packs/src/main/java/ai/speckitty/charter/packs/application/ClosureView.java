package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
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
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
        MergeInputs mergeInputs) {

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

        synchronized MergedView merged(PerPackPolicy policy, EdgeIndex edges) {
            if (built == null) {
                MergedClosure merged = LayerMerge.merge(packs, facts, policy);
                built = new MergedView(merged, edges.merged(merged));
            }
            return built;
        }
    }

    /**
     * The closure as the layer merge sees it: the version of each artifact in force, and the typed edges re-homed
     * through the winners (FR-009, FR-010).
     *
     * @param closure the winners, the layer overrides, the languages and the merged presets
     * @param edges the closure's edges with no edge from a losing place and every edge to one ending at the winner
     */
    public record MergedView(MergedClosure closure, EdgeIndex edges) {

        /**
         * Checks the fields.
         *
         * @param closure the layer merge
         * @param edges the merged edges
         */
        public MergedView {
            Objects.requireNonNull(closure, "closure");
            Objects.requireNonNull(edges, "edges");
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
         * (FR-013 to FR-016): from the merged index, so an edge a losing version declared is not among them.
         *
         * @return each overrider and enhancer with the winning artifacts it names
         */
        InForceResolution.Edges resolutionEdges() {
            Map<Urn, Set<Urn>> overrides = new LinkedHashMap<>();
            Map<Urn, Set<Urn>> enhances = new LinkedHashMap<>();
            closure.winners()
                    .forEach((urn, winner) -> edges.inPack(new PackIdentifier(winner.descriptorPackId()))
                            .resolve(urn)
                            .ifPresent(node -> node.derivedRelationships()
                                    .forEach(edge -> record(urn, edge, overrides, enhances))));
            return new InForceResolution.Edges(overrides, enhances, Map.of());
        }

        /** Files an {@code overrides} or {@code enhances} edge to a winner under its source; any other edge is not read. */
        private void record(Urn source, Relationship edge, Map<Urn, Set<Urn>> overrides, Map<Urn, Set<Urn>> enhances) {
            if (!closure.winners().containsKey(edge.to())) {
                return;
            }
            if (edge.type() == RelationType.OVERRIDES) {
                overrides
                        .computeIfAbsent(source, ignored -> new LinkedHashSet<>())
                        .add(edge.to());
            } else if (edge.type() == RelationType.ENHANCES) {
                enhances.computeIfAbsent(source, ignored -> new LinkedHashSet<>())
                        .add(edge.to());
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
