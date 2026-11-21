package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ClosureVerdict;
import ai.speckitty.charter.packs.domain.ClosureWalk;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.ImpactCheck;
import ai.speckitty.charter.packs.domain.Pack;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.log.LogAccessor;
import org.springframework.stereotype.Component;

/**
 * The registration impact check (IC-15, IC-04 step 9, FR-025, FR-027): with the written pack in place, it composes every
 * closure that holds it, and no other. Those are the closure of every pack that builds on it, found from a reverse
 * {@code depends_on}/lineage index, and the root set of every stored activation whose closure holds it, which the
 * index alone misses (a sibling conflict between packs only one activation lists together). A closure that does not
 * compose, or breaks a relation rule ({@link ImpactRelations}), refuses the write with its code, naming the closure's
 * roots and the packs involved, and nothing is written. PR A stays strict (DM-01M6TQ67C9ZTNGZB9CY28JS54F): a pack
 * already broken is not repaired by a write that leaves a closure broken.
 *
 * <p>It composes through {@code OfferingReadService} and {@code ClosureQueries}, built over {@link StagedPackViews}, so the
 * compose rules are the reads' own and none is copied. It runs under the registry lock and the pack's own, so the stored
 * packs cannot change under it and it needs no verification of its inputs; each pack's files are read once, however
 * many closures hold the pack. A closure's floor and locks are read before and after to give the notices of FR-027.
 *
 * <p>Every path then asks the activations slice's gate ({@link ActivationImpact#requireComposable}, C38, squad A2): a
 * stored activation whose closure the write joins may not be left with a step enhancement in force whose slot the
 * prompt in force does not declare. Its refusal is the write's, never swallowed.
 *
 * <p>Read side: stored packs registered before a rule are not re-judged here, only when a write joins their closure.
 */
@Component
public final class ClosureImpactCheck implements ImpactCheck {

    private static final LogAccessor LOG = new LogAccessor(ClosureImpactCheck.class);

    /** A closure's floor, the union of the copy of every governing preset in force at each level (A1), and its locks, as URN texts. */
    private record Force(Set<String> floor, Set<String> locks) {

        static final Force NONE = new Force(Set.of(), Set.of());

        static Force of(ClosureView view) {
            Set<String> floor = new TreeSet<>();
            view.mergedFloor().requiredBy().keySet().forEach(urn -> floor.add(urn.toString()));
            Set<String> locks = new TreeSet<>();
            for (Urn urn : view.holderOf().keySet()) {
                if (view.isLocked(urn)) {
                    locks.add(urn.toString());
                }
            }
            return new Force(floor, locks);
        }
    }

    /** The compose over one view of the packs: the offering that composes and the closure queries that read its floor. */
    private record Views(OfferingReadService offering, ClosureQueries queries) {

        static Views over(StagedPackViews.View packs) {
            ConsistentRead unverified = new ConsistentRead(() -> "overlay", new PackWriteGeneration());
            OfferingReadService offering = new OfferingReadService(
                            packs.catalog(), packs.descriptors(), packs.artifacts(), unverified)
                    .pinned();
            return new Views(offering, new ClosureQueries(offering, packs.catalog(), unverified));
        }
    }

    private final StagedPackViews views;
    private final ActivationImpact activations;
    private final ClosureWalk walk;
    private final Optional<RemovalDependents> dependents;

    /**
     * The check over the packs and the activations, with the service's closure walk, judging writes only: it has no
     * dependents finder, so it cannot judge a removal.
     *
     * @param views the stored packs, with a written pack laid over them
     * @param activations the stored activations' root sets
     */
    public ClosureImpactCheck(StagedPackViews views, ActivationImpact activations) {
        this(views, activations, new OwnClosureWalk(), Optional.empty());
    }

