package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.packs.domain.ProfileEnhancementGateway;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.resolution.ProfileEnhancementDropsField;
import ai.speckitty.charter.profiles.domain.resolution.ProfileEnhancementMerge;
import ai.speckitty.charter.profiles.domain.resolution.ProfileInheritance;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The profile enhancement merge (rulings D5 and D7) and the inheritance along {@code specializes_from} (ruling D6) over
 * the agent-profile entities the store binds.
 */
public final class AgentProfileEnhancements implements ProfileEnhancementGateway {

    /** The one instance: the merge is pure. */
    public static final AgentProfileEnhancements INSTANCE = new AgentProfileEnhancements();

    private AgentProfileEnhancements() {}

    @Override
    public Optional<Dropped> firstDrop(CharterArtifact base, List<Enhancing> inClosureOrder) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(inClosureOrder, "inClosureOrder");
        try {
            ProfileEnhancementMerge.merge(profile(base), enhancers(inClosureOrder));
            return Optional.empty();
        } catch (ProfileEnhancementDropsField refused) {
            return Optional.of(new Dropped(refused.pack(), refused.field(), String.valueOf(refused.getMessage())));
        }
    }

    @Override
    public Composed compose(List<Layer> rootToLeaf) {
        Objects.requireNonNull(rootToLeaf, "rootToLeaf");
        List<AgentProfile> merged = new ArrayList<>();
        try {
            for (Layer layer : rootToLeaf) {
                merged.add(ProfileEnhancementMerge.merge(profile(layer.profile()), enhancers(layer.enhancers())));
            }
        } catch (ProfileEnhancementDropsField refused) {
            return new Dropped(refused.pack(), refused.field(), String.valueOf(refused.getMessage()));
        }
        return new Effective(ProfileInheritance.fold(merged));
    }

    private static List<ProfileEnhancementMerge.Enhancer> enhancers(List<Enhancing> enhancing) {
        return enhancing.stream()
                .map(one -> new ProfileEnhancementMerge.Enhancer(profile(one.profile()), one.pack()))
                .toList();
    }

    private static AgentProfile profile(CharterArtifact artifact) {
        if (artifact instanceof AgentProfile profile) {
            return profile;
        }
        throw new IllegalArgumentException("an enhancement merge reads agent profiles: " + artifact);
    }
}
