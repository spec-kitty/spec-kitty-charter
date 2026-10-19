package ai.speckitty.charter.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.read.ServiceInfoQuery;
import ai.speckitty.charter.yaml.PackLocation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** How {@link CharterWiring} turns {@code charter.packs[*]} into pack locations. */
class CharterWiringTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CharterProperties.class)
    @Import(CharterWiring.class)
    static class Wiring {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Wiring.class);

    @Test
    void eachConfiguredPackBecomesALocationInConfigurationOrder() {
        runner.withPropertyValues(
                        "charter.packs[0].name=qa",
                        "charter.packs[0].level=org",
                        "charter.packs[0].path=/packs/qa",
                        "charter.packs[1].name=built-in",
                        "charter.packs[1].level=built-in",
                        "charter.packs[1].path=/packs/built-in")
                .run(context -> assertThat(context.getBean(CharterWiring.PackLocations.class)
                                .get())
                        .containsExactly(
                                new PackLocation("qa", PackLevel.ORG, Path.of("/packs/qa")),
                                new PackLocation("built-in", PackLevel.BUILT_IN, Path.of("/packs/built-in"))));
    }

    @Test
    void aPackAtAnUnknownLevelFailsTheStartWithAMessageThatNamesIt() {
        runner.withPropertyValues(
                        "charter.packs[0].name=qa", "charter.packs[0].level=galaxy", "charter.packs[0].path=/packs/qa")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("charter.packs: unknown level 'galaxy' for qa"));
    }

    @Test
    void theWorktreeIdIsTheConfiguredOneOrAStableIdDerivedFromThePackDirectories() {
        runner.withPropertyValues("charter.worktree-id=wt-7")
                .run(context -> assertThat(
                                context.getBean(ServiceInfoQuery.class).worktree())
                        .isEqualTo("wt-7"));
        CharterProperties packs = new CharterProperties(
                List.of(new CharterProperties.Pack("qa", "org", Path.of("/packs/qa"))), null, null);
        String derived = CharterWiring.derivedWorktreeId(packs);
        assertThat(derived).matches("[0-9a-f]{16}").doesNotContain("packs");
        assertThat(CharterWiring.derivedWorktreeId(packs)).isEqualTo(derived);
        assertThat(CharterWiring.derivedWorktreeId(new CharterProperties(
                        List.of(new CharterProperties.Pack("qa", "org", Path.of("/other/qa"))), null, null)))
                .isNotEqualTo(derived);
        runner.withPropertyValues(
                        "charter.packs[0].name=qa", "charter.packs[0].level=org", "charter.packs[0].path=/packs/qa")
                .run(context -> assertThat(
                                context.getBean(ServiceInfoQuery.class).worktree())
                        .isEqualTo(derived));
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
}