    /**
     * The check over the packs, the activations, a closure walk and the inbound scan that finds a removal's referrers.
     *
     * @param views the stored packs, with a written pack laid over them
     * @param activations the stored activations' root sets
     * @param walk the closure walk of each single-root closure
     * @param inbound the inbound scan
     */
    public ClosureImpactCheck(
            StagedPackViews views, ActivationImpact activations, ClosureWalk walk, InboundReferences inbound) {
        this(views, activations, walk, Optional.of(new RemovalDependents(inbound, activations)));
    }

    private ClosureImpactCheck(
            StagedPackViews views,
            ActivationImpact activations,
            ClosureWalk walk,
            Optional<RemovalDependents> dependents) {
        this.views = Objects.requireNonNull(views, "views");
        this.activations = Objects.requireNonNull(activations, "activations");
        this.walk = Objects.requireNonNull(walk, "walk");
        this.dependents = dependents;
    }

    /**
     * The check as Spring wires it.
     *
     * @param views the stored packs, with a written pack laid over them
     * @param activations the activations slice's port, when the deployment has that slice
     * @param walk the closure walk
     * @param inbound the inbound scan
     */
    @Autowired
    public ClosureImpactCheck(
            StagedPackViews views,
            ObjectProvider<ActivationImpact> activations,
            ClosureWalk walk,
            InboundReferences inbound) {
        this(views, activations.getIfAvailable(() -> ActivationImpact.NONE), walk, inbound);
    }

    @Override
    public Impact judge(PackChange change) {
        Objects.requireNonNull(change, "change");
        return judge(views.session(), change);
    }

    /**
     * Judges a change in a session its caller already holds (#556, squad A3): the removal judges in the session it read
     * its pack with, so each stored pack is read once per delete (#517 NFR-001).
     *
     * @param session the caller's session over the stored packs
     * @param change the change
     * @return the verdict, and the notices when it passes
     * @throws CharterRefusal as {@link #judge(PackChange)}
     */
    public Impact judge(StagedPackViews.Session session, PackChange change) {
        Objects.requireNonNull(session, "session");
        return switch (Objects.requireNonNull(change, "change")) {
            case Registration registration -> judgeWrite(session, registration.replacement(), true);
            case Write write -> judgeWrite(session, written(session, write), false);
            case Removal removal -> judgeRemoval(session, removal);
        };
    }

    /**
     * What a stored pack's facts become once one write to it, an artifact or a relationship or a removal, is stored
     * (operator ruling DM-01M6VG6GC40AKYYFRR35SV8HVW): the pack is read from the session the closures are composed
     * through, and the write is laid over the facts it read. The descriptor is the stored one.
     */
    private static Replacement written(StagedPackViews.Session session, Write write) {
        StagedPackViews.View stored = session.stored();
        ConfiguredPack configured = configuredAs(stored, write.pack());
        return new Replacement(
                configured,
                stored.descriptors().get(configured.id()),
                write.write().apply(stored.artifacts().compositionFacts(configured.id())),
                write.staged());
    }

