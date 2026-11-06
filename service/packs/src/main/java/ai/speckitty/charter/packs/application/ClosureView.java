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
import java.util.Set;

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
 * @param floor the must-have items of the closure's governing presets, with the requiring packs
 * @param policy the mutability policies of the closure's packs, by descriptor {@code pack_id}
 * @param closurePackIds the closure's descriptor {@code pack_id}s, in closure order
 * @param presets each preset id with the preset and its holding pack. When two packs of the closure hold one preset id,
 *     the last in merge order wins, the rule ADR-0053 d6 applies to layer overrides
 * @param fingerprint the verified digest of the inputs every field was read from
 */
public record ClosureView(
        List<OfferingReadService.OfferingEntry> packs,
        Map<ArtifactKind, Set<String>> heldIds,
        Map<Urn, String> holderOf,
        Set<String> missionTypes,
        RequiredFloor floor,
        PerPackPolicy policy,
        List<String> closurePackIds,
        Map<String, HeldPreset> presets,
        String fingerprint) {

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
     * @param presets the presets by id
     * @param fingerprint the verified digest
     */
    public ClosureView {
        packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
        heldIds = copyOfHeld(Objects.requireNonNull(heldIds, "heldIds"));
        holderOf = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(holderOf, "holderOf")));
        missionTypes = Set.copyOf(Objects.requireNonNull(missionTypes, "missionTypes"));
        Objects.requireNonNull(floor, "floor");
        Objects.requireNonNull(policy, "policy");
        closurePackIds = List.copyOf(Objects.requireNonNull(closurePackIds, "closurePackIds"));
        presets = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(presets, "presets")));
        Objects.requireNonNull(fingerprint, "fingerprint");
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
