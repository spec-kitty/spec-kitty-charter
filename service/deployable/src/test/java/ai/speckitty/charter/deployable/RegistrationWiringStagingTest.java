package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ai.speckitty.charter.kernel.config.StartupRefusal;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.infra.api.RegistrationProperties;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/**
 * Registration stages archives and sweeps leftovers under the configuration root and nowhere else: without one it does
 * not guess a directory such as the shared temporary one, where a sweep would delete what is not the service's.
 */
class RegistrationWiringStagingTest {

    private final RegistrationWiring wiring = new RegistrationWiring();
    private final ObjectProvider<ConfigRoot> noRoot = new StaticListableBeanFactory().getBeanProvider(ConfigRoot.class);

    @Test
    void registrationSwitchedOnWithoutAConfigRootDoesNotStart() {
        assertThatThrownBy(() -> wiring.registrationStagingRoot(new RegistrationProperties(true, null), noRoot))
                .isInstanceOf(StartupRefusal.class)
                .hasMessageContaining("charter.config-root")
                .hasMessageContaining("charter.registration.enabled");
    }

    private ObjectProvider<ConfigRoot> rootAt(Path root) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("configRoot", new ConfigRoot(root));
        return beans.getBeanProvider(ConfigRoot.class);
    }

    @Test
    void aStagingDirThatIsTheConfigRootOrHoldsItOrHoldsOrLiesInTheStoreIsRefused(@TempDir Path host)
            throws IOException {
        Path root = Files.createDirectories(host.resolve("config/root"));
        ConfigRoot config = new ConfigRoot(root);
        Path link = Files.createSymbolicLink(host.resolve("alias"), root);
        List<Path> dangerous = List.of(
                root,
                root.getParent(),
                host,
                Path.of("/"),
                config.packs(),
                config.packs().resolve("acme"),
                config.activations(),
                config.activations().resolve("a-1"),
                link,
                link.resolve("packs"));

        for (Path directory : dangerous) {
            assertThatThrownBy(() ->
                            wiring.registrationStagingRoot(new RegistrationProperties(true, directory), rootAt(root)))
                    .as("charter.registration.staging-dir=%s", directory)
                    .isInstanceOf(StartupRefusal.class)
                    .hasMessageContaining("charter.registration.staging-dir");
        }
    }

    @Test
    void aStagingDirUnderProcSysOrDevIsRefused(@TempDir Path host) throws IOException {
        Path root = Files.createDirectories(host.resolve("root"));

        for (String directory : List.of("/proc", "/proc/self", "/sys/kernel", "/dev", "/dev/shm/staging")) {
            assertThatThrownBy(() -> wiring.registrationStagingRoot(
                            new RegistrationProperties(true, Path.of(directory)), rootAt(root)))
                    .as("charter.registration.staging-dir=%s", directory)
                    .isInstanceOf(StartupRefusal.class)
                    .hasMessageContaining("/proc, /sys or /dev");
        }
    }

    @Test
    void aStagingDirNextToTheStoreOrTheDefaultUnderTheConfigRootIsAccepted(@TempDir Path host) throws IOException {
        Path root = Files.createDirectories(host.resolve("root"));

        for (Path directory : List.of(root.resolve("staging"), root.resolve("spool/jobs"), host.resolve("spool"))) {
            assertThatCode(() ->
                            wiring.registrationStagingRoot(new RegistrationProperties(true, directory), rootAt(root)))
                    .as("charter.registration.staging-dir=%s", directory)
                    .doesNotThrowAnyException();
        }
        assertThat(wiring.registrationStagingRoot(new RegistrationProperties(true, null), rootAt(root))
                        .directory())
                .isEqualTo(root.resolve("staging"));
    }

    /** A registration id: the only name a sweep removes from a staging root. */
    private static final String JOB = "01M8AAEGAWWX40BC7NQ6GAZVQ2";

    /** A job directory left two hours ago under {@code <parent>/staging}: what a sweep of that root removes. */
    private static Path staleJobUnder(Path parent) throws IOException {
        Path stale = Files.createDirectories(parent.resolve("staging").resolve(JOB));
        Files.writeString(stale.resolve("file"), "left behind");
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minus(Duration.ofHours(2))));
        return stale;
    }

    @Test
    void aSweepOverThatStagingRootRemovesTheStaleJobDirectory(@TempDir Path temporary, @TempDir Path root)
            throws IOException {
        Path stale = staleJobUnder(temporary);
        RegistrationWiring.RegistrationStagingRoot staging =
                new RegistrationWiring.RegistrationStagingRoot(temporary.resolve("staging"));

        try (RegistrationWiring.SweepRunner sweep =
                wiring.stagingSweeper(staging, rootAt(root), mock(RegistrationJobs.class), Clock.systemUTC())) {
            assertThat(sweep).isNotNull();
        }

        assertThat(stale)
                .as("the positive control: a sweep of that root removes the entry the next test leaves")
                .doesNotExist();
    }

    @Test
    void withoutAConfigRootTheSweepNeverTouchesTheTemporaryDirectory(@TempDir Path temporary) throws IOException {
        Path stale = staleJobUnder(temporary);
        String before = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", temporary.toString());
        try {
            RegistrationWiring.RegistrationStagingRoot staging =
                    wiring.registrationStagingRoot(new RegistrationProperties(false, null), noRoot);
            // A sweep that starts sweeps once before it returns (StagingSweeper.start), so what it would remove is gone
            // by the time the runner is handed back; the next sweep is five minutes away.
            try (RegistrationWiring.SweepRunner sweep =
                    wiring.stagingSweeper(staging, noRoot, mock(RegistrationJobs.class), Clock.systemUTC())) {
                assertThat(sweep).isNotNull();
            }
        } finally {
            System.setProperty("java.io.tmpdir", before);
        }

        assertThat(stale.resolve("file"))
                .as("a job directory under the temporary directory, which only a sweep of that root would remove")
                .exists();
    }
}
