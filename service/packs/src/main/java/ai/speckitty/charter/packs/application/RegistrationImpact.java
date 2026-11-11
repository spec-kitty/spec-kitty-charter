package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
 * <p>Read side: stored packs registered before a rule are not re-judged here, only when a write joins their closure.
 */
@Component
public final class RegistrationImpact {

    private static final LogAccessor LOG = new LogAccessor(RegistrationImpact.class);

    /**
     * The floor and lock changes of a registration (FR-027, B4), over every closure the write joined: the must-have
     * items the floor gained and lost, and the artifacts that became locked and stopped being locked. A closure that
     * could not be composed before the write has no before, so everything of it counts as gained.
     *
     * @param floorAdded must-have items the floor gained, as URNs in code point order
     * @param floorRemoved must-have items the floor lost
     * @param locksAdded artifacts locked now and not before
     * @param locksRemoved artifacts locked before and not now
     * @param activationsAffected the stored activations whose in-force set the write changes, sorted
     */
    public record Notices(
            List<String> floorAdded,
            List<String> floorRemoved,
            List<String> locksAdded,
            List<String> locksRemoved,
            List<String> activationsAffected) {

        /** No change. */
        public static final Notices NONE = new Notices(List.of(), List.of(), List.of(), List.of());

        /**
         * Copies the lists.
         *
         * @param floorAdded must-have items gained
         * @param floorRemoved must-have items lost
         * @param locksAdded locks gained
         * @param locksRemoved locks lost
         * @param activationsAffected the activations whose in-force set changes
         */
        public Notices {
            floorAdded = List.copyOf(floorAdded);
            floorRemoved = List.copyOf(floorRemoved);
            locksAdded = List.copyOf(locksAdded);
            locksRemoved = List.copyOf(locksRemoved);
            activationsAffected = List.copyOf(activationsAffected);
        }

        /**
         * Notices that name no activation.
         *
         * @param floorAdded must-have items gained
         * @param floorRemoved must-have items lost
         * @param locksAdded locks gained
         * @param locksRemoved locks lost
         */
        public Notices(
                List<String> floorAdded,
                List<String> floorRemoved,
                List<String> locksAdded,
                List<String> locksRemoved) {
            this(floorAdded, floorRemoved, locksAdded, locksRemoved, List.of());
        }
    }

    /**
     * The state a pack will have once the write is stored.
     *
     * @param configured how the pack is configured: the name it is stored under and its level
     * @param descriptor its descriptor
     * @param facts what the compose reads of it
     * @param pack the staged aggregate, when the write sends a whole pack (ADR-0068 reads the fallback profile here)
     */
    public record Replacement(
            ConfiguredPack configured, PackDescriptor descriptor, PackCompositionFacts facts, Optional<Pack> pack) {

        /**
         * Checks the fields.
         *
         * @param configured how the pack is configured
         * @param descriptor its descriptor
         * @param facts what the compose reads of it
         * @param pack the staged aggregate, when the write sends a whole pack; a rule that reads an entity of the
         *     written pack (ADR-0068) reads it here
         */
        public Replacement {
            Objects.requireNonNull(configured, "configured");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(facts, "facts");
            Objects.requireNonNull(pack, "pack");
        }

        /**
         * A replacement whose entities are read from the store.
         *
         * @param configured how the pack is configured
         * @param descriptor its descriptor
         * @param facts what the compose reads of it
         */
        public Replacement(ConfiguredPack configured, PackDescriptor descriptor, PackCompositionFacts facts) {
            this(configured, descriptor, facts, Optional.empty());
        }
    }

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

    /**
     * The check over the packs and the activations.
     *
     * @param views the stored packs, with a written pack laid over them
     * @param activations the stored activations' root sets
     */
    public RegistrationImpact(StagedPackViews views, ActivationImpact activations) {
        this.views = Objects.requireNonNull(views, "views");
        this.activations = Objects.requireNonNull(activations, "activations");
    }

    /**
     * The check as Spring wires it.
     *
     * @param views the stored packs, with a written pack laid over them
     * @param activations the activations slice's port, when the deployment has that slice
     */
    @Autowired
    public RegistrationImpact(StagedPackViews views, ObjectProvider<ActivationImpact> activations) {
        this(views, activations.getIfAvailable(() -> ActivationImpact.NONE));
    }

