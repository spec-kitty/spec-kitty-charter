package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * The cascade policy (ADR-0059 D2 as amended on 2026-11-06, FR-011 to FR-015), as pure functions over values: what a
 * walk the packs slice made means for the activation document. The domain plans each walk ({@link ReachPlan}) and
 * reads its answer ({@link Reach}); it walks nothing itself. Every walk crosses the same relations
 * ({@link #CASCADE_RELATIONS}), each in its declared direction, with the lineage; {@code enhances} is one of them and
 * has no rule of its own.
 *
 * <ul>
 *   <li><b>Select</b> ({@link #planSelect}, {@link #applySelect}): the target and what the walk reached, within the
 *       scope, join {@code selected}. The rest is reported: out of scope in {@code skippedByScope}, a stop in
 *       {@code skippedDeactivated}, what lies only behind a stop in {@code unreachedBehindDeactivated} (whatever the
 *       scope). A lineage ancestor that is passed through is never in force and never reported; a denied one stops the
 *       pass-through and is reported as any stop is (D2.4).
 *   <li><b>Clearing</b>: a select clears the target's own deny entry; with {@code includeDeactivated} it also clears the
 *       deny entries the walk passes through within the scope: what it reached in scope and the ancestors it passed
 *       whose kind is in scope (A3). A denied artifact outside the scope stops the walk and keeps its deny entry, as
 *       without {@code includeDeactivated}. A cleared ancestor is cleared, never selected.
 *   <li><b>Deactivate</b> ({@link #planDeactivate}, {@link #planShared}, {@link #applyDeactivate}): the target and, with
 *       a cascade, the selected artifacts in scope only it reaches leave {@code selected}. The target's walk follows
 *       the lineage as the select's does, so the two round-trip. A floor artifact is never a candidate: it stays a
 *       start of the shared walk, and one that is selected and reached is reported in {@code skippedShared} when
 *       another active artifact reaches it, named by that artifact. A candidate the shared walk from the other active
 *       artifacts ({@code selected} plus the floor) reaches is kept and reported in {@code skippedShared}. Only the
 *       target gets a deny entry. A mission type is never a candidate: no cascade scope admits it, so a selected one
 *       the target reached is reported in {@code skippedByScope} and stays.
 *   <li><b>Reporting</b> ({@link Predecessors}): {@code SkippedDeactivated.requiredBy} names, for each way to a stop,
 *       the nearest predecessor of a kind an {@code ArtifactReference} can name that is not a passed ancestor, and
 *       {@code SharedSkip.referencedBy} the nearest predecessor active after the write (the other active artifacts and
 *       the kept candidates). A template, an asset, an ancestor or an unselected intermediate leads back to what put it
 *       in the walk; a lineage pass-through leads back to its descendant; among the nearest, the first in code-point
 *       order is named.
 * </ul>
 *
 * <p>The {@code 409} for a required or locked target is {@link DocumentRules}', checked before a plan is made.
 */
public final class CascadeRules {

    /**
     * The relations a cascade walks, each in its declared direction (ADR-0059 D2.1, and D2 as amended on 2026-11-06):
     * core's five and {@code enhances}, from the enhancer to its target.
     */
    public static final Set<RelationType> CASCADE_RELATIONS = Collections.unmodifiableSet(EnumSet.of(
            RelationType.REQUIRES,
            RelationType.SUGGESTS,
            RelationType.REFINES,
            RelationType.SCOPE,
            RelationType.INSTANTIATES,
            RelationType.ENHANCES));

    private CascadeRules() {}

    /**
     * The walk of a select: from the target over the cascade relations with the lineage, stopping at the document's deny
     * entries other than the target's own. With {@code includeDeactivated} it stops only at those whose kind the scope
     * does not admit, so an artifact outside the scope stops the walk as it would without it (ADR-0059 as amended on
     * 2026-11-06, A3); a denied ancestor is passed through only when its kind is in scope.
     *
     * @param document the document before the write
     * @param target the artifact selected
     * @param scope the cascade scope
     * @param includeDeactivated whether denied artifacts are taken back into the cascade
     * @return the plan; one that {@linkplain ReachPlan#walks() walks} nothing without a cascade
     */
    public static ReachPlan planSelect(
            ActivationDocument document, ArtifactRef target, CascadeScope scope, boolean includeDeactivated) {
        if (!scope.cascades()) {
            return ReachPlan.none(target);
        }
        Set<ArtifactRef> stops = new TreeSet<>(denied(document));
        if (includeDeactivated) {
            stops.removeIf(ref -> scope.admits(ref.kind()));
        }
        return new ReachPlan(Set.of(target), CASCADE_RELATIONS, without(stops, Set.of(target)), true);
    }

    /**
     * A select: the delta and the report.
     *
     * @param document the document before the write
     * @param target the artifact selected
     * @param scope the cascade scope
     * @param includeDeactivated whether denied artifacts are taken back into the cascade
     * @param reach the answer of the {@link #planSelect} walk; ignored without a cascade
     * @return the change
     */
    public static ActivationChange applySelect(
            ActivationDocument document,
            ArtifactRef target,
            CascadeScope scope,
            boolean includeDeactivated,
            Reach reach) {
        Reach walked = scope.cascades() ? reach : Reach.empty();
        Split split = scopeSplit(target, scope, walked);
        SortedSet<ArtifactRef> selecting = new TreeSet<>(split.inScope());
        selecting.add(target);
        Set<ArtifactRef> clearing =
                clearing(document, target, includeDeactivated ? walkedThrough(walked, scope) : Set.of());
        ActivationDocument after = withoutDenies(withSelections(document, selecting), clearing);
        SkippedReport skipped = new SkippedReport(
                List.copyOf(split.outOfScope()), deniedReport(walked), behindReport(walked), List.of());
        return ActivationChange.of(document, after, skipped);
    }

    /**
     * The target's own walk for a deactivate: the relations a select walks, with the lineage, stopping nowhere. It
     * follows the lineage exactly as the select walk does (ADR-0059 D2, operator ruling of 2026-11-06), so a cascading
     * deactivate undoes a cascading select: what the target reached through an ancestor is a candidate too.
     *
     * @param document the document before the write
     * @param target the artifact deactivated, of an activatable kind other than {@code mission_type}
     * @param scope the cascade scope
     * @return the plan; one that walks nothing without a cascade
     * @throws IllegalArgumentException when the target's kind has no deny list
     */
    public static ReachPlan planDeactivate(ActivationDocument document, ArtifactRef target, CascadeScope scope) {
        Objects.requireNonNull(document, "document");
        requireDeniable(target);
        return scope.cascades()
                ? new ReachPlan(Set.of(target), CASCADE_RELATIONS, Set.of(), true)
                : ReachPlan.none(target);
    }

    /**
     * The walk of the other active artifacts for a deactivate: it starts from {@code selected} and the floor, less the
     * target and the candidates (which must not keep one another), crosses the cascade relations with the lineage, and
     * stops at the target and the deny entries. A candidate it reaches is shared; since the walk goes on through a
     * candidate it reached, a kept candidate keeps what it reaches in turn, in the same walk. Its answer is
     * {@link #applyDeactivate}'s {@code othersReach}.
     *
     * @param document the document before the write
     * @param target the artifact deactivated
     * @param scope the cascade scope
     * @param ownReach the answer of the {@link #planDeactivate} walk
     * @param floor the artifacts the governing packs require
     * @return the plan; one that walks nothing when the target reached no selected artifact in scope (no candidate and
     *     no selected floor artifact, whose {@code referencedBy} the walk also finds)
     */
    public static ReachPlan planShared(
            ActivationDocument document,
            ArtifactRef target,
            CascadeScope scope,
            Reach ownReach,
            Set<ArtifactRef> floor) {
        if (selectedReached(document, target, scope, ownReach).isEmpty()) {
            return ReachPlan.none(target);
        }
        Set<ArtifactRef> candidates = candidates(document, target, scope, ownReach, floor);
        Set<ArtifactRef> stops = new TreeSet<>(denied(document));
        stops.add(target);
        return new ReachPlan(others(document, target, candidates, floor), CASCADE_RELATIONS, stops, true);
    }

    /**
     * A deactivate: the target is denied and unselected, and with a cascade so is every candidate no other active
     * artifact keeps. A candidate is kept when the shared walk reached it; its {@code referencedBy} is the nearest
     * predecessor active after the write. A selected floor artifact the target reached is never a candidate; it is
     * reported too, named by the nearest other active artifact that reaches it, and left out when none does: the floor
     * keeps it, and it never names itself. {@code skippedByScope} lists only the out-of-scope artifacts the target
     * reached that are selected: one not selected is left alone and not reported.
     *
     * @param document the document before the write
     * @param target the artifact deactivated
     * @param scope the cascade scope
     * @param ownReach the answer of the {@link #planDeactivate} walk; ignored without a cascade
     * @param othersReach the answer of the {@link #planShared} walk
     * @param floor the artifacts the governing packs require
     * @return the change
     * @throws IllegalArgumentException when the target's kind has no deny list
     */
    public static ActivationChange applyDeactivate(
            ActivationDocument document,
            ArtifactRef target,
            CascadeScope scope,
            Reach ownReach,
            Reach othersReach,
            Set<ArtifactRef> floor) {
        requireDeniable(target);
        Reach own = scope.cascades() ? ownReach : Reach.empty();
        Set<ArtifactRef> candidates = candidates(document, target, scope, own, floor);
        Set<ArtifactRef> others = others(document, target, candidates, floor);
        Set<ArtifactRef> kept = retained(candidates, othersReach.reached().keySet());
        Sharing sharing = new Sharing(new Predecessors(othersReach), union(others, kept));
        List<SkippedReport.SharedSkip> shared =
                sharing.report(kept, retained(selectedReached(document, target, scope, own), floor));
        Set<ArtifactRef> leaving = without(candidates, kept);
        leaving.add(target);
        ActivationDocument after = unselected(document, leaving).withDeny(target.kind(), target.id());
        List<ArtifactRef> outOfScope =
                List.copyOf(retained(scopeSplit(target, scope, own).outOfScope(), active(document)));
        return ActivationChange.of(document, after, new SkippedReport(outOfScope, List.of(), List.of(), shared));
    }

    // Select steps.

    /** The reached artifacts other than the target, split by the scope; a kind no reference can name is dropped. */
    private static Split scopeSplit(ArtifactRef target, CascadeScope scope, Reach reach) {
        Split split = new Split(new TreeSet<>(), new TreeSet<>());
        reach.reached().keySet().stream()
                .filter(ref -> !ref.equals(target) && selectable(ref.kind()))
                .forEach(ref -> (scope.admits(ref.kind()) ? split.inScope() : split.outOfScope()).add(ref));
        return split;
    }

    /** The target's own deny entry and, with includeDeactivated, those of what the walk went through. */
    private static Set<ArtifactRef> clearing(
            ActivationDocument document, ArtifactRef target, Set<ArtifactRef> included) {
        Set<ArtifactRef> clearing = new TreeSet<>(included);
        clearing.add(target);
        return retained(clearing, denied(document));
    }

    /** What the walk went through in scope: what it reached and the ancestors it passed, of a kind the scope admits. */
    private static Set<ArtifactRef> walkedThrough(Reach reach, CascadeScope scope) {
        Set<ArtifactRef> through = union(reach.reached().keySet(), reach.ancestorsPassed());
        through.removeIf(ref -> !scope.admits(ref.kind()));
        return through;
    }

    private static List<SkippedReport.SkippedDeactivated> deniedReport(Reach reach) {
        Predecessors predecessors = new Predecessors(reach);
        return reach.stopped().entrySet().stream()
                .filter(stop -> selectable(stop.getKey().kind()))
                .map(stop -> new SkippedReport.SkippedDeactivated(
                        stop.getKey(),
                        stop.getValue().stream().anyMatch(via -> via.relation() == RelationType.REQUIRES),
                        requiredBy(stop.getValue(), predecessors)))
                .toList();
    }

    /** For each way to a stop, the nearest namable predecessor that is not a passed ancestor; sorted, each once. */
    private static List<ArtifactRef> requiredBy(List<Reach.Via> ways, Predecessors predecessors) {
        Set<ArtifactRef> requiredBy = new TreeSet<>();
        ways.forEach(via -> predecessors
                .nearest(List.of(via.from()), ref -> selectable(ref.kind()) && !predecessors.ancestorOnly(ref))
                .ifPresent(requiredBy::add));
        return List.copyOf(requiredBy);
    }

    private static List<ArtifactRef> behindReport(Reach reach) {
        return reach.behindStopped().stream()
                .filter(ref -> selectable(ref.kind()) && !reach.reached().containsKey(ref))
                .toList();
    }

    // Deactivate steps.

    /**
     * The selected artifacts in scope the target's walk reached, less the floor: a floor artifact is never a candidate.
     * No scope admits a mission type ({@link CascadeScope#all()}), so a mission type is never one either.
     */
    private static Set<ArtifactRef> candidates(
            ActivationDocument document,
            ArtifactRef target,
            CascadeScope scope,
            Reach ownReach,
            Set<ArtifactRef> floor) {
        return without(selectedReached(document, target, scope, ownReach), floor);
    }

    /** The selected artifacts in scope the target's walk reached, the floor included. */
    private static Set<ArtifactRef> selectedReached(
            ActivationDocument document, ArtifactRef target, CascadeScope scope, Reach ownReach) {
        return retained(scopeSplit(target, scope, ownReach).inScope(), active(document));
    }

    /** The other active artifacts: {@code selected} and the floor, less the target and the candidates. */
    private static Set<ArtifactRef> others(
            ActivationDocument document, ArtifactRef target, Set<ArtifactRef> candidates, Set<ArtifactRef> floor) {
        Set<ArtifactRef> others = new TreeSet<>(active(document));
        others.addAll(floor);
        others.remove(target);
        others.removeAll(candidates);
        return others;
    }

    private static void requireDeniable(ArtifactRef target) {
        if (!target.kind().activatable() || target.kind() == ArtifactKind.MISSION_TYPE) {
            throw new IllegalArgumentException(
                    "An activation denies no '" + target.kind().token() + "' artifacts.");
        }
    }

    // Document edits.

    private static ActivationDocument withSelections(ActivationDocument document, Set<ArtifactRef> selecting) {
        ActivationDocument after = document;
        for (ArtifactRef ref : selecting) {
            after = after.withSelected(ref.kind(), ref.id());
        }
        return after;
    }

    private static ActivationDocument withoutDenies(ActivationDocument document, Set<ArtifactRef> clearing) {
        ActivationDocument after = document;
        for (ArtifactRef ref : clearing) {
            after = after.withoutDeny(ref.kind(), ref.id());
        }
        return after;
    }

    /** The document less the given ids in {@code selected}; a list it empties is dropped (none of the kind). */
    private static ActivationDocument unselected(ActivationDocument document, Set<ArtifactRef> leaving) {
        Map<ArtifactKind, SortedSet<String>> selected = new EnumMap<>(ArtifactKind.class);
        document.selected().forEach((kind, ids) -> selected.put(kind, new TreeSet<>(ids)));
        leaving.forEach(ref -> Optional.ofNullable(selected.get(ref.kind())).ifPresent(ids -> ids.remove(ref.id())));
        return rebuilt(document, selected);
    }

    /** The document with other allow lists; every other member as it was. */
    private static ActivationDocument rebuilt(
            ActivationDocument document, Map<ArtifactKind, SortedSet<String>> selected) {
        ActivationDocument.Builder builder = ActivationDocument.builder();
        document.packs().ifPresent(builder::packs);
        document.languages().ifPresent(builder::languages);
        selected.forEach(builder::selected);
        document.deactivated().forEach(builder::deactivated);
        return builder.build().document();
    }

    // Sets.

    private static boolean selectable(ArtifactKind kind) {
        return kind.activatable();
    }

    /** What is in force by the document: every allow-list id, mission types included. */
    private static Set<ArtifactRef> active(ActivationDocument document) {
        return new TreeSet<>(ActivationChange.refs(document.selected()));
    }

    private static Set<ArtifactRef> denied(ActivationDocument document) {
        return ActivationChange.refs(document.deactivated());
    }

    private static Set<ArtifactRef> union(Set<ArtifactRef> left, Set<ArtifactRef> right) {
        Set<ArtifactRef> union = new TreeSet<>(left);
        union.addAll(right);
        return union;
    }

    private static Set<ArtifactRef> without(Set<ArtifactRef> from, Set<ArtifactRef> removed) {
        Set<ArtifactRef> rest = new TreeSet<>(from);
        rest.removeAll(removed);
        return rest;
    }

    private static Set<ArtifactRef> retained(Set<ArtifactRef> from, Set<ArtifactRef> kept) {
        Set<ArtifactRef> rest = new TreeSet<>(from);
        rest.retainAll(kept);
        return rest;
    }

    /** The reached artifacts in scope and out of it. */
    private record Split(Set<ArtifactRef> inScope, Set<ArtifactRef> outOfScope) {}

    /**
     * The {@code skippedShared} report of a deactivate: who keeps each kept candidate and each selected floor artifact
     * the target reached. An artifact is never named as what keeps itself: a floor artifact no other active artifact
     * reaches is left out, and a kept candidate always has another (the shared walk reached it from one).
     *
     * @param predecessors the ways of the shared walk
     * @param active what is active after the write: the other active artifacts and the kept candidates
     */
    private record Sharing(Predecessors predecessors, Set<ArtifactRef> active) {

        List<SkippedReport.SharedSkip> report(Set<ArtifactRef> kept, Set<ArtifactRef> floorReached) {
            Set<ArtifactRef> reported = union(kept, floorReached);
            return reported.stream()
                    .flatMap(ref -> referencedBy(ref).map(by -> new SkippedReport.SharedSkip(ref, by)).stream())
                    .toList();
        }

        /** The nearest active artifact back along the shared walk's ways. */
        private Optional<ArtifactRef> referencedBy(ArtifactRef kept) {
            Predicate<ArtifactRef> accepted = ref -> !ref.equals(kept) && active.contains(ref);
            List<ArtifactRef> ways =
                    predecessors.ways(kept).stream().map(Reach.Via::from).toList();
            return predecessors.nearest(ways, accepted);
        }
    }
}
