package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("charter.config-root")
                .hasMessageContaining("charter.registration.enabled");
    }

    @Test
    void withoutAConfigRootTheSweepNeverTouchesTheTemporaryDirectory(@TempDir Path temporary)
            throws IOException, InterruptedException {
        Path stale = Files.createDirectories(temporary.resolve("staging").resolve("left-by-someone-else"));
        Files.writeString(stale.resolve("file"), "not the service's");
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minus(Duration.ofHours(2))));
        String before = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", temporary.toString());
        try {
            RegistrationWiring.RegistrationStagingRoot staging =
                    wiring.registrationStagingRoot(new RegistrationProperties(false, null), noRoot);
            try (RegistrationWiring.SweepRunner sweep =
                    wiring.stagingSweeper(staging, noRoot, mock(RegistrationJobs.class), Clock.systemUTC())) {
                Thread.sleep(1500);
                assertThat(sweep).isNotNull();
            }
        } finally {
            System.setProperty("java.io.tmpdir", before);
        }

        assertThat(stale.resolve("file"))
                .as("what another process left in the temporary directory")
                .exists();
    }
}
