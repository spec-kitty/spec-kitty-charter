package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

/**
 * How {@code charter.container.*} binds (ADR-0041 decision 2): under the {@code container} profile a missing or invalid
 * value fails the start naming the property; without the profile nothing in the namespace is bound or checked.
 */
class ContainerPropertiesTest {

    private static final String PROPERTY = "charter.container.allowed-hosts";
    private static final String CONTAINER = "spring.profiles.active=" + ContainerProperties.PROFILE;

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ContainerWiring.class);

    @Test
    void underTheContainerProfileTheAllowedHostsBindInOrder() {
        runner.withPropertyValues(CONTAINER, PROPERTY + "=charter.example.org,charter")
                .run(context -> assertThat(
                                context.getBean(ContainerProperties.class).allowedHosts())
                        .containsExactly("charter.example.org", "charter"));
    }

    @Test
    void underTheContainerProfileMissingAllowedHostsFailTheStartNamingTheProperty() {
        runner.withPropertyValues(CONTAINER)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining(PROPERTY + " is required"));
    }

    @Test
    void underTheContainerProfileAnEmptyListFailsTheStartNamingTheProperty() {
        runner.withPropertyValues(CONTAINER, PROPERTY + "=")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining(PROPERTY + " is required"));
    }

    @Test
    void underTheContainerProfileAWhitespaceOnlyEntryFailsTheStartNamingThePropertyAndTheEntry() {
        // Through a property source of its own: withPropertyValues trims values, so "  " would arrive as "".
        // One property source holds the whole list: Spring binds a list's indices from a single source.
        runner.withPropertyValues(CONTAINER)
                .withInitializer(context -> context.getEnvironment()
                        .getPropertySources()
                        .addFirst(new MapPropertySource(
                                "whitespace", Map.of(PROPERTY + "[0]", "charter.example.org", PROPERTY + "[1]", "  "))))
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining(PROPERTY + " must not hold a blank host")
                        .hasStackTraceContaining("allowedHosts[1]"));
    }

    @Test
    void withoutTheContainerProfileNothingIsBoundAndAnInvalidValueDoesNotFailTheStart() {
        runner.withPropertyValues(PROPERTY + "[0]= ")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ContainerProperties.class));
    }

    @Test
    void anotherProfileAloneDoesNotBindTheRecord() {
        runner.withPropertyValues("spring.profiles.active=test")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ContainerProperties.class));
    }

    @Test
    void theContainerProfileBesideAnotherStillValidates() {
        runner.withPropertyValues("spring.profiles.active=test," + ContainerProperties.PROFILE)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining(PROPERTY + " is required"));
    }

    /**
     * The service as it ships, without a web server, under the container profile with the token. Builder properties
     * rank below {@code application.yaml}, so the port, the root and the secrets directory come as arguments ({@link #startArguments}).
     */
    private static SpringApplicationBuilder shippedService(Map<String, Object> properties) {
        Map<String, Object> all = new HashMap<>(properties);
        all.put("charter.worktree-token", "0123456789abcdef0123456789abcdef0123");
        return new SpringApplicationBuilder(CharterServiceApplication.class)
                .web(WebApplicationType.NONE)
                .profiles(ContainerProperties.PROFILE)
                .properties(all);
    }

    /**
     * A fixed port, an existing root and an empty secrets directory, as a container's command line would give them.
     * The profile's own file requires the secrets directory (ADR-0041 decision 3).
     */
    private static String[] startArguments(Path root) {
        return startArguments(root, 8080);
    }

    private static String[] startArguments(Path root, int port) {
        return new String[] {
            "--server.port=" + port,
            CharterServiceApplication.CONFIG_ROOT_OPTION + "=" + root,
            "--charter.container.secrets-dir=" + secretsDirectory(root)
        };
    }

    private static Path secretsDirectory(Path root) {
        try {
            return Files.createDirectories(root.resolve("secrets"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void theServiceAsItShipsFailsTheStartUnderTheContainerProfileWithoutAllowedHosts(@TempDir Path root) {
        assertThatThrownBy(() -> shippedService(Map.of()).run(startArguments(root)))
                .hasStackTraceContaining(PROPERTY + " is required");
    }

    @Test
    void theServiceAsItShipsStillRefusesAnExplicitRandomPortUnderTheContainerProfile(@TempDir Path root) {
        // The profile's file sets 8080; a port of 0 given on top of it is still refused (ADR-0041 decision 4).
        assertThatThrownBy(() ->
                        shippedService(Map.of(PROPERTY, "charter.example.org")).run(startArguments(root, 0)))
                .hasStackTraceContaining("server.port must be a fixed port under the container profile");
    }

    @Test
    void theServiceAsItShipsBindsTheRecordUnderTheContainerProfile(@TempDir Path root) {
        try (var context =
                shippedService(Map.of(PROPERTY, "charter.example.org")).run(startArguments(root))) {
            assertThat(context.getBean(ContainerProperties.class).allowedHosts())
                    .containsExactly("charter.example.org");
        }
    }
}
