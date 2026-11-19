package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.missions.domain.Lifecycle;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.domain.NotHeldFile;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
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

    /** The one read's verdict on a pack, which validate mode and a registration judge: every finding. */
    private static List<Reason> findings(String name, Path dir) {
        PackLevel level = ExamplePacks.level(name);
        return INSPECTOR
                .read(new PackRoot(name, Optional.of(level), dir.toString()))
                .rationale()
                .failures();
    }

    @ParameterizedTest
    @ValueSource(strings = {ExamplePacks.BUILT_IN, ExamplePacks.ORG, ExamplePacks.PROJECT, ExamplePacks.ORG_MANDATE})
    void everyExamplePackValidatesWithOnlyFileNotHeldWarnings(String name) {
        // A file the pack does not hold (an orientation README, an author's note) is a file_not_held warning, one for
        // each file the store's load lists not held, and nothing else is found (#554, open case 6).
        assertThat(findings(name, ExamplePacks.pack(name)))
                .as(name)
                .extracting(reason -> reason.location().file(), Reason::code, Reason::detail)
                .containsExactlyElementsOf(new WholePackStore(new YamlSerializationStrategy())
                        .load(ExamplePacks.pack(name)).notHeld().stream()
                                .map(file -> tuple(file.path(), StorageCodes.FILE_NOT_HELD, file.detail()))
                                .toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {ExamplePacks.BUILT_IN, ExamplePacks.ORG, ExamplePacks.PROJECT, ExamplePacks.ORG_MANDATE})
    void aPackWithoutItsDescriptorIsCaught(String name, @TempDir Path dir) throws Exception {
        Path copy = ExamplePacks.copyOf(name, dir);
        Files.delete(copy.resolve("pack.yaml"));

        assertThat(findings(name, copy))
                .as("the control: the check reads the example")
                .isNotEmpty();
    }

    /** The example packs as the store configures them, built-in first. */
    static List<PackLocation> locations() {
        return ExamplePacks.names().stream()
                .map(name -> new PackLocation(name, ExamplePacks.level(name), ExamplePacks.pack(name)))
                .toList();
    }

    /**
     * The five packs compose through the production store and the offering read: org-base, org-alt and org-mandate on
     * built-in, and project on both of the first two org packs, settling their contest (#358).
     */
    @Test
    void theExamplePacksComposeThroughTheOfferingRead() {
        DomainRepositoryService store =
                new DomainRepositoryService(ExamplePacksTest::locations, new YamlSerializationStrategy());
        OfferingReadService read =
                new OfferingReadService(store, store, store, new ConsistentRead(() -> "sha256:" + "a".repeat(64)));

        assertThat(read.offering().value())
                .extracting(entry -> entry.name())
                .containsExactlyElementsOf(ExamplePacks.names());
    }

    /** Every artifact file of every pack loads into the domain through the whole-pack load, with no violation. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                ExamplePacks.BUILT_IN,
                ExamplePacks.ORG,
                ExamplePacks.ORG_ALT,
                ExamplePacks.PROJECT,
                ExamplePacks.ORG_MANDATE
            })
    void everyPackLoadsWholeWithNoViolation(String name) {
        WholePackStore.LoadedPack loaded =
                new WholePackStore(new YamlSerializationStrategy()).load(ExamplePacks.pack(name));

        assertThat(loaded.pack().violations()).as(name).isEmpty();
        assertThat(loaded.notHeld()).as(name).noneMatch(file -> file.kind() == NotHeldFile.Kind.UNMODELLED);
        assertThat(loaded.warnings()).as(name).isEmpty();
    }

    /**
     * The review step contract's one gate is the work-package lifecycle's guard (ADR-0077 decision 3, ADR-0054 decision
     * 12): in the built-in example pack, {@code in_progress -> for_review} carries {@code spec-kitty-pre-review}, and the
     * guard it names is one the pack holds.
     */
    @Test
    void theExampleLifecyclesReviewTransitionCarriesThePreReviewGuard() {
        Pack builtIn = new WholePackStore(new YamlSerializationStrategy())
                .load(ExamplePacks.pack(ExamplePacks.BUILT_IN))
                .pack();
        Lifecycle workPackage = builtIn.artifacts(ArtifactKind.LIFECYCLE, Lifecycle.class).stream()
                .filter(lifecycle -> lifecycle.localId().equals("work-package"))
                .findFirst()
                .orElseThrow();

        assertThat(workPackage.edgesFrom("in_progress"))
                .filteredOn(transition -> transition.to().equals("for_review"))
                .singleElement()
                .satisfies(transition -> assertThat(transition.guards())
                        .extracting(entry -> entry.ref().spelling())
                        .contains("spec-kitty-pre-review"));
        assertThat(builtIn.artifacts(ArtifactKind.GUARD).stream()
                        .map(guard -> guard.urn().id()))
                .contains("spec-kitty-pre-review");
    }

    /**
     * The example packs hold one excluded file of each kind (#451): an author's note the {@code ignored} fallback takes
     * ({@code org-base/docs/authoring-notes.md}) and an orientation README an exclusion names. Neither is unmodelled and
     * neither is held: each is listed as not held with its rule's reason, and the write does not reproduce it.
     */
    @Test
    void anAuthorNoteAndAnOrientationReadmeAreExcludedAndNotHeld(@TempDir Path host) throws IOException {
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        WholePackStore.LoadedPack org = store.load(ExamplePacks.pack(ExamplePacks.ORG));
        WholePackStore.LoadedPack builtIn = store.load(ExamplePacks.pack(ExamplePacks.BUILT_IN));

        assertThat(org.notHeld()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("docs/authoring-notes.md");
            assertThat(file.reason()).startsWith("Author aid: Markdown that no files rule models");
        });
        assertThat(builtIn.notHeld())
                .noneMatch(file -> file.kind() == NotHeldFile.Kind.UNMODELLED)
                .extracting(NotHeldFile::path)
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
