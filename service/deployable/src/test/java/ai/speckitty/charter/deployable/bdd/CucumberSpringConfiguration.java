package ai.speckitty.charter.deployable.bdd;

import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.deployable.CharterServiceApplication;
import ai.speckitty.charter.deployable.CharterWiring;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.infra.fetch.git.GitVersion;
import ai.speckitty.charter.packs.infra.fetch.git.GitVersionCheck;
import ai.speckitty.charter.packs.infra.storage.Locations;
import ai.speckitty.charter.packs.infra.storage.ProjectConfigSources;
import io.cucumber.spring.CucumberContextConfiguration;
import io.cucumber.spring.ScenarioScope;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** One Spring context for every scenario; each scenario supplies its own packs through {@link ScenarioPacks}. */
@CucumberContextConfiguration
@SpringBootTest(
        classes = {CharterServiceApplication.class, CucumberSpringConfiguration.Packs.class},
        properties = {
            "charter.worktree-id=scenario-worktree",
            "charter.worktree-token=" + ScenarioHttp.TOKEN,
            // charter.registration.staging-dir and charter.pack-sources.filesystem.root are set by
            // perJvmDirectories: under the temporary directory, never the user's home, and per JVM.
            // The git source of the scenarios fetches from repositories of its own (GitFixtureServer), for the one host
            // the scenarios allow; the host is on the allowlist, and the kind's own flag is what a step switches on.
            "charter.pack-sources.git.allowed-hosts=git.example.org"
        })
@AutoConfigureMockMvc
public final class CucumberSpringConfiguration {

    /**
     * One name per JVM, so that two suites on one machine (two worktrees) never share a staging directory or a filesystem
     * source root: the steps empty the root before and after each scenario, which would delete the other suite's packs.
     */
    private static final String RUN = UUID.randomUUID().toString();

    private CucumberSpringConfiguration() {
        // Only annotations and static members: Cucumber and Spring read the class, they do not construct it.
    }

    /**
     * The registration staging directory and the filesystem source root: under the temporary directory, never the user's
     * home, and of this JVM alone. They are created when the context binds them and removed when the JVM ends.
     *
     * @param registry the properties of the scenarios' context
     */
    @DynamicPropertySource
    static void perJvmDirectories(DynamicPropertyRegistry registry) {
        Path temporary = Path.of(System.getProperty("java.io.tmpdir"));
        Path staging = temporary.resolve("charter-scenario-staging-" + RUN);
        Path sources = temporary.resolve("charter-scenario-sources-" + RUN);
        registry.add("charter.registration.staging-dir", staging::toString);
        registry.add("charter.pack-sources.filesystem.root", sources::toString);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            deleteTree(staging);
            deleteTree(sources);
        }));
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> tree = Files.walk(root)) {
            tree.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException | UncheckedIOException unreadable) {
            // The directory is this JVM's own, in the temporary directory: what stays is the operating system's to
            // clean.
        }
    }

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

        /** The https exchange every archive source goes through: armed per scenario, it opens no socket. */
        @Bean
        @Primary
        StubArchiveExchange stubArchiveExchange() {
            return new StubArchiveExchange();
        }

        /**
         * The remote and the name resolution of the git source: the repositories a scenario describes, real bare
         * repositories reached under the file transport, and one global address for the host a scenario allowed.
         */
        @Bean
        @Primary
        GitFixtureServer gitFixtureServer() {
            return new GitFixtureServer();
        }

        /**
         * The git of the scenarios is the host's, whatever its version: the 2.50.1 floor is the version check's own
         * tests (NFR-011), and these scenarios are about what the source does with the repositories.
         */
        @Bean
        @Primary
        GitVersionCheck scenarioGitVersionCheck() {
            return GitVersionCheck.resolve(null, System.getenv("PATH"), new GitVersion(2, 0, 0));
        }

        /** The packs the steps wrote and the pack domain a whole-pack registration writes to (ScenarioLocations). */
        @Bean
        @Primary
        Locations scenarioPackLocations(ScenarioPacks packs) {
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
