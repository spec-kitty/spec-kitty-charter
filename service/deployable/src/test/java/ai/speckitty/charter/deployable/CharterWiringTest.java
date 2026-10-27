package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackValidation;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import ai.speckitty.charter.packs.infra.storage.config.StorageConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** How {@link CharterWiring} takes its pack locations from the storage layer's {@code charter.config-root}. */
class CharterWiringTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CharterProperties.class)
    @Import({CharterWiring.class, StorageConfiguration.class})
    static class Wiring {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Wiring.class);

    private static void pack(Path root, String name, String level) throws IOException {
        Path dir = Files.createDirectories(root.resolve(ConfigRoot.PACKS).resolve(name));
        Files.writeString(
                dir.resolve("pack.yaml"),
                "pack_id: " + name + "\npack_version: 1.0.0\nname: " + name + "\npack_level: " + level + "\n");
    }

    @Test
    void eachPackOfThePackDomainIsALocationAtItsDeclaredLevel(@TempDir Path root) throws IOException {
        pack(root, "qa", "org");
        pack(root, "built-in", "built-in");
        pack(root, "my-project", "project");

        runner.withPropertyValues("charter.config-root=" + root)
                .run(context -> assertThat(context.getBean(CharterWiring.PackLocations.class)
                                .get())
                        .containsExactly(
                                new PackLocation("built-in", PackLevel.BUILT_IN, root.resolve("packs/built-in")),
                                new PackLocation("my-project", PackLevel.PROJECT, root.resolve("packs/my-project")),
                                new PackLocation("qa", PackLevel.ORG, root.resolve("packs/qa"))));
    }

    @Test
    void theReadPathAndTheStoreShareOneSourceOfLocations(@TempDir Path root) throws IOException {
        pack(root, "qa", "org");

        runner.withPropertyValues("charter.config-root=" + root).run(context -> {
            List<PackLocation> before =
                    context.getBean(CharterWiring.PackLocations.class).get();
            pack(root, "audit", "org");
            assertThat(context.getBean(CharterWiring.PackLocations.class).get())
                    .hasSize(before.size() + 1)
                    .isEqualTo(context.getBean(DomainRepositoryService.Locations.class)
                            .all());
        });
    }

    @Test
    void aRelativeRootIsFixedAgainstTheWorkingDirectory() {
        runner.withPropertyValues("charter.config-root=relative/root")
                .run(context -> assertThat(context.getBean(ConfigRoot.class).root())
                        .isEqualTo(Path.of("relative/root").toAbsolutePath().normalize()));
    }

    @Test
    void aRootThatDoesNotExistYetServesNoPacksAndIsNotCreated(@TempDir Path directory) {
        Path missing = directory.resolve("not-yet");

        runner.withPropertyValues("charter.config-root=" + missing).run(context -> {
            assertThat(context.getBean(CharterWiring.PackLocations.class).get()).isEmpty();
            assertThat(missing).doesNotExist();
        });
    }

    @Test
    void aRootThatIsAFileOrEmptyFailsTheStartNamingTheProperty(@TempDir Path directory) throws IOException {
        Path file = Files.writeString(directory.resolve("a-file"), "x");

        for (String configured : List.of(file.toString(), "")) {
            runner.withPropertyValues("charter.config-root=" + configured)
                    .run(context -> assertThat(context)
                            .hasFailed()
                            .getFailure()
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("charter.config-root")
                            .hasMessageNotContaining(file.toString()));
        }
    }

    @Test
    void noConfiguredRootMeansNoPacks() {
        runner.run(context -> assertThat(
                        context.getBean(CharterWiring.PackLocations.class).get())
                .isEmpty());
    }

    @Test
    void aRetiredPackPropertyFailsTheStartInsteadOfServingNothing(@TempDir Path root) {
        for (String retired : List.of("charter.packs[0].path=/packs/qa", "charter.storage.root=/packs")) {
            runner.withPropertyValues("charter.config-root=" + root, retired)
                    .run(context -> assertThat(context)
                            .hasFailed()
                            .getFailure()
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("no longer read")
                            .hasMessageContaining("--configRootLocation"));
        }
    }

    @Test
    void theWorktreeIdIsTheConfiguredOneOrAStableIdDerivedFromTheConfigRoot(@TempDir Path root) throws IOException {
        runner.withPropertyValues("charter.worktree-id=wt-7")
                .run(context -> assertThat(
                                context.getBean(ServiceInfoQuery.class).worktree())
                        .isEqualTo("wt-7"));
        String derived = CharterWiring.derivedWorktreeId(new ConfigRoot(Path.of("/srv/charter")));
        assertThat(derived).matches("[0-9a-f]{16}").doesNotContain("srv");
        assertThat(CharterWiring.derivedWorktreeId(new ConfigRoot(Path.of("/srv/charter"))))
                .isEqualTo(derived);
        assertThat(CharterWiring.derivedWorktreeId(new ConfigRoot(Path.of("/srv/other"))))
                .isNotEqualTo(derived);

        pack(root, "qa", "org");
        String before = CharterWiring.derivedWorktreeId(new ConfigRoot(root));
        pack(root, "audit", "org");
        assertThat(CharterWiring.derivedWorktreeId(new ConfigRoot(root)))
                .as("adding a pack does not change the id")
                .isEqualTo(before);
        runner.withPropertyValues("charter.config-root=" + root)
                .run(context -> assertThat(
                                context.getBean(ServiceInfoQuery.class).worktree())
                        .isEqualTo(before));
    }

    @Test
    void aConfiguredProjectConfigurationDirectoryIsTheLocationAndNoneMeansNoLocation(@TempDir Path directory) {
        runner.withPropertyValues("charter.project-config-dir=" + directory)
                .run(context -> assertThat(context.getBean(CharterWiring.ProjectConfigLocation.class)
                                .get())
                        .contains(directory));
        runner.run(context -> assertThat(context.getBean(CharterWiring.ProjectConfigLocation.class)
                        .get())
                .isEmpty());
    }

    @Test
    void aProjectConfigurationDirectoryThatIsNotThereFailsTheStartNamingTheProperty(@TempDir Path directory)
            throws IOException {
        Path missing = directory.resolve("no-such-dir");
        Path file = Files.writeString(directory.resolve("a-file"), "x");

        for (Path configured : List.of(missing, file)) {
            runner.withPropertyValues("charter.project-config-dir=" + configured)
                    .run(context -> assertThat(context)
                            .hasFailed()
                            .getFailure()
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("charter.project-config-dir")
                            .hasMessageNotContaining(configured.toString()));
        }
    }

    @Test
    void theServiceInfoFingerprintFollowsTheConfiguredDirectory(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("config.yaml"), "a");
        runner.withPropertyValues("charter.project-config-dir=" + directory).run(context -> {
            ServiceInfoQuery query = context.getBean(ServiceInfoQuery.class);
            String before = query.answer().sourceFingerprint();
            Files.writeString(directory.resolve("config.yaml"), "b");
            assertThat(query.answer().sourceFingerprint()).isNotEqualTo(before);
        });
    }

    @Test
    void validateModeIsWiredOverTheFileBasedInspector(@TempDir Path dir) throws IOException {
        Files.writeString(
                dir.resolve("pack.yaml"),
                "pack_id: 01ABC\npack_version: 1.0.0\nname: qa\npack_level: org\nmystery: 1\n");
        Files.createDirectories(dir.resolve("drg"));
        Files.writeString(dir.resolve("drg/fragment.yaml"), "nodes: []\nedges: []\n");

        runner.run(context -> assertThat(context.getBean(PackValidation.class)
                        .validate(new PackRoot("qa", Optional.of(PackLevel.ORG), dir.toString()))
                        .findings())
                .extracting(f -> f.code().name())
                .containsExactly("PACK_UNKNOWN_KEY"));
    }
}
