package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.StandardEnvironment;

/**
 * The profile guard of issue #231 as the service ships: the container profile is never a default (ADR-0041
 * decision 1), the local start binds loopback, and a container without its token does not start, naming both places
 * the token may come from.
 */
class ProfileDefaultsTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";

    @ParameterizedTest
    @ValueSource(strings = {"application.yaml", "application-container.yaml"})
    void noShippedFileNamesADefaultActiveOrIncludedProfile(String file) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(file)) {
            assertThat(in).as(file).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (Object document : new Load(LoadSettings.builder().build()).loadAllFromString(text)) {
                Object spring = document instanceof Map<?, ?> root ? root.get("spring") : null;
                Object profiles = spring instanceof Map<?, ?> section ? section.get("profiles") : null;
                assertThat(profiles).as(file + " spring.profiles").isNull();
            }
            assertThat(text).doesNotContain("profiles.default", "profiles.active", "profiles.include");
        }
    }

    /** An environment with no env vars, so the machine running the test cannot select a profile. */
    private static StandardEnvironment clean() {
        return new StandardEnvironment() {
            @Override
            public Map<String, Object> getSystemEnvironment() {
                return Map.of();
            }
        };
    }

    @Test
    void theServiceAsItShipsStartsWithoutTheContainerProfileOnLoopback(@TempDir Path root) {
        try (var context = new SpringApplicationBuilder(CharterServiceApplication.class)
                .web(WebApplicationType.NONE)
                .environment(clean())
                .properties(Map.of("charter.worktree-token", TOKEN))
                .run(CharterServiceApplication.CONFIG_ROOT_OPTION + "=" + root)) {
            assertThat(context.getEnvironment().matchesProfiles(ContainerProperties.PROFILE))
                    .isFalse();
            assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
            assertThat(context.getEnvironment().getProperty("server.address")).isEqualTo("127.0.0.1");
        }
    }

    private static SpringApplicationBuilder container() {
        return new SpringApplicationBuilder(CharterServiceApplication.class)
                .web(WebApplicationType.NONE)
                .environment(clean())
                .profiles(ContainerProperties.PROFILE);
    }

    private static String[] arguments(Path secrets, Path root) {
        return new String[] {
            "--charter.container.secrets-dir=" + secrets,
            "--charter.container.allowed-hosts=localhost",
            CharterServiceApplication.CONFIG_ROOT_OPTION + "=" + root
        };
    }

    @Test
    void underTheContainerProfileAMissingTokenFailsTheStartNamingBothForms(@TempDir Path dir) throws IOException {
        Path secrets = Files.createDirectories(dir.resolve("secrets"));
        Path root = Files.createDirectories(dir.resolve("root"));
        assertThatThrownBy(() -> container().run(arguments(secrets, root)))
                .hasStackTraceContaining("charter.worktree-token is required under the container profile")
                .hasStackTraceContaining("CHARTER_WORKTREE_TOKEN")
                .hasStackTraceContaining(secrets + "/charter.worktree-token");
    }

    @Test
    void underTheContainerProfileTheSameStartWithTheTokenFileStarts(@TempDir Path dir) throws IOException {
        Path secrets = Files.createDirectories(dir.resolve("secrets"));
        Files.writeString(secrets.resolve("charter.worktree-token"), TOKEN, StandardCharsets.UTF_8);
        Path root = Files.createDirectories(dir.resolve("root"));
        try (var context = container().run(arguments(secrets, root))) {
            assertThat(context.getEnvironment().matchesProfiles(ContainerProperties.PROFILE))
                    .isTrue();
        }
    }
}
