package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.FileSystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

@DisplayName("Link tests skip where links cannot be made, and fail there when links are required (#286)")
class SymbolicLinksTest {

    private static final SymbolicLinks.LinkMaker NO_LINKS = () -> {
        throw new FileSystemException("link", "target", "Operation not permitted");
    };

    @Test
    @DisplayName("a file system without links fails the test when links are required")
    void aFileSystemWithoutLinksFailsWhenLinksAreRequired() {
        assertThatThrownBy(() -> SymbolicLinks.make(true, NO_LINKS))
                .isInstanceOf(AssertionFailedError.class)
                .hasMessageContaining(SymbolicLinks.REQUIRED)
                .hasMessageContaining("Operation not permitted");
    }

    @Test
    @DisplayName("a file system without links skips the test when links are not required")
    void aFileSystemWithoutLinksSkipsOtherwise() {
        assertThatThrownBy(() -> SymbolicLinks.make(false, NO_LINKS)).isInstanceOf(TestAbortedException.class);
    }

    @Test
    @DisplayName("a file system without links in a call that does not support them skips the same way")
    void anUnsupportedOperationIsAFileSystemWithoutLinks() {
        SymbolicLinks.LinkMaker unsupported = () -> {
            throw new UnsupportedOperationException("no links here");
        };

        assertThatThrownBy(() -> SymbolicLinks.make(true, unsupported)).isInstanceOf(AssertionFailedError.class);
        assertThatThrownBy(() -> SymbolicLinks.make(false, unsupported)).isInstanceOf(TestAbortedException.class);
    }

    @Test
    @DisplayName("on GitHub Actions the build makes link support mandatory (service.yml passes -PrequireSymlinks=true)")
    void gitHubActionsRequiresLinks() {
        assumeTrue("true".equals(System.getenv("GITHUB_ACTIONS")), "not running on GitHub Actions");

        assertThat(SymbolicLinks.required())
                .as("%s on a GitHub Actions run; pass -PrequireSymlinks=true to Gradle", SymbolicLinks.REQUIRED)
                .isTrue();
    }
}
