package ai.speckitty.charter.activations.infra.packs;

import ai.speckitty.charter.activations.application.AvailablePacks;
import ai.speckitty.charter.activations.application.PacksSession;
import ai.speckitty.charter.activations.domain.ActionContext;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.EffectiveArtifactRead;
import ai.speckitty.charter.activations.domain.GraphSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ArtifactReadService;
import ai.speckitty.charter.packs.application.ClosureQueries;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.OfferingReadService.OfferingEntry;
import ai.speckitty.charter.packs.application.UnknownRootException;
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
 * <p>Each session is one verified read, {@link ClosureQueries#snapshot} (NFR-007), kept for the request: its walks
 * ({@link ClosureViewSession}) answer from the same view. The view is copied field by field into a
 * {@link ClosureSnapshot}: the packs with their resolved dependencies, the held ids, the mission types, the required
 * floor, the locks (only {@code LOCKED} levels) and the presets. The floor and the locks name each pack by the id it is
 * configured under, as a {@code 409}'s {@code requiredBy} does; a refusal of the compose propagates unchanged.
 *
 * <p>A root no pack is configured as is answered by {@link ClosureQueries#snapshot} with the typed
 * {@link UnknownRootException}, naming every such root (research R4), and only that exception is caught: the answer is
 * an {@linkplain ClosureSnapshot#unresolved unresolved} snapshot, which a write refuses as
 * {@code 422 activation_invalid} and the closure read as {@code 500 pack_unreadable}. Any other exception of the
 * compose, an {@link IllegalArgumentException} included, propagates unchanged.
 *
 * <p>Each pack's {@code parent_pack} is copied as the configured id of its parent ({@link ClosureView#parentOf}), so
 * the activation domain can name a refusal's extends chain ({@link ClosureSnapshot#extendsChain}).
 *
 * <p>The same read answers {@link PacksSession#merged} and {@link PacksSession#inForce} ({@link ClosureViewSession}): the
 * layer merge is built from the facts the read holds, on the first question and not before, and never reads again
 * (NFR-007).
 */
@Component
public class PacksAvailability implements AvailablePacks {

    /** The verified closure read, as this adapter calls it: {@link ClosureQueries#snapshot}. */
    @FunctionalInterface
    interface Closures {
        ClosureView snapshot(Optional<List<PackIdentifier>> roots);
    }

    private final Closures closures;

    /**
     * The adapter over the packs slice.
     *
     * @param closures the verified closure read
     */
    @Autowired
    public PacksAvailability(ClosureQueries closures) {
        this(Objects.requireNonNull(closures, "closures")::snapshot);
    }

    /**
     * The adapter over any closure read, so a test can answer with a fixed view or a refusal.
     *
     * @param closures the closure read
     */
    PacksAvailability(Closures closures) {
        this.closures = Objects.requireNonNull(closures, "closures");
    }

    @Override
    public List<ArtifactKind> listedKinds() {
        return ArtifactReadService.listed();
    }

    @Override
    public PacksSession session(Optional<List<String>> packIds) {
        Optional<List<PackIdentifier>> roots =
                packIds.map(ids -> ids.stream().map(PackIdentifier::new).toList());
        try {
            ClosureView view = closures.snapshot(roots);
            return new ClosureViewSession(view, snapshotOf(view));
        } catch (UnknownRootException unknownRoot) {
            return new Unresolved(ClosureSnapshot.unresolved(new LinkedHashSet<>(unknownRoot.unknownIds())));
        }
    }

    /** The session of roots of which some name no configured pack: no closure, so every walk reaches nothing. */
    private record Unresolved(ClosureSnapshot snapshot) implements PacksSession {

        @Override
        public Reach reach(ReachPlan plan) {
            return Reach.empty();
        }

        @Override
        public MergedSnapshot merged() {
            return MergedSnapshot.empty();
        }

        @Override
        public InForceAnswer inForce(InForceQuery query) {
            return InForceAnswer.empty();
        }

        @Override
        public GraphSnapshot graph(InForceQuery query) {
            return GraphSnapshot.empty();
        }

        @Override
        public ActionContext actionContext(String missionType, String action, int depth, InForceQuery query) {
            return ActionContext.notInForce();
        }

        @Override
        public Optional<EffectiveArtifactRead> effectiveArtifact(
                ArtifactKind kind, String id, int depth, InForceQuery query) {
            return Optional.empty();
        }

        @Override
        public void requireAction(String missionType, String action) {
            // a session that holds no closure refuses before any name is judged
        }
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
                view.packs().stream()
                        .map(entry -> entryOf(entry, view, configuredIdOf))
                        .toList(),
                view.heldIds(),
                requiredBy(view, configuredIdOf),
                lockedBy(view, configuredIdOf),
                presets(view),
                view.fingerprint(),
                view.closureFingerprint(),
                Set.of());
    }

    private static ClosureSnapshot.PackEntry entryOf(
            OfferingEntry entry, ClosureView view, Map<String, String> configuredIdOf) {
        return new ClosureSnapshot.PackEntry(
                entry.id().value(),
                entry.descriptorPackId(),
                entry.level().token(),
                entry.name(),
                entry.version(),
                entry.dependencies().stream()
                        .map(edge -> new ClosureSnapshot.Dependency(edge.to(), edge.range(), edge.version()))
                        .toList(),
                view.parentOf(entry.descriptorPackId()).map(parent -> configuredIdOf.getOrDefault(parent, parent)));
    }

    /** Each closure pack's descriptor {@code pack_id} with the id it is configured under, the first in closure order. */
    private static Map<String, String> configuredIds(List<OfferingEntry> packs) {
        Map<String, String> ids = new HashMap<>();
        packs.forEach(pack -> ids.putIfAbsent(pack.descriptorPackId(), pack.id().value()));
        return ids;
    }

    private static Map<String, List<String>> requiredBy(ClosureView view, Map<String, String> configuredIdOf) {
        Map<String, List<String>> requiredBy = new LinkedHashMap<>();
        view.mergedFloor()
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
        view.presets().forEach((id, held) -> presets.put(id, presetOf(held.preset())));
        return presets;
    }

    private static ClosureSnapshot.PresetView presetOf(Preset preset) {
        Map<ArtifactKind, Set<String>> members = new EnumMap<>(ArtifactKind.class);
        preset.artifacts().forEach((kind, items) -> members.put(kind, ids(items)));
        return new ClosureSnapshot.PresetView(preset.id(), members);
    }

    private static Set<String> ids(List<PresetItem> items) {
        return items.stream().map(PresetItem::id).collect(Collectors.toUnmodifiableSet());
    }
}
