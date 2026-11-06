package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What one verified read of an activation's pack closure holds (NFR-007): everything the document rules and
 * the cascade ask about pack content, as one value. The application receives it from its {@code AvailablePacks} port;
 * the adapter copies it from the packs slice's closure view, so no packs type crosses into this record. Every
 * collection is a defensive, unmodifiable copy.
 *
 * @param packs the closure's packs, in dependency order
 * @param held the declared ids the closure holds, per kind
 * @param missionTypes the mission type ids the closure holds
 * @param requiredBy the required floor: URN text (for example {@code directive:D1}) to the configured ids of the packs
 *     that require it, as a {@code 409}'s {@code requiredBy} names them
 * @param lockedBy URN text to the configured id of the pack that locks it
 * @param presets the presets the closure offers, by id
 * @param fingerprint the verified source fingerprint of the read
 * @param unknownPacks the pack ids the request named that no pack is configured as (research R4). When it is not
 *     empty the closure could not be composed, so the snapshot holds nothing else: {@link #resolved()} is
 *     {@code false}, a write is refused naming these ids, and the closure read is refused as unreadable
 */
public record ClosureSnapshot(
        List<PackEntry> packs,
        Map<ArtifactKind, Set<String>> held,
        Set<String> missionTypes,
        Map<String, List<String>> requiredBy,
        Map<String, String> lockedBy,
        Map<String, PresetView> presets,
        String fingerprint,
        Set<String> unknownPacks) {

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
     * @param unknownPacks the ids no pack is configured as
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
        unknownPacks = Set.copyOf(unknownPacks);
    }

    /**
     * A composed closure: every pack the request named is configured.
     *
     * @param packs the packs
     * @param held the held ids per kind
     * @param missionTypes the mission types
     * @param requiredBy the required floor
     * @param lockedBy the locks
     * @param presets the presets
     * @param fingerprint the fingerprint
     */
    public ClosureSnapshot(
            List<PackEntry> packs,
            Map<ArtifactKind, Set<String>> held,
            Set<String> missionTypes,
            Map<String, List<String>> requiredBy,
            Map<String, String> lockedBy,
            Map<String, PresetView> presets,
            String fingerprint) {
        this(packs, held, missionTypes, requiredBy, lockedBy, presets, fingerprint, Set.of());
    }

    /**
     * The answer for roots of which some name no configured pack: no closure, only the unknown ids.
     *
     * @param unknownPacks the ids no pack is configured as, not empty
     * @return the unresolved snapshot
     * @throws IllegalArgumentException when no id is given
     */
    public static ClosureSnapshot unresolved(Set<String> unknownPacks) {
        if (unknownPacks.isEmpty()) {
            throw new IllegalArgumentException("An unresolved closure names at least one unknown pack.");
        }
        return new ClosureSnapshot(List.of(), Map.of(), Set.of(), Map.of(), Map.of(), Map.of(), "", unknownPacks);
    }

    /**
     * Whether the closure was composed: every pack the request named is configured.
     *
     * @return {@code false} when {@link #unknownPacks()} names an id
     */
    public boolean resolved() {
        return unknownPacks.isEmpty();
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
     * @return the configured ids of the requiring packs; empty when nothing requires it
     */
    public List<String> requiringPacks(String urn) {
        return requiredBy.getOrDefault(urn, List.of());
    }

    /**
     * The pack that locks an artifact.
     *
     * @param urn the URN text, for example {@code directive:D1}
     * @return the configured id of the locking pack; empty when nothing locks it
     */
    public Optional<String> lockingPack(String urn) {
        return Optional.ofNullable(lockedBy.get(urn));
    }

    /**
     * The extends chain from a pack the activation names up its {@code parent_pack} line to a requiring pack, as a
     * {@code 409}'s {@code RequiringPack.via} names it: the named pack first, then each parent up to, and without, the
     * requiring pack (the contract's example: an activation of {@code team}, which extends {@code internal}, has
     * {@code via: [team]} for {@code internal}). The walk needs no bound: the compose refuses a {@code parent_pack}
     * cycle, a pack that names itself included, as {@code pack_dependency_cycle}, so a snapshot's line always ends.
     *
     * @param rootPackId the configured id of the pack the activation names
     * @param requiringPackId the configured id of the requiring or locking pack
     * @return the chain; empty when the root is the requiring pack itself (it declares the item), and absent when the
     *     requiring pack is not on the root's {@code parent_pack} line (it is reached over {@code depends_on}, or not
     *     at all)
     */
    public Optional<List<String>> extendsChain(String rootPackId, String requiringPackId) {
        List<String> chain = new ArrayList<>();
        Optional<String> at = Optional.of(rootPackId);
        while (at.isPresent()) {
            String current = at.orElseThrow();
            if (current.equals(requiringPackId)) {
                return Optional.of(List.copyOf(chain));
            }
            chain.add(current);
            at = pack(current).flatMap(PackEntry::parentPackId);
        }
        return Optional.empty();
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
     * @param parentPackId the configured id of the closure pack its {@code parent_pack} names; empty when it extends
     *     none
     */
    public record PackEntry(
            String packId,
            String descriptorPackId,
            String level,
            String name,
            String version,
            List<Dependency> dependsOn,
            Optional<String> parentPackId) {

        /**
         * Copies the dependencies.
         *
         * @param packId the configured id
         * @param descriptorPackId the declared {@code pack_id}
         * @param level the level token
         * @param name the name
         * @param version the version
         * @param dependsOn the dependencies
         * @param parentPackId the configured id of its parent
         */
        public PackEntry {
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(descriptorPackId, "descriptorPackId");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(version, "version");
            dependsOn = List.copyOf(dependsOn);
            Objects.requireNonNull(parentPackId, "parentPackId");
        }

        /**
         * A pack that extends none.
         *
         * @param packId the configured id
         * @param descriptorPackId the declared {@code pack_id}
         * @param level the level token
         * @param name the name
         * @param version the version
         * @param dependsOn the dependencies
         */
        public PackEntry(
                String packId,
                String descriptorPackId,
                String level,
                String name,
                String version,
                List<Dependency> dependsOn) {
            this(packId, descriptorPackId, level, name, version, dependsOn, Optional.empty());
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
     * A preset the closure offers (ADR-0031, ADR-0042), as the preset write needs it. Its must-have items are not
     * here: the closure's {@code requiredBy} already holds the floor every governing preset sets.
     *
     * @param id the preset's id
     * @param activatedKinds the kinds the preset activates; empty when it gates none
     * @param members the declared ids it selects, per kind, must-have items included
     * @param missionTypes the mission types it selects, must-have ones included
     */
    public record PresetView(
            String id,
            Set<ArtifactKind> activatedKinds,
            Map<ArtifactKind, Set<String>> members,
            Set<String> missionTypes) {

        /**
         * Copies every collection.
         *
         * @param id the id
         * @param activatedKinds the kind gate
         * @param members the members per kind
         * @param missionTypes the mission types
         */
        public PresetView {
            Objects.requireNonNull(id, "id");
            activatedKinds = Set.copyOf(activatedKinds);
            members = copyOfSets(members);
            missionTypes = Set.copyOf(missionTypes);
        }
    }
}