    /** The configured pack a registered id names (#583 D3: by the registered id only). */
    private static ConfiguredPack configuredAs(StagedPackViews.View stored, PackIdentifier pack) {
        return stored.catalog().configured().stream()
                .filter(candidate -> candidate.id().equals(pack))
                .findFirst()
                .orElseThrow(() ->
                        new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found."));
    }

    /**
     * The impact of removing a pack whole (ADR-0081 D6, P2): its dependents first, each one named, and nothing composed
     * when there is any (409 before 422); then every closure that holds the pack today composed again without it,
     * every fault collected. The removal names its dependents before it composes, so the closures left are those of an
     * activation that lists no packs; a closure rooted at the removed pack alone has nothing left to compose.
     */
    private Impact judgeRemoval(StagedPackViews.Session session, Removal removed) {
        RemovalDependents finder = dependents.orElseThrow(() ->
                new IllegalStateException("This impact check has no dependents finder, so it cannot judge a removal"));
        List<RemovalDependents.Dependent> found = finder.of(session.stored(), removed);
        if (!found.isEmpty()) {
            return new Impact(
                    new ClosureVerdict(List.of(
                            RemovalDependents.fault(removed.configured().id().value(), found))),
                    Notices.NONE);
        }
        StagedPackViews.View after = session.without(removed.configured().id());
        ClosureRoots.Found closures = removed.descriptor()
                .map(descriptor ->
                        ClosureRoots.of(session.stored(), descriptor.packId(), rootSets(activations.activationRoots())))
                .orElseGet(ClosureRoots.Found::none);
        Judged judged = judgeClosures(session.stored(), after, closures, roots -> {
            List<PackIdentifier> left = roots.ids().stream()
                    .filter(id -> !id.equals(removed.configured().id()))
                    .toList();
            return left.isEmpty() ? Optional.empty() : Optional.of(new ClosureRoots.Roots(left));
        });
        return concluded(closures, judged, true);
    }

    /** The root sets of the stored activations, as the closure finder takes them. */
    private static List<Optional<List<String>>> rootSets(List<ActivationImpact.ActivationRoots> activations) {
        return activations.stream().map(ActivationImpact.ActivationRoots::packs).toList();
    }

    /**
     * Judges every closure the written pack joins, in today's phase order (#556, FR-009): each closure's compose and
     * relation faults, then the walk of every single-root closure that passed, then, only when nothing failed, the
     * activations slice's gate. Only a closure that passed gives its notices and its change.
     */
    private Impact judgeWrite(StagedPackViews.Session session, Replacement replacement, boolean namingActivations) {
        StagedPackViews.View staged = session.with(replacement);
        ClosureRoots.Found found =
                ClosureRoots.of(staged, replacement.descriptor().packId(), rootSets(activations.activationRoots()));
        Judged judged = judgeClosures(session.stored(), staged, found, Optional::of);
        if (!judged.faults().halted()) {
            walkFaults(found, replacement, judged.changes(), staged, session, judged.faults());
        }
        return concluded(found, judged, namingActivations);
    }

    /** What judging the closures of a change found: the closures' changes, the force diff and every fault. */
    private record Judged(
            Map<ClosureRoots.Roots, ActivationImpact.ClosureChange> changes, ForceDiff diff, Faults faults) {}

    /**
     * Judges each closure the change joins, in order, against the stored state it changes. A closure is judged under
     * the roots {@code judgedAs} gives it (a removal drops the removed pack, and skips a closure left with no root), and
     * keeps the roots it was found under for its change. A halt ends the loop with the faults found standing.
     */
    private Judged judgeClosures(
            StagedPackViews.View stored,
            StagedPackViews.View after,
            ClosureRoots.Found found,
            Function<ClosureRoots.Roots, Optional<ClosureRoots.Roots>> judgedAs) {
        Views afterViews = Views.over(after);
        Views beforeViews = Views.over(stored);
        Set<PackIdentifier> configuredBefore = new HashSet<>();
        stored.catalog().configured().forEach(pack -> configuredBefore.add(pack.id()));
        ForceDiff diff = new ForceDiff();
        Map<ClosureRoots.Roots, ActivationImpact.ClosureChange> changes = new LinkedHashMap<>();
        Faults faults = new Faults();
        for (ClosureRoots.Roots roots : found.closures()) {
            Optional<ClosureRoots.Roots> judgedRoots = judgedAs.apply(roots);
            if (judgedRoots.isEmpty()) {
                continue;
            }
            Optional<ClosureView> now = judge(judgedRoots.get(), afterViews, after, faults);
            if (faults.halted()) {
                break;
            }
            if (now.isEmpty()) {
                continue;
            }
            Optional<ClosureView> was = viewBefore(roots, beforeViews, configuredBefore);
            diff.add(was.map(Force::of).orElse(Force.NONE), Force.of(now.get()));
            changes.put(roots, new ActivationImpact.ClosureChange(was, now.get()));
        }
        return new Judged(changes, diff, faults);
    }

    /** The answer once the closures are judged: the faults, or else the activations' gate and the notices. */
    private Impact concluded(ClosureRoots.Found found, Judged judged, boolean namingActivations) {
        if (!judged.faults().isEmpty()) {
            return new Impact(judged.faults().verdict(), Notices.NONE);
        }
        ActivationImpact.ClosureChanges joined =
                roots -> found.holding().apply(roots).map(judged.changes()::get);
        activations.requireComposable(joined);
        List<String> affected = namingActivations ? activations.activationsAffected(joined) : List.of();
        return new Impact(ClosureVerdict.PASSING, judged.diff().notices(affected));
    }

    /**
     * A stored pack the impact check would refuse now, with every fault of its closure (#556, FR-011).
     *
     * @param pack the configured name of the pack whose closure is refused
     * @param refusal the one refusal a write into its closure would now meet: every fault of the closure, by D1
     */
    public record Finding(String pack, CharterRefusal refusal) {

        /**
         * Checks the fields.
         *
         * @param pack the configured name of the pack
         * @param refusal the refusal
         */
        public Finding {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(refusal, "refusal");
        }

        /**
         * The refusal's code: its first fault's.
         *
         * @return the code
         */
        public RefusalCode code() {
            return refusal.code();
        }

        /**
         * What is wrong, as the refusal says it.
         *
         * @return the detail
         */
        public String detail() {
            return String.valueOf(refusal.getMessage());
        }
    }

    /**
     * The census (IC-15, read side): the closure of every stored pack, composed and judged as a registration judges a
     * closure it joins. Packs registered before a rule are not re-judged on a read, which answers {@code pack_unreadable}
     * only as it always has; this says which of them a write into their closure would now refuse. It reads and writes
     * nothing but the packs, once each, and takes no lock.
     *
     * @return one finding per stored pack whose closure would be refused, with every fault, in catalog order
     */
    public List<Finding> census() {
        StagedPackViews.Session session = views.session();
        StagedPackViews.View stored = session.stored();
        Views composing = Views.over(stored);
        List<Finding> findings = new ArrayList<>();
        for (ConfiguredPack pack : stored.catalog().configured()) {
            censusOf(pack, composing, stored, session.packs(), session.enhancements())
                    .ifPresent(findings::add);
        }
        return findings;
    }

    private Optional<Finding> censusOf(
            ConfiguredPack pack,
            Views composing,
            StagedPackViews.View stored,
            Optional<ai.speckitty.charter.packs.domain.PackRepository> packsOfCensus,
            Optional<ai.speckitty.charter.packs.domain.ProfileEnhancementGateway> enhancementsOfCensus) {
        String name = pack.id().value();
        try {
            OfferingReadService.ComposedClosure composed = composing.offering().composedWithFacts(List.of(pack.id()));
            List<ClosureFault> faults = relationFaults(composed).stream()
                    .map(broken -> ClosureFault.of(
                            broken.code(), broken.detail() + " (packs " + packsOf(broken) + ")", broken.packs()))
                    .toList();
            if (faults.isEmpty()) {
                ClosureWalk.Profiles profiles =
                        (holder, id) -> profileIn(requireStore(packsOfCensus).load(holder), id);
                ClosureView own = composing.queries().snapshot(Optional.of(List.of(pack.id())));
                faults = walk.walk(new ClosureWalk.OwnClosure(
                                pack.id(), stored.descriptors().get(pack.id()), own, profiles, enhancementsOfCensus))
                        .faults();
            }
            return faults.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new Finding(name, PackRefusals.of(new ClosureVerdict(faults))));
        } catch (CharterRefusal refused) {
            return Optional.of(new Finding(name, refused));
        }
    }

