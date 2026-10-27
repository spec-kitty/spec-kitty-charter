package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackValidation;
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

/** How {@link CharterWiring} takes its pack locations from the storage layer's {@code charter.storage.root}. */
class CharterWiringTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CharterProperties.class)
    @Import({CharterWiring.class, StorageConfiguration.class})
    static class Wiring {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Wiring.class);

    @Test
    void eachPackUnderTheStorageRootIsALocationTheBuiltInPackFirst(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("qa"));
        Files.createDirectories(root.resolve("built-in"));

        runner.withPropertyValues("charter.storage.root=" + root)
                .run(context -> assertThat(context.getBean(CharterWiring.PackLocations.class)
                                .get())
                        .containsExactly(
                                new PackLocation("built-in", PackLevel.BUILT_IN, root.resolve("built-in")),
                                new PackLocation("qa", PackLevel.ORG, root.resolve("qa"))));
    }

    @Test
    void theReadPathAndTheStoreShareOneSourceOfLocations(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("qa"));

        runner.withPropertyValues("charter.storage.root=" + root).run(context -> {
            List<PackLocation> before =
                    context.getBean(CharterWiring.PackLocations.class).get();
            Files.createDirectories(root.resolve("audit"));
            assertThat(context.getBean(CharterWiring.PackLocations.class).get())
                    .hasSize(before.size() + 1)
                    .isEqualTo(context.getBean(DomainRepositoryService.Locations.class)
                            .all());
        });
    }

    @Test
    void oneConsistentReadOverTheProjectConfigSourcesServesEveryRead() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(ConsistentRead.class)).hasSize(1);
            assertThat(context.getBean(ConsistentRead.class)).isNotNull();
        });
    }

    @Test
    void aStorageRootThatIsNotADirectoryFailsTheStartNamingTheProperty(@TempDir Path directory) throws IOException {
        Path missing = directory.resolve("no-such-dir");
        Path file = Files.writeString(directory.resolve("a-file"), "x");

        for (Path configured : List.of(missing, file)) {
            runner.withPropertyValues("charter.storage.root=" + configured)
                    .run(context -> assertThat(context)
                            .hasFailed()
                            .getFailure()
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("charter.storage.root")
                            .hasMessageNotContaining(configured.toString()));
        }
    }

    @Test
    void noStorageRootMeansNoPacks() {
        runner.run(context -> assertThat(
                        context.getBean(CharterWiring.PackLocations.class).get())
                .isEmpty());
        runner.withPropertyValues("charter.storage.root=")
                .run(context -> assertThat(context.getBean(CharterWiring.PackLocations.class)
                                .get())
                        .isEmpty());
    }

    @Test
    void theWorktreeIdIsTheConfiguredOneOrAStableIdDerivedFromThePackDirectories(@TempDir Path root)
            throws IOException {
        runner.withPropertyValues("charter.worktree-id=wt-7")
                .run(context -> assertThat(
                                context.getBean(ServiceInfoQuery.class).worktree())
                        .isEqualTo("wt-7"));
        List<PackLocation> packs = List.of(new PackLocation("qa", PackLevel.ORG, Path.of("/packs/qa")));
        String derived = CharterWiring.derivedWorktreeId(packs);
        assertThat(derived).matches("[0-9a-f]{16}").doesNotContain("packs");
        assertThat(CharterWiring.derivedWorktreeId(packs)).isEqualTo(derived);
        assertThat(CharterWiring.derivedWorktreeId(
                        List.of(new PackLocation("qa", PackLevel.ORG, Path.of("/other/qa")))))
                .isNotEqualTo(derived);
        Files.createDirectories(root.resolve("qa"));
        String fromRoot =
                CharterWiring.derivedWorktreeId(List.of(new PackLocation("qa", PackLevel.ORG, root.resolve("qa"))));
        runner.withPropertyValues("charter.storage.root=" + root)
                .run(context -> assertThat(
                                context.getBean(ServiceInfoQuery.class).worktree())
                        .isEqualTo(fromRoot));
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
