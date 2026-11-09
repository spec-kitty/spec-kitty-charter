package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NodeKindPluralTest {

    @ParameterizedTest
    @EnumSource(NodeKind.class)
    void thePluralOfEveryNodeKindReadsBackAsThatKind(NodeKind kind) {
        assertThat(NodeKind.fromPlural(kind.plural())).contains(kind);
    }

    @Test
    void theTwoKindsWithoutAnArtifactKindHaveTheirOwnPlurals() {
        assertThat(NodeKind.ACTION.plural()).isEqualTo("actions");
        assertThat(NodeKind.MISSION_TYPE.plural()).isEqualTo("mission_types");
        assertThat(NodeKind.AGENT_PROFILE.plural()).isEqualTo("agent_profiles");
    }
}
