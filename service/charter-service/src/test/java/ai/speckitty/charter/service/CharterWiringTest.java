package ai.speckitty.charter.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.read.ServiceInfoQuery;
import ai.speckitty.charter.yaml.PackLocation;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
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
        CharterProperties packs =
                new CharterProperties(List.of(new CharterProperties.Pack("qa", "org", Path.of("/packs/qa"))), null);
        String derived = CharterWiring.derivedWorktreeId(packs);
        assertThat(derived).matches("[0-9a-f]{16}").doesNotContain("packs");
        assertThat(CharterWiring.derivedWorktreeId(packs)).isEqualTo(derived);
        assertThat(CharterWiring.derivedWorktreeId(new CharterProperties(
                        List.of(new CharterProperties.Pack("qa", "org", Path.of("/other/qa"))), null)))
                .isNotEqualTo(derived);
        runner.withPropertyValues(
                        "charter.packs[0].name=qa", "charter.packs[0].level=org", "charter.packs[0].path=/packs/qa")
                .run(context -> assertThat(
                                context.getBean(ServiceInfoQuery.class).worktree())
                        .isEqualTo(derived));
    }
}
