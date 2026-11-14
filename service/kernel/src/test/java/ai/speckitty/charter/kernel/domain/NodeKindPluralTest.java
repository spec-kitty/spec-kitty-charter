package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class NodeKindPluralTest {

    private static Stream<NodeKind> kindsWithAPlural() {
        return Arrays.stream(NodeKind.values()).filter(NodeKind::hasPlural);
    }

    @ParameterizedTest
    @MethodSource("kindsWithAPlural")
    void thePluralOfEveryNodeKindReadsBackAsThatKind(NodeKind kind) {
        assertThat(NodeKind.fromPlural(kind.plural())).contains(kind);
    }

    @Test
    void everyNodeKindButTheTermHasAPlural() {
        // A term is derived from its glossary context and never declared in a fragment, so it has no plural.
        assertThat(Arrays.stream(NodeKind.values()).filter(kind -> !kind.hasPlural()))
                .containsExactly(NodeKind.TERM);
        assertThat(kindsWithAPlural()).hasSize(NodeKind.values().length - 1);
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
