package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The references of the winner an activation named (research R10): the pack read's answer, unchanged, for the winner's
 * pack, kind and id, when that read verified the fingerprint the caller holds; {@code stale_input} when it verified
 * another, or when the pack or the artifact is gone; any other refusal of the pack read unchanged.
 */
class WinnerReferencesTest {

    private static final PackIdentifier PROJ = new PackIdentifier("proj");
    private static final String HELD = "sha256:" + "1".repeat(64);
    private static final String OTHER = "sha256:" + "2".repeat(64);
    private static final ArtifactReadService.Reference REQUIRES = new ArtifactReadService.Reference(
            PackQualifiedReference.parse("tactic:T"),
            RelationType.REQUIRES,
            Optional.empty(),
            new Urn(NodeKind.TACTIC, "T"));

    private final List<String> asked = new ArrayList<>();

    private WinnerReferences answering(String fingerprint) {
        return new WinnerReferences((pack, kindToken, id) -> {
            asked.add(pack.value() + " " + kindToken + " " + id);
            return new ConsistentRead.Verified<>(List.of(REQUIRES), fingerprint);
        });
    }

    private WinnerReferences refusing(RefusalCode code) {
        return new WinnerReferences((pack, kindToken, id) -> {
            throw new CharterRefusal(code, "refused by the pack read");
        });
    }

    @Test
    void theAnswerIsThePackReadOfTheWinnersPackKindAndIdAtTheHeldFingerprint() {
        ConsistentRead.Verified<List<ArtifactReadService.Reference>> read =
                answering(HELD).of(PROJ, ArtifactKind.DIRECTIVE, "D", HELD);

        assertThat(read.value()).containsExactly(REQUIRES);
        assertThat(read.fingerprint()).isEqualTo(HELD);
        assertThat(asked).containsExactly("proj directive D");
    }

    @Test
    void aReadAtAnotherFingerprintIsStaleInput() {
        assertThatThrownBy(() -> answering(OTHER).of(PROJ, ArtifactKind.DIRECTIVE, "D", HELD))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT);
                    assertThat(refusal.getMessage())
                            .isEqualTo("The packs changed since the activation named the winner; read it again.");
                });
    }

    @Test
    void aPackOrAnArtifactGoneSinceTheActivationNamedItIsStaleInputForEachKindsNotFoundCode() {
        Object[][] gone = {
            {ArtifactKind.DIRECTIVE, RefusalCode.DIRECTIVE_NOT_FOUND},
            {ArtifactKind.AGENT_PROFILE, RefusalCode.ARTIFACT_NOT_FOUND},
            {ArtifactKind.MISSION_TYPE, RefusalCode.MISSION_TYPE_NOT_FOUND},
            {ArtifactKind.ASSET, RefusalCode.PACK_NOT_FOUND}
        };
        for (Object[] each : gone) {
            ArtifactKind kind = (ArtifactKind) each[0];
            RefusalCode code = (RefusalCode) each[1];
            assertThatThrownBy(() -> refusing(code).of(PROJ, kind, "x", HELD))
                    .as("%s refused %s", kind, code)
                    .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT);
                        assertThat(refusal.getCause()).isInstanceOf(CharterRefusal.class);
                    });
        }
    }

    @Test
    void anotherRefusalOfThePackReadIsTheAnswerUnchanged() {
        for (RefusalCode code :
                new RefusalCode[] {RefusalCode.PACK_UNREADABLE, RefusalCode.STALE_INPUT, RefusalCode.TACTIC_NOT_FOUND
                }) {
            assertThatThrownBy(() -> refusing(code).of(PROJ, ArtifactKind.DIRECTIVE, "D", HELD))
                    .as("%s", code)
                    .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(code);
                        assertThat(refusal.getMessage()).isEqualTo("refused by the pack read");
                    });
        }
    }
}