    /**
     * Judges every closure the written pack joins, under the locks.
     *
     * @param replacement the pack as it will be stored
     * @return the floor and lock changes over those closures
     * @throws CharterRefusal a composition code, or {@code override_not_permitted}, {@code enhances_cycle},
     *     {@code conflicting_relations}, {@code self_override} or {@code override_cycle} (422), naming the closure's
     *     roots and the packs involved, or {@code pack_unreadable} when a pack of a closure cannot be read
     */
    public Notices check(Replacement replacement) {
        Objects.requireNonNull(replacement, "replacement");
        return check(views.session(), replacement, true);
    }

    /**
     * Judges every closure a stored pack joins once one write to it, an artifact or a relationship or a removal, is
     * stored (operator ruling DM-01M6VG6GC40AKYYFRR35SV8HVW): the pack is read from the same session the closures are
     * composed through, so the check lists the configured locations and reads the pack once, and the write is laid over
     * the facts it read. The descriptor is the stored one, which a per-artifact write does not change.
     *
     * @param pack the pack written to, by the name it is configured under or by its declared {@code pack_id}
     * @param write what the pack's facts become once the write is stored
     * @return the floor and lock changes over those closures; the answer of such a write declares no notices, so the
     *     activations it affects are not asked for
     * @throws CharterRefusal {@code pack_not_found} when no configured pack has this identifier, or as
     *     {@link #check(Replacement)} refuses
     */
    public Notices check(PackIdentifier pack, UnaryOperator<PackCompositionFacts> write) {
        return check(pack, write, Optional.empty());
    }