    private static String packsOf(ClosureFault broken) {
        return String.join(
                ", ", broken.packs().stream().map(pack -> pack.packId().value()).toList());
    }

    /** Every relation fault of a composed closure: the relation rules, then the specialization cycles (#556, FR-003). */
    private static List<ClosureFault> relationFaults(OfferingReadService.ComposedClosure composed) {
        List<ClosureFault> faults = new ArrayList<>(ImpactRelations.check(composed));
        faults.addAll(SpecializationCycleRule.check(composed));
        return faults;
    }

    /**
     * Logs the census at startup, one warning per finding and one per fault of it (T075, #556 FR-011). A census that cannot run is itself a warning, never a
     * failure to start.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logCensus() {
        try {
            census().forEach(finding -> {
                LOG.warn("The stored pack " + finding.pack() + " would be refused by the registration impact check ("
                        + finding.code().token() + "): " + finding.detail());
                finding.refusal()
                        .violations()
                        .forEach(violation -> LOG.warn("  " + violation.code() + ": " + violation.detail()));
            });
        } catch (RuntimeException unreadable) {
            LOG.warn("The census of the stored packs could not run: " + unreadable.getMessage());
        }
    }

    /**
     * Tells the activations slice that a registration was stored, under the locks.
     *
     * @param packId the name the pack is stored under
     * @param notices what the registration changed
     */
    public void committed(String packId, Notices notices) {
        activations.registered(packId, notices);
    }

