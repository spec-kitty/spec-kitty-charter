package ai.speckitty.charter.service.bdd;

import ai.speckitty.charter.service.CharterServiceApplication;
import ai.speckitty.charter.service.CharterWiring;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** One Spring context for every scenario; each scenario supplies its own packs through {@link ScenarioPacks}. */
@CucumberContextConfiguration
@SpringBootTest(classes = {CharterServiceApplication.class, CucumberSpringConfiguration.Packs.class})
@AutoConfigureMockMvc
public class CucumberSpringConfiguration {

    @TestConfiguration(proxyBeanMethods = false)
    static class Packs {
        @Bean
        ScenarioPacks scenarioPacks() {
            return new ScenarioPacks();
        }

        @Bean
        @Primary
        CharterWiring.PackLocations scenarioPackLocations(ScenarioPacks packs) {
            return packs::locations;
        }
    }
}
