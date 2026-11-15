package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.packs.domain.ProfileEnhancementGateway;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.resolution.ProfileEnhancementDropsField;
import ai.speckitty.charter.profiles.domain.resolution.ProfileEnhancementMerge;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The profile enhancement merge over the agent-profile entities the store binds (rulings D5 and D7). */
public final class AgentProfileEnhancements implements ProfileEnhancementGateway {

    /** The one instance: the merge is pure. */
    public static final AgentProfileEnhancements INSTANCE = new AgentProfileEnhancements();

    private AgentProfileEnhancements() {}

    @Override
    public Optional<Dropped> firstDrop(CharterArtifact base, List<Enhancing> inClosureOrder) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(inClosureOrder, "inClosureOrder");
        try {
            ProfileEnhancementMerge.merge(
                    profile(base),
                    inClosureOrder.stream()
                            .map(enhancing -> new ProfileEnhancementMerge.Enhancer(
                                    profile(enhancing.profile()), enhancing.pack()))
                            .toList());
            return Optional.empty();
        } catch (ProfileEnhancementDropsField refused) {
            return Optional.of(new Dropped(refused.pack(), refused.field(), String.valueOf(refused.getMessage())));
        }
    }

    private static AgentProfile profile(CharterArtifact artifact) {
        if (artifact instanceof AgentProfile profile) {
            return profile;
        }
        throw new IllegalArgumentException("an enhancement merge reads agent profiles: " + artifact);
    }
}
