package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.ProfileEnhancementGateway;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.DirectiveReference;
import ai.speckitty.charter.profiles.domain.Specialization;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The effective profile the storage ring composes through {@link ProfileEnhancementGateway#compose} (rulings D5, D6 and
 * D7): each profile of a chain merged with its own enhancers, then the chain folded root to leaf, and the first drop
 * answered instead, naming the enhancing pack and the field.
 */
@DisplayName("AgentProfileEnhancements.compose")
class AgentProfileEnhancementsTest {

    private static final PackIdentifier BASE = new PackIdentifier("base");

    private static AgentProfile.Builder profile(String id, String focus) {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier(id))
                .packId(BASE)
                .name(id)
                .role("reviewer")
                .purpose("Review.")
                .specialization(new Specialization(focus, Optional.empty(), Optional.empty(), Optional.empty()));
    }

    private static DirectiveReference directive(String code) {
        return new DirectiveReference(code, "Directive " + code, "Because.");
    }

    @Test
    @DisplayName("an ancestor's enhancement reaches the descendant, which inherits what it leaves absent")
    void eachLayerIsMergedThenTheChainIsFolded() {
        AgentProfile root = profile("root", "Review")
                .capability("code-review")
                .directiveReference(directive("001"))
                .build();
        AgentProfile rootEnhancer = profile("root-org", "Review")
                .directiveReference(directive("001"))
                .directiveReference(directive("038"))
                .build();
        AgentProfile leaf = profile("leaf", "Security review")
                .maxConcurrentTasks(Optional.of(2))
                .build();

        ProfileEnhancementGateway.Composed composed = AgentProfileEnhancements.INSTANCE.compose(List.of(
                new ProfileEnhancementGateway.Layer(
                        root, List.of(new ProfileEnhancementGateway.Enhancing(rootEnhancer, "org"))),
                new ProfileEnhancementGateway.Layer(leaf, List.of())));

        assertThat(composed).isInstanceOf(ProfileEnhancementGateway.Effective.class);
        AgentProfile effective = (AgentProfile) ((ProfileEnhancementGateway.Effective) composed).profile();
        assertThat(effective.localId()).isEqualTo("leaf");
        assertThat(effective.specialization().primaryFocus()).isEqualTo("Security review");
        assertThat(effective.maxConcurrentTasks()).contains(2);
        assertThat(effective.capabilities()).as("inherited from the root").containsExactly("code-review");
        assertThat(effective.directiveReferences())
                .as("the root's enhancement, inherited")
                .extracting(DirectiveReference::code)
                .containsExactly("001", "038");
    }

    @Test
    @DisplayName("a chain of the profile alone with no enhancer is the profile")
    void aRootAloneIsItself() {
        AgentProfile root = profile("root", "Review").capability("code-review").build();

        ProfileEnhancementGateway.Composed composed = AgentProfileEnhancements.INSTANCE.compose(
                List.of(new ProfileEnhancementGateway.Layer(root, List.of())));

        assertThat(((ProfileEnhancementGateway.Effective) composed).profile()).isEqualTo(root);
    }

    @Test
    @DisplayName("an enhancer that leaves out an ancestor's specialization is the drop, naming its pack and the field")
    void aDropAnywhereOnTheChainIsAnswered() {
        AgentProfile root = profile("root", "Review").build();
        AgentProfile dropping = profile("root-team", "Security").build();
        AgentProfile leaf = profile("leaf", "Security review").build();

        ProfileEnhancementGateway.Composed composed = AgentProfileEnhancements.INSTANCE.compose(List.of(
                new ProfileEnhancementGateway.Layer(
                        root, List.of(new ProfileEnhancementGateway.Enhancing(dropping, "team"))),
                new ProfileEnhancementGateway.Layer(leaf, List.of())));

        assertThat(composed).isInstanceOf(ProfileEnhancementGateway.Dropped.class);
        ProfileEnhancementGateway.Dropped dropped = (ProfileEnhancementGateway.Dropped) composed;
        assertThat(dropped.pack()).isEqualTo("team");
        assertThat(dropped.field()).isEqualTo("specialization");
        assertThat(dropped.detail()).contains("root-team", "team", "root", "specialization");
    }
}
