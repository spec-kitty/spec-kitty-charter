package ai.speckitty.charter.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class NodeKindTest {

    @Test
    void everyArtifactPluralNamesItsNodeKind() {
        for (ArtifactKind kind : ArtifactKind.values()) {
            assertThat(NodeKind.fromPlural(kind.plural())).as(kind.plural()).contains(NodeKind.of(kind));
        }
    }

    @Test
    void missionTypesAndActionsNameTheirNodeKinds() {
        assertThat(NodeKind.fromPlural("mission_types")).contains(NodeKind.MISSION_TYPE);
        assertThat(NodeKind.fromPlural("actions")).contains(NodeKind.ACTION);
    }

    @Test
    void coresAliasesAndSingularsAreRefused() {
        assertThat(NodeKind.fromPlural("mission_steps")).isEqualTo(Optional.empty());
        assertThat(NodeKind.fromPlural("mission_type")).isEqualTo(Optional.empty());
        assertThat(NodeKind.fromPlural("anti_pattern")).isEqualTo(Optional.empty());
        assertThat(NodeKind.fromPlural("")).isEqualTo(Optional.empty());
    }

    @Test
    void theFifteenPluralsCoverEveryNodeKindExactlyOnce() {
        long named = java.util.Arrays.stream(NodeKind.values())
                .filter(k -> NodeKind.fromPlural(pluralOf(k)).isPresent())
                .count();
        assertThat(named).isEqualTo(NodeKind.values().length);
    }

    private static String pluralOf(NodeKind kind) {
        return switch (kind) {
            case MISSION_TYPE -> "mission_types";
            case ACTION -> "actions";
            default -> kind.artifactKind().orElseThrow().plural();
        };
    }
}
