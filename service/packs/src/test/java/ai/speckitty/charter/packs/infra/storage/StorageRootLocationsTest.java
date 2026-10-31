package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    private void pack(String name, String level) throws IOException {
        Files.createDirectories(packs.resolve(name));
        Files.writeString(
                packs.resolve(name + "/pack.yaml"),
                "pack_id: " + name + "\npack_version: 1.0.0\nname: " + name + "\npack_level: " + level + "\n");
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
        Files.writeString(target.resolve("pack.yaml"), "pack_id: external\npack_level: project\n");
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

    private static void link(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException noLinks) {
            abort("the filesystem cannot create symbolic links");
        }
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

        assertThat(store.get(qa).packId()).isEqualTo("qa");
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
