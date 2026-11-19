package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * The tensions in force under an activation, each with the artifacts that reconcile it (ADR-0045 decisions 8 to 10,
 * rulings G1 and G2).
 *
 * <p>A tension is resolved from the edge its authoring pack wrote, not through {@link EdgeIndex#merged}'s source-place
 * rule, so that neither side is privileged (G2): when the authoring pack holds its own version of a side and that
 * version loses the layer merge, the tension is absent; a side the pack does not hold resolves to the version in force.
 * A tension is in force when both resolved sides are. It is answered canonically, the smaller bare URN as the source; the
 * kernel refuses a tension from an artifact to itself, so no pair resolves to one artifact. Two packs that write the same pair collapse to one tension, and the
 * first writer in merge order (the closure order, then document order within a pack) keeps its {@code when} and
 * {@code reason}. An artifact in force reconciles a tension when its resolved {@code reconciles_tension} links reach
 * both sides; a link to one side only reconciles nothing.
 *
 * @param all the tensions in force, in (source, target) order
 */
public record TensionsInForce(List<TensionInForce> all) {

    private static final Comparator<TensionInForce> ORDER =
            Comparator.comparing(TensionInForce::source).thenComparing(TensionInForce::target);

    /** No tension. */
    public static final TensionsInForce NONE = new TensionsInForce(List.of());

    /**
     * A tension in force.
     *
     * @param source the smaller bare URN of the pair
     * @param target the larger bare URN of the pair
     * @param when the authored condition of the first writer
     * @param reason the authored reason of the first writer
     * @param reconciledBy the artifacts in force that reconcile the pair, in URN order
     */
    public record TensionInForce(
            Urn source, Urn target, Optional<String> when, Optional<String> reason, SortedSet<Urn> reconciledBy) {

        /**
         * Checks the fields and the order of the pair.
         *
         * @param source the source
         * @param target the target
         * @param when the condition
         * @param reason the reason
         * @param reconciledBy the reconcilers
         */
        public TensionInForce {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
            if (source.compareTo(target) >= 0) {
                throw new IllegalArgumentException(
                        "a tension in force runs from the smaller URN: " + source + " " + target);
            }
            reconciledBy = Collections.unmodifiableSortedSet(
                    new TreeSet<>(Objects.requireNonNull(reconciledBy, "reconciledBy")));
        }

        /**
         * Whether an artifact in force reconciles the tension.
         *
         * @return true when {@link #reconciledBy()} holds one
         */
        public boolean reconciled() {
            return !reconciledBy.isEmpty();
        }

        /**
         * Whether the tension involves the artifact.
         *
         * @param urn the artifact
         * @return true when it is one of the two sides
         */
        public boolean involves(Urn urn) {
            return source.equals(urn) || target.equals(urn);
        }
    }

    /**
     * Checks, sorts and copies the tensions.
     *
     * @param all the tensions
     */
    public TensionsInForce {
        all = Objects.requireNonNull(all, "all").stream().sorted(ORDER).toList();
    }

    /**
     * Resolves the tensions of a closure under its layer merge.
     *
     * @param packs the composed closure, in closure order
     * @param facts the facts each pack was read with, by configured identifier
     * @param edges the closure's unmerged index, which resolves a qualified side to its holding pack
     * @param merged the layer merge of the closure and its merged index
     * @param effective whether an artifact is in force
     * @return the tensions in force
     */
    static TensionsInForce of(
            List<PackUnderResolution> packs,
            Map<String, PackCompositionFacts> facts,
            EdgeIndex edges,
            ClosureView.MergedView merged,
            Predicate<Urn> effective) {
        Objects.requireNonNull(packs, "packs");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(merged, "merged");
        Objects.requireNonNull(effective, "effective");
        Map<Set<Urn>, Kept> firstByPair = new LinkedHashMap<>();
        for (PackUnderResolution pack : packs) {
            PackUlid writer = pack.descriptorId();
            for (Relationship edge : facts.getOrDefault(pack.configured().id().value(), PackCompositionFacts.NONE)
                    .edges()) {
                if (edge.type() != RelationType.TENSION) {
                    continue;
                }
                Optional<Urn> from = resolved(edge.from(), edge.fromReference(), pack, writer, edges, merged.closure());
                Optional<Urn> to = resolved(edge.to(), edge.toReference(), pack, writer, edges, merged.closure());
                if (from.isPresent() && to.isPresent() && effective.test(from.get()) && effective.test(to.get())) {
                    firstByPair.putIfAbsent(Set.of(from.get(), to.get()), Kept.of(from.get(), to.get(), edge));
                }
            }
        }
        Map<Urn, Set<Urn>> reconciled = reconciledSides(merged.edges(), effective);
        List<TensionInForce> tensions = firstByPair.values().stream()
                .map(kept -> {
                    SortedSet<Urn> reconcilers = new TreeSet<>();
                    reconciled.forEach((reconciler, sidesReached) -> {
                        if (sidesReached.contains(kept.source()) && sidesReached.contains(kept.target())) {
                            reconcilers.add(reconciler);
                        }
                    });
                    return new TensionInForce(
                            kept.source(),
                            kept.target(),
                            kept.first().when(),
                            kept.first().rationale(),
                            reconcilers);
                })
                .toList();
        return new TensionsInForce(tensions);
    }

    /**
     * The tensions whose two sides are both listed.
     *
     * @param listed the artifacts a read lists
     * @return the tensions among them, in (source, target) order
     */
    public List<TensionInForce> deliveredTo(Set<Urn> listed) {
        Objects.requireNonNull(listed, "listed");
        return all.stream()
                .filter(tension -> listed.contains(tension.source()) && listed.contains(tension.target()))
                .toList();
    }

    /**
     * The tensions no artifact in force reconciles.
     *
     * @return them, in (source, target) order
     */
    public List<TensionInForce> unreconciled() {
        return all.stream().filter(tension -> !tension.reconciled()).toList();
    }

    /**
     * A side as the merge sees it (G2): the authoring pack's own version must win; any other side is the version in
     * force; empty when the pack's own version loses or no version of the side is held.
     */
    private static Optional<Urn> resolved(
            Urn urn,
            PackQualifiedReference side,
            PackUnderResolution pack,
            PackUlid writer,
            EdgeIndex edges,
            MergedClosure merged) {
        MergedClosure.Winner winner = merged.winners().get(urn);
        if (winner == null) {
            return Optional.empty();
        }
        Optional<PackUlid> holder = side.qualified() ? edges.holdingPack(side, writer) : Optional.of(writer);
        boolean ownVersion = holder.filter(writer::equals).isPresent() && pack.holds(urn.toString());
        if (ownVersion && !winner.descriptorPackId().equals(writer)) {
            return Optional.empty();
        }
        return Optional.of(urn);
    }

    /** The first written tension of a pair, with its resolved sides in canonical order. */
    private record Kept(Urn source, Urn target, Relationship first) {

        static Kept of(Urn one, Urn other, Relationship first) {
            return one.compareTo(other) < 0 ? new Kept(one, other, first) : new Kept(other, one, first);
        }
    }

    /** The sides each reconciler in force reaches through its resolved {@code reconciles_tension} links. */
    private static Map<Urn, Set<Urn>> reconciledSides(EdgeIndex merged, Predicate<Urn> effective) {
        Map<Urn, Set<Urn>> sides = new TreeMap<>();
        merged.links().stream()
                .filter(link -> link.relation() == RelationType.RECONCILES_TENSION)
                .filter(link -> effective.test(link.source()))
                .forEach(link -> sides.computeIfAbsent(link.source(), ignored -> new TreeSet<>())
                        .add(link.target()));
        return sides;
    }
}
