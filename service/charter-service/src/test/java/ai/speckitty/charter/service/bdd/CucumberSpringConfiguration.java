package ai.speckitty.charter.service.bdd;

import ai.speckitty.charter.read.ConsistentLoad;
import ai.speckitty.charter.service.CharterServiceApplication;
import ai.speckitty.charter.service.CharterWiring;
import ai.speckitty.charter.yaml.FileSystemCharterPackRepository;
import ai.speckitty.charter.yaml.ProjectConfigSources;
import io.cucumber.spring.CucumberContextConfiguration;
import io.cucumber.spring.ScenarioScope;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

/** One Spring context for every scenario; each scenario supplies its own packs through {@link ScenarioPacks}. */
@CucumberContextConfiguration
@SpringBootTest(
        classes = {CharterServiceApplication.class, CucumberSpringConfiguration.Packs.class},
        properties = {"charter.worktree-id=scenario-worktree", "charter.worktree-token=" + ScenarioHttp.TOKEN})
@AutoConfigureMockMvc
public class CucumberSpringConfiguration {

    @TestConfiguration(proxyBeanMethods = false)
    static class Packs {
        @Bean
        ScenarioPacks scenarioPacks() {
            return new ScenarioPacks();
        }

        @Bean
        @ScenarioScope
        ScenarioHttp scenarioHttp(MockMvc mvc) {
            return new ScenarioHttp(mvc, ScenarioHttp.TOKEN);
        }

        /** The real digest of the inputs, with the one change a scenario arms (see {@link DriftingSources}). */
        @Bean
        DriftingSources driftingSources(ProjectConfigSources real) {
            return new DriftingSources(real);
        }

        /** The real repository and the production {@link ConsistentLoad}, over the sources the seam wraps. */
        @Bean
        @Primary
        ConsistentLoad driftingConsistentLoad(FileSystemCharterPackRepository packs, DriftingSources sources) {
            return new ConsistentLoad(packs, sources);
        }

        @Bean
        @Primary
        CharterWiring.PackLocations scenarioPackLocations(ScenarioPacks packs) {
            return packs::locations;
        }

        @Bean
        @Primary
        CharterWiring.ProjectConfigLocation scenarioProjectConfigLocation(ScenarioPacks packs) {
            return packs::configDir;
        }
    }
}
