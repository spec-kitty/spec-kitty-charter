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
 * The cascade policy (ADR-0059 D2, FR-011 to FR-015), as pure functions over values: what a walk the packs slice made
 * means for the activation document. The domain plans each walk ({@link ReachPlan}) and reads its answer
 * ({@link Reach}); it walks nothing itself.
 *
 * <ul>
 *   <li><b>Select</b> ({@link #planSelect}, {@link #applySelect}): the target and what the walk reached over the five
 *       relations ({@code requires}, {@code suggests}, {@code refines}, {@code scope}, {@code instantiates}) and the
 *       lineage pass-through, within the scope, join {@code selected}. The rest is reported: out of scope in
 *       {@code skippedByScope}, a stop in {@code skippedDeactivated}, what lies only behind a stop in
 *       {@code unreachedBehindDeactivated} (whatever the scope). A lineage ancestor that is passed through is never in
 *       force and never reported; a denied one stops the pass-through and is reported as any stop is (D2.4).
 *   <li><b>Clearing</b>: a select clears the target's own deny entry; with {@code includeDeactivated} it also clears the
 *       deny entries of everything the walk went through, whatever the scope (what it reached, the ancestors it passed),
 *       and of what the {@code enhances} pull reaches from what it selects, bounded by the document's
 *       {@code activatedKinds} ({@link #pullKinds}). A cleared artifact outside the scope, a cleared ancestor and a
 *       pulled artifact are cleared, never selected (D2.2): the first stays in {@code skippedByScope}.
 *   <li><b>Deactivate</b> ({@link #planDeactivate}, {@link #planShared}, {@link #applyDeactivate}): the target and, with
 *       a cascade, the selected artifacts in scope only it reaches leave {@code selected}. A floor artifact is never a
 *       candidate: it stays a start of the shared walk, and one that is selected and reached is reported in
 *       {@code skippedShared} when another active artifact reaches it, named by that artifact. A candidate another active artifact ({@code selected} plus the floor) reaches, or an
 *       active enhancer references, is kept and reported in {@code skippedShared}. Only the target gets a deny entry.
 *       A mission type is never a candidate: no cascade scope admits it, so a selected one the target reached is
 *       reported in {@code skippedByScope} and stays.
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

    /** The relations a cascade walks forward (ADR-0059 D2.1). */
    public static final Set<RelationType> CASCADE_RELATIONS = Collections.unmodifiableSet(EnumSet.of(
            RelationType.REQUIRES,
            RelationType.SUGGESTS,
            RelationType.REFINES,
            RelationType.SCOPE,
            RelationType.INSTANTIATES));

    private CascadeRules() {}

    /**
     * The walk of a select: from the target over the five relations with the lineage, stopping at the document's deny
     * entries (other than the target's own), or nowhere with {@code includeDeactivated}.
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
        Set<ArtifactRef> stops = includeDeactivated ? Set.of() : denied(document);
        return new ReachPlan(Set.of(target), CASCADE_RELATIONS, without(stops, Set.of(target)), true);
    }

    /**
     * What a select puts in {@code selected}: the target and the reached artifacts in scope. The application starts
     * the {@code enhances} pull here when the write includes deactivated artifacts.
     *
     * @param target the artifact selected
     * @param scope the cascade scope
     * @param reach the walk's answer
     * @return the artifacts, sorted
     */
    public static SortedSet<ArtifactRef> walkSelected(ArtifactRef target, CascadeScope scope, Reach reach) {
        SortedSet<ArtifactRef> selecting =
                new TreeSet<>(scopeSplit(target, scope, reach).inScope());
        selecting.add(target);
        return selecting;
    }

    /**
     * The kinds the write-time {@code enhances} pull of {@code includeDeactivated} may reach: those the document's
     * {@code activatedKinds} names, as the read-time pull is bounded (ADR-0059 D2.2, FR-013). Absent means none, as
     * {@code []} does ({@link ActivationDocument#inForce}). The pull keeps no artifact of another kind and does not
     * continue through one.
     *
     * @param document the document before the write
     * @return the kinds; empty when the document activates none
     */
    public static Set<ArtifactKind> pullKinds(ActivationDocument document) {
        Set<ArtifactKind> kinds = EnumSet.noneOf(ArtifactKind.class);
        ActivationDocument.kinds().stream().filter(document::inForce).forEach(kinds::add);
        return kinds;
    }

    /**
     * A select: the delta and the report.
     *
     * @param document the document before the write
     * @param target the artifact selected
     * @param scope the cascade scope
     * @param includeDeactivated whether denied artifacts are taken back into the cascade
     * @param reach the answer of the {@link #planSelect} walk; ignored without a cascade
     * @param pulled what the {@code enhances} pull reached from {@link #walkSelected}; ignored without
     *     {@code includeDeactivated}
     * @return the change
     */
    public static ActivationChange applySelect(
            ActivationDocument document,
            ArtifactRef target,
            CascadeScope scope,
            boolean includeDeactivated,
            Reach reach,
            Set<ArtifactRef> pulled) {
        Reach walked = scope.cascades() ? reach : Reach.empty();
        Split split = scopeSplit(target, scope, walked);
        SortedSet<ArtifactRef> selecting = walkSelected(target, scope, walked);
        Set<ArtifactRef> clearing =
                clearing(document, target, includeDeactivated ? union(walkedThrough(walked), pulled) : Set.of());
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
     * @param target the artifact deactivated, of a kind of {@link ActivationDocument#kinds()}
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
     * The walk of the other active artifacts for a deactivate, one step of a fixed point the application drives.
     *
     * <p>The walk starts from {@code selected} and the floor, less the target and the candidates (which must not keep
     * one another), plus each candidate an active enhancer references: a kept candidate is active after the write, so
     * what it reaches must stay too. It crosses the five relations with the lineage and stops at the target and the
     * deny entries. An enhancer is active when it is one of the other active artifacts or the previous walk reached
     * it, so a kept candidate that enhances another candidate keeps that one on the next step.
     *
     * <p>The application starts with {@code previous} = {@link Reach#empty()}, walks the plan, and calls again with
     * the answer until the plan equals the one before (at most one step per candidate, since the starts only grow);
     * the last answer is {@link #applyDeactivate}'s {@code othersReach}. Every step reads the same closure snapshot.
     *
     * @param document the document before the write
     * @param target the artifact deactivated
     * @param scope the cascade scope
     * @param ownReach the answer of the {@link #planDeactivate} walk
     * @param enhancers for each candidate, its enhancers, one hop over the enhances relation
     * @param floor the artifacts the governing packs require
     * @param previous the answer of the previous step's walk; {@link Reach#empty()} for the first step
     * @return the plan; one that walks nothing when the target reached no selected artifact in scope (no candidate and
     *     no selected floor artifact, whose {@code referencedBy} the walk also finds)
     */
    public static ReachPlan planShared(
            ActivationDocument document,
            ArtifactRef target,
            CascadeScope scope,
            Reach ownReach,
            Map<ArtifactRef, Set<ArtifactRef>> enhancers,
            Set<ArtifactRef> floor,
            Reach previous) {
        if (selectedReached(document, target, scope, ownReach).isEmpty()) {
            return ReachPlan.none(target);
        }
        Set<ArtifactRef> candidates = candidates(document, target, scope, ownReach, floor);
        Set<ArtifactRef> starts = new TreeSet<>(others(document, target, candidates, floor));
        starts.addAll(enhancerKept(
                candidates, enhancers, union(starts, previous.reached().keySet())));
        Set<ArtifactRef> stops = new TreeSet<>(denied(document));
        stops.add(target);
        return new ReachPlan(starts, CASCADE_RELATIONS, stops, true);
    }

    /**
     * A deactivate: the target is denied and unselected, and with a cascade so is every candidate no other active
     * artifact keeps. A candidate is kept when the shared walk reached it or an active enhancer references it; its
     * {@code referencedBy} is the nearest predecessor active after the write, or else an active enhancer. A selected
     * floor artifact the target reached is never a candidate; it is reported too, named by the nearest other active
     * artifact that reaches it, and left out when none does: the floor keeps it, and it never names itself.
     * {@code skippedByScope} lists only the out-of-scope
     * artifacts the target reached that are selected: one not selected is left alone and not reported.
     *
     * @param document the document before the write
     * @param target the artifact deactivated
     * @param scope the cascade scope
     * @param ownReach the answer of the {@link #planDeactivate} walk; ignored without a cascade
     * @param othersReach the answer of the last {@link #planShared} step's walk (its fixed point)
     * @param enhancers for each candidate, its enhancers, one hop over the enhances relation
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
            Map<ArtifactRef, Set<ArtifactRef>> enhancers,
            Set<ArtifactRef> floor) {
        requireDeniable(target);
        Reach own = scope.cascades() ? ownReach : Reach.empty();
        Set<ArtifactRef> candidates = candidates(document, target, scope, own, floor);
        Set<ArtifactRef> others = others(document, target, candidates, floor);
        Set<ArtifactRef> kept = kept(
                candidates,
                othersReach,
                enhancers,
                union(others, othersReach.reached().keySet()));
        Sharing sharing = new Sharing(new Predecessors(othersReach), union(others, kept), enhancers);
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

    /** The target's own deny entry and, with includeDeactivated, those of what is selected and what is pulled. */
    private static Set<ArtifactRef> clearing(
            ActivationDocument document, ArtifactRef target, Set<ArtifactRef> included) {
        Set<ArtifactRef> clearing = new TreeSet<>(included);
        clearing.add(target);
        return retained(clearing, denied(document));
    }

    /** What the walk went through: what it reached, the target included, and the ancestors it passed. */
    private static Set<ArtifactRef> walkedThrough(Reach reach) {
        return union(reach.reached().keySet(), reach.ancestorsPassed());
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

    /** The candidates the shared walk reached or an active enhancer references: they stay selected. */
    private static Set<ArtifactRef> kept(
            Set<ArtifactRef> candidates,
            Reach othersReach,
            Map<ArtifactRef, Set<ArtifactRef>> enhancers,
            Set<ArtifactRef> active) {
        Set<ArtifactRef> kept = retained(candidates, othersReach.reached().keySet());
        kept.addAll(enhancerKept(candidates, enhancers, active));
        return kept;
    }

    /** The candidates an active enhancer references. */
    private static Set<ArtifactRef> enhancerKept(
            Set<ArtifactRef> candidates, Map<ArtifactRef, Set<ArtifactRef>> enhancers, Set<ArtifactRef> active) {
        Set<ArtifactRef> kept = new TreeSet<>();
        candidates.stream()
                .filter(candidate ->
                        enhancers.getOrDefault(candidate, Set.of()).stream().anyMatch(active::contains))
                .forEach(kept::add);
        return kept;
    }

    private static void requireDeniable(ArtifactRef target) {
        if (!ActivationDocument.kinds().contains(target.kind())) {
            throw new IllegalArgumentException(
                    "An activation denies no '" + target.kind().token() + "' artifacts.");
        }
    }

    // Document edits.

    private static ActivationDocument withSelections(ActivationDocument document, Set<ArtifactRef> selecting) {
        ActivationDocument after = document;
        for (ArtifactRef ref : selecting) {
            after = ref.kind() == ArtifactKind.MISSION_TYPE
                    ? withMissionType(after, ref.id())
                    : after.withSelected(ref.kind(), ref.id());
        }
        return after;
    }

    private static ActivationDocument withMissionType(ActivationDocument document, String id) {
        SortedSet<String> ids = new TreeSet<>(document.missionTypes().orElseGet(TreeSet::new));
        ids.add(id);
        return rebuilt(document, document.selected(), Optional.of(ids));
    }

    private static ActivationDocument withoutDenies(ActivationDocument document, Set<ArtifactRef> clearing) {
        ActivationDocument after = document;
        for (ArtifactRef ref : clearing) {
            after = after.withoutDeny(ref.kind(), ref.id());
        }
        return after;
    }

    /** The document less the given ids in {@code selected} and {@code missionTypes}; a list it empties stays, empty. */
    private static ActivationDocument unselected(ActivationDocument document, Set<ArtifactRef> leaving) {
        Map<ArtifactKind, SortedSet<String>> selected = new EnumMap<>(ArtifactKind.class);
        document.selected().forEach((kind, ids) -> selected.put(kind, new TreeSet<>(ids)));
        leaving.forEach(ref -> Optional.ofNullable(selected.get(ref.kind())).ifPresent(ids -> ids.remove(ref.id())));
        Optional<SortedSet<String>> missionTypes = document.missionTypes().map(TreeSet::new);
        missionTypes.ifPresent(ids -> leaving.stream()
                .filter(ref -> ref.kind() == ArtifactKind.MISSION_TYPE)
                .forEach(ref -> ids.remove(ref.id())));
        return rebuilt(document, selected, missionTypes);
    }

    /** The document with other allow lists and mission types; every other member as it was. */
    private static ActivationDocument rebuilt(
            ActivationDocument document,
            Map<ArtifactKind, SortedSet<String>> selected,
            Optional<SortedSet<String>> missionTypes) {
        ActivationDocument.Builder builder = ActivationDocument.builder();
        document.packs().ifPresent(builder::packs);
        document.activatedKinds().ifPresent(kinds -> builder.activatedKinds(List.copyOf(kinds)));
        missionTypes.ifPresent(builder::missionTypes);
        document.languages().ifPresent(builder::languages);
        selected.forEach(builder::selected);
        document.deactivated().forEach(builder::deactivated);
        return builder.build().document();
    }

    // Sets.

    private static boolean selectable(ArtifactKind kind) {
        return kind == ArtifactKind.MISSION_TYPE || ActivationDocument.kinds().contains(kind);
    }

    /** What is in force by the document: every allow-list id and every mission type. */
    private static Set<ArtifactRef> active(ActivationDocument document) {
        Set<ArtifactRef> active = new TreeSet<>(ActivationChange.refs(document.selected()));
        document.missionTypes()
                .ifPresent(ids -> ids.forEach(id -> active.add(new ArtifactRef(ArtifactKind.MISSION_TYPE, id))));
        return active;
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
     * reaches is left out, and a kept candidate always has another (the shared walk or an enhancer kept it).
     *
     * @param predecessors the ways of the shared walk's fixed point
     * @param active what is active after the write: the other active artifacts and the kept candidates
     * @param enhancers for each candidate, its enhancers
     */
    private record Sharing(
            Predecessors predecessors, Set<ArtifactRef> active, Map<ArtifactRef, Set<ArtifactRef>> enhancers) {

        List<SkippedReport.SharedSkip> report(Set<ArtifactRef> kept, Set<ArtifactRef> floorReached) {
            Set<ArtifactRef> reported = union(kept, floorReached);
            return reported.stream()
                    .flatMap(ref -> referencedBy(ref).map(by -> new SkippedReport.SharedSkip(ref, by)).stream())
                    .toList();
        }

        /** The nearest active artifact back along the shared walk's ways, or else an active enhancer's. */
        private Optional<ArtifactRef> referencedBy(ArtifactRef kept) {
            Predicate<ArtifactRef> accepted = ref -> !ref.equals(kept) && active.contains(ref);
            List<ArtifactRef> ways =
                    predecessors.ways(kept).stream().map(Reach.Via::from).toList();
            return predecessors
                    .nearest(ways, accepted)
                    .or(() -> predecessors.nearest(enhancers.getOrDefault(kept, Set.of()), accepted));
        }
    }
}
