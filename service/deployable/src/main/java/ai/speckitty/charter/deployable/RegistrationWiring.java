package ai.speckitty.charter.deployable;

import ai.speckitty.charter.kernel.config.StartupRefusal;
import ai.speckitty.charter.packs.application.AuditLog;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackRegistrationService;
import ai.speckitty.charter.packs.application.PackRevisions;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import ai.speckitty.charter.packs.application.StagingArea;
import ai.speckitty.charter.packs.infra.api.RegistrationProperties;
import ai.speckitty.charter.packs.infra.api.RegistrationSwitch;
import ai.speckitty.charter.packs.infra.archive.BoundedArchiveExtractor;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import ai.speckitty.charter.packs.infra.storage.FileStagingArea;
import ai.speckitty.charter.packs.infra.storage.FreeSpaceCheck;
import ai.speckitty.charter.packs.infra.storage.StagingSweeper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
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
    private static final String NO_STAGING = "registration-switched-off-no-config-root";
    private static final String STAGING_KEY = "charter.registration.staging-dir";
    private static final String CONFIG_KEY = "charter.config-root";
    private static final List<Path> KERNEL_TREES = List.of(Path.of("/proc"), Path.of("/sys"), Path.of("/dev"));

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

    /**
     * The directory registrations spool and extract in: configured, else {@code <config-root>/staging}. Registration
     * switched on needs the configuration root, since that is where a registered pack is stored, and the service does not
     * guess another place: without one it refuses to start. Switched off, nothing is staged, and the directory is a
     * relative name that is neither created nor swept.
     *
     * @throws StartupRefusal when registration is switched on and no configuration root is set, or the configured
     *     staging directory is unsafe to sweep
     */
    @Bean
    RegistrationStagingRoot registrationStagingRoot(
            RegistrationProperties properties, ObjectProvider<ConfigRoot> root) {
        ConfigRoot configuredRoot = root.getIfAvailable();
        if (configuredRoot == null) {
            if (properties.enabled()) {
                throw new StartupRefusal(
                        "charter.registration.enabled needs charter.config-root: registered packs"
                                + " are staged and stored under it, and the service does not stage them in a directory it guesses.");
            }
            return new RegistrationStagingRoot(Path.of(NO_STAGING));
        }
        Path configured = properties.stagingDir();
        if (configured == null) {
            return new RegistrationStagingRoot(configuredRoot.root().resolve(STAGING));
        }
        return new RegistrationStagingRoot(requireSafeStagingDir(configured, configuredRoot));
    }

    /**
     * A staging directory the sweep may work in: the sweep removes what it finds there, so it must not be the
     * configuration root or hold it, be the pack or activation store or lie inside one, or lie under a kernel tree.
     * Links are resolved before the comparison.
     */
    private static Path requireSafeStagingDir(Path configured, ConfigRoot root) {
        Path directory = realOrNormalised(configured);
        for (Path tree : KERNEL_TREES) {
            if (directory.startsWith(tree)) {
                throw new StartupRefusal(STAGING_KEY + " must not be under /proc, /sys or /dev: " + configured);
            }
        }
        Path config = realOrNormalised(root.root());
        if (config.startsWith(directory)) {
            throw new StartupRefusal(STAGING_KEY + " must not be " + CONFIG_KEY + " or a directory that holds it: "
                    + configured + ". The sweep removes what it finds in the staging directory.");
        }
        for (Path store : List.of(config.resolve(ConfigRoot.PACKS), config.resolve(ConfigRoot.ACTIVATIONS))) {
            if (directory.startsWith(store)) {
                throw new StartupRefusal(STAGING_KEY + " must not be the pack or the activation store of " + CONFIG_KEY
                        + ", nor lie inside one: " + configured);
            }
        }
        return configured.toAbsolutePath().normalize();
    }

    private static Path realOrNormalised(Path path) {
        Path normalised = path.toAbsolutePath().normalize();
        // The longest existing ancestor is resolved, so a link anywhere on the way is seen even where the leaf is not
        // made yet.
        Path existing = normalised;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return normalised;
        }
        try {
            return existing.toRealPath()
                    .resolve(existing.relativize(normalised))
                    .normalize();
        } catch (IOException unresolved) {
            return normalised;
        }
    }

    @Bean
    RegistrationJobs.StorageRoom storageRoom(ObjectProvider<ConfigRoot> root, RegistrationStagingRoot staging) {
        ConfigRoot configured = root.getIfAvailable();
        return configured == null
                ? RegistrationJobs.StorageRoom.UNBOUNDED
                : FreeSpaceCheck.of(configured.packs(), staging.directory());
    }

    /** The staging area the registrations work in: owner-only directories under the staging root. */
    @Bean
    StagingArea stagingArea(RegistrationStagingRoot staging) {
        return new FileStagingArea(staging.directory());
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
            StagingArea staging) {
        return new RegistrationJobs(new RegistrationJobs.Dependencies(
                        registrationClock, service, revisions, audit, gate, room, staging))
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
        if (configured == null) {
            return SweepRunner.idle();
        }
        StagingSweeper sweeper = new StagingSweeper(
                staging.directory(), configured.packs(), jobs::owned, registrationClock, RegistryWriteLock.shared());
        return SweepRunner.started(sweeper);
    }

    /**
     * The staging directory of registrations.
     *
     * @param directory the directory
     */
    record RegistrationStagingRoot(Path directory) {}

    /** The sweeper and the scheduler that repeats it; with no configuration root there is nothing to sweep. */
    static final class SweepRunner implements AutoCloseable {
        private final @Nullable ScheduledExecutorService scheduler;
        private final @Nullable ScheduledFuture<?> repeats;

        private SweepRunner(@Nullable ScheduledExecutorService scheduler, @Nullable ScheduledFuture<?> repeats) {
            this.scheduler = scheduler;
            this.repeats = repeats;
        }

        static SweepRunner idle() {
            return new SweepRunner(null, null);
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
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
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
