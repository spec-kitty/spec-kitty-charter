package ai.speckitty.charter.deployable.bdd;

import ai.speckitty.charter.deployable.CharterServiceApplication;
import ai.speckitty.charter.deployable.CharterWiring;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.infra.storage.ProjectConfigSources;
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

        /**
         * The one verifier every read goes through, over {@link DriftingSources}: {@code /info} and the tactic reads
         * both see a change a scenario armed.
         */
        @Bean
        @Primary
        ConsistentRead driftingConsistentRead(DriftingSources sources) {
            return new ConsistentRead(sources);
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
