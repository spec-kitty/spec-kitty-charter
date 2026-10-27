package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** How {@code charter.*} binds: the worktree id and the project configuration directory are optional. Where the packs
 * are is the storage layer's {@code charter.config-root} (ADR-0038); the former {@code charter.packs[*]} /
 * {@code charter.storage.*} properties are retired and fail the start. */
class CharterPropertiesTest {

    private static CharterProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("charter", CharterProperties.class);
    }

    @Test
    void theWorktreeIdBindsWhenSetAndIsNullWhenNot() {
        assertThat(bind(Map.of("charter.worktree-id", "wt-7")).worktreeId()).isEqualTo("wt-7");
        assertThat(bind(Map.of("charter.other", "x")).worktreeId()).isNull();
    }

    @Test
    void theProjectConfigurationDirectoryBindsWhenSetAndIsNullWhenNot() {
        assertThat(bind(Map.of("charter.project-config-dir", "/work/.kittify")).projectConfigDir())
                .isEqualTo(Path.of("/work/.kittify"));
        assertThat(bind(Map.of("charter.other", "x")).projectConfigDir()).isNull();
    }
}
