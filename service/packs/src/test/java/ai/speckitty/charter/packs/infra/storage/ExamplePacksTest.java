package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.domain.PackInspector.Scope;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackVerdict;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The example packs (ADR-0070) are sound: each one validates with no finding, the same check validate mode runs over a
 * real pack, and removing a file it needs is caught, so the check reads the examples.
 */
class ExamplePacksTest {

    private static final FileSystemPackInspector INSPECTOR = new FileSystemPackInspector();

    private static PackVerdict judge(String name, Path dir) {
        PackLevel level = ExamplePacks.level(name);
        return PackPolicy.judge(INSPECTOR.inspect(new PackRoot(name, Optional.of(level), dir.toString()), Scope.FULL));
    }

    @ParameterizedTest
    @ValueSource(strings = {ExamplePacks.BUILT_IN, ExamplePacks.ORG, ExamplePacks.PROJECT})
    void everyExamplePackValidatesWithNoFinding(String name) {
        assertThat(judge(name, ExamplePacks.pack(name)).findings()).as(name).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {ExamplePacks.BUILT_IN, ExamplePacks.ORG, ExamplePacks.PROJECT})
    void aPackWithoutItsDescriptorIsCaught(String name, @TempDir Path dir) throws Exception {
        Path copy = ExamplePacks.copyOf(name, dir);
        Files.delete(copy.resolve("pack.yaml"));

        assertThat(judge(name, copy).findings())
                .as("the control: the check reads the example")
                .isNotEmpty();
    }

    /** The three example packs as the store configures them, built-in first. */
    static List<PackLocation> locations() {
        return ExamplePacks.names().stream()
                .map(name -> new PackLocation(name, ExamplePacks.level(name), ExamplePacks.pack(name)))
                .toList();
    }

    /** The three packs compose through the production store and the offering read: org-base on built-in, project on both. */
    @Test
    void theThreePacksComposeThroughTheOfferingRead() {
        DomainRepositoryService store =
                new DomainRepositoryService(ExamplePacksTest::locations, new YamlSerializationStrategy());
        OfferingReadService read =
                new OfferingReadService(store, store, store, new ConsistentRead(() -> "sha256:" + "a".repeat(64)));

        assertThat(read.offering().value())
                .extracting(entry -> entry.name())
                .containsExactly(ExamplePacks.BUILT_IN, ExamplePacks.ORG, ExamplePacks.PROJECT);
    }

    /** Every artifact file of every pack loads into the domain through the whole-pack load, with no violation. */
    @ParameterizedTest
    @ValueSource(strings = {ExamplePacks.BUILT_IN, ExamplePacks.ORG, ExamplePacks.PROJECT})
    void everyPackLoadsWholeWithNoViolation(String name) {
        WholePackStore.LoadedPack loaded =
                new WholePackStore(new YamlSerializationStrategy()).load(ExamplePacks.pack(name));

        assertThat(loaded.pack().violations()).as(name).isEmpty();
        assertThat(loaded.unmodelled()).as(name).isEmpty();
        assertThat(loaded.warnings()).as(name).isEmpty();
    }

    /**
     * The example packs hold one excluded file of each kind (#451): an author's note the {@code ignored} fallback takes
     * ({@code org-base/docs/authoring-notes.md}) and an orientation README an exclusion names. Neither is unmodelled and
     * neither is held: the write does not reproduce it.
     */
    @Test
    void anAuthorNoteAndAnOrientationReadmeAreExcludedAndNotHeld(@TempDir Path host) throws IOException {
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        WholePackStore.LoadedPack org = store.load(ExamplePacks.pack(ExamplePacks.ORG));
        WholePackStore.LoadedPack builtIn = store.load(ExamplePacks.pack(ExamplePacks.BUILT_IN));

        assertThat(org.excluded()).singleElement().satisfies(file -> {
            assertThat(file.file()).isEqualTo("docs/authoring-notes.md");
            assertThat(file.reason()).startsWith("Author aid: Markdown that no files rule models");
        });
        assertThat(builtIn.excluded())
                .extracting(WholePackWriteStore.ExcludedFile::file)
                .contains("missions/software-dev/templates/README.md");
        Path written = host.resolve("org-base");
        store.write(org, written);
        try (Stream<Path> files = Files.walk(written)) {
            assertThat(files.filter(Files::isRegularFile)
                            .map(path -> written.relativize(path).toString()))
                    .as("the note is not held")
                    .doesNotContain("docs/authoring-notes.md");
        }
    }
}
