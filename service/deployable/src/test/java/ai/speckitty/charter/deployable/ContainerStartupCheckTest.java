package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.util.Throwables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The container profile's start-up rules on keys outside {@code charter.container.*} (ADR-0041 decisions 4 to 6): each
 * breach fails the start naming the property and never its value; without the profile none of them applies.
 */
class ContainerStartupCheckTest {

    private static final String CONTAINER = "spring.profiles.active=" + ContainerProperties.PROFILE;

    @TempDir
    private Path root;

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ContainerStartupCheck.class);

    /** The container profile with every rule met; each test breaks one. */
    private ApplicationContextRunner container() {
        return runner.withPropertyValues(CONTAINER, "server.port=8080", "charter.config-root=" + root);
    }

    @Test
    void underTheContainerProfileAFixedPortAndAnExistingAbsoluteRootStart() {
        container().run(context -> assertThat(context).hasNotFailed().hasSingleBean(ContainerStartupCheck.class));
    }

    @Test
    void underTheContainerProfileAnUnsetPortIsSpringsFixedDefaultAndStarts() {
        runner.withPropertyValues(CONTAINER, "charter.config-root=" + root)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void underTheContainerProfilePortZeroFailsTheStartNamingTheProperty() {
        container()
                .withPropertyValues("server.port=0")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("server.port must be a fixed port under the container profile"));
    }

    @Test
    void underTheContainerProfileAMissingConfigRootFailsTheStartNamingTheProperty() {
        runner.withPropertyValues(CONTAINER, "server.port=8080")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("charter.config-root is required under the container profile"));
    }

    @Test
    void underTheContainerProfileAConfigRootThatDoesNotExistFailsTheStartWithoutItsValue() {
        String missing = root.resolve("not-mounted").toString();
        container()
                .withPropertyValues("charter.config-root=" + missing)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("charter.config-root must name an existing absolute directory")
                        .satisfies(failure ->
                                assertThat(Throwables.getStackTrace(failure)).doesNotContain(missing)));
    }

    @Test
    void underTheContainerProfileARelativeConfigRootFailsTheStart() {
        container()
                .withPropertyValues("charter.config-root=charter")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("charter.config-root must name an existing absolute directory"));
    }

    @Test
    void underTheContainerProfileAConfigRootThatIsAFileFailsTheStart() throws IOException {
        Path file = Files.writeString(root.resolve("a-file"), "");
        container()
                .withPropertyValues("charter.config-root=" + file)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("charter.config-root must name an existing absolute directory")
                        .satisfies(failure ->
                                assertThat(Throwables.getStackTrace(failure)).doesNotContain(file.toString())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void underTheContainerProfileTheDevelopmentFlagFailsTheStartWhateverItsValue(String value) {
        container()
                .withPropertyValues(GuardWiring.INSECURE_PROPERTY + "=" + value)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining(
                                "charter.insecure-no-token must not be set under the container profile"));
    }

    @Test
    void underTheContainerProfileAProjectConfigDirFailsTheStart() {
        container()
                .withPropertyValues("charter.project-config-dir=" + root)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining(
                                "charter.project-config-dir must not be set under the container profile"));
    }

    @Test
    void withoutTheContainerProfileNoneOfTheRulesApplies() {
        runner.withPropertyValues(
                        "server.port=0",
                        "charter.config-root=charter",
                        GuardWiring.INSECURE_PROPERTY + "=true",
                        "charter.project-config-dir=" + root)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ContainerStartupCheck.class));
    }
}
