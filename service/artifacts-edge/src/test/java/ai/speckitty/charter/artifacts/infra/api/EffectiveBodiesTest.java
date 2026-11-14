package ai.speckitty.charter.artifacts.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A reference of the effective reads names the artifact kind a URN's node kind stands for, never the URN's own token
 * (ADR-0053 decision 13).
 */
class EffectiveBodiesTest {

    private static Stream<ArtifactKind> activatableKinds() {
        return Arrays.stream(ArtifactKind.values()).filter(ArtifactKind::activatable);
    }

    @ParameterizedTest
    @MethodSource("activatableKinds")
    void aReferenceNamesTheArtifactKindOfItsUrnsNodeKind(ArtifactKind kind) {
        Urn urn = new Urn(NodeKind.of(kind), "x");
        assertThat(urn.kind().artifactKind()).contains(kind);

        assertThat(EffectiveBodies.reference(urn).getKind().getValue()).isEqualTo(kind.token());
    }

    @Test
    void anActionUrnIsNoArtifactReference() {
        Urn action = Urn.action("software-dev", "plan");
        assertThat(action.kind().artifactKind()).isEmpty();

        assertThatThrownBy(() -> EffectiveBodies.reference(action)).isInstanceOf(IllegalArgumentException.class);
    }
}
