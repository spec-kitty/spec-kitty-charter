package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What one verified read of an activation's pack closure holds (squad A1, NFR-007): everything the document rules and
 * the cascade ask about pack content, as one value. The application receives it from its {@code AvailablePacks} port;
 * the adapter copies it from the packs slice's closure view, so no packs type crosses into this record. Every
 * collection is a defensive, unmodifiable copy.
 *
 * @param packs the closure's packs, in dependency order
 * @param held the declared ids the closure holds, per kind
 * @param missionTypes the mission type ids the closure holds
 * @param requiredBy the required floor: URN text (for example {@code directive:D1}) to the descriptor {@code pack_id}s
 *     of the packs that require it
 * @param lockedBy URN text to the descriptor {@code pack_id} of the pack that locks it
 * @param presets the presets the closure offers, by id
 * @param fingerprint the verified source fingerprint of the read
 */
public record ClosureSnapshot(
        List<PackEntry> packs,
        Map<ArtifactKind, Set<String>> held,
        Set<String> missionTypes,
        Map<String, List<String>> requiredBy,
        Map<String, String> lockedBy,
        Map<String, PresetView> presets,
        String fingerprint) {

    /**
     * Copies every collection.
     *
     * @param packs the packs
     * @param held the held ids per kind
     * @param missionTypes the mission types
     * @param requiredBy the required floor
     * @param lockedBy the locks
     * @param presets the presets
     * @param fingerprint the fingerprint
     */
    public ClosureSnapshot {
        packs = List.copyOf(packs);
        held = copyOfSets(held);
        missionTypes = Set.copyOf(missionTypes);
        requiredBy = requiredBy.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
        lockedBy = Map.copyOf(lockedBy);
        presets = Map.copyOf(presets);
        Objects.requireNonNull(fingerprint, "fingerprint");
    }

    /**
     * A configured pack of the closure, by the id it is configured under.
     *
     * @param packId the configured id
     * @return the pack; empty when the closure has none of that id
     */
    public Optional<PackEntry> pack(String packId) {
        return packs.stream().filter(p -> p.packId().equals(packId)).findFirst();
    }

    /**
     * The declared ids of one kind the closure holds.
     *
     * @param kind the kind
     * @return the ids; empty when it holds none
     */
    public Set<String> held(ArtifactKind kind) {
        return held.getOrDefault(kind, Set.of());
    }

    /**
     * The packs that require an artifact.
     *
     * @param urn the URN text, for example {@code directive:D1}
     * @return the requiring descriptor {@code pack_id}s; empty when nothing requires it
     */
    public List<String> requiringPacks(String urn) {
        return requiredBy.getOrDefault(urn, List.of());
    }

    /**
     * The pack that locks an artifact.
     *
     * @param urn the URN text, for example {@code directive:D1}
     * @return the locking descriptor {@code pack_id}; empty when nothing locks it
     */
    public Optional<String> lockingPack(String urn) {
        return Optional.ofNullable(lockedBy.get(urn));
    }

    private static <K> Map<K, Set<String>> copyOfSets(Map<K, ? extends Set<String>> sets) {
        return sets.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
    }

    /**
     * One pack of the closure, in the offering's pack shape (the closure read maps it to {@code OfferingPack}).
     *
     * @param packId the id the pack is configured under
     * @param descriptorPackId the {@code pack_id} its {@code pack.yaml} declares
     * @param level the level token it is served at, for example {@code org}
     * @param name its human-readable name
     * @param version its {@code pack_version}
     * @param dependsOn the dependencies the compose resolved for it
     */
    public record PackEntry(
            String packId,
            String descriptorPackId,
            String level,
            String name,
            String version,
            List<Dependency> dependsOn) {

        /**
         * Copies the dependencies.
         *
         * @param packId the configured id
         * @param descriptorPackId the declared {@code pack_id}
         * @param level the level token
         * @param name the name
         * @param version the version
         * @param dependsOn the dependencies
         */
        public PackEntry {
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(descriptorPackId, "descriptorPackId");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(version, "version");
            dependsOn = List.copyOf(dependsOn);
        }
    }

    /**
     * One dependency the compose resolved (the contract's {@code ResolvedDependency}).
     *
     * @param descriptorPackId the depended-on pack's {@code pack_id}
     * @param range the PEP 440 range the depending descriptor declared
     * @param version the held version that satisfied it
     */
    public record Dependency(String descriptorPackId, String range, String version) {

        /**
         * Checks the fields.
         *
         * @param descriptorPackId the depended-on pack
         * @param range the declared range
         * @param version the held version
         */
        public Dependency {
            Objects.requireNonNull(descriptorPackId, "descriptorPackId");
            Objects.requireNonNull(range, "range");
            Objects.requireNonNull(version, "version");
        }
    }

    /**
     * A preset the closure offers (ADR-0031, ADR-0042), as the preset write needs it.
     *
     * @param id the preset's id
     * @param holdingPack the descriptor {@code pack_id} of the pack that holds it
     * @param activatedKinds the kinds the preset activates; empty when it gates none
     * @param members the declared ids it selects, per kind, must-have items included
     * @param mustHave the must-have ids per kind: its share of the required floor
     * @param missionTypes the mission types it selects, must-have ones included
     * @param mustHaveMissionTypes the must-have mission types
     */
    public record PresetView(
            String id,
            String holdingPack,
            Set<ArtifactKind> activatedKinds,
            Map<ArtifactKind, Set<String>> members,
            Map<ArtifactKind, Set<String>> mustHave,
            Set<String> missionTypes,
            Set<String> mustHaveMissionTypes) {

        /**
         * Copies every collection.
         *
         * @param id the id
         * @param holdingPack the holding pack
         * @param activatedKinds the kind gate
         * @param members the members per kind
         * @param mustHave the must-have members per kind
         * @param missionTypes the mission types
         * @param mustHaveMissionTypes the must-have mission types
         */
        public PresetView {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(holdingPack, "holdingPack");
            activatedKinds = Set.copyOf(activatedKinds);
            members = copyOfSets(members);
            mustHave = copyOfSets(mustHave);
            missionTypes = Set.copyOf(missionTypes);
            mustHaveMissionTypes = Set.copyOf(mustHaveMissionTypes);
        }

        /**
         * Whether the preset binds: it has a must-have item (ADR-0031).
         *
         * @return {@code true} for a governing preset, {@code false} for a convenience one
         */
        public boolean governing() {
            return !mustHaveMissionTypes.isEmpty() || mustHave.values().stream().anyMatch(s -> !s.isEmpty());
        }
    }
}
