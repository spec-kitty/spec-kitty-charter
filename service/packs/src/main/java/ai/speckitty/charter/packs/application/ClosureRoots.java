package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Which closures a registration joins (IC-15, plan delta squad finding 7): the closure of every pack whose composition
 * closure holds the written pack, found from a reverse {@code depends_on}/{@code parent_pack} index, and the closure of
 * every stored activation's root set that holds it, which the reverse index alone misses (a sibling conflict between
 * packs only one activation lists together). Distinct closures are judged once each, however they were found.
 *
 * <p>The work is linear in the packs and their edges: the descriptors are read once, the index is one pass over them and
 * one walk upward from the written pack, and each root set costs one walk of its closure.
 */
final class ClosureRoots {

    /**
     * The roots of one closure to judge.
     *
     * @param ids the configured identifiers the closure is rooted at, in the order written
     */
    record Roots(List<PackIdentifier> ids) {

        Roots {
            ids = List.copyOf(ids);
        }

        /** The roots as a reader names them. */
        String label() {
            return ids.stream()
                    .map(PackIdentifier::value)
                    .reduce((first, next) -> first + ", " + next)
                    .orElse("");
        }
    }

    private final StagedPackViews.View packs;
    private final Map<PackIdentifier, PackDescriptor> readable = new LinkedHashMap<>();
    private final Map<String, PackUnderResolution> firstByPackId = new LinkedHashMap<>();
    private final Map<Set<String>, Roots> byClosure = new LinkedHashMap<>();
    private final String writtenPackId;

    private ClosureRoots(StagedPackViews.View packs, String writtenPackId) {
        this.packs = packs;
        this.writtenPackId = writtenPackId;
    }

    /**
     * The closures the written pack joins.
     *
     * @param packs the stored packs with the written one in place
     * @param writtenPackId the descriptor {@code pack_id} of the written pack
     * @param activations the root sets of the stored activations; an empty optional roots at every configured pack
     * @return the roots of each distinct closure that holds the written pack, packs first and then activations
     */
    static List<Roots> of(StagedPackViews.View packs, String writtenPackId, List<Optional<List<String>>> activations) {
        ClosureRoots found = new ClosureRoots(packs, writtenPackId);
        found.readDescriptors();
        found.addHolders();
        activations.forEach(found::addActivation);
        return List.copyOf(found.byClosure.values());
    }

    /** Every descriptor once; a pack whose descriptor cannot be read is in no closure that can be walked. */
    private void readDescriptors() {
        for (ConfiguredPack configured : packs.catalog().configured()) {
            try {
                packs.descriptors().find(configured.id()).ifPresent(descriptor -> {
                    readable.put(configured.id(), descriptor);
                    firstByPackId.putIfAbsent(descriptor.packId(), new PackUnderResolution(configured, descriptor));
                });
            } catch (CharterRefusal unreadable) {
                if (unreadable.code() != RefusalCode.PACK_UNREADABLE) {
                    throw unreadable;
                }
            }
        }
    }

    /** One root per pack whose closure holds the written pack: the written pack and everything that builds on it. */
    private void addHolders() {
        Set<String> holders = dependents();
        readable.forEach((id, descriptor) -> {
            if (holders.contains(descriptor.packId())) {
                add(new Roots(List.of(id)));
            }
        });
    }

    /** The written pack's {@code pack_id} and every {@code pack_id} that reaches it over {@code depends_on} or lineage. */
    private Set<String> dependents() {
        Map<String, List<String>> reverse = new HashMap<>();
        for (PackDescriptor descriptor : readable.values()) {
            List<String> targets = new ArrayList<>();
            descriptor.dependencies().forEach(dependency -> targets.add(dependency.packId()));
            descriptor.parentPack().ifPresent(targets::add);
            targets.forEach(target -> reverse.computeIfAbsent(target, ignored -> new ArrayList<>())
                    .add(descriptor.packId()));
        }
        Set<String> reached = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(List.of(writtenPackId));
        while (!pending.isEmpty()) {
            String next = pending.removeFirst();
            if (reached.add(next)) {
                pending.addAll(reverse.getOrDefault(next, List.of()));
            }
        }
        return reached;
    }

    /**
     * An activation's root set: all configured packs when it lists none, else the packs it names. A set that names a
     * pack nobody configures is unresolved already, whatever is registered now, and a root whose descriptor cannot be
     * read has no closure to walk; both are left out.
     */
    private void addActivation(Optional<List<String>> listed) {
        List<PackIdentifier> ids = listed.map(
                        names -> names.stream().map(PackIdentifier::new).toList())
                .orElseGet(() -> packs.catalog().configured().stream()
                        .map(ConfiguredPack::id)
                        .toList());
        if (!ids.isEmpty() && readable.keySet().containsAll(ids)) {
            add(new Roots(ids));
        }
    }

    private void add(Roots roots) {
        List<String> rootPackIds = roots.ids().stream()
                .map(id ->
                        Objects.requireNonNull(readable.get(id), "read above").packId())
                .toList();
        Set<String> closure = PackCompositionResolver.compositionClosure(rootPackIds, firstByPackId);
        if (closure.contains(writtenPackId)) {
            byClosure.putIfAbsent(new HashSet<>(closure), roots);
        }
    }
}