    // ---- the fallback profile (ADR-0068), the role pins (ruling D3) and the profile enhancements (ruling D5) ----

    /**
     * Judges the {@code fallback_profile} and the role pins of every staged pack whose closure holds the written pack,
     * the write in place (ADR-0068 decisions 2 to 4, rulings D3 and D4): the written pack itself, and each pack that
     * builds on it, whose declared fallback or pinned profile may be held in the written pack and so be removed,
     * unlocked, given a role or a performer by this write, and whose closure may gain a differing pin or an enhancement
     * that drops part of the profile it enhances (rulings D5, D7). Each is judged over its own closure, the fallback
     * first, then the pins, then the enhancements ({@link ClosureWalk}), every breach (#556, FR-005). A closure that did
     * not compose or broke a relation has no view, and is not walked.
     */
    private void walkFaults(
            ClosureRoots.Found found,
            Replacement replacement,
            Map<ClosureRoots.Roots, ActivationImpact.ClosureChange> judged,
            StagedPackViews.View staged,
            StagedPackViews.Session session,
            Faults faults) {
        PackIdentifier written = replacement.configured().id();
        ClosureWalk.Profiles profiles =
                (holder, id) -> holder.equals(written) && replacement.pack().isPresent()
                        ? profileIn(replacement.pack().get(), id)
                        : profileIn(storeOf(session).load(holder), id);
        for (ClosureRoots.Roots roots : found.closures()) {
            if (roots.ids().size() != 1 || !judged.containsKey(roots)) {
                continue;
            }
            PackIdentifier declaring = roots.ids().getFirst();
            PackDescriptor descriptor = declaring.equals(written)
                    ? replacement.descriptor()
                    : staged.descriptors().get(declaring);
            ClosureView own = Objects.requireNonNull(judged.get(roots), "a judged closure")
                    .after();
            List<ClosureFault> breaches;
            try {
                ClosureWalk.OwnClosure ownClosure =
                        new ClosureWalk.OwnClosure(declaring, descriptor, own, profiles, session.enhancements());
                breaches = walk.walk(ownClosure).faults();
            } catch (CharterRefusal unreadable) {
                // A read failure of a later closure's walk never displaces a fault already found (D1).
                if (faults.isEmpty()) {
                    throw unreadable;
                }
                return;
            }
            breaches.forEach(breach -> faults.add(withinClosure(roots, breach, staged)));
        }
    }

    /** The store that loads the aggregates; a session that has none cannot judge a fallback, which is a defect, not a refusal. */
    private static ai.speckitty.charter.packs.domain.PackRepository storeOf(StagedPackViews.Session session) {
        return requireStore(session.packs());
    }

    private static ai.speckitty.charter.packs.domain.PackRepository requireStore(
            Optional<ai.speckitty.charter.packs.domain.PackRepository> store) {
        return store.orElseThrow(() -> new IllegalStateException(
                "The staged packs cannot load agent profiles, so a fallback_profile cannot be judged"));
    }

