package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Asset entity")
class AssetEntityTest {

    private static final PackIdentifier PACK = new PackIdentifier("01ASSETPACK");

    /** A fully valid builder; each invariant test mutates exactly one field, so the valid case is the positive control. */
    private static Asset.Builder valid() {
        return Asset.builder()
                .id(new AssetIdentifier("debrief-logo"))
                .packId(PACK)
                .mime("image/png")
                .path("debrief/logo.png")
                .title("Debrief logo");
    }

    @Test
    @DisplayName("a fully populated asset builds and exposes its fields")
    void aValidAssetBuilds() {
        Asset asset = valid().build();

        assertThat(asset.id()).isEqualTo(new AssetIdentifier("debrief-logo"));
        assertThat(asset.packId()).isEqualTo(PACK);
        assertThat(asset.mime()).isEqualTo("image/png");
        assertThat(asset.path()).isEqualTo("debrief/logo.png");
        assertThat(asset.title()).contains("Debrief logo");
    }

    @Test
    @DisplayName("the spine is kind=ASSET, label=title, no description, no schema version, urn=asset:<id>")
    void theSpineFollowsTheContract() {
        Asset asset = valid().build();

        assertThat(asset.kind()).isEqualTo(ArtifactKind.ASSET);
        assertThat(asset.localId()).isEqualTo("debrief-logo");
        assertThat(asset.label()).isEqualTo("Debrief logo");
        assertThat(asset.description()).isEmpty();
        assertThat(asset.schemaVersion()).isEmpty();
        assertThat(asset.relationships()).isEmpty();
        assertThat(asset.urn().toString()).isEqualTo("asset:debrief-logo");
    }

    @Test
    @DisplayName("without a title the label falls back to the id; null and absent mean the same")
    void labelFallsBackToTheId() {
        Asset untitled = valid().title(null).build();

        assertThat(untitled.title()).isEmpty();
        assertThat(untitled.label()).isEqualTo("debrief-logo");
    }

    @Test
    @DisplayName("a blank mime breaches")
    void blankMimeBreaches() {
        assertThatThrownBy(() -> valid().mime(" ").build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("mime");
    }

    @Test
    @DisplayName("a blank path breaches")
    void blankPathBreaches() {
        assertThatThrownBy(() -> valid().path("").build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("path");
    }

    @Test
    @DisplayName("a blank identifier breaches")
    void blankIdentifierBreaches() {
        assertThatThrownBy(() -> new AssetIdentifier(" ")).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("mime and path are kept as authored: no MIME check, no path normalization (C-003)")
    void mimeAndPathAreKeptVerbatim() {
        Asset asset =
                valid().mime("text/x-unknown").path("assets/audiences/x.md").build();

        assertThat(asset.mime()).isEqualTo("text/x-unknown");
        assertThat(asset.path()).isEqualTo("assets/audiences/x.md");
    }

    @Test
    @DisplayName("two assets are equal by identity, whatever the other fields hold")
    void equalByIdentity() {
        assertThat(valid().build()).isEqualTo(valid().mime("image/jpeg").build());
        assertThat(valid().build())
                .isNotEqualTo(valid().id(new AssetIdentifier("other")).build());
    }
}
