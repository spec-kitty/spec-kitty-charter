package ai.speckitty.charter.deployable.bdd;

import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.deployable.CharterServiceApplication;
import ai.speckitty.charter.deployable.CharterWiring;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
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
        properties = {
            "charter.worktree-id=scenario-worktree",
            "charter.worktree-token=" + ScenarioHttp.TOKEN,
            // The registration staging directory is under the temporary directory, never the user's home.
            "charter.registration.staging-dir=${java.io.tmpdir}/charter-scenario-staging"
        })
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

        /**
         * The activation register of the scenarios: in memory, emptied before each scenario by {@code ActivationSteps}.
         * Without it the production wiring would store the documents under the default configuration root, in the
         * user's home directory.
         */
        @Bean
        @Primary
        InMemoryActivationRepository scenarioActivationRepository() {
            return new InMemoryActivationRepository();
        }

        /** The guide fetcher every registration goes through: armed per scenario, it opens no socket. */
        @Bean
        @Primary
        StubGuideFetcher stubGuideFetcher() {
            return new StubGuideFetcher();
        }

        /** The packs the steps wrote and the pack domain a whole-pack registration writes to (ScenarioLocations). */
        @Bean
        @Primary
        DomainRepositoryService.Locations scenarioPackLocations(ScenarioPacks packs) {
            return new ScenarioLocations(packs);
        }

        /** Holds registration jobs where a scenario asks (the latch seam of T077); production lets every job through. */
        @Bean
        @Primary
        HoldableGate scenarioJobGate() {
            return new HoldableGate();
        }

        /** The clock of the registration jobs: real time, moved on by the step that lets the retention hour pass. */
        @Bean
        @Primary
        ScenarioClock scenarioRegistrationClock() {
            return new ScenarioClock();
        }

        @Bean
        @Primary
        CharterWiring.ProjectConfigLocation scenarioProjectConfigLocation(ScenarioPacks packs) {
            return packs::configDir;
        }
    }
}
