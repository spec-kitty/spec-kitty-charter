package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PathsTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "../outside.md",
                "docs/../../outside.md",
                "/etc/passwd",
                "C:\\x.md",
                "C:/x.md",
                "a\\..\\..\\x.md",
                "\\\\host\\x.md"
            })
    void aBodyPathThatLeavesThePackRootIsAFaultInTheSharedWording(String path) {
        assertThat(Paths.bodyPathEscape("directive:x", Optional.of(path)))
                .contains("node directive:x body_path '" + path + "' escapes the pack root");
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs/x.md", "x.md", "docs/./x.md", "directives/b..md", "./b.md"})
    void aBodyPathThatStaysInsideIsNoFault(String path) {
        assertThat(Paths.bodyPathEscape("directive:x", Optional.of(path))).isEmpty();
    }

    @Test
    void anAbsentBodyPathIsNoFault() {
        assertThat(Paths.bodyPathEscape("directive:x", Optional.empty())).isEmpty();
    }

    @Test
    void anAssetPathEscapeNamesThePathAndTheAssetsRoot() {
        assertThat(Paths.assetPathEscape("../secret.png"))
                .isEqualTo("asset path '../secret.png' escapes the pack's assets/ root");
    }

    @Test
    void anOrgFragmentIsTheRootFragmentOrANestedOne() {
        assertThat(Paths.isFragment("drg/fragment.yaml")).isTrue();
        assertThat(Paths.isFragment("nested/drg/fragment.yaml")).isTrue();
        assertThat(Paths.isFragment("xdrg/fragment.yaml")).isFalse();
        assertThat(Paths.isFragment("drg/fragment.yml")).isFalse();
    }
}
