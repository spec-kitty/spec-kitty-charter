package ai.speckitty.charter.packs.fixtures;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.ClosureView.HeldPreset;
import ai.speckitty.charter.packs.application.ClosureView.MergeInputs;
import ai.speckitty.charter.packs.application.EdgeIndex;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.RequiredFloor;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Closure views a test builds with fewer parts than the composition root does: no typed edge, no merge input, or the
 * closure digest taken as the whole source digest. The service builds every part (#659: these overloads moved out of
 * {@link ClosureView}, which only tests called).
 */
public final class ClosureViews {

    private ClosureViews() {}

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
     * @return the view
     */
    public static ClosureView of(
            List<OfferingReadService.OfferingEntry> packs,
            Map<ArtifactKind, Set<String>> heldIds,
            Map<Urn, PackUlid> holderOf,
            RequiredFloor floor,
            PerPackPolicy policy,
            List<PackUlid> closurePackIds,
            List<HeldPreset> heldPresets,
            Map<PackUlid, PackUlid> parents,
            String fingerprint) {
        return of(
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
     * A view whose closure digest is the whole source digest.
     *
     * @param packs the closure's packs
     * @param heldIds the held ids per kind
     * @param holderOf the holder of each held URN
     * @param floor the required floor
     * @param policy the per-pack policies
     * @param closurePackIds the closure's {@code pack_id}s
     * @param heldPresets the presets, each id once
     * @param parents the parent of each pack that has one
     * @param fingerprint the verified digest, also taken as the closure's own
     * @param edges the closure's typed edges
     * @param mergeInputs what the layer merge is built from
     * @return the view
     */
    public static ClosureView of(
            List<OfferingReadService.OfferingEntry> packs,
            Map<ArtifactKind, Set<String>> heldIds,
            Map<Urn, PackUlid> holderOf,
            RequiredFloor floor,
            PerPackPolicy policy,
            List<PackUlid> closurePackIds,
            List<HeldPreset> heldPresets,
            Map<PackUlid, PackUlid> parents,
            String fingerprint,
            EdgeIndex edges,
            MergeInputs mergeInputs) {
        return new ClosureView(
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
     * A view with every part.
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
     * @return the view
     */
    public static ClosureView of(
            List<OfferingReadService.OfferingEntry> packs,
            Map<ArtifactKind, Set<String>> heldIds,
            Map<Urn, PackUlid> holderOf,
            RequiredFloor floor,
            PerPackPolicy policy,
            List<PackUlid> closurePackIds,
            List<HeldPreset> heldPresets,
            Map<PackUlid, PackUlid> parents,
            String fingerprint,
            EdgeIndex edges,
            MergeInputs mergeInputs,
            String closureFingerprint) {
        return new ClosureView(
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
                closureFingerprint);
    }
}