    /**
     * As {@link #check(PackIdentifier, UnaryOperator)}, with the pack as it will be stored, so a rule that reads an
     * entity of the written pack (ADR-0068) reads the written version and not the stored one.
     *
     * @param pack the pack written to
     * @param write what the pack's facts become once the write is stored
     * @param staged the pack aggregate once the write is stored, when the write has one
     * @return the floor and lock changes over those closures
     * @throws CharterRefusal as {@link #check(PackIdentifier, UnaryOperator)}
     */
    public Notices check(PackIdentifier pack, UnaryOperator<PackCompositionFacts> write, Optional<Pack> staged) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(write, "write");
        Objects.requireNonNull(staged, "staged");
        StagedPackViews.Session session = views.session();
        StagedPackViews.View stored = session.stored();
        ConfiguredPack configured = configuredAs(stored, pack);
        Replacement written = new Replacement(
                configured,
                stored.descriptors().get(configured.id()),
                write.apply(stored.artifacts().compositionFacts(configured.id())),
                staged);
        return check(session, written, false);
    }

    /** The configured pack an identifier names: by configured name first, otherwise by the {@code pack_id} it declares. */
    private static ConfiguredPack configuredAs(StagedPackViews.View stored, PackIdentifier pack) {
        List<ConfiguredPack> configured = stored.catalog().configured();
        Optional<ConfiguredPack> named = configured.stream()
                .filter(candidate -> candidate.id().equals(pack))
                .findFirst();
        if (named.isPresent()) {
            return named.get();
        }
        for (ConfiguredPack candidate : configured) {
            if (declares(stored, candidate, pack)) {
                return candidate;
            }
        }
        throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.");
    }

    private static boolean declares(StagedPackViews.View stored, ConfiguredPack candidate, PackIdentifier pack) {
        try {
            return stored.descriptors()
                    .find(candidate.id())
                    .filter(descriptor -> descriptor.packId().equals(pack.value()))
                    .isPresent();
        } catch (CharterRefusal unreadable) {
            return false;
        }
    }

    private Notices check(StagedPackViews.Session session, Replacement replacement, boolean namingActivations) {
        StagedPackViews.View stored = session.stored();
        StagedPackViews.View staged = session.with(replacement);
        ClosureRoots.Found found =
                ClosureRoots.of(staged, replacement.descriptor().packId(), activations.activationRoots());
        Views after = Views.over(staged);
        Views before = Views.over(stored);
        Set<PackIdentifier> configuredBefore = new HashSet<>();
        stored.catalog().configured().forEach(pack -> configuredBefore.add(pack.id()));
        ForceDiff diff = new ForceDiff();
        Map<ClosureRoots.Roots, ActivationImpact.ClosureChange> changes = new LinkedHashMap<>();
        for (ClosureRoots.Roots roots : found.closures()) {
            ClosureView now = judge(roots, after, staged);
            Optional<ClosureView> was = viewBefore(roots, before, configuredBefore);
            diff.add(was.map(Force::of).orElse(Force.NONE), Force.of(now));
            changes.put(roots, new ActivationImpact.ClosureChange(was, now));
        }
        judgeFallbacks(found, replacement, after, staged, session);
        List<String> affected = namingActivations
                ? activations.activationsAffected(
                        roots -> found.holding().apply(roots).map(changes::get))
                : List.of();
        return diff.notices(affected);
    }

    /**
     * A stored pack the impact check would refuse now.
     *
     * @param pack the configured name of the pack whose closure is refused
     * @param code the refusal code
     * @param detail what is wrong, as the refusal would say it
     */
    public record Finding(String pack, RefusalCode code, String detail) {

        /**
         * Checks the fields.
         *
         * @param pack the configured name of the pack
         * @param code the refusal code
         * @param detail what is wrong
         */
        public Finding {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /**
     * The census (IC-15, read side): the closure of every stored pack, composed and judged as a registration judges a
     * closure it joins. Packs registered before a rule are not re-judged on a read, which answers {@code pack_unreadable}
     * only as it always has; this says which of them a write into their closure would now refuse. It reads and writes
     * nothing but the packs, once each, and takes no lock.
     *
     * @return one finding per stored pack whose closure would be refused, in catalog order
     */
    public List<Finding> census() {
        StagedPackViews.Session session = views.session();
        StagedPackViews.View stored = session.stored();
        Views composing = Views.over(stored);
        List<Finding> findings = new ArrayList<>();
        for (ConfiguredPack pack : stored.catalog().configured()) {
            censusOf(pack, composing, stored, session.packs()).ifPresent(findings::add);
        }
        return findings;
    }

    private static Optional<Finding> censusOf(
            ConfiguredPack pack,
            Views composing,
            StagedPackViews.View stored,
            Optional<ai.speckitty.charter.packs.domain.PackRepository> packsOfCensus) {
        String name = pack.id().value();
        try {
            Optional<Finding> relations = ImpactRelations.check(
                            composing.offering().composedWithFacts(List.of(pack.id())))
                    .map(broken ->
                            new Finding(name, broken.code(), broken.detail() + " (packs " + packsOf(broken) + ")"));
            if (relations.isPresent()) {
                return relations;
            }
            FallbackProfileRule.Profiles profiles =
                    (holder, id) -> profileIn(requireStore(packsOfCensus).load(holder), id);
            return fallbackBreach(pack.id(), stored.descriptors().get(pack.id()), composing, profiles)
                    .map(breach -> new Finding(name, breach.code(), breach.detail()));
        } catch (CharterRefusal refused) {
            return Optional.of(new Finding(name, refused.code(), String.valueOf(refused.getMessage())));
        }
    }

    private static String packsOf(ImpactRelations.Violation broken) {
        return String.join(
                ", ",
                broken.packs().stream()
                        .map(pack -> pack.configured().id().value())
                        .toList());
    }

    /**
     * Logs the census at startup, one warning per finding (T075). A census that cannot run is itself a warning, never a
     * failure to start.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logCensus() {
        try {
            census().forEach(finding -> LOG.warn("The stored pack " + finding.pack()
                    + " would be refused by the registration impact check ("
                    + finding.code().token() + "): "
                    + finding.detail()));
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

    // ---- the fallback profile (ADR-0068) --------------------------------------------------------------------------

    /**
     * Judges the {@code fallback_profile} of every staged pack whose closure holds the written pack, the write in place
     * (ADR-0068 decisions 2 to 4): the written pack itself, and each pack that builds on it, whose declared fallback may
     * be held in the written pack and so be removed, unlocked or given a role by this write. A pack that declares none is
     * not judged.
     */
    private static void judgeFallbacks(
            ClosureRoots.Found found,
            Replacement replacement,
            Views views,
            StagedPackViews.View staged,
            StagedPackViews.Session session) {
        PackIdentifier written = replacement.configured().id();
        FallbackProfileRule.Profiles profiles =
                (holder, id) -> holder.equals(written) && replacement.pack().isPresent()
                        ? profileIn(replacement.pack().get(), id)
                        : profileIn(storeOf(session).load(holder), id);
        for (ClosureRoots.Roots roots : found.closures()) {
            if (roots.ids().size() != 1) {
                continue;
            }
            PackIdentifier declaring = roots.ids().getFirst();
            PackDescriptor descriptor = declaring.equals(written)
                    ? replacement.descriptor()
                    : staged.descriptors().get(declaring);
            Optional<FallbackProfileRule.Breach> breach = fallbackBreach(declaring, descriptor, views, profiles);
            if (breach.isPresent()) {
                throw new CharterRefusal(
                        breach.get().code(),
                        "In the closure rooted at " + roots.label() + ": "
                                + breach.get().detail(),
                        withRoots(List.of(), roots, staged));
            }
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

    private static Optional<FallbackProfileRule.Breach> fallbackBreach(
            PackIdentifier pack, PackDescriptor descriptor, Views views, FallbackProfileRule.Profiles profiles) {
        Optional<String> named = descriptor.fallbackProfile();
        if (named.isEmpty()) {
            return Optional.empty();
        }
        ClosureView own = views.queries().snapshot(Optional.of(List.of(pack)));
        return FallbackProfileRule.check(pack.value(), named.get(), own, profiles);
    }

    /** The agent profile of this id an aggregate holds. */
    private static Optional<CharterArtifact> profileIn(Pack pack, String id) {
        return pack.artifacts(ArtifactKind.AGENT_PROFILE).stream()
                .filter(artifact -> artifact.localId().equals(id))
                .<CharterArtifact>map(artifact -> artifact)
                .findFirst();
    }

    // ---- one closure ---------------------------------------------------------------------------------------------

    /** Composes the closure with the write in place and judges its relations; its view carries the floor and locks. */
    private ClosureView judge(ClosureRoots.Roots roots, Views views, StagedPackViews.View staged) {
        OfferingReadService.ComposedClosure composed;
        try {
            composed = views.offering().composedWithFacts(roots.ids());
        } catch (CharterRefusal refused) {
            throw inClosure(roots, refused, staged);
        }
        Optional<ImpactRelations.Violation> broken = ImpactRelations.check(composed);
        if (broken.isPresent()) {
            throw refusal(roots, broken.get(), staged);
        }
        try {
            return views.queries().snapshot(Optional.of(roots.ids()));
        } catch (CharterRefusal refused) {
            throw inClosure(roots, refused, staged);
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

    /** A compose refusal, as the registration's refusal: the same code, the closure named, its roots among the packs. */
    private static CharterRefusal inClosure(
            ClosureRoots.Roots roots, CharterRefusal refused, StagedPackViews.View staged) {
        if (!refused.violations().isEmpty() || !refused.requiredBy().isEmpty()) {
            return refused;
        }
        String detail = "In the closure rooted at " + roots.label() + ": " + refused.getMessage();
        if (refused.packs().isEmpty()) {
            return new CharterRefusal(refused.code(), detail, refused);
        }
        return new CharterRefusal(refused.code(), detail, withRoots(refused.packs(), roots, staged));
    }

    private static CharterRefusal refusal(
            ClosureRoots.Roots roots, ImpactRelations.Violation broken, StagedPackViews.View staged) {
        List<RefusalPack> involved =
                broken.packs().stream().map(RegistrationImpact::refusalPack).toList();
        return new CharterRefusal(
                broken.code(),
                "In the closure rooted at " + roots.label() + ": " + broken.detail(),
                withRoots(involved, roots, staged));
    }

    /** The packs involved, then the closure's roots, each pack once. */
    private static List<RefusalPack> withRoots(
            List<RefusalPack> involved, ClosureRoots.Roots roots, StagedPackViews.View staged) {
        Map<String, RefusalPack> named = new LinkedHashMap<>();
        involved.forEach(pack -> named.putIfAbsent(pack.packId(), pack));
        for (PackIdentifier root : roots.ids()) {
            staged.descriptors()
                    .find(root)
                    .map(descriptor -> new RefusalPack(descriptor.packId(), root.value(), descriptor.name()))
                    .ifPresent(pack -> named.putIfAbsent(pack.packId(), pack));
        }
        return new ArrayList<>(named.values());
    }

    private static RefusalPack refusalPack(PackUnderResolution pack) {
        return new RefusalPack(
                pack.descriptor().packId(),
                pack.configured().id().value(),
                pack.descriptor().name());
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
