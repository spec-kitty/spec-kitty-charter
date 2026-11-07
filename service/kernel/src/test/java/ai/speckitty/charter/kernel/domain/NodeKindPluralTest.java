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

    @ParameterizedTest
    @EnumSource(ArtifactKind.class)
    void aNodeKindOfAnArtifactKindTakesItsPluralFromTheRegistry(ArtifactKind kind) {
        assertThat(NodeKind.of(kind).plural()).isEqualTo(kind.plural());
    }

    @Test
    void onlyTheActionHasAPluralOfItsOwn() {
        assertThat(NodeKind.ACTION.plural()).isEqualTo("actions");
        assertThat(NodeKind.MISSION_TYPE.plural()).isEqualTo(ArtifactKind.MISSION_TYPE.plural());
        assertThat(NodeKind.AGENT_PROFILE.plural()).isEqualTo("agent_profiles");
    }
}
