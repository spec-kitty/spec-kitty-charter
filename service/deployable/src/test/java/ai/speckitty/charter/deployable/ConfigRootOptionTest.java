package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** {@code --configRootLocation <path>} is accepted beside Spring's own {@code --configRootLocation=<path>}. */
class ConfigRootOptionTest {

    @Test
    void theSpaceSeparatedFormIsJoinedAndEverythingElsePassesUnchanged() {
        assertThat(CharterServiceApplication.joinConfigRootOption(
                        new String[] {"--server.port=0", "--configRootLocation", "/srv/charter", "x"}))
                .containsExactly("--server.port=0", "--configRootLocation=/srv/charter", "x");
    }

    @Test
    void theEqualsFormIsLeftForSpring() {
        assertThat(CharterServiceApplication.joinConfigRootOption(new String[] {"--configRootLocation=/srv/charter"}))
                .containsExactly("--configRootLocation=/srv/charter");
    }

    @Test
    void theOptionWithoutAPathIsRefused() {
        assertThatThrownBy(() -> CharterServiceApplication.joinConfigRootOption(new String[] {"--configRootLocation"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a path");
    }
}
