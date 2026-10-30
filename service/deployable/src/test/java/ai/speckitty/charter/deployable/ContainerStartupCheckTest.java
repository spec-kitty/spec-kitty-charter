package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.assertj.core.util.Throwables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.ConfigTreePropertySource;
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

    // The secrets directory may set only its own keys and must be safe (ADR-0041 decision 3, research R10).

    private static final String PLANTED = "planted-value-0123456789";

    /** A config tree in a directory of its own, with these files, as the profile's import would add it. */
    private ApplicationContextRunner withConfigTree(ApplicationContextRunner base, Path dir, String... names)
            throws IOException {
        Files.createDirectories(dir);
        for (String name : names) {
            Files.writeString(dir.resolve(name), PLANTED);
        }
        return base.withInitializer(context -> context.getEnvironment()
                .getPropertySources()
                .addLast(new ConfigTreePropertySource("Config tree test", dir)));
    }

    @Test
    void underTheContainerProfileTheTokenAndContainerKeysInTheSecretsDirectoryStart() throws IOException {
        withConfigTree(
                        container(),
                        root.resolve("secrets"),
                        "charter.worktree-token",
                        "charter.container.allowed-hosts")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void underTheContainerProfileAnEmptySecretsDirectoryStarts() throws IOException {
        withConfigTree(container(), root.resolve("secrets"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void underTheContainerProfileAForeignFileInTheSecretsDirectoryFailsTheStartNamingItNotItsContent()
            throws IOException {
        Path secrets = root.resolve("secrets");
        withConfigTree(container(), secrets, "charter.worktree-token", "server.address")
                .run(context -> {
                    assertThat(context).hasFailed();
                    String failure = Throwables.getStackTrace(context.getStartupFailure());
                    assertThat(failure).contains("server.address").contains(secrets.toString());
                    assertThat(failure).doesNotContain(PLANTED);
                });
    }

    @Test
    void underTheContainerProfileEveryForeignFileIsNamedInOrder() throws IOException {
        withConfigTree(container(), root.resolve("secrets"), "spring.config.import", "logging.level.root")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("remove logging.level.root, spring.config.import"));
    }

    @Test
    void underTheContainerProfileAKeyThatOnlyLooksLikeTheNamespaceIsForeign() throws IOException {
        withConfigTree(container(), root.resolve("secrets"), "charter.containerx.allowed-hosts")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("charter.containerx.allowed-hosts"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"relative/secrets", "/"})
    void underTheContainerProfileAnUnsafeSecretsDirectoryFailsTheStartNamingTheProperty(String dir) {
        container()
                .withPropertyValues("charter.container.secrets-dir=" + dir)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("charter.container.secrets-dir must name an absolute directory"));
    }

    @Test
    void underTheContainerProfileABlankSecretsDirectoryFailsTheStartNamingTheProperty() {
        container()
                .withPropertyValues("charter.container.secrets-dir=")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("charter.container.secrets-dir must name an absolute directory"));
    }

    @Test
    void underTheContainerProfileASecretsDirectoryOthersCanWriteFailsTheStart() throws IOException {
        assumeTrue(
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX permissions are a platform capability");
        Path secrets = Files.createDirectories(root.resolve("secrets"));
        Files.setPosixFilePermissions(secrets, PosixFilePermissions.fromString("rwxrwxrwx"));
        container()
                .withPropertyValues("charter.container.secrets-dir=" + secrets)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("others can write to")
                        .hasStackTraceContaining(secrets.toString()));
    }

    @Test
    void underTheContainerProfileASecretsDirectoryOnlyItsOwnerCanWriteStarts() throws IOException {
        Path secrets = Files.createDirectories(root.resolve("secrets"));
        container()
                .withPropertyValues("charter.container.secrets-dir=" + secrets)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void withoutTheContainerProfileAForeignFileInAConfigTreeDoesNotFailTheStart() throws IOException {
        withConfigTree(runner, root.resolve("secrets"), "server.address")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ContainerStartupCheck.class));
    }
}
