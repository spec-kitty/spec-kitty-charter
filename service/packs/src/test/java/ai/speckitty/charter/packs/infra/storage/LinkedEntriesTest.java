package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.application.PackVersionHistory;
import ai.speckitty.charter.packs.application.PackWriteGeneration;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.domain.PackContents.Assembly;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.infra.storage.rules.LinkRules;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import ai.speckitty.charter.packs.infra.storage.rules.TreeCensus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A symbolic link inside a pack root is never read through, and every surface refuses it with one
 * {@code pack_entry_linked} (D1, D6, #597): the walk lists it apart from the regular files, the storage rules state it
 * once per link, and the store half states it too, so the composing read of a configured pack refuses the pack. The pack
 * root itself may be a link (#174).
 */
@DisplayName("a link inside a pack is never read through and is refused on every surface (D1, D6, #597)")
class LinkedEntriesTest {

    private static final String PACK = "qa";

    private static final String DESCRIPTOR =
            "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n";

    /** A text no pack file holds: if any read went through a link, it would surface somewhere. */
    private static final String MARKER = "zqlinkmarker7f3a";

    private static final YamlSerializationStrategy STRATEGY = new YamlSerializationStrategy();

    private static String directive(String id, String title) {
        return "schema_version: \"1.0\"\nid: " + id + "\ntitle: " + title + "\nintent: Intent of " + id
                + "\nenforcement: required\n";
    }

    /** A valid org pack named {@value #PACK} holding one directive, in {@code host/packs/qa}. */
    private static Path pack(Path host) throws IOException {
        Path pack = Files.createDirectories(host.resolve("packs").resolve(PACK));
        write(pack.resolve("pack.yaml"), DESCRIPTOR);
        write(pack.resolve("directives/A.directive.yaml"), directive("A", "A"));
        return pack;
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static Path link(Path link, Path target) throws IOException {
        Files.createDirectories(link.getParent());
        return SymbolicLinks.create(link, target);
    }

    private static PackRoot root(Path pack) {
        return new PackRoot(PACK, Optional.empty(), pack.toString());
    }

    private static List<Reason> linked(List<Reason> reasons) {
        return reasons.stream()
                .filter(reason -> StorageCodes.PACK_ENTRY_LINKED.equals(reason.code()))
                .toList();
    }

    // ---- the walk (T001) -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("a pack root given through a link is read whole, with the verdict of its real path (#174)")
    void aRootGivenThroughALinkIsReadWhole(@TempDir Path host) throws IOException {
        Path real = pack(host);
        Path linkedRoot = link(host.resolve("elsewhere").resolve(PACK), real);

        PackFileSet.Walk walk = PackFileSet.walk(linkedRoot, PACK);

        assertThat(walk.regular()).containsExactly("directives/A.directive.yaml", "pack.yaml");
        assertThat(walk.links()).isEmpty();
        List<Reason> throughLink =
                new FileSystemPackInspector().read(root(linkedRoot)).rationale().details();
        assertThat(throughLink)
                .filteredOn(reason -> reason.isFailing(Severity.VIOLATION))
                .isEmpty();
        assertThat(throughLink)
                .isEqualTo(new FileSystemPackInspector()
                        .read(root(real))
                        .rationale()
                        .details());
    }

    @Test
    @DisplayName("a file link is a link, never a regular file of the walk")
    void aFileLinkIsALink(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        Path outside = host.resolve("outside/B.directive.yaml");
        write(outside, directive("B", "B"));
        link(pack.resolve("directives/B.directive.yaml"), outside);

        PackFileSet.Walk walk = PackFileSet.walk(pack, PACK);

        assertThat(walk.links()).containsExactly("directives/B.directive.yaml");
        assertThat(walk.regular()).containsExactly("directives/A.directive.yaml", "pack.yaml");
    }

    @Test
    @DisplayName("a directory link, a dangling link and a link loop are links only; nothing below them is walked")
    void directoryDanglingAndLoopingLinksAreLinksOnly(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        Path outside = host.resolve("outside");
        write(outside.resolve("C.directive.yaml"), directive("C", "C"));
        link(pack.resolve("directives/more"), outside);
        link(pack.resolve("dangling.yaml"), host.resolve("nothing-here"));
        link(pack.resolve("loop"), Path.of("loop"));

        PackFileSet.Walk walk = PackFileSet.walk(pack, PACK);

        assertThat(walk.links()).containsExactly("dangling.yaml", "directives/more", "loop");
        assertThat(walk.regular()).containsExactly("directives/A.directive.yaml", "pack.yaml");
    }

    @Test
    @DisplayName("a link to a file inside the pack is still a link")
    void aLinkToAFileInsideThePackIsALink(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        link(pack.resolve("directives/B.directive.yaml"), Path.of("A.directive.yaml"));

        PackFileSet.Walk walk = PackFileSet.walk(pack, PACK);

        assertThat(walk.links()).containsExactly("directives/B.directive.yaml");
        assertThat(walk.regular()).containsExactly("directives/A.directive.yaml", "pack.yaml");
    }

    // ---- no target byte reaches an output (T001, S2) -------------------------------------------------------------

    /**
     * Where a link stands and what its target holds. Each target names the marker where a read would surface it: as a
     * field the schema refuses by name, so a read through the link would quote it in a schema fault, and as a value.
     */
    static Stream<Arguments> placements() {
        String extra = MARKER + ": " + MARKER + "\n";
        return Stream.of(
                Arguments.of("directives/B.directive.yaml", "B.directive.yaml", directive("B", MARKER) + extra),
                Arguments.of("directives/more", "more/B.directive.yaml", directive("B", MARKER) + extra),
                Arguments.of("pack.yaml", "pack.yaml", DESCRIPTOR + extra),
                Arguments.of("drg/fragment.yaml", "fragment.yaml", "schema_version: '1.0'\n" + extra),
                Arguments.of("drg", "drg/fragment.yaml", "schema_version: '1.0'\n" + extra),
                Arguments.of(
                        "x.graph.yaml",
                        "x.graph.yaml",
                        "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges: []\n" + extra),
                Arguments.of("pack-manifest.yaml", "pack-manifest.yaml", extra),
                Arguments.of("policy.yaml (named by mutability_policy)", "policy.yaml", extra),
                Arguments.of("README.md", "README.md", "# " + MARKER + "\n\n" + MARKER + "\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("placements")
    @DisplayName("the marker a link's target holds reaches no finding, no read fact and no refusal")
    void noTargetByteReachesAnOutput(String placement, String target, String text, @TempDir Path host)
            throws IOException {
        Path pack = pack(host);
        int space = placement.indexOf(' ');
        String at = space < 0 ? placement : placement.substring(0, space);
        if (!at.equals(placement)) {
            write(pack.resolve("pack.yaml"), DESCRIPTOR + "mutability_policy: policy.yaml\n");
        }
        Path outside = host.resolve("outside");
        write(outside.resolve(target), text);
        Path targetOfLink = outside.resolve(Path.of(target).getName(0));
        Files.deleteIfExists(pack.resolve(at));
        link(pack.resolve(at), targetOfLink);
        List<String> outputs = new ArrayList<>();

        FileSystemPackInspector inspector = new FileSystemPackInspector();
        FileSystemPackInspector.OneRead one = inspector.readWithStore(root(pack));
        outputs.add(one.toString());
        inspector.read(root(pack)).rationale().details().forEach(reason -> outputs.add(reason.toString()));
        outputs.add(StoreRead.of(
                        pack,
                        PACK,
                        PackFileSet.walk(pack, PACK),
                        STRATEGY,
                        KindBindings.all(),
                        Companions.Rules.bundled())
                .toString());
        outputs.add(String.valueOf(PackFileSet.digested(pack, PACK)));
        outputs.add(
                messageOf(() -> new WholePackStore(STRATEGY).load(pack).pack().violations()));
        outputs.add(messageOf(() -> new DomainRepositoryService(new StorageRootLocations(pack.getParent()), STRATEGY)
                .compositionFacts(new PackIdentifier(PACK))));

        assertThat(String.join("\n", outputs)).doesNotContain(MARKER);
        assertThat(one.tree().census().entries())
                .as("the link itself is in the census, as a link")
                .contains(new TreeCensus.Entry(at, TreeCensus.Standing.LINK, "", Optional.empty()));
        assertThat(linked(inspector.read(root(pack)).rationale().details()))
                .extracting(reason -> reason.location().file())
                .containsExactly(at);
    }

    /** What an action answers or the message it is refused with. */
    private static String messageOf(ThrowingSupplier action) {
        try {
            return String.valueOf(action.get());
        } catch (RuntimeException | IOException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get() throws IOException;
    }

    // ---- one pack_entry_linked per link (T002) -------------------------------------------------------------------

    @Test
    @DisplayName(
            "the verdict states one pack_entry_linked per link, at the link, in the one wording, and no other fault")
    void oneViolationPerLink(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        Path outside = host.resolve("outside");
        write(outside.resolve("B.directive.yaml"), directive("B", "B"));
        write(outside.resolve("NOTES.txt"), "notes\n");
        link(pack.resolve("directives/B.directive.yaml"), outside.resolve("B.directive.yaml"));
        link(pack.resolve("directives/more"), outside);
        link(pack.resolve("NOTES.txt"), outside.resolve("NOTES.txt"));

        List<Reason> verdict =
                new FileSystemPackInspector().read(root(pack)).rationale().details();

        assertThat(verdict)
                .filteredOn(reason -> reason.isFailing(Severity.VIOLATION))
                .as("exactly one statement per link, and nothing else refused")
                .extracting(reason ->
                        reason.code().value() + " " + reason.location().file() + " " + reason.detail())
                .containsExactly(
                        "pack_entry_linked NOTES.txt NOTES.txt: " + LinkRules.LINKED,
                        "pack_entry_linked directives/B.directive.yaml directives/B.directive.yaml: "
                                + LinkRules.LINKED,
                        "pack_entry_linked directives/more directives/more: " + LinkRules.LINKED);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"pack.yaml", "pack-manifest.yaml", "drg/fragment.yaml"})
    @DisplayName("at a link's path only the link is stated, not what its absence makes missing or irregular there")
    void atALinksPathOnlyTheLinkIsStated(String at, @TempDir Path host) throws IOException {
        Path pack = pack(host);
        Path outside = host.resolve("outside").resolve(Path.of(at).getFileName());
        write(outside, Files.exists(pack.resolve(at)) ? Files.readString(pack.resolve(at)) : "nodes: []\nedges: []\n");
        Files.deleteIfExists(pack.resolve(at));
        link(pack.resolve(at), outside);

        assertThat(new FileSystemPackInspector().read(root(pack)).rationale().details())
                .filteredOn(reason -> reason.isFailing(Severity.VIOLATION))
                .filteredOn(reason -> reason.location().file().equals(at))
                .extracting(reason -> reason.code().value())
                .containsExactly("pack_entry_linked");
    }

    @Test
    @DisplayName("the same tree without its link has no pack_entry_linked")
    void theSameTreeWithoutTheLinkPasses(@TempDir Path host) throws IOException {
        Path pack = pack(host);

        assertThat(new FileSystemPackInspector().read(root(pack)).rationale().details())
                .noneMatch(reason -> StorageCodes.PACK_ENTRY_LINKED.equals(reason.code()));
    }

    // ---- the store half states it, so the configured load refuses the pack (T003, D6) ----------------------------

    @Test
    @DisplayName(
            "the load of a configured pack holding a link records one pack_entry_linked and the compose refuses it")
    void theConfiguredLoadRefusesALinkedPack(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        Path outside = host.resolve("outside/B.directive.yaml");
        write(outside, directive("B", "B"));
        link(pack.resolve("directives/B.directive.yaml"), outside);

        StoreRead read = StoreRead.of(
                pack, PACK, PackFileSet.walk(pack, PACK), STRATEGY, KindBindings.all(), Companions.Rules.bundled());
        assertThat(read.assembly()).isInstanceOfSatisfying(Assembly.Assembled.class, held -> {
            assertThat(held.violations()).singleElement().satisfies(violation -> {
                assertThat(violation.kind()).isEqualTo(StorageCodes.PACK_ENTRY_LINKED);
                assertThat(violation.file()).isEqualTo("directives/B.directive.yaml");
                assertThat(violation.reason()).isEqualTo(LinkRules.LINKED);
            });
            assertThat(held.storeOnly())
                    .as("never a store-only fault, so the verdict states it once")
                    .isEmpty();
            assertThat(held.artifacts())
                    .extracting(placed -> placed.file())
                    .containsExactly("directives/A.directive.yaml");
        });
        assertThat(new WholePackStore(STRATEGY).load(pack).pack().violations())
                .extracting(PackElementViolation::kind)
                .containsExactly(StorageCodes.PACK_ENTRY_LINKED);

        CharterRefusal refused = catchThrowableOfType(
                CharterRefusal.class,
                () -> new DomainRepositoryService(new StorageRootLocations(host.resolve("packs")), STRATEGY)
                        .compositionFacts(new PackIdentifier(PACK)));
        assertThat(refused).as("the composing read refuses the pack").isNotNull();
        assertThat(refused.code())
                .as("as it refuses a pack with another store-half content fault")
                .isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(refused.getMessage())
                .as("the link's path is stated once")
                .isEqualTo("the file directives/B.directive.yaml of qa cannot be read: " + LinkRules.LINKED);
    }

    @Test
    @DisplayName("the same configured pack without the link composes")
    void theSamePackWithoutTheLinkComposes(@TempDir Path host) throws IOException {
        pack(host);

        assertThatCode(() -> new DomainRepositoryService(new StorageRootLocations(host.resolve("packs")), STRATEGY)
                        .compositionFacts(new PackIdentifier(PACK)))
                .doesNotThrowAnyException();
    }

    // ---- the configured load names a linked pack.yaml (review cycle 1, item 5) -----------------------------------

    @Test
    @DisplayName("a configured pack whose pack.yaml is a link is refused naming the link, not as having no pack.yaml")
    void aLinkedDescriptorIsNamedAsALink(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        Path outside = host.resolve("outside/pack.yaml");
        write(outside, DESCRIPTOR);
        Files.delete(pack.resolve("pack.yaml"));
        link(pack.resolve("pack.yaml"), outside);

        CharterRefusal refused = catchThrowableOfType(
                CharterRefusal.class,
                () -> new DomainRepositoryService(new StorageRootLocations(host.resolve("packs")), STRATEGY)
                        .compositionFacts(new PackIdentifier(PACK)));

        assertThat(refused).isNotNull();
        assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(refused.getMessage()).isEqualTo("pack qa: " + LinkRules.detail("pack.yaml"));
    }

    // ---- the store's own reads of a stored pack follow no link (review cycle 1, items 1 to 3, 7) -----------------

    private static final String SHARD = """
            schema_version: '1.0'
            generated_at: '2026-01-01T00:00:00Z'
            generated_by: charter-registration
            nodes: []
            edges: []
            """;

    private static PackCommitStore commits(Path host) {
        return new PackCommitStore(
                new StorageRootLocations(host.resolve("packs")),
                STRATEGY,
                new RegistryWriteLock(Duration.ofSeconds(5)),
                new PackWriteGeneration());
    }

    @Test
    @DisplayName(
            "a per-file commit to a stored pack holding a file link is refused, and the link is not copied through")
    void aCommitNeverCopiesThroughALink(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        Path outside = host.resolve("outside/B.directive.yaml");
        write(outside, directive("B", "B"));
        Path linked = link(pack.resolve("directives/B.directive.yaml"), outside);

        CharterRefusal refused = catchThrowableOfType(
                CharterRefusal.class, () -> commits(host).commit(new PackIdentifier(PACK), null, null, SHARD));

        assertThat(refused).as("the commit is refused").isNotNull();
        assertThat(refused.getMessage()).isEqualTo(LinkRules.detail("directives/B.directive.yaml"));
        assertThat(Files.isSymbolicLink(linked))
                .as("the live link is left as it was")
                .isTrue();
        assertThat(pack.resolve(RelationshipShard.SHARD_PATH))
                .as("nothing was committed")
                .doesNotExist();
    }

    @Test
    @DisplayName("a replace never reads the stored pack's version through a pack.yaml that is a link")
    void aReplaceNeverReadsTheStoredVersionThroughALink(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        Path outside = host.resolve("outside/pack.yaml");
        write(outside, DESCRIPTOR.replace("1.0.0", "9.9.9"));
        Files.delete(pack.resolve("pack.yaml"));
        link(pack.resolve("pack.yaml"), outside);
        Path upload = Files.createDirectories(host.resolve("upload").resolve(PACK));
        write(upload.resolve("pack.yaml"), DESCRIPTOR);
        write(upload.resolve("directives/A.directive.yaml"), directive("A", "A"));
        PackCommitStore store = commits(host);

        store.replaceWhole(new PackIdentifier(PACK), new StagedPack(upload));

        assertThat(store.versionHistory(PACK))
                .extracting(PackVersionHistory.Entry::packVersion)
                .containsExactly("1.0.0");
    }

    @Test
    @DisplayName("the registered relationships are never read through a registered.graph.yaml that is a link")
    void theRegisteredShardIsNeverReadThroughALink(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        write(pack.resolve("directives/B.directive.yaml"), directive("B", "B"));
        Path outside = host.resolve("outside/registered.graph.yaml");
        write(
                outside,
                RelationshipShard.write(List.of(Relationship.of(
                        Urn.parse("directive:A").orElseThrow(),
                        Urn.parse("directive:B").orElseThrow(),
                        RelationType.REQUIRES))));
        link(pack.resolve(RelationshipShard.SHARD_PATH), outside);
        StorageRootLocations locations = new StorageRootLocations(host.resolve("packs"));

        assertThat(new CommitStorePackWriteStore(locations, STRATEGY, commits(host))
                        .registeredRelationships(new PackIdentifier(PACK)))
                .isEmpty();
    }

    @Test
    @DisplayName("the pack-scoped load of a configured pack holding a link records its pack_entry_linked")
    void thePackScopedLoadRecordsTheLink(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        Path outside = host.resolve("outside/B.directive.yaml");
        write(outside, directive("B", "B"));
        link(pack.resolve("directives/B.directive.yaml"), outside);

        assertThat(new DomainRepositoryService(new StorageRootLocations(host.resolve("packs")), STRATEGY)
                        .load(new PackIdentifier(PACK))
                        .violations())
                .extracting(PackElementViolation::kind, PackElementViolation::file)
                .containsExactly(org.assertj.core.api.Assertions.tuple(
                        StorageCodes.PACK_ENTRY_LINKED, "directives/B.directive.yaml"));
    }
}
