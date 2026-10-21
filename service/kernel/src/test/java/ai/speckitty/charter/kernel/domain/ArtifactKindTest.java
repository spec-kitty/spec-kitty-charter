package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ArtifactKindTest {

    @Test
    void factsMatchTheLogicalModelTable() {
        assertThat(Arrays.stream(ArtifactKind.values())
                        .filter(ArtifactKind::activatable)
                        .map(ArtifactKind::token))
                .containsExactly(
                        "directive",
                        "tactic",
                        "styleguide",
                        "toolguide",
                        "paradigm",
                        "procedure",
                        "agent_profile",
                        "mission_step_contract",
                        "glossary_pack",
                        "skill",
                        "anti_pattern");
        assertThat(Arrays.stream(ArtifactKind.values()).filter(ArtifactKind::selectionOverlayable))
                .allMatch(ArtifactKind::orgRequirable)
                .hasSize(8);
        assertThat(Arrays.stream(ArtifactKind.values())
                        .filter(k -> k.inForceWhenAbsent() == ArtifactKind.InForceWhenAbsent.REQUIRED))
                .containsExactly(ArtifactKind.SKILL);
        assertThat(ArtifactKind.ASSET.orgRequirable()).isTrue();
        assertThat(ArtifactKind.TEMPLATE.orgRequirable()).isFalse();
    }

    @Test
    void everyArtifactKindIsANodeKind() {
        for (ArtifactKind kind : ArtifactKind.values()) {
            assertThat(NodeKind.of(kind).artifactKind()).contains(kind);
        }
        assertThat(NodeKind.ACTION.artifactKind()).isEmpty();
    }

    @Test
    void pluralsResolve() {
        assertThat(ArtifactKind.fromPlural("agent_profiles")).contains(ArtifactKind.AGENT_PROFILE);
        assertThat(ArtifactKind.fromPlural("mission_types")).isEmpty();
    }
}
