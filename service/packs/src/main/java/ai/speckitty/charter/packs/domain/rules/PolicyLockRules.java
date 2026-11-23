package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackOrigins;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * The case-only policy lock (FR-016; operator rulings R-y, R-k; plan D15): a lock of a pack's mutability policy file
 * (a URN key of {@link ai.speckitty.charter.packs.domain.MutabilityPolicySource#urnEntries()}) that names an artifact of
 * the pack's own closure only by letter case is refused, by pack-doctor's {@code CLOSURE} as registration refuses it.
 *
 * <p>The own closure is the declaring pack and every pack it reaches over {@code depends_on} and {@code parent_pack}
 * ({@link PackCompositionResolver#compositionClosure} rooted at the pack, never its dependents, R-k), the one
 * {@link PresetItemRules} computes: no walk of its own. Over it, the existing case-only answer
 * ({@link PackCompositionResolver#caseOnlyMatches}) judges the pack's locks as written, and each refusal is a fault as
 * that answer gives it: {@code pack_reference_artifact_missing}, naming the declaring pack then the holder, with the
 * resolver's case-only detail and no violation of its own, about the pack and the lock's URN, under the rule
 * {@value #RULE}. A lock held exactly, or held by no pack of the own closure in any letter case, is no fault: it changes
 * no write.
 *
 * <p>The faults come in pack-id order, then in the policy's URN order (the order its file is written in). A pack whose
 * own closure reaches a pack the set does not hold is not judged, as the preset-item phase does not judge it.
 *
 * <p>Registration and every write answer the same in the composing judge's phase 6 (its {@code mentionsOf}: every preset
 * item and every policy lock), with the closure named in front; they do not run this rule, and a composing read is
 * unchanged. The read's {@code mentionsOf} moves into this factory with #636 slice 7, so the mentions have one body.
 */
public final class PolicyLockRules {

    /** The rule name of the policy-lock phase's reasons. */
    public static final String RULE = "closure.policy-locks";

    private PolicyLockRules() {}

    /**
     * The phase over a set of packs: every case-only lock of every pack's policy, in the order above.
     *
     * @param factsOf each pack's composition facts: its mutability policy
     * @return the phase; it answers an empty list when no lock names an artifact of its own closure only by case
     */
    public static Function<List<PackUnderResolution>, List<ClosureFault>> phase(
            Function<PackUnderResolution, PackCompositionFacts> factsOf) {
        Objects.requireNonNull(factsOf, "factsOf");
        return packs -> faults(packs, factsOf);
    }

    private static List<ClosureFault> faults(
            List<PackUnderResolution> packs, Function<PackUnderResolution, PackCompositionFacts> factsOf) {
        Objects.requireNonNull(packs, "packs");
        Map<String, PackUnderResolution> byPackId = new LinkedHashMap<>();
        packs.forEach(pack -> byPackId.putIfAbsent(pack.descriptor().packId(), pack));
        List<PackUnderResolution> ordered = packs.stream()
                .sorted(Comparator.comparing(pack -> pack.configured().id().value()))
                .toList();
        List<ClosureFault> faults = new ArrayList<>();
        for (PackUnderResolution pack : ordered) {
            TreeSet<Urn> locks =
                    new TreeSet<>(factsOf.apply(pack).policy().urnEntries().keySet());
            if (locks.isEmpty()) {
                continue;
            }
            PresetItemRules.ownClosure(pack, byPackId)
                    .ifPresent(closure ->
                            locks.forEach(lock -> lockFault(pack, lock, closure).ifPresent(faults::add)));
        }
        return List.copyOf(faults);
    }

    /** The one fault of a lock: the case-only answer, as the resolver gives it; empty when the lock is not case-only. */
    private static Optional<ClosureFault> lockFault(
            PackUnderResolution pack, Urn lock, List<PackUnderResolution> closure) {
        String mention = lock.toString();
        return PackCompositionResolver.caseOnlyMatches(
                        closure, member -> member.configured().equals(pack.configured()) ? List.of(mention) : List.of())
                .stream()
                .findFirst()
                .map(refusal -> ClosureFault.of(
                        PackOrigins.of(pack.configured().id(), lock),
                        RULE,
                        refusal.failure().code(),
                        refusal.detail(),
                        refusal.packs()));
    }
}