    /** The agent profile of this id an aggregate holds. */
    private static Optional<CharterArtifact> profileIn(Pack pack, String id) {
        return pack.artifacts(ArtifactKind.AGENT_PROFILE).stream()
                .filter(artifact -> artifact.localId().equals(id))
                .<CharterArtifact>map(artifact -> artifact)
                .findFirst();
    }

    // ---- one closure ---------------------------------------------------------------------------------------------

    /**
     * Composes the closure with the write in place and judges its relations, adding every fault to {@code faults}
     * (#556, FR-003, FR-009). A closure that does not compose, or breaks a relation, has no view: its notices and its
     * walk are not judged. Each fault of a compose phase is placed in the closure one by one, never as one refusal
     * inside another (PR #618 review B1). A refusal that is no closure check's (a pack that cannot be read) propagates
     * when no fault was found yet, and otherwise halts the walk of the closures, the faults found standing (D1).
     *
     * @return the closure's view, which carries its floor and locks; empty when it has a fault
     */
    private static Optional<ClosureView> judge(
            ClosureRoots.Roots roots, Views views, StagedPackViews.View staged, Faults faults) {
        OfferingReadService.Judged judged;
        try {
            judged = views.offering().judged(roots.ids());
        } catch (CharterRefusal refused) {
            settle(roots, refused, staged, faults);
            return Optional.empty();
        }
        if (judged.closure().isEmpty()) {
            judged.faults().forEach(fault -> place(roots, fault, staged, faults));
            return Optional.empty();
        }
        OfferingReadService.ComposedClosure composed = judged.closure().get();
        List<ClosureFault> broken = relationFaults(composed);
        if (!broken.isEmpty()) {
            broken.forEach(fault -> faults.add(withinClosure(roots, fault, staged)));
            return Optional.empty();
        }
        EdgeIndex.Indexed edges = EdgeIndex.of(composed.packs(), composed.facts());
        if (edges.index().isEmpty()) {
            edges.faults().forEach(fault -> place(roots, fault, staged, faults));
            return Optional.empty();
        }
        try {
            return Optional.of(views.queries().snapshot(Optional.of(roots.ids())));
        } catch (CharterRefusal refused) {
            settle(roots, refused, staged, faults);
            return Optional.empty();
        }
    }

    /**
     * A closure's refusal placed as one of its faults. A refusal that is none (a read failure) propagates as it is when
     * no fault was found yet, and halts the judging when an earlier closure has one, so that fault stays the answer.
     */
    private static void settle(
            ClosureRoots.Roots roots, CharterRefusal refused, StagedPackViews.View staged, Faults faults) {
        Optional<ClosureFault> lifted = ClosureFault.lift(refused);
        if (lifted.isPresent()) {
            place(roots, lifted.get(), staged, faults);
        } else if (faults.isEmpty()) {
            throw refused;
        } else {
            faults.halt();
        }
    }

