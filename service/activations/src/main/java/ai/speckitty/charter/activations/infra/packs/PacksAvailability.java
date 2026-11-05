package ai.speckitty.charter.activations.infra.packs;

import ai.speckitty.charter.activations.application.AvailablePacks;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ClosureQueries;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.OfferingReadService.OfferingEntry;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The packs slice, as the activations slice asks it: the one implementation of {@link AvailablePacks}. It is the only
 * class of the activations slice that may name {@code packs}, and it names only {@code packs.application} (and the
 * {@code packs.domain} values its answers expose), never {@code packs.infra}; {@code HexagonalArchitectureTest} pins
 * both.
 *
 * <p>Each call is one verified read, {@link ClosureQueries#snapshot} (NFR-007), copied field by field into a
 * {@link ClosureSnapshot}: the packs with their resolved dependencies, the held ids, the mission types, the required
 * floor, the locks (only {@code LOCKED} levels) and the presets. The floor and the locks name each pack by the id it is
 * configured under, as a {@code 409}'s {@code requiredBy} does; a refusal of the compose propagates unchanged.
 *
 * <p>A root no pack is configured as is the one case the compose answers with an {@link IllegalArgumentException}
 * (research R4). Only then is the catalog listed, to name every such root: the answer is an
 * {@linkplain ClosureSnapshot#unresolved unresolved} snapshot, which a write refuses as {@code 422 activation_invalid}
 * and the closure read as {@code 500 pack_unreadable}. A root the catalog does configure after all was added between
 * the two looks, so the read is refused as {@code stale_input}.
 */
@Component
public class PacksAvailability implements AvailablePacks {

    private final ClosureQueries closures;
    private final PackCatalogRepository catalog;

    /**
     * The adapter over the packs slice.
     *
     * @param closures the verified closure read
     * @param catalog the configured packs, listed only to name a root no pack is configured as
     */
    @Autowired
    public PacksAvailability(ClosureQueries closures, PackCatalogRepository catalog) {
        this.closures = Objects.requireNonNull(closures, "closures");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public ClosureSnapshot snapshot(Optional<List<String>> packIds) {
        Optional<List<PackIdentifier>> roots =
                packIds.map(ids -> ids.stream().map(PackIdentifier::new).toList());
        try {
            return snapshotOf(closures.snapshot(roots));
        } catch (IllegalArgumentException unknownRoot) {
            return unresolved(packIds.orElseGet(List::of), unknownRoot);
        }
    }

    private ClosureSnapshot unresolved(List<String> packIds, IllegalArgumentException unknownRoot) {
        Set<String> configured = catalog.configured().stream()
                .map(ConfiguredPack::id)
                .map(PackIdentifier::value)
                .collect(Collectors.toSet());
        Set<String> unknown = packIds.stream()
                .filter(id -> !configured.contains(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unknown.isEmpty()) {
            throw new CharterRefusal(
                    RefusalCode.STALE_INPUT,
                    "The packs changed while the closure was read; read it again.",
                    unknownRoot);
        }
        return ClosureSnapshot.unresolved(unknown);
    }

    /**
     * Copies a closure view into the activation domain's snapshot.
     *
     * @param view the verified view
     * @return the snapshot
     */
    static ClosureSnapshot snapshotOf(ClosureView view) {
        Map<String, String> configuredIdOf = configuredIds(view.packs());
        return new ClosureSnapshot(
                view.packs().stream().map(PacksAvailability::entryOf).toList(),
                view.heldIds(),
                view.missionTypes(),
                requiredBy(view, configuredIdOf),
                lockedBy(view, configuredIdOf),
                presets(view),
                view.fingerprint());
    }

    private static ClosureSnapshot.PackEntry entryOf(OfferingEntry entry) {
        return new ClosureSnapshot.PackEntry(
                entry.id().value(),
                entry.descriptorPackId(),
                entry.level().token(),
                entry.name(),
                entry.version(),
                entry.dependencies().stream()
                        .map(edge -> new ClosureSnapshot.Dependency(edge.to(), edge.range(), edge.version()))
                        .toList());
    }

    /** Each closure pack's descriptor {@code pack_id} with the id it is configured under, the first in closure order. */
    private static Map<String, String> configuredIds(List<OfferingEntry> packs) {
        Map<String, String> ids = new HashMap<>();
        packs.forEach(pack -> ids.putIfAbsent(pack.descriptorPackId(), pack.id().value()));
        return ids;
    }

    private static Map<String, List<String>> requiredBy(ClosureView view, Map<String, String> configuredIdOf) {
        Map<String, List<String>> requiredBy = new LinkedHashMap<>();
        view.floor()
                .requiredBy()
                .forEach((urn, packs) -> requiredBy.put(
                        urn.toString(),
                        packs.stream()
                                .map(pack -> configuredIdOf.getOrDefault(pack, pack))
                                .distinct()
                                .toList()));
        return requiredBy;
    }

    /** The locks of the held URNs: a lock on an id the closure does not hold changes no write. */
    private static Map<String, String> lockedBy(ClosureView view, Map<String, String> configuredIdOf) {
        Map<String, String> lockedBy = new LinkedHashMap<>();
        for (Urn urn : view.holderOf().keySet()) {
            if (view.isLocked(urn)) {
                view.lockOf(urn)
                        .setBy()
                        .ifPresent(pack -> lockedBy.put(urn.toString(), configuredIdOf.getOrDefault(pack, pack)));
            }
        }
        return lockedBy;
    }

    private static Map<String, ClosureSnapshot.PresetView> presets(ClosureView view) {
        Map<String, ClosureSnapshot.PresetView> presets = new LinkedHashMap<>();
        view.presets().forEach((id, held) -> presets.put(id, presetOf(held.preset(), held.descriptorPackId())));
        return presets;
    }

    private static ClosureSnapshot.PresetView presetOf(Preset preset, String holdingPack) {
        Map<ArtifactKind, Set<String>> members = new EnumMap<>(ArtifactKind.class);
        preset.artifacts().forEach((kind, items) -> members.put(kind, ids(items)));
        Map<ArtifactKind, Set<String>> mustHave = new EnumMap<>(ArtifactKind.class);
        preset.required().forEach((kind, ids) -> mustHave.put(kind, Set.copyOf(ids)));
        return new ClosureSnapshot.PresetView(
                preset.id(),
                holdingPack,
                Set.copyOf(preset.activatedKinds()),
                members,
                mustHave,
                ids(preset.missionTypes()),
                Set.copyOf(preset.requiredMissionTypes()));
    }

    private static Set<String> ids(List<PresetItem> items) {
        return items.stream().map(PresetItem::id).collect(Collectors.toUnmodifiableSet());
    }
}
