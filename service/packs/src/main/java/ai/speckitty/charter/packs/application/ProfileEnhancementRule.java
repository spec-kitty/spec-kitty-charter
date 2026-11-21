package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ClosureFacts;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ClosureWalk;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.ProfileEnhancementGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The profile enhancement rule of rulings D5 and D7 (#380), judged over the declaring pack's own closure with the write
 * in place, beside the fallback and role-pin rules: every {@code enhances} edge from an agent profile to an agent
 * profile among the closure's winners, read as the in-force resolution reads them
 * ({@link ClosureView.MergedView#resolutionEdges()}), names an enhancer whose merge with the profile it enhances
 * (the profiles slice's merge, through {@link ProfileEnhancementGateway}) loses no content ({@code profile_enhancement_drops_field}). Each enhanced profile is
 * merged with all its enhancers in closure order, the nearest pack last, so a later enhancer must keep what an earlier
 * one added. The enhanced profiles are judged in URN order, each enhanced profile its own fault (#556, FR-005): the
 * merge names the first field an enhancer drops.
 */
final class ProfileEnhancementRule {

    private ProfileEnhancementRule() {}

    /**
     * Judges the profile enhancements of one closure, every enhanced profile that loses content (#556, FR-005).
     *
     * @param closure the declaring pack's own closure
     * @param profiles where the winning profiles' entities are read
     * @param merge the profiles slice's merge; needed only when a profile enhances a profile
     * @return one fault per enhanced profile whose merge drops part of it, in URN order; empty when none does
     * @throws IllegalStateException when a profile enhances a profile and the deployment has no merge to judge it
     */
    static List<ClosureFault> check(
            ClosureFacts closure, ClosureWalk.Profiles profiles, Optional<ProfileEnhancementGateway> merge) {
        Optional<ClosureFacts.ProfileEnhancements> enhancements = closure.profileEnhancements();
        if (enhancements.isEmpty()) {
            return List.of();
        }
        ProfileEnhancementGateway judge = merge.orElseThrow(() -> new IllegalStateException(
                "The staged packs cannot merge agent profiles, so a profile enhancement cannot be judged"));
        Map<Urn, MergedClosure.Winner> winners = enhancements.get().winners();
        List<ClosureFault> breaches = new ArrayList<>();
        for (Map.Entry<Urn, List<MergedClosure.Winner>> enhanced :
                enhancements.get().enhancersOf().entrySet()) {
            Optional<CharterArtifact> base = profileOf(enhanced.getKey(), winners, profiles);
            if (base.isEmpty()) {
                continue;
            }
            List<ProfileEnhancementGateway.Enhancing> enhancers = new ArrayList<>();
            enhanced.getValue()
                    .forEach(winner -> profileOf(winner.urn(), winners, profiles)
                            .ifPresent(profile ->
                                    enhancers.add(new ProfileEnhancementGateway.Enhancing(profile, winner.packId()))));
            judge.firstDrop(base.get(), enhancers)
                    .ifPresent(dropped -> breaches.add(ClosureFault.of(
                            ClosureFault.ORIGIN,
                            RefusalCode.PROFILE_ENHANCEMENT_DROPS_FIELD,
                            dropped.detail(),
                            closure.named(List.of(dropped.pack())))));
        }
        return List.copyOf(breaches);
    }

    private static Optional<CharterArtifact> profileOf(
            Urn urn, Map<Urn, MergedClosure.Winner> winners, ClosureWalk.Profiles profiles) {
        return Optional.ofNullable(winners.get(urn)).flatMap(winner -> profiles.read(winner.packId(), urn.id()));
    }
}
