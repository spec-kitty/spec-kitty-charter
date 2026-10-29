package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
     * rank below {@code application.yaml}, so the port and the root come as arguments ({@link #startArguments}).
     */
    private static SpringApplicationBuilder shippedService(Map<String, Object> properties) {
        Map<String, Object> all = new HashMap<>(properties);
        all.put("charter.worktree-token", "0123456789abcdef0123456789abcdef0123");
        return new SpringApplicationBuilder(CharterServiceApplication.class)
                .web(WebApplicationType.NONE)
                .profiles(ContainerProperties.PROFILE)
                .properties(all);
    }

    /** A fixed port and an existing root, as a container's command line would give them. */
    private static String[] startArguments(Path root) {
        return new String[] {"--server.port=8080", CharterServiceApplication.CONFIG_ROOT_OPTION + "=" + root};
    }

    @Test
    void theServiceAsItShipsFailsTheStartUnderTheContainerProfileWithoutAllowedHosts(@TempDir Path root) {
        assertThatThrownBy(() -> shippedService(Map.of()).run(startArguments(root)))
                .hasStackTraceContaining(PROPERTY + " is required");
    }

    @Test
    void theServiceAsItShipsRefusesItsLocalRandomPortUnderTheContainerProfile(@TempDir Path root) {
        // Only the root is given: the port is the local application.yaml's 0.
        assertThatThrownBy(() -> shippedService(Map.of(PROPERTY, "charter.example.org"))
                        .run(CharterServiceApplication.CONFIG_ROOT_OPTION + "=" + root))
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
