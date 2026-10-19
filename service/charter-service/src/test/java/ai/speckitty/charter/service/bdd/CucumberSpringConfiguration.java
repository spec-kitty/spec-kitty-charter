package ai.speckitty.charter.service.bdd;

import ai.speckitty.charter.service.CharterServiceApplication;
import ai.speckitty.charter.service.CharterWiring;
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
        properties = "charter.worktree-id=scenario-worktree")
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
            return new ScenarioHttp(mvc);
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
