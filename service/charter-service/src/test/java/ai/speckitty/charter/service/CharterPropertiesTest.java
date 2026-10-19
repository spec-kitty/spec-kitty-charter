package ai.speckitty.charter.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** How {@code charter.*} binds: no configured pack is an empty list, never {@code null}; the project configuration
 * directory is optional. */
class CharterPropertiesTest {

    private static CharterProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("charter", CharterProperties.class);
    }

    @Test
    void noConfiguredPackBindsAsAnEmptyList() {
        assertThat(bind(Map.of("charter.other", "x")).packs()).isEmpty();
    }

    @Test
    void aConfiguredPackBindsWithItsNameLevelAndPath() {
        assertThat(bind(Map.of(
                                "charter.packs[0].name", "built-in",
                                "charter.packs[0].level", "built-in",
                                "charter.packs[0].path", "/packs/built-in"))
                        .packs())
                .containsExactly(new CharterProperties.Pack("built-in", "built-in", Path.of("/packs/built-in")));
    }

    @Test
    void theProjectConfigurationDirectoryBindsWhenSetAndIsNullWhenNot() {
        assertThat(bind(Map.of("charter.project-config-dir", "/work/.kittify")).projectConfigDir())
                .isEqualTo(Path.of("/work/.kittify"));
        assertThat(bind(Map.of("charter.other", "x")).projectConfigDir()).isNull();
    }
}
