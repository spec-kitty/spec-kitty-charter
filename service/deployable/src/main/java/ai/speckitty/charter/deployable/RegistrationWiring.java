package ai.speckitty.charter.deployable;

import ai.speckitty.charter.packs.application.AuditLog;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackRegistrationService;
import ai.speckitty.charter.packs.application.PackRevisions;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import ai.speckitty.charter.packs.infra.api.RegistrationProperties;
import ai.speckitty.charter.packs.infra.api.RegistrationSwitch;
import ai.speckitty.charter.packs.infra.archive.BoundedArchiveExtractor;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import ai.speckitty.charter.packs.infra.storage.FreeSpaceCheck;
import ai.speckitty.charter.packs.infra.storage.StagingSweeper;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composes whole-pack registration (IC-14, IC-18): the switch and the properties, the archive extractor, the audit trail,
 * the registration jobs with their clock and test seam, the free-space check and the sweep of what a registration leaves
 * when it does not end. The ports and adapters are the packs slice's; only the composition root names both sides.
 *
 * <p>The registry lock is the one the pack writes share ({@link RegistryWriteLock#shared()}), so the jobs, the
 * per-artifact writes, the sweep and the activation writes exclude each other.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RegistrationProperties.class)
public class RegistrationWiring {

    private static final Duration WATCHDOG_PERIOD = Duration.ofSeconds(5);
    private static final String STAGING = "staging";

    /** For Spring, which instantiates the configuration class. */
    public RegistrationWiring() {}

    @Bean
    RegistrationSwitch registrationSwitch(RegistrationProperties properties) {
        return new RegistrationSwitch(properties.enabled());
    }

    @Bean
    PackArchiveReader packArchiveReader() {
        return new BoundedArchiveExtractor();
    }

    /** The audit trail: one structured log line per attempt, on the {@code charter.audit} logger. */
    @Bean
    AuditLog auditLog() {
        return new Slf4jAuditLog(LoggerFactory.getLogger(Slf4jAuditLog.LOGGER));
    }

    /** The clock of the statuses, the retention and the deadline; a test supplies its own as {@code @Primary}. */
    @Bean
    Clock registrationClock() {
        return Clock.systemUTC();
    }

    /** Lets every job through; a test supplies a gate that holds one as {@code @Primary}. */
    @Bean
    RegistrationJobs.JobGate registrationJobGate() {
        return RegistrationJobs.JobGate.OPEN;
    }

    /** The directory registrations spool and extract in: configured, else {@code <config-root>/staging}. */
    @Bean
    RegistrationStagingRoot registrationStagingRoot(
            RegistrationProperties properties, ObjectProvider<ConfigRoot> root) {
        Path configured = properties.stagingDir();
        if (configured != null) {
            return new RegistrationStagingRoot(configured.toAbsolutePath().normalize());
        }
        ConfigRoot configuredRoot = root.getIfAvailable();
        Path base = configuredRoot != null
                ? configuredRoot.root()
                : Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        return new RegistrationStagingRoot(base.resolve(STAGING));
    }

    @Bean
    RegistrationJobs.StorageRoom storageRoom(ObjectProvider<ConfigRoot> root) {
        ConfigRoot configured = root.getIfAvailable();
        return configured == null ? RegistrationJobs.StorageRoom.UNBOUNDED : FreeSpaceCheck.of(configured.packs());
    }

    /** The jobs, with the watchdog that ends the ones past their deadline; closed at shutdown. */
    @Bean(destroyMethod = "close")
    RegistrationJobs registrationJobs(
            Clock registrationClock,
            PackRegistrationService service,
            PackRevisions revisions,
            AuditLog audit,
            RegistrationJobs.JobGate gate,
            RegistrationJobs.StorageRoom room,
            RegistrationStagingRoot staging) {
        return new RegistrationJobs(new RegistrationJobs.Dependencies(
                        registrationClock, service, revisions, audit, gate, room, staging.directory()))
                .startWatchdog(WATCHDOG_PERIOD);
    }

    /** The sweep of orphaned staging and crash leftovers: at startup and every five minutes; stopped at shutdown. */
    @Bean(destroyMethod = "close")
    SweepRunner stagingSweeper(
            RegistrationStagingRoot staging,
            ObjectProvider<ConfigRoot> root,
            RegistrationJobs jobs,
            Clock registrationClock) {
        ConfigRoot configured = root.getIfAvailable();
        Path packs =
                configured != null ? configured.packs() : staging.directory().resolveSibling("packs");
        StagingSweeper sweeper = new StagingSweeper(
                staging.directory(), packs, jobs::owned, registrationClock, RegistryWriteLock.shared());
        return SweepRunner.started(sweeper);
    }

    /**
     * The staging directory of registrations.
     *
     * @param directory the directory
     */
    record RegistrationStagingRoot(Path directory) {}

    /** The sweeper and the scheduler that repeats it. */
    static final class SweepRunner implements AutoCloseable {
        private final ScheduledExecutorService scheduler;
        private final @Nullable ScheduledFuture<?> repeats;

        private SweepRunner(ScheduledExecutorService scheduler, @Nullable ScheduledFuture<?> repeats) {
            this.scheduler = scheduler;
            this.repeats = repeats;
        }

        static SweepRunner started(StagingSweeper sweeper) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "charter-registration-sweep");
                thread.setDaemon(true);
                return thread;
            });
            return new SweepRunner(scheduler, sweeper.start(scheduler));
        }

        @Override
        public void close() {
            if (repeats != null) {
                repeats.cancel(false);
            }
            scheduler.shutdownNow();
        }
    }

    /** One structured line per attempt: {@code key="value"} pairs, the values already cleaned of control characters. */
    static final class Slf4jAuditLog implements AuditLog {

        /** The logger audit lines are written to. */
        static final String LOGGER = "charter.audit";

        private final Logger log;

        Slf4jAuditLog(Logger log) {
            this.log = log;
        }

        @Override
        public void record(AuditLog.Entry entry) {
            log.info(
                    "audit {}",
                    Stream.of(
                                    pair("at", entry.at().toString()),
                                    pair("worktree", entry.worktree()),
                                    pair("credential", entry.credential().wire()),
                                    pair("operation", entry.operation()),
                                    pair("pack", entry.packId()),
                                    pair("source", entry.sourceKind()),
                                    pair("locator", entry.locator()),
                                    pair("outcome", entry.outcome()),
                                    pair("revisionBefore", entry.revisionBefore()),
                                    pair("revisionAfter", entry.revisionAfter()))
                            .collect(Collectors.joining(" ")));
        }

        private static String pair(String key, @Nullable String value) {
            String text = value == null ? "" : value;
            return key + "=\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
