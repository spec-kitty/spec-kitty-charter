package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicyRepository;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The real ADR-0012 mutability write gate (FR-011, FR-022, FR-033): the {@code @Primary} {@link WritePolicyGate} that
 * supersedes the {@link PermissiveWritePolicyGate} default, so every registration service enforces the level through
 * the WP04 port without any service, controller or commit-store edit.
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
 * <p>Cost: the gate composes P's closure once per registration, in one verified read: {@link
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
@Primary
public final class PackMutabilityPolicy implements WritePolicyGate {

    /**
     * One pack of a closure: the identifier it is configured as, the {@code pack_id} its descriptor declares, the packs
     * it builds on and the artifacts it holds.
     *
     * @param configured the configured identifier, the one its policy is read by
     * @param descriptorPackId the descriptor's {@code pack_id}, the key of {@link PerPackPolicy}
     * @param buildsOn the descriptor {@code pack_id}s of the packs it builds on ({@code depends_on}, {@code parent_pack})
     * @param held the artifacts it holds
     */
    record Member(PackIdentifier configured, String descriptorPackId, List<String> buildsOn, Set<Urn> held) {

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
    private record Judging(Map<String, MutabilityPolicySource> policies, Set<Urn> held) {}

    /** One closure pack as the lock scope reads it. */
    private record Scope(String packId, List<String> buildsOn, Set<Urn> held, MutabilityPolicySource policy) {}

    private static final String STALE = "The packs changed while the write's policy was read; send the write again.";

    /** The name the single-source seam gives the packs whose levels it plants. */
    private static final String BUILT_ON = "built-on";

    private final JudgingPolicies judgingPolicies;
    private final JudgingPolicies stagedJudgingPolicies;

    /**
     * The gate Spring wires: the closure is the compose rooted at the pack being written, and each member's policy is
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
        this(liveJudging(offerings, catalog, descriptors, read), stagedJudging(offerings, catalog, descriptors, read));
    }

    /**
     * A gate over a given closure and policy port: the seam a test uses to plant a dependency's lock. Production reads
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
     * A gate that enforces the given levels as the packs every written pack builds on declare them, over every
     * target: the seam a test uses to plant a level without a closure. Since a pack's own policy never binds it (B8),
     * the levels are those of the packs it builds on, not its own.
     *
     * @param source the levels this gate enforces
     */
    public PackMutabilityPolicy(MutabilityPolicySource source) {
        this(pack -> new Judging(Map.of(BUILT_ON, Objects.requireNonNull(source, "source")), Set.of()));
    }

    /** A gate with no policy anywhere: every target is open, as before any pack shipped a policy file. */
    public PackMutabilityPolicy() {
        this(MutabilityPolicySource.empty());
    }

    /**
     * Authorizes a write, or refuses it when it exceeds the target's level along the pack's closure, the pack itself
     * left out.
     *
     * @param pack the pack being mutated
     * @param operation the write about to be performed
     * @param target the artifact or relationship endpoint the write concerns
     * @throws CharterRefusal {@link RefusalCode#GOVERNANCE_POLICY_VIOLATION} (409) when the target's level does not
     *     permit the operation; {@code PACK_NOT_FOUND} when no pack is configured as the pack written;
     *     {@code PACK_UNREADABLE} or a composition refusal when the closure cannot be judged
     */
    @Override
    public void authorize(Pack pack, Operation operation, Urn target) {
        require(pack, judgingPolicies.of(pack), operation, target);
    }

    /**
     * Authorizes the writes of a whole-pack registration over the closure the staged pack will have (ADR-0065 D5): the
     * policies are those of the packs the staged pack builds on, its own left out as ever (B8), read from the stored
     * packs its {@code depends_on} and {@code parent_pack} name. The staged pack need not be stored, and the closure
     * that is read is the one its descriptor declares, not the one it had. The closure is read once for all the writes.
     *
     * @param staged the staged pack
     * @param writes the diff of the registration
     * @throws CharterRefusal {@link RefusalCode#GOVERNANCE_POLICY_VIOLATION} (409) for the first write whose target's
     *     level does not permit it; {@code PACK_UNREADABLE} or a composition refusal when the closure cannot be judged
     */
    @Override
    public void authorizeStaged(Pack staged, List<Write> writes) {
        Objects.requireNonNull(writes, "writes");
        if (writes.isEmpty()) {
            return;
        }
        Judging judging = stagedJudgingPolicies.of(staged);
        for (Write write : writes) {
            require(staged, judging, write.operation(), write.target());
        }
    }

