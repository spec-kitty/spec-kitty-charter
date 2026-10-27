package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.TacticRepository;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The whole service over the test configuration root: {@link ConfigRootTestConfiguration} replaces the production
 * root with a copy of {@code src/test/resources/config-root}, and every pack bean follows it.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "charter.worktree-token=0123456789abcdef0123456789abcdef0123")
@Import(ConfigRootTestConfiguration.class)
class ConfigRootWiringTest {

    @Autowired
    ConfigRoot root;

    @Autowired
    CharterWiring.PackLocations packs;

    @Autowired
    TacticRepository tactics;

    @Test
    void theTestRootIsACopyOutsideTheRepositoryWithADirectoryPerDomain() {
        assertThat(root.root().toString()).doesNotContain("src" + File.separator + "test");
        assertThat(root.packs()).isDirectory();
        assertThat(root.activations()).isDirectory();
    }

    @Test
    void thePacksOfTheTestRootAreServedAtTheirDeclaredLevels() {
        assertThat(packs.get())
                .containsExactly(
                        new PackLocation(
                                "built-in", PackLevel.BUILT_IN, root.packs().resolve("built-in")),
                        new PackLocation("qa", PackLevel.ORG, root.packs().resolve("qa")));
    }

    @Test
    void aTacticOfTheTestRootReadsThroughTheWiredRepository() {
        assertThat(tactics.load(new PackIdentifier("built-in"), new TacticIdentifier("fixture-inline-temp")))
                .hasValueSatisfying(tactic -> assertThat(tactic.name()).isEqualTo("Fixture Inline Temp"));
    }

    @Test
    void theRootNeverPointsAtTheProductionDefault() {
        assertThat(root.root()).isNotEqualTo(Path.of(System.getProperty("user.home"), ".spec-kitty", "charter"));
    }
}
