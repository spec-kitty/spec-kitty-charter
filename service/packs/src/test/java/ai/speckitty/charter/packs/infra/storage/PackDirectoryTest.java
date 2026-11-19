package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PackDirectory: the name of one directory under a storage root (#583)")
class PackDirectoryTest {

    @ParameterizedTest
    @ValueSource(strings = {"pack-a", ".pack-commit-123", "pack-a.pack-old-1", "My_Pack", "01M5DEH8R0VNDTR1PF1XTVRE0A"})
    @DisplayName("any one path segment names a directory, a staging or aside name included")
    void oneSegmentNamesADirectory(String name) {
        assertThat(new PackDirectory(name).name()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ".", "..", "a/b", "../elsewhere", "a\\b", "/"})
    @DisplayName("a blank name, a separator, . and .. are refused")
    void aNameThatIsNoOneSegmentIsRefused(String name) {
        assertThatThrownBy(() -> new PackDirectory(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a registered pack's directory is named after its id")
    void aRegisteredPacksDirectoryIsNamedAfterItsId() {
        assertThat(PackDirectory.of(new PackIdentifier("pack-a"))).isEqualTo(new PackDirectory("pack-a"));
    }

    @Test
    @DisplayName("a path names the directory of its last segment")
    void aPathNamesItsLastSegment() {
        assertThat(PackDirectory.of(Path.of("/srv", "packs", ".pack-commit-7")))
                .isEqualTo(new PackDirectory(".pack-commit-7"));
    }

    @Test
    @DisplayName("a path with no file name names no directory")
    void aPathWithNoFileNameIsRefused() {
        assertThatThrownBy(() -> PackDirectory.of(Path.of("/").getRoot())).isInstanceOf(IllegalArgumentException.class);
    }
}
