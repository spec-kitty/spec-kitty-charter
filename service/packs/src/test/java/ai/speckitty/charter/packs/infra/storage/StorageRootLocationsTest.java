package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackWriteGeneration;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * The packs of the pack domain: which directories are packs, at which level, in which order. A link to a directory is
 * a pack (#174, ADR-0038 as amended 2026-10-31); a link that resolves to no directory, to the domain itself or to a
 * directory another entry resolves to is not, and is reported once.
 */
class StorageRootLocationsTest {

    @TempDir
    Path host;

    /** The pack domain, {@code <config-root>/packs}, as {@code ConfigRoot.packs()} places it. */
    private Path packs;

    @BeforeEach
    void aConfigRoot() throws IOException {
        packs = Files.createDirectories(host.resolve("config/packs"));
    }

    /** Every pack of these tests declares this id; the tests never put two packs in one compose. */
    private static final String PACK_ID = "01ARWG13C000000000000000QA";

    private void pack(String name, String level) throws IOException {
        Files.createDirectories(packs.resolve(name));
        Files.writeString(
                packs.resolve(name + "/pack.yaml"),
                "pack_id: " + PACK_ID + "\npack_version: 1.0.0\nname: " + name + "\npack_level: " + level + "\n");
    }

    @Test
    void everyDirectoryIsAPackAtTheLevelItDeclaresTheBuiltInOneFirst() throws IOException {
        pack("qa", "org");
        pack("built-in", "built-in");
        pack("my-project", "project");

        assertThat(new StorageRootLocations(packs).all())
                .containsExactly(
                        new PackLocation("built-in", PackLevel.BUILT_IN, packs.resolve("built-in")),
                        new PackLocation("my-project", PackLevel.PROJECT, packs.resolve("my-project")),
                        new PackLocation("qa", PackLevel.ORG, packs.resolve("qa")));
    }

    @Test
    void aPackWithoutAReadableDeclarationIsAnOrgPack() throws IOException {
        Files.createDirectories(packs.resolve("bare"));
        Files.writeString(packs.resolve("bare/notes.md"), "no declaration");
        Files.createDirectories(packs.resolve("garbled"));
        Files.writeString(packs.resolve("garbled/pack.yaml"), "pack_level: [unclosed\n");
        Files.createDirectories(packs.resolve("galaxy"));
        Files.writeString(packs.resolve("galaxy/pack.yaml"), "pack_level: galaxy\n");

        assertThat(new StorageRootLocations(packs).all())
                .extracting(PackLocation::level)
                .containsOnly(PackLevel.ORG);
    }

    @Test
    void filesHiddenDirectoriesAndCommitLeftoversAreNotPacks() throws IOException {
        pack("qa", "org");
        Files.writeString(packs.resolve("README.md"), "x");
        Files.createDirectories(packs.resolve(".pack-commit-123"));
        Files.createDirectories(packs.resolve(".git"));
        Files.createDirectories(packs.resolve("qa.pack-old-abc"));

        assertThat(new StorageRootLocations(packs).all())
                .extracting(PackLocation::name)
                .containsExactly("qa");
    }

    @Test
    void aLinkToADirectoryIsAPackNamedAfterTheLinkAtTheLevelItsTargetDeclares(@TempDir Path elsewhere)
            throws IOException {
        pack("qa", "org");
        Path target = elsewhere.resolve("real-name");
        Files.createDirectories(target);
        Files.writeString(target.resolve("pack.yaml"), "pack_id: 01ARWG13C000000000EXTERNA1\npack_level: project\n");
        link(packs.resolve("external"), target);

        assertThat(new StorageRootLocations(packs).all())
                .containsExactly(
                        new PackLocation("external", PackLevel.PROJECT, packs.resolve("external")),
                        new PackLocation("qa", PackLevel.ORG, packs.resolve("qa")));
    }

    @Test
    void aDanglingLinkAndALinkToAFileAreNotPacksAndEachIsReportedOnce(@TempDir Path elsewhere) throws IOException {
        pack("qa", "org");
        Files.writeString(elsewhere.resolve("a-file"), "x");
        link(packs.resolve("to-a-file"), elsewhere.resolve("a-file"));
        link(packs.resolve("dangling"), elsewhere.resolve("gone"));
        StorageRootLocations locations = new StorageRootLocations(packs);

        try (LogCapture log = new LogCapture()) {
            // List twice: the warning fires on the first listing; the second listing proves it is not re-logged.
            locations.all();
            assertThat(locations.all()).extracting(PackLocation::name).containsExactly("qa");

            assertThat(log.warnings())
                    .as("one warning per refused entry, however often the domain is listed")
                    .hasSize(2)
                    .anySatisfy(warning -> assertThat(warning).contains("dangling", "does not resolve"))
                    .anySatisfy(warning -> assertThat(warning).contains("to-a-file", "not a directory"));
        }
    }

    @Test
    void entriesThatResolveToOneDirectoryAreRefusedTogether(@TempDir Path elsewhere) throws IOException {
        pack("qa", "org");
        pack("solo", "org");
        link(packs.resolve("qa-alias"), packs.resolve("qa"));
        Files.createDirectories(elsewhere.resolve("shared"));
        link(packs.resolve("one"), elsewhere.resolve("shared"));
        link(packs.resolve("two"), elsewhere.resolve("shared"));

        try (LogCapture log = new LogCapture()) {
            assertThat(new StorageRootLocations(packs).all())
                    .as("a commit to one name would change the pack under the other; neither is served")
                    .extracting(PackLocation::name)
                    .containsExactly("solo");
            assertThat(log.warnings())
                    .anySatisfy(warning -> assertThat(warning).contains("qa-alias", "overlaps", "qa"))
                    .anySatisfy(warning -> assertThat(warning).contains("one", "overlaps", "two"));
        }
    }

    @Test
    void entriesWhoseDirectoriesNestAreRefusedTogether(@TempDir Path elsewhere) throws IOException {
        pack("qa", "org");
        pack("solo", "org");
        Files.createDirectories(packs.resolve("qa/sub"));
        link(packs.resolve("inner"), packs.resolve("qa/sub"));
        Files.createDirectories(elsewhere.resolve("outer/nested"));
        link(packs.resolve("outer"), elsewhere.resolve("outer"));
        link(packs.resolve("nested"), elsewhere.resolve("outer/nested"));

        StorageRootLocations locations = new StorageRootLocations(packs);

        try (LogCapture log = new LogCapture()) {
            assertThat(locations.all())
                    .as("a commit to the outer pack would copy and replace the inner one; neither is served")
                    .extracting(PackLocation::name)
                    .containsExactly("solo");
            locations.all();
            assertThat(log.warnings())
                    .as("one warning per refused entry, over two listings")
                    .hasSize(4)
                    .anySatisfy(warning -> assertThat(warning).contains("inner", "overlaps", "qa"))
                    .anySatisfy(warning -> assertThat(warning).contains("outer", "overlaps", "nested"));
        }
    }

    @Test
    void aLinkIntoTheConfigRootOutsideThePackDomainIsNotAPack() throws IOException {
        pack("qa", "org");
        Path activations = Files.createDirectories(packs.resolveSibling("activations"));
        link(packs.resolve("acts"), activations);

        try (LogCapture log = new LogCapture()) {
            assertThat(new StorageRootLocations(packs).all())
                    .extracting(PackLocation::name)
                    .containsExactly("qa");
            assertThat(log.warnings())
                    .singleElement()
                    .satisfies(warning -> assertThat(warning).contains("acts", "outside the pack domain"));
        }
    }

    @Test
    void aRefusedLinkThatIsFixedAndBrokenAgainIsReportedAgain(@TempDir Path elsewhere) throws IOException {
        pack("qa", "org");
        Path link = packs.resolve("flaky");
        link(link, elsewhere.resolve("gone"));
        StorageRootLocations locations = new StorageRootLocations(packs);

        try (LogCapture log = new LogCapture()) {
            locations.all();
            Files.createDirectories(elsewhere.resolve("gone"));
            assertThat(locations.all()).extracting(PackLocation::name).containsExactly("flaky", "qa");
            Files.delete(elsewhere.resolve("gone"));
            locations.all();

            assertThat(log.warnings())
                    .as("reported when it breaks, and again when it breaks after it was fixed")
                    .hasSize(2)
                    .allSatisfy(warning -> assertThat(warning).contains("flaky", "does not resolve"));
        }
    }

    @Test
    void aLinkToThePackDomainOrAnAncestorOfItIsNotAPack() throws IOException {
        pack("qa", "org");
        link(packs.resolve("loop"), packs);
        link(packs.resolve("up"), packs.getParent());

        try (LogCapture log = new LogCapture()) {
            assertThat(new StorageRootLocations(packs).all())
                    .extracting(PackLocation::name)
                    .containsExactly("qa");
            assertThat(log.warnings())
                    .anySatisfy(warning -> assertThat(warning).contains("loop", "holds the pack domain"))
                    .anySatisfy(warning -> assertThat(warning).contains("up", "holds the pack domain"));
        }
    }

    /** Makes {@code <config-root>/packs} a link to {@code target}, a directory holding the packs qa and built-in. */
    private void linkTheDomainTo(Path target) throws IOException {
        Files.delete(packs);
        Path real = Files.createDirectories(target);
        for (String name : List.of("qa", "built-in")) {
            Files.createDirectories(real.resolve(name));
            Files.writeString(
                    real.resolve(name + "/pack.yaml"),
                    "pack_id: " + name + "\npack_level: " + (name.equals("qa") ? "org" : "built-in") + "\n");
        }
        link(packs, real);
    }

    @Test
    void aPackDomainLinkedFromOutsideTheConfigRootIsListedAndEveryPackInItIsReadOnly(@TempDir Path elsewhere)
            throws IOException {
        linkTheDomainTo(elsewhere.resolve("shared-packs"));
        StorageRootLocations locations = new StorageRootLocations(packs);

        List<PackLocation> listed = locations.all();

        assertThat(listed)
                .as("the linked domain is followed (#285), each pack named and placed under the configured domain")
                .containsExactly(
                        new PackLocation("built-in", PackLevel.BUILT_IN, packs.resolve("built-in")),
                        new PackLocation("qa", PackLevel.ORG, packs.resolve("qa")));
        for (PackLocation location : listed) {
            assertThat(locations.whyReadOnly(location.root().toRealPath()))
                    .as("%s lives outside the configuration root", location.name())
                    .contains("is linked from outside the configuration root; it is read-only through the service");
        }
    }

    @Test
    void aPackDomainLinkedInsideTheConfigRootIsListedAndWritable() throws IOException {
        linkTheDomainTo(packs.resolveSibling(".store/packs"));
        StorageRootLocations locations = new StorageRootLocations(packs);

        List<PackLocation> listed = locations.all();

        assertThat(listed).extracting(PackLocation::name).containsExactly("built-in", "qa");
        for (PackLocation location : listed) {
            assertThat(locations.whyReadOnly(location.root().toRealPath())).isEmpty();
        }
    }

    @Test
    void inALinkedPackDomainTheLinkRulesStillHold(@TempDir Path elsewhere) throws IOException {
        linkTheDomainTo(elsewhere.resolve("shared-packs"));
        link(packs.resolve("qa-alias"), packs.resolve("qa"));
        link(packs.resolve("acts"), Files.createDirectories(packs.resolveSibling("activations")));
        link(packs.resolve("root"), packs.getParent());
        link(packs.resolve("home"), host);

        try (LogCapture log = new LogCapture()) {
            assertThat(new StorageRootLocations(packs).all())
                    .extracting(PackLocation::name)
                    .containsExactly("built-in");
            assertThat(log.warnings())
                    .anySatisfy(warning -> assertThat(warning).contains("qa-alias", "overlaps", "qa"))
                    .anySatisfy(warning -> assertThat(warning).contains("acts", "outside the pack domain"))
                    .anySatisfy(warning -> assertThat(warning).contains("root", "configuration root"))
                    .anySatisfy(warning -> assertThat(warning).contains("home", "holds the configuration root"));
        }
    }

    @Test
    void aPackDomainLinkedIntoTheActivationsDomainListsNothingAndIsReportedOnce() throws IOException {
        Path activations = Files.createDirectories(packs.resolveSibling(ConfigRoot.ACTIVATIONS));
        Files.createDirectories(activations.resolve("01ACTIVATION/nested"));
        for (Path target : List.of(activations, activations.resolve("01ACTIVATION"))) {
            Files.deleteIfExists(packs);
            link(packs, target);
            StorageRootLocations locations = new StorageRootLocations(packs);

            try (LogCapture log = new LogCapture()) {
                assertThat(locations.all())
                        .as("an activation directory is never served, nor written, as a pack (%s)", target)
                        .isEmpty();
                locations.all();
                assertThat(log.warnings())
                        .singleElement()
                        .satisfies(warning -> assertThat(warning).contains("pack domain", "activations domain"));
            }
        }
    }

    @Test
    void aPackDomainLinkedToTheConfigRootOrAboveItListsNothingAndIsReportedOnce() throws IOException {
        Files.delete(packs);
        link(packs, host);
        StorageRootLocations locations = new StorageRootLocations(packs);

        try (LogCapture log = new LogCapture()) {
            assertThat(locations.all()).isEmpty();
            assertThat(locations.all()).isEmpty();

            assertThat(log.warnings())
                    .as("one warning, however often the domain is listed")
                    .singleElement()
                    .satisfies(warning -> assertThat(warning).contains("pack domain", "holds the configuration root"));
        }
        Files.delete(packs);
        link(packs, packs.getParent());
        try (LogCapture log = new LogCapture()) {
            assertThat(new StorageRootLocations(packs).all()).isEmpty();
            assertThat(log.warnings())
                    .singleElement()
                    .satisfies(warning -> assertThat(warning).contains("pack domain", "holds the configuration root"));
        }
    }

    @Test
    void theCatalogListsALinkedPackAtItsLevel(@TempDir Path elsewhere) throws IOException {
        pack("qa", "org");
        Path target = elsewhere.resolve("real-name");
        Files.createDirectories(target);
        Files.writeString(
                target.resolve("pack.yaml"),
                "pack_id: 01ARWG13C000000000EXTERNA1\npack_version: 1.0.0\nname: external\npack_level: project\n");
        link(packs.resolve("external"), target);
        DomainRepositoryService store =
                new DomainRepositoryService(new StorageRootLocations(packs), new YamlSerializationStrategy());

        assertThat(store.configured())
                .containsExactly(
                        new ConfiguredPack(new PackIdentifier("external"), PackLevel.PROJECT),
                        new ConfiguredPack(new PackIdentifier("qa"), PackLevel.ORG));
        assertThat(store.find(new PackIdentifier("external")))
                .hasValueSatisfying(descriptor -> assertThat(descriptor.name()).isEqualTo("external"));
    }

    @Test
    void aPackDomainLinkThatResolvesToNoDirectoryListsNothingAndIsReportedOnceUntilItIsFixed(@TempDir Path elsewhere)
            throws IOException {
        Files.delete(packs);
        link(packs, elsewhere.resolve("gone"));
        StorageRootLocations locations = new StorageRootLocations(packs);

        try (LogCapture log = new LogCapture()) {
            assertThat(locations.all()).isEmpty();
            assertThat(locations.all()).isEmpty();
            Files.createDirectories(elsewhere.resolve("gone/qa"));
            Files.writeString(elsewhere.resolve("gone/qa/pack.yaml"), "pack_level: org\n");
            assertThat(locations.all()).extracting(PackLocation::name).containsExactly("qa");
            PackCommitStore.deleteTree(elsewhere.resolve("gone/qa"));
            Files.delete(elsewhere.resolve("gone"));
            Files.writeString(elsewhere.resolve("gone"), "a file");
            assertThat(locations.all()).isEmpty();

            assertThat(log.warnings())
                    .as("reported when it breaks, and again when it breaks after it was fixed")
                    .satisfiesExactly(
                            warning -> assertThat(warning).contains("pack domain", "does not resolve"),
                            warning -> assertThat(warning).contains("pack domain", "not a directory"));
        }
    }

    private static void link(Path link, Path target) throws IOException {
        SymbolicLinks.create(link, target);
    }

    /** The warnings {@link StorageRootLocations} logs while open. */
    private static final class LogCapture implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger(StorageRootLocations.class);
        private final ListAppender<ILoggingEvent> events = new ListAppender<>();

        LogCapture() {
            events.start();
            logger.addAppender(events);
        }

        List<String> warnings() {
            return events.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
        }

        @Override
        public void close() {
            logger.detachAppender(events);
        }
    }

    @Test
    void anEmptyDirectoryIsACreatesReservationAndIsNotListed() throws IOException {
        pack("qa", "org");
        Files.createDirectories(packs.resolve("reserved"));

        try (LogCapture log = new LogCapture()) {
            assertThat(new StorageRootLocations(packs).all())
                    .extracting(PackLocation::name)
                    .as("an empty entry reads as no pack (#490, squad D7)")
                    .containsExactly("qa");
            assertThat(log.warnings()).isEmpty();
        }
    }

    /** A listing that never reads a pack's level whole is refused, never answered with the pack at org (STALE_INPUT). */
    @Test
    void aLevelReadAPackKeepsMovingUnderRefusesStaleInputInsteadOfListingTheOrgLevel() throws IOException {
        pack("qa", "project");
        PackWriteGeneration generation = new PackWriteGeneration();
        AtomicInteger reads = new AtomicInteger();
        StorageRootLocations locations = new StorageRootLocations(packs, generation, directory -> {
            reads.incrementAndGet();
            generation.moved("qa"); // a swap moves the pack's directory during every read
            return PackLevel.PROJECT;
        });

        assertThatThrownBy(locations::all)
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
        assertThat(reads).hasValue(PackDirectories.MAX_PROGRESS);
    }

    @Test
    void aHalfSwappedPackWhoseLockIsHeldIsListedThroughItsAsideAtItsLevelWithoutWaiting() throws Exception {
        pack("qa", "project");
        Path live = packs.resolve("qa");
        Path aside = packs.resolve("qa.pack-old-inflight");
        ReentrantLock lock = PackWriteLocks.of(live);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // Another thread holds the pack's write lock with the pack moved aside, as a swap between its renames does.
        CompletableFuture<Void> swap = CompletableFuture.runAsync(() -> {
            lock.lock();
            try {
                Files.move(live, aside);
                held.countDown();
                release.await(30, TimeUnit.SECONDS);
                Files.move(aside, live);
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            } finally {
                lock.unlock();
            }
        });
        try (LogCapture log = new LogCapture()) {
            assertThat(held.await(30, TimeUnit.SECONDS)).isTrue();
            long started = System.nanoTime();

            List<PackLocation> listed = new StorageRootLocations(packs).all();

            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                    .as("the listing did not wait for the lock")
                    .isLessThan(1000);
            assertThat(lock.hasQueuedThreads()).isFalse();
            assertThat(listed)
                    .as("listed under its own entry, at the level its aside copy declares")
                    .containsExactly(new PackLocation("qa", PackLevel.PROJECT, live));
            assertThat(aside).as("nothing was repaired under a held lock").isDirectory();
            assertThat(log.warnings())
                    .as("a swap in flight is not warned about")
                    .isEmpty();
        } finally {
            release.countDown();
        }
        swap.get(30, TimeUnit.SECONDS);
        assertThat(new StorageRootLocations(packs).all())
                .extracting(PackLocation::name)
                .containsExactly("qa");
    }

    @Test
    void aHalfSwappedPackWhoseLockIsFreeIsRepairedAsBefore() throws IOException {
        pack("qa", "org");
        Files.move(packs.resolve("qa"), packs.resolve("qa.pack-old-crashed"));

        assertThat(new StorageRootLocations(packs).all())
                .extracting(PackLocation::name)
                .containsExactly("qa");
        assertThat(packs.resolve("qa/pack.yaml"))
                .as("the aside copy was moved back")
                .isRegularFile();
        assertThat(packs.resolve("qa.pack-old-crashed")).doesNotExist();
    }

    @Test
    void theDomainIsListedOnEveryCallSoAPackCopiedInIsSeen() throws IOException {
        StorageRootLocations locations = new StorageRootLocations(packs);
        assertThat(locations.all()).isEmpty();

        pack("qa", "org");

        assertThat(locations.all()).extracting(PackLocation::name).containsExactly("qa");
    }

    @Test
    void aDomainDirectoryThatDoesNotExistYetHoldsNoPacks() {
        assertThat(new StorageRootLocations(packs.resolve("not-yet")).all()).isEmpty();
        assertThat(packs.resolve("not-yet")).doesNotExist();
    }

    @Test
    void aPackUnderTheDomainIsReadAndWrittenThroughTheStore() throws IOException {
        pack("qa", "org");
        DomainRepositoryService store =
                new DomainRepositoryService(new StorageRootLocations(packs), new YamlSerializationStrategy());
        PackIdentifier qa = new PackIdentifier("qa");

        store.save(qa, store.get(qa));

        assertThat(store.get(qa).packId()).isEqualTo(PACK_ID);
        assertThat(packs.resolve("qa/pack.yaml")).exists();
    }

    @Test
    void theConfigRootKeepsEachDomainInItsOwnDirectoryAndIsAbsolute() {
        ConfigRoot root = new ConfigRoot(Path.of("relative/root"));

        assertThat(root.root()).isAbsolute();
        assertThat(root.packs()).isEqualTo(root.root().resolve("packs"));
        assertThat(root.activations()).isEqualTo(root.root().resolve("activations"));
    }
}
