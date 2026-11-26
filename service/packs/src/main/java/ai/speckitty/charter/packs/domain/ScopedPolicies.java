package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Each closure pack's mutability policy scoped to what the pack or a pack it builds on holds (A15, rule C22): the
 * policy's level for such an artifact, and {@link MutabilityLevel#OPEN} for every other target. The mutability check,
 * the read side's locks and the relation rules all read a closure's policies through it. The descent is
 * {@link PackGraph#ancestorsOf}'s.
 */
public final class ScopedPolicies {

    /**
     * One closure pack as the lock scope reads it.
     *
     * @param packId its descriptor {@code pack_id}
     * @param buildsOn the descriptor {@code pack_id}s it builds on
     * @param held the artifacts it holds
     * @param policy its declared policy
     */
    public record Scope(PackUlid packId, List<PackUlid> buildsOn, Set<Urn> held, MutabilityPolicySource policy) {}

    private ScopedPolicies() {}

    /**
     * Each pack of a composed closure's policy, scoped.
     *
     * @param closure the closure's packs, in closure order
     * @param policyOf each pack's declared policy
     * @return each pack's scoped policy, by descriptor {@code pack_id}, in closure order; a new map the caller may change
     */
    public static Map<PackUlid, MutabilityPolicySource> of(
            List<PackUnderResolution> closure, Function<PackUnderResolution, MutabilityPolicySource> policyOf) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(policyOf, "policyOf");
        return scoped(closure.stream()
                .map(member -> new Scope(member.descriptorId(), buildsOn(member), held(member), policyOf.apply(member)))
                .toList());
    }

    /**
     * Each pack's policy restricted to what it and the packs it builds on hold, in closure order, each id once.
     *
     * @param closure the closure's packs as the lock scope reads them, in closure order
     * @return each pack's scoped policy, by descriptor {@code pack_id}; a new map the caller may change
     */
    public static Map<PackUlid, MutabilityPolicySource> scoped(List<Scope> closure) {
        Map<PackUlid, Scope> byId = new LinkedHashMap<>();
        closure.forEach(scope -> byId.putIfAbsent(scope.packId(), scope));
        Map<PackUlid, MutabilityPolicySource> scoped = new LinkedHashMap<>();
        byId.values().forEach(scope -> scoped.put(scope.packId(), restricted(scope.policy(), heldAlong(scope, byId))));
        return scoped;
    }

    /**
     * The artifacts a pack holds, as URNs.
     *
     * @param member the pack
     * @return what it holds, in its order
     */
    public static Set<Urn> held(PackUnderResolution member) {
        Set<Urn> held = new LinkedHashSet<>();
        member.referableArtifacts().forEach(referable -> Urn.parse(referable).ifPresent(held::add));
        return held;
    }

    /** What a pack and every pack it builds on, transitively, hold; a pack outside the closure holds nothing here. */
    private static Set<Urn> heldAlong(Scope from, Map<PackUlid, Scope> byId) {
        Set<Urn> held = new LinkedHashSet<>();
        PackGraph.ancestorsOf(List.of(from.packId()), byId, Scope::buildsOn)
                .forEach(packId -> Optional.ofNullable(byId.get(packId)).ifPresent(scope -> held.addAll(scope.held())));
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

    /** The descriptor {@code pack_id}s a pack builds on: {@link PackGraph#buildsOn}. */
    private static List<PackUlid> buildsOn(PackUnderResolution member) {
        return PackGraph.buildsOn(member.descriptor()).stream()
                .map(PackUlid::new)
                .toList();
    }
}
