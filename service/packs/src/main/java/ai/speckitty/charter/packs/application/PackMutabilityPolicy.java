package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ClosureVerdict;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityCheck;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicyRepository;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackOrigins;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The ADR-0012 mutability check (FR-011, FR-022, FR-033; #556 D5): the one {@link MutabilityCheck} the service wires,
 * so every pack write enforces the level through the port. Its no-argument constructor is the check with no policy
 * anywhere, which permits every write. Every write the levels refuse is one {@code governance_policy_violation} fault of the verdict, in the order the
 * writes are given.
 *
 * <p>A write into pack P is judged at the highest level along P's composition closure without P (ADR-0063 decision 3,
 * rule C25): each pack of the closure contributes the policy its descriptor names, scoped to what it holds (below), and
 * {@link PerPackPolicy#levelIn} answers the highest level and the pack that set it, the one function the activation
 * lock query shares. P's own policy never binds P (B8, decision {@code DM-01M6RAA6Y7NVVGKVKQV05GZYES}): it protects
 * P's artifacts from the packs built on P, as a layer override's gate leaves the winning pack out (rule C21). A level
 * the write exceeds is refused with {@link RefusalCode#GOVERNANCE_POLICY_VIOLATION} (409), naming the pack that set it.
 * The level&rarr;operation mapping is documented on {@link MutabilityLevel}.
 *
 * <p>Lock scope (A15, decision {@code DM-01M6RA97220XBR7RR447FE2JXD}, rule C22): a pack's declaration counts only for
 * what the pack or a pack it builds on ({@code depends_on} and {@code parent_pack}, transitively) holds. A pack may lock
 * the artifacts of the packs it builds on, as an enterprise pack enforces a built-in practice, never those of an
 * unrelated pack that shares a closure, and an id no pack of its own closure holds is open for it.
 * {@link #scopedPolicies} is that rule, and {@link ClosureQueries} reads the closure's locks through it too.
 *
 * <p>Cost: the check composes P's closure once per request, for all its writes, in one verified read: {@link
 * OfferingReadService#composedWithFacts(java.util.Collection)} inside a {@link ConsistentRead}, and each member's
 * policy is the {@link ai.speckitty.charter.packs.domain.PackCompositionFacts#policy()} that compose read, so the
 * closure and its policies are one answer from one source, the source the closure snapshot reads too. A pack change
 * during the read refuses the write as {@code stale_input}, never a mixed answer. Registrations are rare, so the read
 * is acceptable; a closure that cannot be composed, or a member whose policy file is missing or invalid, refuses the
 * write as the compose refuses it, since a lock that cannot be read is never taken as open. Each pack's
 * {@code policy.yaml} is read (ADR-0063); no open pack ships one yet, so every target of the open packs is open and
 * their writes keep working (tighten never loosen).
 *
 * <p>It is a Spring-managed edge (it carries the framework annotations), so it lives in the application ring, not in
 * the pure {@code packs.domain} ring where the policy model resides; {@code HexagonalArchitectureTest} gates that split.
 */
@Component
public final class PackMutabilityPolicy implements MutabilityCheck {

    /**
     * One pack of a closure: the identifier it is configured as, the {@code pack_id} its descriptor declares, the packs
     * it builds on and the artifacts it holds.
     *
     * @param configured the configured identifier, the one its policy is read by
     * @param descriptorPackId the descriptor's {@code pack_id}, the key of {@link PerPackPolicy}
     * @param buildsOn the descriptor {@code pack_id}s of the packs it builds on ({@code depends_on}, {@code parent_pack})
     * @param held the artifacts it holds
     */
    record Member(PackIdentifier configured, PackUlid descriptorPackId, List<PackUlid> buildsOn, Set<Urn> held) {

        /**
         * Checks and copies the fields.
         *
         * @param configured the configured identifier
         * @param descriptorPackId the descriptor's {@code pack_id}
         * @param buildsOn the packs it builds on
         * @param held the artifacts it holds
         */
        Member {
            Objects.requireNonNull(configured, "configured");
            Objects.requireNonNull(descriptorPackId, "descriptorPackId");
            buildsOn = List.copyOf(Objects.requireNonNull(buildsOn, "buildsOn"));
            held = Set.copyOf(Objects.requireNonNull(held, "held"));
        }
    }

    /** The composition closure of the pack being written, dependencies first. */
    @FunctionalInterface
    interface Closure {
        /**
         * The closure of a pack.
         *
         * @param pack the pack being written
         * @return its closure's members, in closure order, the pack itself included
         */
        List<Member> of(Pack pack);
    }

    /**
     * The policies that judge a write into a pack: each pack of its closure but the pack itself, by descriptor
     * {@code pack_id} in closure order, each scoped to what it and the packs it builds on hold.
     */
    @FunctionalInterface
    private interface JudgingPolicies {
        Judging of(Pack pack);
    }

    /**
     * What judges a write into a pack: the scoped policies of the packs below it, and what those packs hold. An id a
     * pack below holds is already someone's artifact, so a write of it into the pack is a replace of that artifact (C21,
     * decision 01M6VEDYX8TQ4AKCP7C19WAP75), never a registration.
     */
    private record Judging(Map<PackUlid, MutabilityPolicySource> policies, Set<Urn> held) {}

    /** One closure pack as the lock scope reads it. */
    private record Scope(PackUlid packId, List<PackUlid> buildsOn, Set<Urn> held, MutabilityPolicySource policy) {}

    private static final String STALE = "The packs changed while the write's policy was read; send the write again.";

    /**
     * The key the single-source seam files the levels it plants under: the packs every written pack builds on, which
     * the seam does not name, so it is the nil ULID, a {@code pack_id} no descriptor declares.
     */
    private static final PackUlid BUILT_ON = new PackUlid("00000000000000000000000000");

    private final JudgingPolicies judgingPolicies;
    private final JudgingPolicies stagedJudgingPolicies;

    /**
     * The check Spring wires: the closure is the compose rooted at the pack being written, and each member's policy is
     * the one that compose read, all in one verified read.
     *
     * @param offerings the compose
     * @param catalog the configured packs, to root the compose at the configured pack being written
     * @param descriptors the descriptors, to find the configured pack a write addresses by its {@code pack_id}
     * @param read the verifier that digests the inputs before and after the read
     */
    @Autowired
    public PackMutabilityPolicy(
            OfferingReadService offerings,
            PackCatalogRepository catalog,
            PackDescriptorRepository descriptors,
            ConsistentRead read) {
        this(liveJudging(offerings, catalog, read), stagedJudging(offerings, catalog, descriptors, read));
    }

    /**
     * A check over a given closure and policy port: the seam a test uses to plant a dependency's lock. Production reads
     * no policy through the port; it stays for this seam, and {@code DomainRepositoryService} still implements it.
     *
     * @param policies the port that reads each member's policy
     * @param closure the closure of the pack being written
     */
    PackMutabilityPolicy(MutabilityPolicyRepository policies, Closure closure) {
        this(policiesOver(Objects.requireNonNull(policies, "policies"), Objects.requireNonNull(closure, "closure")));
    }

    private PackMutabilityPolicy(JudgingPolicies judgingPolicies) {
        this(judgingPolicies, judgingPolicies);
    }

    private PackMutabilityPolicy(JudgingPolicies judgingPolicies, JudgingPolicies stagedJudgingPolicies) {
        this.judgingPolicies = judgingPolicies;
        this.stagedJudgingPolicies = stagedJudgingPolicies;
    }

    /**
     * A check that enforces the given levels as the packs every written pack builds on declare them, over every
     * target: the seam a test uses to plant a level without a closure. Since a pack's own policy never binds it (B8),
     * the levels are those of the packs it builds on, not its own.
     *
     * @param source the levels this check enforces
     */
    public PackMutabilityPolicy(MutabilityPolicySource source) {
        this(pack -> new Judging(Map.of(BUILT_ON, Objects.requireNonNull(source, "source")), Set.of()));
    }

    /** A check with no policy anywhere: every target is open, as before any pack shipped a policy file. */
    public PackMutabilityPolicy() {
        this(MutabilityPolicySource.empty());
    }

    /**
     * Judges the writes of one request, over one read of the pack's closure: a stored pack's live closure, the pack
     * itself left out (B8), or, for a whole-pack registration, the closure the staged pack's descriptor declares, read
     * from the stored packs its {@code depends_on} and {@code parent_pack} name (ADR-0065 D5). The staged pack need not
     * be stored.
     *
     * @param writes the writes
     * @return one {@link RefusalCode#GOVERNANCE_POLICY_VIOLATION} (409) fault per write whose target's level does not
     *     permit it, naming the pack that set the level, in the order given; passing when there is none
     * @throws CharterRefusal {@code PACK_NOT_FOUND} when no pack is configured as the stored pack written;
     *     {@code PACK_UNREADABLE} or a composition refusal when the closure cannot be judged
     */
    @Override
    public ClosureVerdict judge(Writes writes) {
        Objects.requireNonNull(writes, "writes");
        if (writes.writes().isEmpty()) {
            return ClosureVerdict.PASSING;
        }
        Judging judging = switch (writes) {
            case Live live -> judgingPolicies.of(live.pack());
            case Staged staged -> stagedJudgingPolicies.of(staged.pack());
        };
        List<ClosureFault> faults = new ArrayList<>();
        for (Write write : writes.writes()) {
            refused(writes.pack(), judging, write.operation(), write.target()).ifPresent(faults::add);
        }
        return new ClosureVerdict(faults);
    }

    private static Optional<ClosureFault> refused(Pack pack, Judging judging, Operation written, Urn target) {
        Map<PackUlid, MutabilityPolicySource> byPack = judging.policies();
        Operation operation =
                written == Operation.REGISTER && judging.held().contains(target) ? Operation.REPLACE : written;
        PerPackPolicy.Level level = new PerPackPolicy(byPack).levelIn(List.copyOf(byPack.keySet()), target);
        if (level.level().permits(operation)) {
            return Optional.empty();
        }
        return Optional.of(ClosureFault.of(
                PackOrigins.of(pack.id(), target),
                RefusalCode.GOVERNANCE_POLICY_VIOLATION,
                "Operation " + operation + " on " + target + " is refused: pack "
                        + level.setBy().map(PackUlid::value).orElse(pack.id().value()) + " of the composition of "
                        + pack.id().value()
                        + " declares it " + level.level().token()
                        + ", which does not permit this write (ADR-0012, ADR-0063).",
                List.of()));
    }

    /**
     * Each closure pack's policy scoped to what the pack or a pack it builds on holds (A15, rule C22): the policy's
     * level for such an artifact, and {@link MutabilityLevel#OPEN} for every other target. The read side's locks and the
     * mutability check both read a closure's policies through it.
     *
     * @param closure the closure's packs, in closure order
     * @param policyOf each pack's declared policy
     * @return each pack's scoped policy, by descriptor {@code pack_id}, in closure order
     */
    public static Map<PackUlid, MutabilityPolicySource> scopedPolicies(
            List<PackUnderResolution> closure, Function<PackUnderResolution, MutabilityPolicySource> policyOf) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(policyOf, "policyOf");
        return scoped(closure.stream()
                .map(member -> new Scope(member.descriptorId(), buildsOn(member), held(member), policyOf.apply(member)))
                .toList());
    }

    /** The policies judging a write into {@code root}: the closure's scoped policies, the root's own left out (B8). */
    private static Judging judgingOf(OfferingReadService.ComposedClosure composed, PackIdentifier root) {
        Map<PackUlid, MutabilityPolicySource> judging = scopedPolicies(
                composed.packs(), member -> composed.factsOf(member).policy());
        composed.packs().stream()
                .filter(member -> member.configured().id().equals(root))
                .forEach(member -> judging.remove(member.descriptorId()));
        Set<Urn> held = new HashSet<>();
        composed.packs().stream()
                .filter(member -> !member.configured().id().equals(root))
                .forEach(member -> held.addAll(held(member)));
        return new Judging(judging, held);
    }

    /** The policies judging a write into a stored pack: its live closure, composed in one verified read. */
    private static JudgingPolicies liveJudging(
            OfferingReadService offerings, PackCatalogRepository catalog, ConsistentRead read) {
        return pack -> read.read(STALE, () -> {
                    PackIdentifier root = rootOf(catalog, pack);
                    return judgingOf(offerings.composedWithFacts(Set.of(root)), root);
                })
                .value();
    }

    /** The policies judging a write of a staged pack: the closure its descriptor declares, in one verified read. */
    private static JudgingPolicies stagedJudging(
            OfferingReadService offerings,
            PackCatalogRepository catalog,
            PackDescriptorRepository descriptors,
            ConsistentRead read) {
        return staged -> read.read(STALE, () -> ancestorsJudging(offerings, catalog, descriptors, staged))
                .value();
    }

    /**
     * The policies judging a write of a staged pack: the closure of the packs it builds on, composed from the stored
     * packs its descriptor names by {@code pack_id} ({@code parent_pack} and {@code depends_on}), each scoped as
     * {@link #scopedPolicies} scopes it. The staged pack is not in it, so its own policy cannot bind it. A pack it names
     * that is not stored has nothing to judge here: the impact check refuses the closure that cannot be composed.
     */
    private static Judging ancestorsJudging(
            OfferingReadService offerings,
            PackCatalogRepository catalog,
            PackDescriptorRepository descriptors,
            Pack staged) {
        Set<String> named =
                new HashSet<>(staged.descriptor().parentPack().stream().toList());
        staged.descriptor().dependencies().forEach(dependency -> named.add(dependency.packId()));
        List<PackIdentifier> roots = catalog.configured().stream()
                .map(ConfiguredPack::id)
                .filter(candidate -> named.stream().anyMatch(packId -> declares(descriptors, candidate, packId)))
                .toList();
        if (roots.isEmpty()) {
            return new Judging(new LinkedHashMap<>(), Set.of());
        }
        OfferingReadService.ComposedClosure composed = offerings.composedWithFacts(roots);
        Set<Urn> held = new HashSet<>();
        composed.packs().forEach(member -> held.addAll(held(member)));
        return new Judging(
                scopedPolicies(
                        composed.packs(), member -> composed.factsOf(member).policy()),
                held);
    }

    /** The test seam's policies: each member's policy read through the port, scoped, the written pack's left out. */
    private static JudgingPolicies policiesOver(MutabilityPolicyRepository policies, Closure closure) {
        return pack -> {
            List<Member> members = closure.of(pack);
            Map<PackUlid, MutabilityPolicySource> judging = scoped(members.stream()
                    .map(member -> new Scope(
                            member.descriptorPackId(),
                            member.buildsOn(),
                            member.held(),
                            policies.policyOf(member.configured())))
                    .toList());
            members.stream()
                    .filter(member -> member.configured().equals(pack.id()))
                    .forEach(member -> judging.remove(member.descriptorPackId()));
            Set<Urn> held = new HashSet<>();
            members.stream()
                    .filter(member -> !member.configured().equals(pack.id()))
                    .forEach(member -> held.addAll(member.held()));
            return new Judging(judging, held);
        };
    }

    /** Each pack's policy restricted to what it and the packs it builds on hold, in closure order, each id once. */
    private static Map<PackUlid, MutabilityPolicySource> scoped(List<Scope> closure) {
        Map<PackUlid, Scope> byId = new LinkedHashMap<>();
        closure.forEach(scope -> byId.putIfAbsent(scope.packId(), scope));
        Map<PackUlid, MutabilityPolicySource> scoped = new LinkedHashMap<>();
        byId.values().forEach(scope -> scoped.put(scope.packId(), restricted(scope.policy(), heldAlong(scope, byId))));
        return scoped;
    }

    /** What a pack and every pack it builds on, transitively, hold; a pack outside the closure holds nothing here. */
    private static Set<Urn> heldAlong(Scope from, Map<PackUlid, Scope> byId) {
        Set<Urn> held = new HashSet<>();
        Set<PackUlid> seen = new HashSet<>();
        Deque<Scope> pending = new ArrayDeque<>(List.of(from));
        while (!pending.isEmpty()) {
            Scope next = pending.pop();
            if (seen.add(next.packId())) {
                held.addAll(next.held());
                next.buildsOn().stream().map(byId::get).filter(Objects::nonNull).forEach(pending::push);
            }
        }
        return held;
    }

    /** The policy's level for each held target that is above open, as per-target entries; every other target open. */
    private static MutabilityPolicySource restricted(MutabilityPolicySource policy, Set<Urn> held) {
        if (policy.equals(MutabilityPolicySource.empty())) {
            return policy;
        }
        MutabilityPolicySource.Builder scoped = MutabilityPolicySource.builder();
        held.forEach(urn -> {
            MutabilityLevel level = policy.levelOf(urn);
            if (level != MutabilityLevel.OPEN) {
                scoped.entry(urn, level);
            }
        });
        return scoped.build();
    }

    /** The descriptor {@code pack_id}s a pack builds on: its {@code depends_on} edges and its {@code parent_pack}. */
    private static List<PackUlid> buildsOn(PackUnderResolution member) {
        return Stream.concat(
                        member.descriptor().dependencies().stream().map(PackDependency::packId),
                        member.descriptor().parentPack().stream())
                .map(PackUlid::new)
                .toList();
    }

    /** The artifacts a pack holds, as URNs. */
    private static Set<Urn> held(PackUnderResolution member) {
        Set<Urn> held = new HashSet<>();
        member.referableArtifacts().forEach(referable -> Urn.parse(referable).ifPresent(held::add));
        return held;
    }

    /**
     * The configured pack a write addresses: the one configured as the pack's identifier, its registered id (#583,
     * D3). A pack that is not configured is refused {@code pack_not_found}, as on every other read.
     */
    private static PackIdentifier rootOf(PackCatalogRepository catalog, Pack pack) {
        if (catalog.configured().stream().anyMatch(candidate -> candidate.id().equals(pack.id()))) {
            return pack.id();
        }
        throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.id().value() + " was not found.");
    }

    /** Whether the configured pack's descriptor declares {@code packId}; an unreadable one declares nothing. */
    private static boolean declares(PackDescriptorRepository descriptors, PackIdentifier candidate, String packId) {
        try {
            return descriptors
                    .find(candidate)
                    .filter(found -> found.packId().equals(packId))
                    .isPresent();
        } catch (CharterRefusal unreadable) {
            return false;
        }
    }
}