    /** The closure before the write; none when it did not exist or cannot be composed. */
    private static Optional<ClosureView> viewBefore(
            ClosureRoots.Roots roots, Views views, Set<PackIdentifier> configured) {
        List<PackIdentifier> existing =
                roots.ids().stream().filter(configured::contains).toList();
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(views.queries().snapshot(Optional.of(existing)));
        } catch (CharterRefusal brokenAlready) {
            return Optional.empty();
        }
    }

    /**
     * A compose fault, placed in its closure as a lone refusal of it always was: the closure named in its detail and
     * its roots among its packs. A fault that carries violations or requiring packs answers alone as its check raised
     * it, and is named in its closure only beside other faults (#556, SC-002, US1.3). A qualified substep candidate
     * the closure does not hold is the author's fault, refused as the load refuses an in-pack one (FR-005).
     */
    private static void place(
            ClosureRoots.Roots roots, ClosureFault fault, StagedPackViews.View staged, Faults faults) {
        String detail = "In the closure rooted at " + roots.label() + ": " + fault.detail();
        if (fault.isUnresolvedDelegation()) {
            faults.add(ClosureFault.of(
                    RefusalCode.VALIDATION_FAILED, detail, fault.packs(), fault.violations(), fault.requiredBy()));
        } else if (!fault.violations().isEmpty() || !fault.requiredBy().isEmpty()) {
            faults.add(fault, alone -> alone.with(detail, withRoots(alone.packs(), roots, staged)));
        } else {
            faults.add(fault.with(
                    detail, fault.packs().isEmpty() ? fault.packs() : withRoots(fault.packs(), roots, staged)));
        }
    }

    /**
     * The faults of the closures a change joins, in the order judged (#556, D1). A fault that answers alone as its
     * check raised it is named in its closure only when it is not alone, so one fault is refused as before and several
     * each say where they were found.
     */
    private static final class Faults {
        private final List<ClosureFault> found = new ArrayList<>();
        private final List<UnaryOperator<ClosureFault>> besideOthers = new ArrayList<>();
        private boolean halted;

        void halt() {
            halted = true;
        }

        boolean halted() {
            return halted;
        }

        void add(ClosureFault fault) {
            add(fault, UnaryOperator.identity());
        }

        void add(ClosureFault fault, UnaryOperator<ClosureFault> placedBesideOthers) {
            found.add(fault);
            besideOthers.add(placedBesideOthers);
        }

        boolean isEmpty() {
            return found.isEmpty();
        }

        ClosureVerdict verdict() {
            if (found.size() < 2) {
                return new ClosureVerdict(found);
            }
            List<ClosureFault> placed = new ArrayList<>();
            for (int each = 0; each < found.size(); each++) {
                placed.add(besideOthers.get(each).apply(found.get(each)));
            }
            return new ClosureVerdict(placed);
        }
    }

    /** A relation fault as the closure names it: its detail in the closure, its packs then the closure's roots. */
    private static ClosureFault withinClosure(
            ClosureRoots.Roots roots, ClosureFault broken, StagedPackViews.View staged) {
        return ClosureFault.of(
                broken.code(),
                "In the closure rooted at " + roots.label() + ": " + broken.detail(),
                withRoots(broken.packs(), roots, staged));
    }

    /** The packs involved, then the closure's roots, each pack once. */
    private static List<RefusalPack> withRoots(
            List<RefusalPack> involved, ClosureRoots.Roots roots, StagedPackViews.View staged) {
        Map<PackIdentifier, RefusalPack> named = new LinkedHashMap<>();
        involved.forEach(pack -> named.putIfAbsent(pack.packId(), pack));
        for (PackIdentifier root : roots.ids()) {
            staged.descriptors()
                    .find(root)
                    .map(descriptor -> new RefusalPack(new PackUlid(descriptor.packId()), root, descriptor.name()))
                    .ifPresent(pack -> named.putIfAbsent(pack.packId(), pack));
        }
        return new ArrayList<>(named.values());
    }

    // ---- the notices ----------------------------------------------------------------------------------------------

    /** What the closures gained and lost, each URN once, in code point order. */
    private static final class ForceDiff {
        private final Set<String> floorAdded = new TreeSet<>();
        private final Set<String> floorRemoved = new TreeSet<>();
        private final Set<String> locksAdded = new TreeSet<>();
        private final Set<String> locksRemoved = new TreeSet<>();

        void add(Force before, Force after) {
            gained(before.floor(), after.floor(), floorAdded);
            gained(after.floor(), before.floor(), floorRemoved);
            gained(before.locks(), after.locks(), locksAdded);
            gained(after.locks(), before.locks(), locksRemoved);
        }

        private static void gained(Set<String> was, Set<String> now, Set<String> into) {
            now.stream().filter(urn -> !was.contains(urn)).forEach(into::add);
        }

        Notices notices(List<String> activationsAffected) {
            return new Notices(
                    List.copyOf(floorAdded),
                    List.copyOf(floorRemoved),
                    List.copyOf(locksAdded),
                    List.copyOf(locksRemoved),
                    activationsAffected);
        }
    }
}
