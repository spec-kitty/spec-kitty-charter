package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.InForceStates;
import ai.speckitty.charter.packs.domain.MergedClosure;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The agent profiles a profile selection chooses from, over one verified {@link ClosureView} and the states the
 * request resolved (ADR-0069 decision 4, ruling D2 of slice G): every winning agent profile in force that enhances no
 * agent profile, each with the entity its winning pack stores, that pack, its pack's position in the closure and the
 * longest in-force {@code specializes_from} chain the governance walk's lineage step answers
 * ({@link ProfileGovernance.Governance#lineage()}). Nothing is read but through the view, so every entity is read
 * against the digest the view was verified at and a change since is {@code stale_input}.
 */
public final class InForceProfiles {

    private static final NodeKind PROFILE = NodeKind.of(ArtifactKind.AGENT_PROFILE);

    /**
     * One agent profile of the closure, as selection reads it.
     *
     * @param profile the entity the winning pack stores
     * @param holder the pack that holds the version in force
     * @param closurePosition the position of the holding pack in the closure, dependencies first
     * @param depth the length of its longest in-force {@code specializes_from} chain; 0 for a root
     * @param ancestors the ids of its in-force {@code specializes_from} ancestors, nearest first
     */
    public record Profile(
            CharterArtifact profile,
            ClosureView.Holder holder,
            int closurePosition,
            int depth,
            List<String> ancestors) {

        /**
         * Checks and copies the fields.
         *
         * @param profile the entity
         * @param holder the holding pack
         * @param closurePosition the closure position
         * @param depth the longest chain
         * @param ancestors the ancestors' ids
         */
        public Profile {
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(holder, "holder");
            ancestors = List.copyOf(ancestors);
        }
    }

    private InForceProfiles() {}

    /**
     * The non-enhancer agent profiles in force, in id order.
     *
     * @param view the request's verified view
     * @param states what the request put in force
     * @return the profiles selection chooses from
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as {@link ClosureView#merged()} and
     *     {@link ClosureView#effectiveArtifact} do
     */
    public static List<Profile> candidates(ClosureView view, InForceStates states) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(states, "states");
        Set<Urn> enhancers = enhancersOf(view.merged());
        List<Profile> profiles = new ArrayList<>();
        for (MergedClosure.Winner winner : view.heldWinners(ArtifactKind.AGENT_PROFILE)) {
            boolean inForce = states.stateOf(winner.urn())
                    .filter(InForceStates.ArtifactState::active)
                    .isPresent();
            if (inForce && !enhancers.contains(winner.urn())) {
                profiles.add(profileOf(view, winner, states));
            }
        }
        return List.copyOf(profiles);
    }

    /**
     * One agent profile the closure holds, whatever its state: the fallback a pack names, which selection answers
     * when no candidate fits.
     *
     * @param view the request's verified view
     * @param profileId the profile's id
     * @param states what the request put in force
     * @return the profile; empty when no pack of the closure holds it
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as {@link #candidates} does
     */
    public static Optional<Profile> held(ClosureView view, String profileId, InForceStates states) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(states, "states");
        return view.heldWinner(ArtifactKind.AGENT_PROFILE, profileId).map(winner -> profileOf(view, winner, states));
    }

    /** The winners that enhance an agent profile, as the in-force resolution reads the {@code enhances} edges. */
    private static Set<Urn> enhancersOf(ClosureView.MergedView merged) {
        Set<Urn> enhancers = new HashSet<>();
        merged.resolutionEdges().enhances().forEach((source, targets) -> {
            if (source.kind() == PROFILE && targets.stream().anyMatch(target -> target.kind() == PROFILE)) {
                enhancers.add(source);
            }
        });
        return enhancers;
    }

    private static Profile profileOf(ClosureView view, MergedClosure.Winner winner, InForceStates states) {
        ClosureView.EffectiveArtifactView read = view.effectiveArtifact(
                        ArtifactKind.AGENT_PROFILE, winner.urn().id(), 0, states)
                .orElseThrow(() -> new IllegalStateException(
                        "The merge holds " + winner.urn() + ", and the view read no winner of it."));
        List<ProfileGovernance.LineageLink> lineage =
                read.governance().map(ProfileGovernance.Governance::lineage).orElse(List.of());
        int depth = lineage.stream()
                .mapToInt(ProfileGovernance.LineageLink::depth)
                .max()
                .orElse(0);
        Set<String> ancestors = new LinkedHashSet<>();
        lineage.forEach(link -> ancestors.add(link.urn().id()));
        return new Profile(
                read.artifact(),
                read.holder(),
                view.closurePackIds().indexOf(winner.descriptorPackId()),
                depth,
                List.copyOf(ancestors));
    }
}
