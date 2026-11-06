package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.RequiredFloor;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What one verified read of a composition closure answers (NFR-007, FR-009, FR-023): a read-only value an adapter maps
 * into its own snapshot, so no caller beyond it holds packs types. Every field comes from the same
 * {@link ConsistentRead}, so they all name the inputs {@link #fingerprint()} digests.
 *
 * @param packs the closure's packs in the offering's shape, in dependency order, each with its resolved edges
 * @param heldIds the ids each pack of the closure holds, per artifact kind
 * @param holderOf each held URN with the descriptor {@code pack_id} of the first pack, in the closure's order, that holds
 *     it: the pack a bare reference from outside the holders resolves to
 * @param missionTypes the mission types the closure holds
 * @param floor the must-have items of the closure's governing presets, with the requiring packs. It is built from
 *     {@link #heldPresets()}, so it follows the copy of a preset that wins (ADR-0053 d6): the must-have items of a
 *     copy that loses require nothing
 * @param policy the mutability policies of the closure's packs, by descriptor {@code pack_id}
 * @param closurePackIds the closure's descriptor {@code pack_id}s, in closure order
 * @param heldPresets the closure's presets, each id once with its holding pack. When two packs of the closure hold one
 *     preset id, the last in merge order wins, the rule ADR-0053 d6 applies to layer overrides; {@link #presets()} is
 *     a view by id over this list
 * @param parents each closure pack's {@code parent_pack}, by descriptor {@code pack_id}; a pack without one is absent
 * @param fingerprint the verified digest of the inputs every field was read from
 * @param edges the typed edges of the closure's packs, indexed from the facts the same read holds (WP11); the reach and
 *     the {@code enhances} lookups answer from it with no further read
 */
public record ClosureView(
        List<OfferingReadService.OfferingEntry> packs,
        Map<ArtifactKind, Set<String>> heldIds,
        Map<Urn, String> holderOf,
        Set<String> missionTypes,
        RequiredFloor floor,
        PerPackPolicy policy,
        List<String> closurePackIds,
        List<HeldPreset> heldPresets,
        Map<String, String> parents,
        String fingerprint,
        EdgeIndex edges) {

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
     * @param missionTypes the held mission types
     * @param floor the required floor
     * @param policy the per-pack policies
     * @param closurePackIds the closure's {@code pack_id}s
     * @param heldPresets the presets, each id once
     * @param parents the parent of each pack that has one
     * @param fingerprint the verified digest
     * @param edges the closure's typed edges
     */
    public ClosureView {
        packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
        heldIds = copyOfHeld(Objects.requireNonNull(heldIds, "heldIds"));
        holderOf = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(holderOf, "holderOf")));
        missionTypes = Set.copyOf(Objects.requireNonNull(missionTypes, "missionTypes"));
        Objects.requireNonNull(floor, "floor");
        Objects.requireNonNull(policy, "policy");
        closurePackIds = List.copyOf(Objects.requireNonNull(closurePackIds, "closurePackIds"));
        heldPresets = List.copyOf(Objects.requireNonNull(heldPresets, "heldPresets"));
        parents = Map.copyOf(Objects.requireNonNull(parents, "parents"));
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(edges, "edges");
    }

    /**
     * A view whose closure writes no typed edge.
     *
     * @param packs the closure's packs
     * @param heldIds the held ids per kind
     * @param holderOf the holder of each held URN
     * @param missionTypes the held mission types
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
            Set<String> missionTypes,
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
                missionTypes,
                floor,
                policy,
                closurePackIds,
                heldPresets,
                parents,
                fingerprint,
                EdgeIndex.empty());
    }

    /**
     * The forward reach over the closure's edges ({@link Reachability#reach}).
     *
     * @param request what to walk
     * @return what the walk reached, stopped at, left behind a stop and passed through
     */
    public ReachResult reach(ReachRequest request) {
        return Reachability.reach(edges, request);
    }

    /**
     * What the {@code enhances} pull reaches from the artifacts, both ways and transitively
     * ({@link Reachability#enhancesPull}).
     *
     * @param starts the artifacts the pull starts from
     * @return each pulled artifact with every way it was reached
     */
    public Map<Urn, List<ReachResult.Pulled>> enhancesPull(Set<Urn> starts) {
        return Reachability.enhancesPull(edges, starts);
    }

    /**
     * The {@code enhances} pull bounded by the caller's policy ({@link Reachability#enhancesPull}): a node it does not
     * admit is neither pulled nor walked through.
     *
     * @param starts the artifacts the pull starts from
     * @param admits the nodes the pull may reach
     * @return each pulled artifact with every way it was reached
     */
    public Map<Urn, List<ReachResult.Pulled>> enhancesPull(Set<Urn> starts, Predicate<Urn> admits) {
        return Reachability.enhancesPull(edges, starts, admits);
    }

    /**
     * The artifacts that enhance each target, one hop ({@link Reachability#enhancersOf}).
     *
     * @param targets the targets
     * @return each target that has an enhancer, with its enhancers' places
     */
    public Map<Urn, List<EdgeIndex.Place>> enhancersOf(Set<Urn> targets) {
        return Reachability.enhancersOf(edges, targets);
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
