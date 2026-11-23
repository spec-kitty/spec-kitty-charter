package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link Pack#without}: the aggregate a removal leaves (#355). The removed artifacts go, and every other part of the pack
 * stays as it is; a URN that names no artifact the pack holds, or a kind that is no artifact, removes nothing.
 */
@DisplayName("Pack.without leaves out the removed artifacts and keeps every other part")
class PackWithoutTest {

    private static final PackIdentifier ID = new PackIdentifier("pack");
    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            "01PACK00000000000000000000", "1.0.0", Optional.empty(), "demo", Optional.empty(), Optional.empty());

    private static Tactic tactic(String id) {
        return Tactic.builder()
                .id(new TacticIdentifier(id))
                .packId(ID)
                .schemaVersion("1.0")
                .name("n")
                .step(new TacticStep("s", Optional.empty(), List.of()))
                .build();
    }

    private static final AssetFile LOGO = new AssetFile("logo.png", Payload.of(new byte[] {1, 2}));
    private static final Preset PRESET = new Preset(
            "minimal", "The least a review needs.", Map.of(ArtifactKind.TACTIC, List.of(PresetItem.of("a"))));
    private static final PackDocument DOCUMENT =
            new PackDocument(Optional.empty(), Optional.empty(), "The pack.", List.of());
    private static final PackElementViolation VIOLATION =
            PackElementViolation.wholeFile("broken.tactic.yaml", PackCodes.ARTIFACT_SCHEMA_ERROR, "missing steps");

    private static Pack pack() {
        return Pack.builder()
                .id(ID)
                .descriptor(DESCRIPTOR)
                .tactic(tactic("a"))
                .tactic(tactic("b"))
                .assetFile(LOGO)
                .preset(PRESET)
                .document(DOCUMENT)
                .violation(VIOLATION)
                .build();
    }

    @Test
    @DisplayName("the removed artifact goes; id, descriptor, the other artifacts and every other part stay")
    void theRemovedArtifactGoesAndTheRestStays() {
        Pack after = pack().without(List.of(new Urn(NodeKind.TACTIC, "a")));

        assertThat(after.artifacts()).extracting(artifact -> artifact.localId()).containsExactly("b");
        assertThat(after.id()).isEqualTo(ID);
        assertThat(after.descriptor()).isEqualTo(DESCRIPTOR);
        assertThat(after.assetFiles()).containsExactly(LOGO);
        assertThat(after.presets()).containsExactly(PRESET);
        assertThat(after.document()).contains(DOCUMENT);
        assertThat(after.violations()).containsExactly(VIOLATION);
    }

    @Test
    @DisplayName("a URN of an artifact the pack does not hold, or of a kind that is no artifact, removes nothing")
    void anUnheldOrNonArtifactUrnRemovesNothing() {
        Pack before = pack();

        Pack absent = before.without(List.of(new Urn(NodeKind.TACTIC, "missing")));
        Pack notAnArtifact = before.without(List.of(new Urn(NodeKind.ACTION, "software-dev/plan")));

        assertThat(absent.artifacts()).containsExactlyElementsOf(before.artifacts());
        assertThat(notAnArtifact.artifacts()).containsExactlyElementsOf(before.artifacts());
        assertThat(absent.violations()).containsExactlyElementsOf(before.violations());
    }

    @Test
    @DisplayName("no URN leaves the pack as it was")
    void noUrnLeavesThePackAsItWas() {
        Pack before = pack();

        assertThat(before.without(List.of()).artifacts()).containsExactlyElementsOf(before.artifacts());
    }
}
