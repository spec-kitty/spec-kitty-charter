package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.activations.domain.ClosureSnapshot.PackEntry;
import ai.speckitty.charter.activations.domain.ClosureSnapshot.PresetView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A test data builder of {@link ClosureSnapshot}, for the activation tests of every ring: a snapshot holds only
 * what a test names, and every pack is at {@code org} with no dependency unless the test says otherwise.
 */
public final class ClosureSnapshots {

    private final List<PackEntry> packs = new ArrayList<>();
    private final Map<ArtifactKind, Set<String>> held = new EnumMap<>(ArtifactKind.class);
    private final Map<String, List<String>> requiredBy = new LinkedHashMap<>();
    private final Map<String, String> lockedBy = new LinkedHashMap<>();
    private final Map<String, PresetView> presets = new LinkedHashMap<>();
    private String fingerprint = "sha256:fixture";
    private String closureFingerprint;

    private ClosureSnapshots() {}

    /** An empty snapshot to add to. */
    public static ClosureSnapshots snapshot() {
        return new ClosureSnapshots();
    }

    /** A configured pack at {@code org}, its descriptor id derived from its configured id. */
    public ClosureSnapshots pack(String packId, String version) {
        packs.add(new PackEntry(packId, "PID-" + packId, "org", packId + " pack", version, List.of()));
        return this;
    }

    /** A configured pack at {@code org} whose {@code parent_pack} is another pack of the closure, by configured id. */
    public ClosureSnapshots extending(String packId, String version, String parentPackId) {
        packs.add(new PackEntry(
                packId, "PID-" + packId, "org", packId + " pack", version, List.of(), Optional.of(parentPackId)));
        return this;
    }

    /** Artifacts of one kind the closure holds, mission types included. */
    public ClosureSnapshots held(ArtifactKind kind, String... ids) {
        held.computeIfAbsent(kind, k -> new HashSet<>()).addAll(List.of(ids));
        return this;
    }

    /** An artifact some packs require, by descriptor {@code pack_id}. */
    public ClosureSnapshots requiredBy(String urn, String... packIds) {
        requiredBy.put(urn, List.of(packIds));
        return this;
    }

    /** An artifact a pack locks. */
    public ClosureSnapshots lockedBy(String urn, String packId) {
        lockedBy.put(urn, packId);
        return this;
    }

    /** A preset the closure offers. */
    public ClosureSnapshots preset(PresetView preset) {
        presets.put(preset.id(), preset);
        return this;
    }

    /** The verified source fingerprint. */
    public ClosureSnapshots fingerprint(String value) {
        fingerprint = value;
        return this;
    }

    /** The verified digest of the closure's own packs; the source fingerprint when a test sets none. */
    public ClosureSnapshots closureFingerprint(String value) {
        closureFingerprint = value;
        return this;
    }

    /** The snapshot. */
    public ClosureSnapshot build() {
        return new ClosureSnapshot(
                packs,
                held,
                requiredBy,
                lockedBy,
                presets,
                fingerprint,
                closureFingerprint == null ? fingerprint : closureFingerprint,
                Set.of());
    }
}
