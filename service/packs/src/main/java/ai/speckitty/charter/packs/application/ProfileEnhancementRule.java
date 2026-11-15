package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.ProfileEnhancementGateway;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The profile enhancement rule of rulings D5 and D7 (#380), judged over the declaring pack's own closure with the write
 * in place, beside the fallback and role-pin rules: every {@code enhances} edge from an agent profile to an agent
 * profile among the closure's winners, read as the in-force resolution reads them
 * ({@link ClosureView.MergedView#resolutionEdges()}), names an enhancer whose merge with the profile it enhances
 * (the profiles slice's merge, through {@link ProfileEnhancementGateway}) loses no content ({@code profile_enhancement_drops_field}). Each enhanced profile is
 * merged with all its enhancers in closure order, the nearest pack last, so a later enhancer must keep what an earlier
 * one added. The enhanced profiles are judged in URN order.
 */
final class ProfileEnhancementRule {

    private static final NodeKind PROFILE = NodeKind.of(ArtifactKind.AGENT_PROFILE);

    /**
     * A breach of the rule.
     *
     * @param code the refusal code
     * @param detail what is wrong, naming the enhancing pack first and the field
     * @param packs the enhancing pack
     */
    record Breach(RefusalCode code, String detail, List<PackIdentifier> packs) {
        Breach {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(detail, "detail");
            packs = List.copyOf(packs);
        }
    }

    private ProfileEnhancementRule() {}

    /**
     * Judges the profile enhancements of one closure.
     *
     * @param closure the declaring pack's own closure
     * @param profiles where each winning profile's entity is read
     * @param merge the profile enhancement merge, when the caller has one
     * @return the first breach; empty when every enhancement keeps what it enhances
     * @throws IllegalStateException when the closure holds an enhancer of a profile and there is no merge to judge it
     */
    static Optional<Breach> check(
            ClosureView closure, FallbackProfileRule.Profiles profiles, Optional<ProfileEnhancementGateway> merge) {
        boolean anyProfileEnhanced = closure.edges().links().stream()
                .anyMatch(link -> link.relation() == RelationType.ENHANCES
                        && link.source().kind() == PROFILE
                        && link.target().kind() == PROFILE);
        if (!anyProfileEnhanced) {
            return Optional.empty();
        }
        ProfileEnhancementGateway judge = merge.orElseThrow(() -> new IllegalStateException(
                "The staged packs cannot merge agent profiles, so a profile enhancement cannot be judged"));
        ClosureView.MergedView merged;
        try {
            merged = closure.merged();
        } catch (CharterRefusal unmerged) {
            // A closure whose layers do not merge is not this rule's to judge: the write that leaves it so is refused
            // by
            // the rules and the stores that own the layer merge, with their own code.
            return Optional.empty();
        }
        Map<Urn, MergedClosure.Winner> winners = merged.closure().winners();
        Map<Urn, List<Urn>> enhancersOf = new TreeMap<>();
        merged.resolutionEdges().enhances().forEach((source, targets) -> {
            if (source.kind() == PROFILE) {
                targets.stream()
                        .filter(target -> target.kind() == PROFILE)
                        .forEach(target -> enhancersOf
                                .computeIfAbsent(target, ignored -> new ArrayList<>())
                                .add(source));
            }
        });
        List<String> order = closure.closurePackIds();
        for (Map.Entry<Urn, List<Urn>> enhanced : enhancersOf.entrySet()) {
            Optional<CharterArtifact> base = profileOf(enhanced.getKey(), winners, profiles);
            if (base.isEmpty()) {
                continue;
            }
            Map<Urn, MergedClosure.Winner> enhancing = new LinkedHashMap<>();
            enhanced.getValue().stream()
                    .map(urn -> Objects.requireNonNull(winners.get(urn), "an enhancer is a winner"))
                    .sorted(Comparator.comparingInt(
                                    (MergedClosure.Winner winner) -> order.indexOf(winner.descriptorPackId()))
                            .thenComparing(MergedClosure.Winner::urn))
                    .forEach(winner -> enhancing.put(winner.urn(), winner));
            List<ProfileEnhancementGateway.Enhancing> enhancers = new ArrayList<>();
            enhancing.forEach((urn, winner) -> profileOf(urn, winners, profiles)
                    .ifPresent(profile -> enhancers.add(new ProfileEnhancementGateway.Enhancing(
                            profile, winner.packId().value()))));
            Optional<ProfileEnhancementGateway.Dropped> dropped = judge.firstDrop(base.get(), enhancers);
            if (dropped.isPresent()) {
                return Optional.of(new Breach(
                        RefusalCode.PROFILE_ENHANCEMENT_DROPS_FIELD,
                        dropped.get().detail(),
                        List.of(new PackIdentifier(dropped.get().pack()))));
            }
        }
        return Optional.empty();
    }

    /** The winning entity of a profile, when the closure holds it and its pack can read it. */
    private static Optional<CharterArtifact> profileOf(
            Urn urn, Map<Urn, MergedClosure.Winner> winners, FallbackProfileRule.Profiles profiles) {
        return Optional.ofNullable(winners.get(urn)).flatMap(winner -> profiles.read(winner.packId(), urn.id()));
    }
}
