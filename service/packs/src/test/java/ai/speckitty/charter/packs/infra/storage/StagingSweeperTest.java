package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.application.RegistryWriteLock;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The staging sweep and the crash-leftover sweep of NFR-009 (T151), on a fake clock: an orphan older than 15 minutes goes,
 * a job's own directory and a younger orphan stay, and the pack domain's leftovers of an interrupted commit go whatever
 * their age, under the registry lock.
 */
class StagingSweeperTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** Names in the grammar of a registration id, a ULID: the only entries of the staging root the sweep may remove. */
    private static final String ORPHAN_16 = "01KA0000000000000000000016";

    private static final String RUNNING_16 = "01KA0000000000000000000017";
    private static final String ORPHAN_14 = "01KA0000000000000000000014";

    @Test
    void anOrphanOlderThanFifteenMinutesIsRemovedAndTheOthersAreKept(@TempDir Path host) throws IOException {
        Path staging = Files.createDirectories(host.resolve("staging"));
        Path orphan = directoryAged(staging, ORPHAN_16, Duration.ofMinutes(16));
        Path running = directoryAged(staging, RUNNING_16, Duration.ofMinutes(16));
        Path young = directoryAged(staging, ORPHAN_14, Duration.ofMinutes(14));
        StagingSweeper sweeper =
                sweeper(staging, host.resolve("packs"), Set.of(running), new RegistryWriteLock(Duration.ofSeconds(1)));

        int removed = sweeper.sweep();

        assertThat(orphan).as("a 16-minute-old directory no job owns").doesNotExist();
        assertThat(running).as("a 16-minute-old directory of a running job").isDirectory();
        assertThat(young).as("a 14-minute-old orphan").isDirectory();
        assertThat(removed).isEqualTo(1);
    }

    @Test
    void onlyAJobIdNamedEntryOfTheStagingRootIsSweptWhateverItsAge(@TempDir Path host) throws IOException {
        Path staging = Files.createDirectories(host.resolve("staging"));
        Path job = directoryAged(staging, ORPHAN_16, Duration.ofMinutes(20));
        Path aPack = directoryAged(staging, "acme", Duration.ofDays(2));
        Path hidden = directoryAged(staging, ".git", Duration.ofDays(2));
        Path packs = directoryAged(staging, "packs", Duration.ofDays(2));
        Path lowerCase = directoryAged(staging, ORPHAN_16.toLowerCase(Locale.ROOT), Duration.ofDays(2));
        Path tooShort = directoryAged(staging, "01KA000000000000000000001", Duration.ofDays(2));
        Path withLetterU = directoryAged(staging, "01KA000000000000000000001U", Duration.ofDays(2));
        Path aFile = Files.writeString(staging.resolve("01KA0000000000000000000018"), "a file named like a job");
        Files.setLastModifiedTime(aFile, FileTime.from(NOW.minus(Duration.ofDays(2))));
        StagingSweeper sweeper =
                sweeper(staging, host.resolve("packs-domain"), Set.of(), new RegistryWriteLock(Duration.ofSeconds(1)));

        int removed = sweeper.sweep();

        assertThat(job).as("an orphaned job directory").doesNotExist();
        assertThat(List.of(aPack, hidden, packs, lowerCase, tooShort, withLetterU))
                .as("an entry that is no job id is not the registration's to delete")
                .allSatisfy(entry -> assertThat(entry).isDirectory());
        assertThat(aFile).as("a plain file is never a job directory").isRegularFile();
        assertThat(removed).isEqualTo(1);
    }

    @Test
    void aDirectoryWithFilesInsideIsRemovedWhole(@TempDir Path host) throws IOException {
        Path staging = Files.createDirectories(host.resolve("staging"));
        Path orphan = Files.createDirectories(staging.resolve(ORPHAN_16));
        Files.createDirectories(orphan.resolve("tree/directives"));
        Files.writeString(orphan.resolve("tree/directives/A.directive.yaml"), "x");
        Files.writeString(orphan.resolve("archive"), "bytes");
        Files.setLastModifiedTime(orphan, FileTime.from(NOW.minus(Duration.ofMinutes(20))));

        sweeper(staging, host.resolve("packs"), Set.of(), new RegistryWriteLock(Duration.ofSeconds(1)))
                .sweep();

        assertThat(orphan).doesNotExist();
    }

    @Test
    void theLeftoversOfAnInterruptedCommitGoAndAPackAndAPlainFileStay(@TempDir Path host) throws IOException {
        Path packs = Files.createDirectories(host.resolve("packs"));
        Path commit = Files.createDirectories(packs.resolve(".pack-commit-abc123"));
        Files.writeString(commit.resolve("pack.yaml"), "half a pack");
        Path write = Files.createDirectories(packs.resolve(".pack-write-def456"));
        Path history = Files.createDirectories(packs.resolve(".history"));
        Path tempHistory = Files.writeString(history.resolve("acme.json.tmp-0b9f"), "[]");
        Path publishedHistory = Files.writeString(history.resolve("acme.json"), "[]");
        Path pack = Files.createDirectories(packs.resolve("acme"));
        Files.writeString(pack.resolve("pack.yaml"), "pack_id: x");
        Path hiddenPack = Files.createDirectories(packs.resolve(".pack-old-aside"));
        Path stray = Files.writeString(packs.resolve("notes.txt"), "not ours");
        StagingSweeper sweeper =
                sweeper(host.resolve("staging"), packs, Set.of(), new RegistryWriteLock(Duration.ofSeconds(1)));

        int removed = sweeper.sweep();

        assertThat(commit).as("a commit folder a crash left, however young").doesNotExist();
        assertThat(write).doesNotExist();
        assertThat(tempHistory)
                .as("the history file a crash left before its publish")
                .doesNotExist();
        assertThat(publishedHistory)
                .as("the published history is the pack's record")
                .isRegularFile();
        assertThat(pack.resolve("pack.yaml")).as("a stored pack").isRegularFile();
        assertThat(hiddenPack)
                .as("an aside copy belongs to the listing's repair, not to this sweep")
                .isDirectory();
        assertThat(stray).isRegularFile();
        assertThat(removed).isEqualTo(3);
    }

    @Test
    void theLeftoversAreSweptUnderTheRegistryLockAndSkippedWhenItIsBusy(@TempDir Path host) throws Exception {
        Path packs = Files.createDirectories(host.resolve("packs"));
        Path commit = Files.createDirectories(packs.resolve(".pack-commit-inflight"));
        RegistryWriteLock lock = new RegistryWriteLock(Duration.ofMillis(50));
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread committing = new Thread(() -> lock.exclusively(() -> {
            holding.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        committing.setDaemon(true);
        committing.start();
        assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();
        StagingSweeper sweeper = sweeper(host.resolve("staging"), packs, Set.of(), lock);

        int whileBusy = sweeper.sweep();
        release.countDown();
        committing.join(10_000);
        int afterwards = sweeper.sweep();

        assertThat(whileBusy)
                .as("a commit may be copying into that folder: it is not touched")
                .isZero();
        assertThat(afterwards).isEqualTo(1);
        assertThat(commit).doesNotExist();
    }

    @Test
    void aMissingStagingRootAndPackRootAreNothingToSweep(@TempDir Path host) {
        StagingSweeper sweeper = sweeper(
                host.resolve("no-staging"),
                host.resolve("no-packs"),
                Set.of(),
                new RegistryWriteLock(Duration.ofSeconds(1)));

        assertThat(sweeper.sweep()).isZero();
    }

    @Test
    void theSweepRunsAtStartupAndEveryFiveMinutes(@TempDir Path host) throws IOException {
        Path staging = Files.createDirectories(host.resolve("staging"));
        Path orphan = directoryAged(staging, ORPHAN_16, Duration.ofMinutes(20));
        List<long[]> scheduled = new ArrayList<>();
        try (ScheduledThreadPoolExecutor recording = new ScheduledThreadPoolExecutor(1) {
            @Override
            public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(
                    Runnable command, long initialDelay, long delay, TimeUnit unit) {
                scheduled.add(new long[] {unit.toMillis(initialDelay), unit.toMillis(delay)});
                return null;
            }
        }) {
            Object repeating = sweeper(
                            staging, host.resolve("packs"), Set.of(), new RegistryWriteLock(Duration.ofSeconds(1)))
                    .start(recording);
            assertThat(repeating)
                    .as("the repeating run, which the recording scheduler does not make")
                    .isNull();
        }

        assertThat(orphan).as("the startup sweep ran before start returned").doesNotExist();
        assertThat(scheduled).hasSize(1);
        assertThat(scheduled.getFirst()[1])
                .as("the repeat is every five minutes")
                .isEqualTo(Duration.ofMinutes(5).toMillis());
    }

    private static StagingSweeper sweeper(Path staging, Path packs, Set<Path> owned, RegistryWriteLock lock) {
        return new StagingSweeper(staging, packs, () -> owned, CLOCK, lock);
    }

    private static Path directoryAged(Path parent, String name, Duration age) {
        try {
            Path directory = Files.createDirectories(parent.resolve(name));
            Files.setLastModifiedTime(directory, FileTime.from(NOW.minus(age)));
            return directory;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
