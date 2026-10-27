package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Pack asset files (R1, P3)")
class PackAssetFilesTest {

    private static final PackIdentifier ID = new PackIdentifier("01PACK");
    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            "01PACK", "1.0.0", Optional.empty(), "demo", Optional.empty(), Optional.empty(), List.of());

    private static final AssetFile LOGO = new AssetFile("debrief/logo.png", Payload.of(new byte[] {0, 1, 2}));
    private static final AssetFile CONFIG = new AssetFile("tooling/lint.config.yaml", Payload.of(new byte[] {'a'}));

    private static Asset asset(String id, String path) {
        return Asset.builder()
                .id(new AssetIdentifier(id))
                .packId(ID)
                .mime("image/png")
                .path(path)
                .build();
    }

    private static Directive directive(String id, String title) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(ID)
                .schemaVersion("1.0")
                .title(title)
                .intent("i")
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    private static Pack.Builder bare() {
        return Pack.builder().id(ID).descriptor(DESCRIPTOR);
    }

    @Nested
    @DisplayName("AssetFile")
    class AssetFileValue {

        @Test
        @DisplayName("a nested relative path is valid")
        void nestedPathIsValid() {
            assertThatCode(() -> new AssetFile("a/b/c.otf", Payload.of(new byte[0])))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  ", "../x", "a/../x", "/x", "a//b", "a/", ".."})
        @DisplayName("a path that is blank, absolute, has a '..' or an empty segment breaches")
        void invalidPathsBreach(String path) {
            assertThatThrownBy(() -> new AssetFile(path, Payload.of(new byte[0])))
                    .isInstanceOf(ConstraintBreachException.class);
        }

        @Test
        @DisplayName("two asset files are equal by path and payload")
        void equalByPathAndPayload() {
            AssetFile file = new AssetFile("x.md", Payload.of(new byte[] {1}));

            assertThat(file).isEqualTo(new AssetFile("x.md", Payload.of(new byte[] {1})));
            assertThat(file).isNotEqualTo(new AssetFile("y.md", Payload.of(new byte[] {1})));
            assertThat(file).isNotEqualTo(new AssetFile("x.md", Payload.of(new byte[] {2})));
        }
    }

    @Test
    @DisplayName("asset files are held sorted by path, with or without a sidecar")
    void assetFilesAreSortedAndMayBeSidecarLess() {
        Pack pack = bare().assetFile(CONFIG)
                .assetFile(LOGO)
                .artifact(asset("logo", LOGO.path()))
                .build();

        assertThat(pack.assetFiles()).containsExactly(LOGO, CONFIG);
        assertThat(pack.assets()).containsExactly(asset("logo", LOGO.path()));
    }

    @Test
    @DisplayName("a sidecar-less asset file is allowed")
    void sidecarLessFileIsAllowed() {
        assertThat(bare().assetFile(CONFIG).build().assetFiles()).containsExactly(CONFIG);
    }

    @Test
    @DisplayName("two asset files with one path breach")
    void repeatedPathBreaches() {
        var builder = bare().assetFile(LOGO).assetFile(new AssetFile(LOGO.path(), Payload.of(new byte[] {9})));

        assertThatThrownBy(builder::build)
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining(LOGO.path());
    }

    @Test
    @DisplayName("an asset naming a file the pack does not hold breaches, naming the asset and the path")
    void missingFileBreaches() {
        var builder = bare().assetFile(CONFIG).artifact(asset("logo", "debrief/logo.png"));

        assertThatThrownBy(builder::build)
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("logo")
                .hasMessageContaining("debrief/logo.png");
    }

    @Test
    @DisplayName("two assets naming one file breach, naming both assets and the path")
    void twoAssetsOnOneFileBreach() {
        var builder =
                bare().assetFile(LOGO).artifact(asset("logo", LOGO.path())).artifact(asset("logo-again", LOGO.path()));

        assertThatThrownBy(builder::build)
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("logo-again")
                .hasMessageContaining("'logo'")
                .hasMessageContaining(LOGO.path());
    }

    @Test
    @DisplayName("with(directive) keeps the asset, its file and the violations, and stays valid (P3)")
    void withKeepsAssetFiles() {
        PackElementViolation violation = new PackElementViolation("broken.directive.yaml", "schema", "bad");
        Pack pack = bare().assetFile(LOGO)
                .assetFile(CONFIG)
                .artifact(asset("logo", LOGO.path()))
                .violation(violation)
                .build();

        Pack registered = pack.with(directive("DIRECTIVE_900", "new"));

        assertThat(registered.assetFiles()).containsExactly(LOGO, CONFIG);
        assertThat(registered.assets()).containsExactly(asset("logo", LOGO.path()));
        assertThat(registered.directives()).extracting(Directive::localId).containsExactly("DIRECTIVE_900");
        assertThat(registered.violations()).containsExactly(violation);
    }

    @Test
    @DisplayName("positive control: the same pack without the asset file breaches")
    void withoutTheFileTheSamePackBreaches() {
        var builder = bare().artifact(asset("logo", LOGO.path())).artifact(directive("DIRECTIVE_900", "new"));

        assertThatThrownBy(builder::build).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("with(..) replacing an artifact still moves it to the end of its kind and keeps asset files")
    void withReplacesAndMovesToTheEnd() {
        Pack pack = bare().assetFile(LOGO)
                .artifact(asset("logo", LOGO.path()))
                .artifact(directive("DIRECTIVE_001", "first"))
                .artifact(directive("DIRECTIVE_002", "second"))
                .build();

        Pack replaced = pack.with(directive("DIRECTIVE_001", "first, revised"));

        assertThat(replaced.directives())
                .extracting(Directive::localId, Directive::title)
                .containsExactly(tuple("DIRECTIVE_002", "second"), tuple("DIRECTIVE_001", "first, revised"));
        assertThat(replaced.assetFiles()).containsExactly(LOGO);
    }

    @Test
    @DisplayName("toBuilder().build() holds everything the source holds")
    void toBuilderRoundTrips() {
        Pack pack = bare().assetFile(LOGO)
                .assetFile(CONFIG)
                .artifact(asset("logo", LOGO.path()))
                .artifact(directive("DIRECTIVE_001", "first"))
                .build();

        Pack copy = pack.toBuilder().build();

        assertThat(copy).isEqualTo(pack);
        assertThat(copy.descriptor()).isEqualTo(pack.descriptor());
        assertThat(copy.artifacts()).containsExactlyElementsOf(pack.artifacts());
        assertThat(copy.assetFiles()).containsExactlyElementsOf(pack.assetFiles());
        assertThat(copy.violations()).containsExactlyElementsOf(pack.violations());
    }
}