    private static void require(Pack pack, Judging judging, Operation written, Urn target) {
        Map<String, MutabilityPolicySource> byPack = judging.policies();
        Operation operation =
                written == Operation.REGISTER && judging.held().contains(target) ? Operation.REPLACE : written;
        PerPackPolicy.Level level = new PerPackPolicy(byPack).levelIn(List.copyOf(byPack.keySet()), target);
        if (!level.level().permits(operation)) {
            throw new CharterRefusal(
                    RefusalCode.GOVERNANCE_POLICY_VIOLATION,
                    "Operation " + operation + " on " + target + " is refused: pack "
                            + level.setBy().orElse(pack.id().value()) + " of the composition of "
                            + pack.id().value()
                            + " declares it " + level.level().token()
                            + ", which does not permit this write (ADR-0012, ADR-0063).");
        }
    }

    /**
     * Each closure pack's policy scoped to what the pack or a pack it builds on holds (A15, rule C22): the policy's
     * level for such an artifact, and {@link MutabilityLevel#OPEN} for every other target. The read side's locks and the
     * write gate both read a closure's policies through it.
     *
     * @param closure the closure's packs, in closure order
     * @param policyOf each pack's declared policy
     * @return each pack's scoped policy, by descriptor {@code pack_id}, in closure order
     */
    public static Map<String, MutabilityPolicySource> scopedPolicies(
            List<PackUnderResolution> closure, Function<PackUnderResolution, MutabilityPolicySource> policyOf) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(policyOf, "policyOf");
        return scoped(closure.stream()
                .map(member ->
                        new Scope(member.descriptor().packId(), buildsOn(member), held(member), policyOf.apply(member)))
                .toList());
    }

    /** The policies judging a write into {@code root}: the closure's scoped policies, the root's own left out (B8). */
    private static Judging judgingOf(OfferingReadService.ComposedClosure composed, PackIdentifier root) {
        Map<String, MutabilityPolicySource> judging = scopedPolicies(
                composed.packs(), member -> composed.factsOf(member).policy());
        composed.packs().stream()
                .filter(member -> member.configured().id().equals(root))
                .forEach(member -> judging.remove(member.descriptor().packId()));
        Set<Urn> held = new HashSet<>();
        composed.packs().stream()
                .filter(member -> !member.configured().id().equals(root))
                .forEach(member -> held.addAll(held(member)));
        return new Judging(judging, held);
    }

    /** The policies judging a write into a stored pack: its live closure, composed in one verified read. */
    private static JudgingPolicies liveJudging(
            OfferingReadService offerings,
            PackCatalogRepository catalog,
            PackDescriptorRepository descriptors,
            ConsistentRead read) {
        return pack -> read.read(STALE, () -> {
                    PackIdentifier root = rootOf(catalog, descriptors, pack);
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
            Map<String, MutabilityPolicySource> judging = scoped(members.stream()
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
    private static Map<String, MutabilityPolicySource> scoped(List<Scope> closure) {
        Map<String, Scope> byId = new LinkedHashMap<>();
        closure.forEach(scope -> byId.putIfAbsent(scope.packId(), scope));
        Map<String, MutabilityPolicySource> scoped = new LinkedHashMap<>();
        byId.values().forEach(scope -> scoped.put(scope.packId(), restricted(scope.policy(), heldAlong(scope, byId))));
        return scoped;
    }

    /** What a pack and every pack it builds on, transitively, hold; a pack outside the closure holds nothing here. */
    private static Set<Urn> heldAlong(Scope from, Map<String, Scope> byId) {
        Set<Urn> held = new HashSet<>();
        Set<String> seen = new HashSet<>();
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
    private static List<String> buildsOn(PackUnderResolution member) {
        return Stream.concat(
                        member.descriptor().dependencies().stream().map(PackDependency::packId),
                        member.descriptor().parentPack().stream())
                .toList();
    }

    /** The artifacts a pack holds, as URNs. */
    private static Set<Urn> held(PackUnderResolution member) {
        Set<Urn> held = new HashSet<>();
        member.definedArtifacts().forEach(defined -> Urn.parse(defined).ifPresent(held::add));
        return held;
    }

    /**
     * The configured pack a write addresses: the one configured as the pack's identifier, else the one whose descriptor
     * declares the pack's {@code pack_id} (a pack may be addressed by either). A pack that is neither is refused
     * {@code pack_not_found}, as on every other read.
     */
    private static PackIdentifier rootOf(
            PackCatalogRepository catalog, PackDescriptorRepository descriptors, Pack pack) {
        List<PackIdentifier> configured =
                catalog.configured().stream().map(ConfiguredPack::id).toList();
        if (configured.contains(pack.id())) {
            return pack.id();
        }
        String packId = pack.descriptor().packId();
        return configured.stream()
                .filter(candidate -> declares(descriptors, candidate, packId))
                .findFirst()
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.PACK_NOT_FOUND, "Pack " + pack.id().value() + " was not found."));
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
