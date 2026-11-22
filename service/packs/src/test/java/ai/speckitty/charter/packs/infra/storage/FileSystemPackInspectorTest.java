package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.ClassifiedFile;
import ai.speckitty.charter.packs.domain.MissionTypeFacts;
import ai.speckitty.charter.packs.domain.NotHeldFile;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.rules.Findings;
import ai.speckitty.charter.packs.domain.rules.Precedence;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.rules.ManifestRules;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import ai.speckitty.charter.packs.infra.storage.rules.StorageRulesets;
import ai.speckitty.charter.packs.infra.storage.rules.TreeCensus;
import ai.speckitty.charter.packs.infra.storage.rules.TreeRead;
import ai.speckitty.charter.packvalidation.PackCheck;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The load-path inspector reads {@code pack.yaml}, the root shards and the fragment as facts, and nothing else. */
class FileSystemPackInspectorTest {

    private static final FileSystemPackInspector INSPECTOR = new FileSystemPackInspector();

    /** The files validate mode classifies in the built-in example pack. */
    private static PackRoot root(Path dir) {
        return new PackRoot("qa", Optional.of(PackLevel.ORG), dir.toString());
    }

    private static <T> FileRead.Read<T> read(FileRead<T> file) {
        if (file instanceof FileRead.Read<T> read) {
            return read;
        }
        throw new AssertionError("expected a Read but was " + file);
    }

    private static <T> FileRead.Unreadable<T> unreadable(FileRead<T> file) {
        if (file instanceof FileRead.Unreadable<T> unreadable) {
            return unreadable;
        }
        throw new AssertionError("expected an Unreadable but was " + file);
    }

    private static void write(Path dir, String relative, String text) throws IOException {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    @Test
    void aPackIsReadAsItsDescriptorItsShardsInNameOrderAndItsFragment(@TempDir Path dir) throws IOException {
        write(
                dir,
                "pack.yaml",
                "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");
        write(dir, "b.graph.yaml", "schema_version: '1.0'\ngenerated_at: S\nnodes: []\nedges: []\n");
        write(dir, "a.graph.yaml", "schema_version: '1.0'\ngenerated_at: S\nnodes: []\nedges: []\n");
        write(dir, "readme.md", "# not read\n");
        write(dir, "tactics/x.tactic.yaml", "id: x\n");
        write(dir, "drg/fragment.yaml", "nodes: []\nedges: []\n");

        PackContents contents = full(dir).contents();

        assertThat(contents.root()).isEqualTo(root(dir));
        assertThat(read(contents.descriptor()).file()).isEqualTo("pack.yaml");
        assertThat(read(contents.descriptor()).value().declaredLevel()).contains(PackLevel.ORG);
        assertThat(contents.shards()).extracting(FileRead::file).containsExactly("a.graph.yaml", "b.graph.yaml");
        assertThat(contents.shards()).allSatisfy(s -> assertThat(s).isInstanceOf(FileRead.Read.class));
        assertThat(read(contents.fragment()).file()).isEqualTo("drg/fragment.yaml");
        assertThat(read(contents.fragment()).value().shape()).isEqualTo(RawGraph.Shape.FRAGMENT);
        assertThat(contents.missionTypes()).isEmpty();
        assertThat(contents.artifacts()).isEmpty();
        assertThat(contents.assets()).isEmpty();
    }

    @Test
    void aSourceShardNamedLikeTheRegistrationShardIsReadAndTheCensusRefusesIt(@TempDir Path dir) throws IOException {
        write(
                dir,
                "pack.yaml",
                "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");
        write(
                dir,
                "registered.graph.yaml",
                "schema_version: '1.0'\ngenerated_at: STATIC\nnodes:\n- {urn: 'directive:A', kind: directive}\nedges: []\n");

        // #554, C-4: the shard is read as any root shard; the census states that no registration wrote it.
        read(full(dir).contents().shards().get(0));
        assertThat(full(dir).census().reservedShard()).isEqualTo(TreeCensus.ReservedShard.UNSTAMPED);
        assertThat(full(dir).readFaults()).noneMatch(fault -> StorageCodes.RESERVED_SHARD.equals(fault.code()));

        // #554, R2 row 28: validate mode answers reserved_shard in the registration's words, once.
        assertThat(new PackCheck(INSPECTOR).validate(root(dir)).findings())
                .filteredOn(finding -> finding.location().file().equals("registered.graph.yaml"))
                .extracting(Reason::code, Reason::detail)
                .containsExactly(tuple(StorageCodes.RESERVED_SHARD, RelationshipShard.RESERVED));
        assertThatThrownBy(() -> StagedReads.contents(new WholePackStore(new YamlSerializationStrategy()), dir))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.violations())
                                .filteredOn(violation -> violation.pointer().equals("registered.graph.yaml"))
                                .extracting(RefusalViolation::code, RefusalViolation::detail)
                                .containsExactly(
                                        tuple(StorageCodes.RESERVED_SHARD.value(), RelationshipShard.RESERVED)));

        write(dir, "registered.graph.yaml", RelationshipShard.write(java.util.List.of()));

        read(full(dir).contents().shards().get(0));
        assertThat(full(dir).census().reservedShard()).isEqualTo(TreeCensus.ReservedShard.STAMPED);
    }

    @Test
    void aFileThatIsNotThereIsAbsent(@TempDir Path dir) {
        PackContents contents = full(dir).contents();

        assertThat(contents.descriptor()).isEqualTo(new FileRead.Absent<PackDescriptor>("pack.yaml"));
        assertThat(contents.shards()).isEmpty();
        assertThat(contents.fragment()).isEqualTo(new FileRead.Absent<RawGraph>("drg/fragment.yaml"));
    }

    @Test
    void aFileThatIsNotYamlOrBreaksItsSchemaIsUnreadableAndNeverThrows(@TempDir Path dir) throws IOException {
        write(dir, "pack.yaml", "pack_id: [\n");
        write(dir, "a.graph.yaml", "nodes: [\n");
        write(dir, "b.graph.yaml", "surprise: true\n");
        write(dir, "drg/fragment.yaml", "- a\n");

        TreeRead tree = full(dir);
        PackContents contents = tree.contents();

        // WP09 review: an unparseable root shard is one read fault, never stated twice.
        assertThat(tree.readFaults())
                .extracting(fault -> fault.location().file())
                .filteredOn(file -> file.equals("a.graph.yaml"))
                .hasSize(1);
        assertThat(tree.readFaults())
                .filteredOn(fault -> fault.location().file().equals("b.graph.yaml"))
                .as("a schema breach is one fault per violation")
                .isNotEmpty()
                .doesNotHaveDuplicates()
                .allSatisfy(fault -> assertThat(fault.code()).isEqualTo(PackCodes.DRG_SCHEMA_ERROR));
        assertThat(unreadable(contents.descriptor()).parse()).isNotNull();
        assertThat(unreadable(contents.shards().get(0)).file()).isEqualTo("a.graph.yaml");
        assertThat(unreadable(contents.shards().get(0)).parse()).isNotNull();
        assertThat(unreadable(contents.shards().get(1)).file()).isEqualTo("b.graph.yaml");
        assertThat(unreadable(contents.shards().get(1)).violations()).isNotEmpty();
        assertThat(unreadable(contents.fragment()).violations()).isNotEmpty();
    }

    @Test
    void aRootThatIsNoDirectoryIsRefusedAsUnreadable(@TempDir Path dir) {
        assertThat(full(dir.resolve("missing")).readFaults()).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(StorageCodes.PACK_UNREADABLE);
            assertThat(fault.location().file()).isEqualTo(FileSystemPackInspector.ROOT);
            assertThat(fault.detail()).isEqualTo("pack qa is not a directory");
        });
    }

    @Test
    void aFileThatIsNotUtf8IsNotYamlAndTheCodeFollowsItsKind(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("pack.yaml"), new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00});
        Files.write(dir.resolve("tactic.graph.yaml"), new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00});
        Files.createDirectories(dir.resolve("drg"));
        Files.write(dir.resolve("drg/fragment.yaml"), new byte[] {'a', ':', ' ', (byte) 0xC3});

        PackContents contents = full(dir).contents();

        assertThat(unreadable(contents.descriptor()).parse())
                .isEqualTo(new PackContents.ParseFailure("pack.yaml", "the file is not valid UTF-8"));
        assertThat(unreadable(contents.shards().get(0)).parse())
                .isEqualTo(new PackContents.ParseFailure("tactic.graph.yaml", "the file is not valid UTF-8"));
        assertThat(unreadable(contents.fragment()).parse())
                .isEqualTo(new PackContents.ParseFailure("drg/fragment.yaml", "the file is not valid UTF-8"));
    }

    /**
     * A {@code pack.yaml} with 217 unknown keys, {@code N} and {@code Nx} mixed, once made the segment order a cycle, and
     * both the load and validate mode threw TimSort's "Comparison method violates its general contract!". Each key is now a
     * schema refusal at the root (ADR-0036 decision 3), so every finding has the same place and this fixture no longer
     * exercises the segment order: {@code PrecedenceTest} guards the comparator. This test keeps the fixture as a
     * load-and-judge smoke test: the pack loads, and each key is refused once.
     */
    @Test
    void aPackYamlWithManyUnknownKeysIsLoadedAndEachKeyIsRefusedOnce(@TempDir Path dir)
            throws IOException, URISyntaxException {
        Files.copy(
                Path.of(FileSystemPackInspectorTest.class
                        .getResource("/pack-order/pack.yaml")
                        .toURI()),
                dir.resolve("pack.yaml"));
        PackRoot project = new PackRoot("demo", Optional.of(PackLevel.PROJECT), dir.toString());

        // The closed descriptor schema (ADR-0036 decision 3) refuses each key at the root, naming it in the detail.
        assertThat(verdict(project))
                .extracting(f -> f.detail()
                        .substring(f.detail().indexOf('\'') + 1, f.detail().lastIndexOf('\'')))
                .as("every unknown key refused once")
                .hasSize(217)
                .doesNotHaveDuplicates();
    }

    // ---- the full scope ------------------------------------------------------------------------------------------

    private static final String TACTIC = "id: %s\nschema_version: '1.0'\nname: T\nsteps:\n- title: s\n";
    private static final String ASSET = "id: %s\nmime: %s\npath: %s\n";

    private static TreeRead full(Path dir) {
        return INSPECTOR.readTree(root(dir));
    }

    /**
     * The one read's verdict, which validate mode and a registration judge, in validate mode's order: every finding,
     * sorted as {@code PackVerdicts} sorts them, with no placement group (the placement rules are validate mode's own).
     */
    private static List<Reason> verdict(PackRoot root) {
        List<Reason> findings = new ArrayList<>(INSPECTOR.read(root).rationale().failures());
        findings.sort(Precedence.comparator(Set.of()));
        return findings;
    }

    private static List<Reason> verdict(Path dir) {
        return verdict(root(dir));
    }

    /** The files the read refused, each once, in the order of their first fault. */
    private static List<String> faultFiles(TreeRead read) {
        return read.readFaults().stream()
                .map(fault -> fault.location().file())
                .distinct()
                .toList();
    }

    /** The details of the faults the read met at one file, in order. */
    private static List<String> faultsAt(TreeRead read, String file) {
        return read.readFaults().stream()
                .filter(fault -> fault.location().file().equals(file))
                .map(Reason::detail)
                .toList();
    }

    @Test
    void theFullScopeEntersAPackRootThatIsALinkAndNoDirectoryLinkBelowIt(@TempDir Path dir) throws IOException {
        Path real = dir.resolve("checkout/qa");
        write(
                real,
                "pack.yaml",
                "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");
        write(real, "tactics/x.tactic.yaml", TACTIC.formatted("x"));
        write(real, "notes.yaml", "no: rule\n");
        Path outside = dir.resolve("outside");
        write(outside, "y.tactic.yaml", TACTIC.formatted("y"));
        SymbolicLinks.create(real.resolve("linked"), outside);
        Path link = SymbolicLinks.create(
                Files.createDirectories(dir.resolve("packs")).resolve("qa"), real);

        TreeRead linked = full(link);
        TreeRead direct = full(real);

        assertThat(linked.census().files())
                .as("validate mode walks a linked root from its real path (#284)")
                .extracting(ClassifiedFile::file)
                .containsExactly("pack.yaml", "tactics/x.tactic.yaml");
        assertThat(linked.census().files()).isEqualTo(direct.census().files());
        assertThat(linked.contents().artifacts())
                .isEqualTo(direct.contents().artifacts())
                .isNotEmpty();
        assertThat(linked.readFaults()).isEqualTo(direct.readFaults());
    }

    @Test
    void everyYamlFileIsClassifiedInPathOrderAndAnUnclassifiedOneIsLeftOut(@TempDir Path dir) throws IOException {
        write(dir, "pack.yaml", "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\n");
        write(dir, "a.graph.yaml", "schema_version: '1.0'\ngenerated_at: S\nnodes: []\nedges: []\n");
        write(dir, "drg/fragment.yaml", "nodes: []\nedges: []\n");
        write(dir, "org-charter.yaml", "schema_version: 2\n");
        write(dir, "tactics/x.tactic.yaml", TACTIC.formatted("x"));
        write(dir, "tactics-b/y.tactic.yaml", TACTIC.formatted("y"));
        write(dir, "missions/m/mission.yaml", "anything: goes\n");
        write(dir, "missions/m/templates/plan-field-declaration.yaml", "anything: goes\n");
        write(dir, "notes.yaml", "no: rule\n");
        write(dir, "readme.md", "# not yaml\n");
        Files.createDirectories(dir.resolve("dir.tactic.yaml"));

        assertThat(full(dir).census().files())
                .containsExactly(
                        classified("a.graph.yaml", "graph-shard"),
                        classified("drg/fragment.yaml", "org-fragment"),
                        // A retired file (#194; the org-charter.yaml of #431) is refused by name, never listed; an
                        // excluded one is listed.
                        excluded(
                                "missions/m/templates/plan-field-declaration.yaml",
                                "**/templates/plan-field-declaration.yaml"),
                        classified("pack.yaml", "pack-descriptor"),
                        classified("tactics/x.tactic.yaml", "tactic"),
                        classified("tactics-b/y.tactic.yaml", "tactic"));
    }

    @Test
    void excludedMarkdownIsListedWithItsRuleButNeverReadAndNeverAFinding(@TempDir Path dir) throws IOException {
        write(dir, "pack.yaml", "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\n");
        // Text no Markdown reader would accept in a pack file: it must never be parsed (#451).
        write(dir, "docs/notes.md", "---\nunclosed: [\n");
        write(dir, "NOTES.MD", "---\nunclosed: [\n");
        write(dir, "missions/m/README.md", "---\nunclosed: [\n");
        // A skill body is claimed by its skill: held with its owner, so not excluded.
        write(dir, "skills/foo.skill.yaml", """
                schema_version: '1.0'
                id: foo
                title: Foo
                description: Use when foo.
                form: prompt
                body_path: foo.skill.md
                invocation:
                  user_invocable: true
                  side_effects: []
                """);
        write(dir, "skills/foo.skill.md", "Do foo.");

        TreeRead contents = full(dir);

        assertThat(contents.census().files())
                .filteredOn(file -> file.exclusionClass().isPresent())
                .containsExactlyInAnyOrder(
                        excluded("NOTES.MD", "**/*.md"),
                        excluded("docs/notes.md", "**/*.md"),
                        excluded("missions/m/README.md", "*/missions/*/README.md"));
        assertThat(contents.census().files()).extracting(ClassifiedFile::file).doesNotContain("skills/foo.skill.md");
        assertThat(contents.readFaults())
                .as("an excluded file is not read, so it is never unreadable")
                .isEmpty();
    }

    private static ClassifiedFile classified(String file, String kind) {
        return new ClassifiedFile(file, Optional.of(kind), Optional.empty());
    }

    private static ClassifiedFile excluded(String file, String exclusionClass) {
        return new ClassifiedFile(file, Optional.empty(), Optional.of(exclusionClass));
    }

    @Test
    void aMappedFileThatIsNotYamlOrBreaksItsSchemaIsUnreadableNotAnException(@TempDir Path dir) throws IOException {
        write(dir, "tactics/bad.tactic.yaml", "id: [\n");
        write(dir, "tactics/short.tactic.yaml", "id: only\n");
        write(dir, "project/charter.yaml", "governance: 3\n");
        write(dir, "directives/bytes.directive.yaml", "id: !!binary AAAA\n");
        write(dir, "paradigms/utf.paradigm.yaml", "a: b\n");
        Files.write(dir.resolve("paradigms/utf.paradigm.yaml"), new byte[] {(byte) 0xC3, 0x28});

        TreeRead contents = full(dir);
        assertThat(faultFiles(contents))
                .containsExactly(
                        "directives/bytes.directive.yaml",
                        "paradigms/utf.paradigm.yaml",
                        "tactics/bad.tactic.yaml",
                        "tactics/short.tactic.yaml");
        assertThat(faultsAt(contents, "directives/bytes.directive.yaml")).hasSize(1);
        assertThat(faultsAt(contents, "paradigms/utf.paradigm.yaml"))
                .containsExactly("paradigms/utf.paradigm.yaml: the file is not valid UTF-8");
        assertThat(faultsAt(contents, "tactics/bad.tactic.yaml")).hasSize(1);
        assertThat(contents.readFaults())
                .filteredOn(fault -> fault.location().file().equals("tactics/short.tactic.yaml"))
                .isNotEmpty()
                .allSatisfy(fault -> assertThat(fault.code()).isEqualTo(PackCodes.ARTIFACT_SCHEMA_ERROR));
        // No rule maps a charter.yaml: it is not held and never read, however it breaks the charter schema (#554).
        assertThat(contents.census().notHeld()).containsExactly(NotHeldFile.unmodelled("project/charter.yaml"));
    }

    @Test
    void aRetiredOrgCharterIsRefusedByNameNotListedAndNotReadAsASchema(@TempDir Path dir) throws IOException {
        write(dir, "tactics/x.tactic.yaml", TACTIC.formatted("x"));
        write(dir, "org-charter.yaml", "schema_version: 2\norg_name: acme\n");

        TreeRead contents = full(dir);

        assertThat(contents.census().files()).extracting(ClassifiedFile::file).doesNotContain("org-charter.yaml");
        assertThat(contents.readFaults()).singleElement().satisfies(fault -> {
            assertThat(fault.location().file()).isEqualTo("org-charter.yaml");
            assertThat(fault.code()).isEqualTo(StorageCodes.RETIRED);
            assertThat(fault.detail())
                    .isEqualTo("org-charter.yaml: org enforcement is an activation preset with must-have items"
                            + " plus policy.yaml levels (#431)");
        });
    }

    private static final String GUIDE = "# Use make\n\n## Overview\n\nHow to use make.\n";

    @Test
    void aToolguideGuideIsReadAsMarkdownAndHeldToTheRulesTheLoadHoldsItTo(@TempDir Path dir) throws IOException {
        write(dir, "toolguides/good.guide.md", GUIDE);
        write(dir, "toolguides/preamble.guide.md", "# Use make\n\nRead this first.\n\n## Overview\n\nO.\n");
        write(dir, "toolguides/no-overview.guide.md", "# Use make\n\n## Usage\n\nRun make.\n");
        write(dir, "toolguides/no-h1.guide.md", "## Overview\n\nO.\n");
        write(dir, "toolguides/fenced.guide.md", "# Use make\n\n## Overview\n\n```\nunclosed\n");
        write(dir, "toolguides/README.md", "Not a guide.\n");
        write(dir, "notes/loose.md", "Read me.\n");
        // Each guide is read with the toolguide that names it; a guide no toolguide names is not held (open case 9).
        for (String id : List.of("good", "preamble", "no-overview", "no-h1", "fenced")) {
            write(dir, "toolguides/" + id + ".toolguide.yaml", """
                    id: %s
                    schema_version: '1.0'
                    tool: make
                    title: Make
                    guide_path: %s.guide.md
                    summary: S.
                    commands:
                    - make
                    last_updated: '2026-07-28'
                    """.formatted(id, id));
        }

        TreeRead read = full(dir);

        assertThat(faultFiles(read))
                .containsExactlyInAnyOrder(
                        "toolguides/preamble.guide.md",
                        "toolguides/no-overview.guide.md",
                        "toolguides/no-h1.guide.md",
                        "toolguides/fenced.guide.md");
        assertThat(read.readFaults())
                .filteredOn(fault -> fault.location().file().equals("toolguides/preamble.guide.md"))
                .singleElement()
                .satisfies(fault -> assertThat(fault.code()).isEqualTo(PackCodes.ARTIFACT_SCHEMA_ERROR));
        assertThat(faultsAt(read, "toolguides/no-overview.guide.md"))
                .containsExactly("toolguides/no-overview.guide.md: the guide has no ## Overview");
        assertThat(faultsAt(read, "toolguides/no-h1.guide.md"))
                .singleElement()
                .asString()
                .contains("has no H1");
        // #554, R2 row 19: a companion's own fault is companion in validate mode, as the registration answers it.
        assertThat(verdict(dir))
                .filteredOn(finding -> finding.location().file().equals("toolguides/no-overview.guide.md"))
                .extracting(Reason::code, Reason::detail)
                .containsExactly(
                        tuple(StorageCodes.COMPANION, "toolguides/no-overview.guide.md: the guide has no ## Overview"));
        assertThat(full(dir).census().files())
                .as("a Markdown file a rule models is read, not listed; Markdown nothing models is listed as excluded"
                        + " (#451)")
                .filteredOn(file -> file.file().endsWith(".md"))
                .containsExactlyInAnyOrder(
                        excluded("notes/loose.md", "**/*.md"), excluded("toolguides/README.md", "**/*.md"));
    }

    /** A toolguide that names {@code toolguides/<id>.guide.md}, so the read takes the guide as its companion. */
    private static void toolguide(Path dir, String id) throws IOException {
        write(dir, "toolguides/" + id + ".toolguide.yaml", """
                id: %s
                schema_version: '1.0'
                tool: make
                title: Make
                guide_path: %s.guide.md
                summary: S.
                commands:
                - make
                last_updated: '2026-07-28'
                """.formatted(id, id));
    }

    @Test
    void aCompanionTheMarkdownCodecRefusesIsOneCompanionFaultAtTheCompanion(@TempDir Path dir) throws IOException {
        toolguide(dir, "fenced");
        write(dir, "toolguides/fenced.guide.md", "# Use make\n\n## Overview\n\n```\nunclosed\n");

        // #554, WP04 review NIT: the codec's refusal is the companion's own fault, under companion, naming the line.
        assertThat(full(dir).readFaults())
                .filteredOn(fault -> fault.location().file().equals("toolguides/fenced.guide.md"))
                .singleElement()
                .satisfies(fault -> {
                    assertThat(fault.code()).isEqualTo(StorageCodes.COMPANION);
                    assertThat(fault.detail()).contains("line 5: unclosed fence opened at line 5");
                });
    }

    @Test
    void aCompanionThatIsNotUtf8IsOneCompanionFaultAndAFragmentThatIsNotUtf8IsPromptFragmentInvalid(@TempDir Path dir)
            throws IOException {
        validPair(dir);
        Files.write(dir.resolve(fragment("spdd-specify")), new byte[] {'#', ' ', (byte) 0xC3, '\n'});

        // #554, WP04 review NIT: bytes that are not UTF-8 carry no code of their own; the read gives the companion's
        // code (coded()), prompt_fragment_invalid for a prompt fragment, which the read always reads.
        assertThat(full(dir).readFaults())
                .filteredOn(fault -> fault.location().file().equals(fragment("spdd-specify")))
                .extracting(Reason::code, Reason::detail)
                .containsExactly(tuple(
                        PackCodes.PROMPT_FRAGMENT_INVALID,
                        fragment("spdd-specify") + ": " + FilesystemPackStore.NOT_UTF8));

        // Any other companion that is not UTF-8 is not held, so the read does not read it: the store half meets it,
        // and answers one companion fault at the companion (R2 row 19).
        write(
                dir,
                "pack.yaml",
                "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");
        toolguide(dir, "latin");
        Files.write(dir.resolve("toolguides/latin.guide.md"), new byte[] {'#', ' ', (byte) 0xC3, '\n'});

        assertThat(verdict(dir))
                .filteredOn(finding -> finding.location().file().equals("toolguides/latin.guide.md"))
                .filteredOn(finding -> finding.code().equals(StorageCodes.COMPANION))
                .extracting(Reason::detail)
                .containsExactly("is not valid UTF-8");
    }

    private static final String ENHANCEMENT_DIR = "missions/software-dev/enhancements/";
    private static final String ENHANCEMENT_GRAPH = "enhancements.graph.yaml";
    private static final String SPDD_EDGE = """
            schema_version: '1.0'
            generated_at: STATIC
            nodes: []
            edges:
            - source: step_enhancement:spdd-specify
              target: base::action:software-dev/specify
              relation: enhances
            """;
    private static final String SPDD_ENHANCEMENT = """
            schema_version: "1.0"
            id: spdd-specify
            prompt_fragments:
              - slot: spdd:reasons-block
                path: spdd-specify.prompt-fragment.md
            """;

    /**
     * The shape of open-packs#175: a step enhancement and its prompt fragment. The enhancement is a bound kind (#428):
     * the listing holds its yaml under {@link ArtifactKind#STEP_ENHANCEMENT}, and the store holds the fragment as the
     * enhancement's companion, so the store's read of the enhancement carries the body ({@code byKind()} never lists a
     * companion). Validate reads the fragment against its structure schema as it reads the yaml against its own.
     * Neither is a 500.
     */
    @Test
    void anEnhancementAndItsPromptFragmentAreLoadedAndValidatedWithoutAFailureAndHeldByTheStore(@TempDir Path dir)
            throws IOException {
        write(dir, ENHANCEMENT_GRAPH, SPDD_EDGE);
        write(dir, ENHANCEMENT_DIR + "spdd-specify.step-enhancement.yaml", SPDD_ENHANCEMENT);
        write(dir, ENHANCEMENT_DIR + "spdd-specify.prompt-fragment.md", "## REASONS guidance\n\nState the reasons.\n");

        PackFileSet.Listing listing = PackFileSet.listing(dir);
        TreeRead contents = full(dir);

        assertThat(listing.byKind().get(ArtifactKind.STEP_ENHANCEMENT))
                .as("the store lists the enhancement yaml, and only that, under its kind (#428)")
                .containsExactly(ENHANCEMENT_DIR + "spdd-specify.step-enhancement.yaml");
        assertThat(new FilesystemPackStore(dir.getParent(), new YamlSerializationStrategy())
                        .readListed(
                                dir,
                                ENHANCEMENT_DIR + "spdd-specify.step-enhancement.yaml",
                                StepEnhancementBinding.INSTANCE))
                .as("the fragment is held as the enhancement's companion: the store's read carries its body")
                .hasValueSatisfying(dso -> assertThat(dso.promptFragments())
                        .singleElement()
                        .satisfies(entry -> assertThat(entry.body()).isPresent()));
        assertThat(contents.readFaults()).isEmpty();
        assertThat(contents.census().files())
                .extracting(ClassifiedFile::file)
                .contains(ENHANCEMENT_DIR + "spdd-specify.step-enhancement.yaml");
    }

    @Test
    void aPromptFragmentThatBreaksItsStructureIsUnreadableInValidateAndNotAFailureOnLoad(@TempDir Path dir)
            throws IOException {
        write(dir, ENHANCEMENT_GRAPH, SPDD_EDGE);
        write(dir, ENHANCEMENT_DIR + "spdd-specify.step-enhancement.yaml", SPDD_ENHANCEMENT);
        write(dir, ENHANCEMENT_DIR + "spdd-specify.prompt-fragment.md", "---\ntitle: x\n---\n\nText.\n");
        write(dir, ENHANCEMENT_DIR + "blank.prompt-fragment.md", "\n");

        PackFileSet.listing(dir);

        assertThat(faultFiles(full(dir)))
                .containsExactlyInAnyOrder(
                        ENHANCEMENT_DIR + "spdd-specify.prompt-fragment.md",
                        ENHANCEMENT_DIR + "blank.prompt-fragment.md");
        // OD-10 row 4: validate mode answers prompt_fragment_invalid, at the fragment, for the frontmatter fragment and
        // for the blank one (an orphan: the staged load refuses it as well, StepEnhancementSurfacesTest).
        // A blank body breaks the structure schema three ways (no title, a preamble that is not text, no section); the
        // frontmatter fragment breaks it once. Nothing is reported twice.
        assertThat(promptFragmentFindings(dir))
                .extracting(reason -> reason.location().file())
                .containsExactly(
                        ENHANCEMENT_DIR + "blank.prompt-fragment.md",
                        ENHANCEMENT_DIR + "blank.prompt-fragment.md",
                        ENHANCEMENT_DIR + "blank.prompt-fragment.md",
                        ENHANCEMENT_DIR + "spdd-specify.prompt-fragment.md");
        assertThat(promptFragmentFindings(dir))
                .extracting(Reason::detail)
                .as("three different reasons for the blank fragment")
                .doesNotHaveDuplicates();
    }

    @Test
    void theTwinOfThePromptFragmentThatBreaksItsStructureIsReadableAndAnswersNoPromptFragmentInvalid(@TempDir Path dir)
            throws IOException {
        write(dir, ENHANCEMENT_GRAPH, SPDD_EDGE);
        write(dir, ENHANCEMENT_DIR + "spdd-specify.step-enhancement.yaml", SPDD_ENHANCEMENT);
        write(dir, ENHANCEMENT_DIR + "spdd-specify.prompt-fragment.md", "## REASONS guidance\n\nState the reasons.\n");

        assertThat(full(dir).readFaults()).isEmpty();
        assertThat(promptFragmentFindings(dir)).isEmpty();
    }

    private static final String PROCEDURE = """
            schema_version: '1.0'
            id: %s
            title: T
            description: D
            entry_condition: E
            exit_condition: X
            steps:
              - title: S
            """;

    @Test
    void aProcedureWhoseIdIsNotItsFileStemIsUnreadableAsTheLoadRefusesIt(@TempDir Path dir) throws IOException {
        write(dir, "procedures/named.procedure.yaml", PROCEDURE.formatted("named"));
        write(dir, "procedures/misnamed.procedure.yaml", PROCEDURE.formatted("other"));

        TreeRead contents = full(dir);

        assertThat(contents.readFaults()).singleElement().satisfies(fault -> {
            assertThat(fault.location().file()).isEqualTo("procedures/misnamed.procedure.yaml");
            assertThat(fault.location().pointer()).isEqualTo("/id");
            assertThat(fault.code()).isEqualTo(PackCodes.ARTIFACT_SCHEMA_ERROR);
        });
        assertThat(contents.contents().artifacts())
                .extracting(PackContents.ArtifactFacts::id)
                .containsExactly("named");
    }

    @Test
    void aFileThatIsReadCleanlyIsNotListedAsUnreadable(@TempDir Path dir) throws IOException {
        write(dir, "tactics/x.tactic.yaml", TACTIC.formatted("x"));

        assertThat(full(dir).readFaults()).isEmpty();
    }

    @Test
    void substepIdsAndArtifactIdsAreFactsOfTheFilesThatPassedTheirSchema(@TempDir Path dir) throws IOException {
        write(dir, "missions/rx/rx.mission-type.yaml", """
                schema_version: '1.0'
                id: rx
                title: Rx
                description: A mission type.
                steps:
                - id: a
                  title: A
                  substeps:
                  - id: s1
                    description: d
                  - id: s2
                    description: d
                  - id: s1
                    description: d
                """);
        write(dir, "missions/broken/broken.mission-type.yaml", "id: broken\n");
        write(dir, "tactics/x.tactic.yaml", TACTIC.formatted("x"));
        write(dir, "tactics/sub/y.tactic.yaml", TACTIC.formatted("y"));
        write(dir, "tactics/bad.tactic.yaml", "id: nope\n");
        write(dir, "directives/d.directive.yaml", """
                id: DIRECTIVE_001
                schema_version: '1.0'
                title: T
                intent: i
                enforcement: required
                """);
        write(dir, "agent-profiles/p.agent.yaml", """
                profile-id: p
                name: P
                role: implementer
                purpose: x
                specialization:
                  primary-focus: f
                """);

        TreeRead contents = full(dir);

        assertThat(contents.contents().missionTypes())
                .singleElement()
                .satisfies(type -> assertThat(type.substeps())
                        .extracting(MissionTypeFacts.SubstepFacts::id)
                        .containsExactly("s1", "s2", "s1"));
        assertThat(contents.contents().artifacts())
                .as("a file that failed its schema owns no id; an agent profile has no id, as in core")
                .containsExactly(
                        new PackContents.ArtifactFacts("directives/d.directive.yaml", "directive", "DIRECTIVE_001"),
                        new PackContents.ArtifactFacts("missions/rx/rx.mission-type.yaml", "mission_type", "rx"),
                        new PackContents.ArtifactFacts("tactics/sub/y.tactic.yaml", "tactic", "y"),
                        new PackContents.ArtifactFacts("tactics/x.tactic.yaml", "tactic", "x"));
    }

    @Test
    void anAssetIsAFactOfItsFirstOwnerOnlyAndOnlyWhenItPassedItsSchema(@TempDir Path dir) throws IOException {
        write(dir, "assets/a.asset.yaml", ASSET.formatted("logo", "image/png", "logo.png"));
        write(dir, "assets/b/z.asset.yaml", ASSET.formatted("logo", "image/jpeg", "../../escape.png"));
        write(dir, "assets/b-other/z.asset.yaml", ASSET.formatted("logo", "text/plain", "/etc/passwd"));
        write(dir, "assets/c.asset.yaml", "id: broken\n");
        write(dir, "assets/d.asset.yaml", ASSET.formatted("doc", "text/plain", "doc.txt"));

        assertThat(full(dir).contents().assets())
                .as("core checks the first manifest of a repeated id, in path order, and only schema-valid ones")
                .containsExactly(
                        new PackContents.AssetFacts("assets/a.asset.yaml", "logo", "logo.png", "image/png", true),
                        new PackContents.AssetFacts("assets/d.asset.yaml", "doc", "doc.txt", "text/plain", true));
        assertThat(full(dir).contents().artifacts())
                .extracting(PackContents.ArtifactFacts::file)
                .as("every owner and duplicate is still an artifact fact, for the duplicate id rule")
                .containsExactly(
                        "assets/a.asset.yaml",
                        "assets/b/z.asset.yaml",
                        "assets/b-other/z.asset.yaml",
                        "assets/d.asset.yaml");
    }

    @Test
    void anAssetPathAndMimeAreTheRawDeclaredStrings(@TempDir Path dir) throws IOException {
        write(dir, "assets/a.asset.yaml", ASSET.formatted("a", "'Image/PNG '", "'..\\x.png'"));

        assertThat(full(dir).contents().assets())
                .containsExactly(
                        new PackContents.AssetFacts("assets/a.asset.yaml", "a", "..\\x.png", "Image/PNG ", true));
    }

    /**
     * #251: every payload and template the store's listing holds is listed with its kind, whatever its extension, and
     * none is read.
     */
    @Test
    void aSchemaLessPayloadIsListedWithItsKindAndNotReadWhileItsSidecarsFactsStand(@TempDir Path dir)
            throws IOException {
        write(dir, "assets/x.md.asset.yaml", ASSET.formatted("x", "text/markdown", "x.md"));
        write(dir, "assets/x.md", "# a payload\n");
        // No payload is YAML a schema would pass: were one read and validated, it would be unreadable.
        write(dir, "assets/tools/x.config.yaml", "rules: [\n");
        write(dir, "templates/writing/t.md", "---\ntitle: [\n---\n");
        write(dir, "templates/writing/t.yaml", "a: [\n");
        Files.write(dir.resolve("assets/logo.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G'});

        TreeRead contents = full(dir);

        assertThat(contents.readFaults()).isEmpty();
        assertThat(contents.census().files())
                .containsExactly(
                        classified("assets/logo.png", "asset-payload"),
                        classified("assets/tools/x.config.yaml", "asset-payload"),
                        classified("assets/x.md", "asset-payload"),
                        classified("assets/x.md.asset.yaml", "asset"),
                        classified("templates/writing/t.md", "template"),
                        classified("templates/writing/t.yaml", "template"));
        assertThat(contents.contents().assets())
                .as("the sidecar is read, and its payload is held")
                .containsExactly(
                        new PackContents.AssetFacts("assets/x.md.asset.yaml", "x", "x.md", "text/markdown", true));
        assertThat(contents.contents().artifacts())
                .containsExactly(new PackContents.ArtifactFacts("assets/x.md.asset.yaml", "asset", "x"));
    }

    /**
     * #251: validate mode refuses the sidecars the store's load refuses, with the load's words: a payload the pack does
     * not hold (missing, or spelled in another case), a path that leaves {@code assets/} (#382: an escape of its own,
     * whether or not a file is at the target), and a payload an earlier sidecar named. The sidecar's facts stand, so the
     * asset rules still judge it.
     */
    @Test
    void aSidecarWhosePayloadThePackDoesNotHoldIsRefusedAsTheLoadRefusesIt(@TempDir Path host) throws IOException {
        Path dir = host.resolve("qa");
        write(
                dir,
                "pack.yaml",
                "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");
        write(dir, "assets/a.asset.yaml", ASSET.formatted("a", "text/markdown", "a.md"));
        write(dir, "assets/a.md", "# a\n");
        write(dir, "assets/b.asset.yaml", ASSET.formatted("b", "text/markdown", "b.md"));
        write(dir, "assets/c.asset.yaml", ASSET.formatted("c", "image/png", "Logo.png"));
        write(dir, "assets/logo.png", "png");
        write(dir, "assets/d.asset.yaml", ASSET.formatted("d", "text/markdown", "a.md"));
        write(dir, "assets/e.asset.yaml", ASSET.formatted("e", "image/png", "../../escape.png"));
        write(host, "escape.png", "png");
        write(dir, "assets/f.asset.yaml", ASSET.formatted("f", "text/markdown", "sub/../a.md"));
        write(dir, "assets/g.asset.yaml", ASSET.formatted("g", "text/markdown", "/etc/hosts"));
        List<Tuple> refused = List.of(
                tuple("assets/b.asset.yaml", "payload assets/b.md does not exist"),
                tuple("assets/c.asset.yaml", "payload assets/Logo.png does not exist"),
                tuple("assets/d.asset.yaml", "payload assets/a.md is already named by asset a"),
                tuple("assets/e.asset.yaml", "asset path '../../escape.png' escapes the pack's assets/ root"),
                tuple("assets/f.asset.yaml", "asset path 'sub/../a.md' escapes the pack's assets/ root"),
                tuple("assets/g.asset.yaml", "asset path '/etc/hosts' escapes the pack's assets/ root"));

        TreeRead contents = full(dir);
        FilesystemPackStore.PackRead load =
                new FilesystemPackStore(host, new YamlSerializationStrategy()).read(new PackDirectory("qa"));

        assertThat(contents.readFaults())
                .filteredOn(fault -> !fault.code().equals(StorageCodes.ASSET_MIME_INVALID))
                .extracting(fault -> fault.location().file(), fault -> fault.detail())
                .containsExactlyElementsOf(refused.stream()
                        .map(row -> tuple(
                                row.toList().get(0),
                                row.toList().get(0) + ": " + row.toList().get(1)))
                        .toList());
        assertThat(load.violations())
                .as("the load refuses the same sidecars, in the same words")
                .extracting(PackElementViolation::file, PackElementViolation::reason)
                .containsExactlyElementsOf(refused);
        assertThat(contents.contents().assets())
                .extracting(PackContents.AssetFacts::file)
                .containsExactly(
                        "assets/a.asset.yaml",
                        "assets/b.asset.yaml",
                        "assets/c.asset.yaml",
                        "assets/d.asset.yaml",
                        "assets/e.asset.yaml",
                        "assets/f.asset.yaml",
                        "assets/g.asset.yaml");
    }

    /**
     * #254: a fragment node whose body_path leaves the pack root refuses the whole fragment, in validate mode as on the
     * load and in the same words, so neither judges its edges (here one to a node the fragment declares). Both report
     * the first escaping node only, as the fragment is refused whole (a second node escapes too, and is not listed).
     */
    @Test
    void aFragmentWithABodyPathOutsideThePackIsRefusedWholeInValidateModeAsOnTheLoad(@TempDir Path host)
            throws IOException {
        Path dir = host.resolve("qa");
        write(dir, "pack.yaml", """
                pack_id: 01ARWG13C00000000000001ABC
                pack_version: 1.0.0
                name: qa
                pack_level: org
                pack_drg: drg/fragment.yaml
                """);
        write(dir, "drg/fragment.yaml", """
                nodes:
                - id: D1
                  kind: directives
                  body_path: ../outside.directive.yaml
                - id: D2
                  kind: directives
                  body_path: /etc/second.directive.yaml
                edges:
                - {source: 'directive:D1', target: 'directive:D2', relation: suggests}
                """);
        String reason = "node D1 body_path '../outside.directive.yaml' escapes the pack root";

        List<Reason> found = verdict(dir);
        FilesystemPackStore.GraphRead load =
                new FilesystemPackStore(host, new YamlSerializationStrategy()).readGraphs(new PackDirectory("qa"));

        assertThat(found)
                .extracting(Reason::code, finding -> finding.location().file(), Reason::detail)
                .containsExactly(
                        tuple(PackCodes.DRG_SCHEMA_ERROR, "drg/fragment.yaml", "drg/fragment.yaml: " + reason));
        assertThat(load.documents())
                .as("the load reads no part of the fragment")
                .isEmpty();
        assertThat(load.violations())
                .extracting(PackElementViolation::file, PackElementViolation::reason)
                .containsExactly(tuple("drg/fragment.yaml", reason));
    }

    /**
     * #251: an asset file the store's read does not hold (here, a link that leaves the pack, which neither read reads
     * through, D6) is not a payload, and a sidecar naming it names no payload, in validate mode as in the load. Its path
     * stays inside {@code assets/} as written, so it is a missing payload, not an escape (#382 judges the path as
     * written). The link itself is the storage rules' {@code pack_entry_linked}.
     */
    @Test
    void anAssetFileTheLoadDoesNotHoldIsRefusedAndNamesNoPayload(@TempDir Path host) throws IOException {
        SymbolicLinks.requireSupport(host);
        Path dir = host.resolve("qa");
        write(
                dir,
                "pack.yaml",
                "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");
        write(dir, "assets/f.asset.yaml", ASSET.formatted("f", "image/png", "out.png"));
        write(host, "outside.png", "png");
        SymbolicLinks.create(dir.resolve("assets/out.png"), host.resolve("outside.png"));
        List<Tuple> refused = List.of(tuple("assets/f.asset.yaml", "payload assets/out.png does not exist"));

        TreeRead contents = full(dir);
        FilesystemPackStore.PackRead load =
                new FilesystemPackStore(host, new YamlSerializationStrategy()).read(new PackDirectory("qa"));

        assertThat(contents.readFaults())
                .filteredOn(fault -> !fault.code().equals(StorageCodes.ASSET_MIME_INVALID))
                .extracting(fault -> fault.location().file(), fault -> fault.detail())
                .containsExactlyElementsOf(refused.stream()
                        .map(row -> tuple(
                                row.toList().get(0),
                                row.toList().get(0) + ": " + row.toList().get(1)))
                        .toList());
        assertThat(load.violations())
                .extracting(PackElementViolation::file, PackElementViolation::reason)
                .containsExactlyElementsOf(refused);
        assertThat(StorageRulesets.verdict(contents).details())
                .filteredOn(reason -> StorageCodes.PACK_ENTRY_LINKED.equals(reason.code()))
                .extracting(reason -> reason.location().file())
                .containsExactly("assets/out.png");
    }

    /**
     * Acceptance check 12: validate mode over the built-in example pack (ADR-0070) reports no finding and no exception
     * for its sidecar-less asset payload {@code assets/docs_structural_lint.config.yaml}, which it lists as an asset
     * payload. The listing is held to the listing rule restated from the file layout.
     * The control after it shows the same read reports a finding once a file the pack names is deleted.
     */
    @Test
    void validateModeOverTheBuiltInExampleFindsNothingAndListsTheConfigPayload() throws IOException {
        Path builtIn = ExamplePacks.pack(ExamplePacks.BUILT_IN);
        PackRoot root = new PackRoot("built-in", Optional.of(PackLevel.BUILT_IN), builtIn.toString());

        PackReadout verdict = INSPECTOR.read(root);

        // The five software-dev step prompts each declare an empty spdd:reasons-block slot (its text lives in the
        // org-base example's prompt fragments), which raises no slot finding. The one finding is the file_not_held
        // warning of the templates' orientation README, which the pack does not hold (#554, open case 6).
        assertThat(verdict.rationale().failures())
                .extracting(Reason::code, reason -> reason.location().file())
                .containsExactly(tuple(StorageCodes.FILE_NOT_HELD, "missions/software-dev/templates/README.md"));
        for (String step : List.of("implement", "plan", "review", "specify", "tasks")) {
            assertThat(builtIn.resolve("missions/software-dev/steps/" + step + ".prompt.md"))
                    .content(StandardCharsets.UTF_8)
                    .contains("<!-- spdd:reasons-block:start -->\n<!-- spdd:reasons-block:end -->");
        }

        // The listing rule (FileSystemPackInspector, FULL scope), restated here from the file layout alone, without
        // PackFileKind: every *.yaml file the pack holds (a file the table excludes for a reviewed reason is listed
        // with its exclusion class), and every payload or template file whatever its extension (#251): anything under
        // assets/, templates/ or missions/<type>/templates/. Hidden files are excluded there (the table's exclusion for
        // dot-files). An orientation README below the pack root is Markdown the table excludes (#451): it is listed
        // with its exclusion class, never read. The rule is stated narrowly on purpose: the built-in pack holds no
        // excluded Markdown but READMEs (any other unmodelled .md is the fallback's, pinned by the dedicated tests
        // above and in ExcludedFilesAgreementTest). The classifier is used below only to check each
        // listed file's kind, so a pattern regression cannot drop a file from both sides.
        List<String> listedByTheRule;
        try (Stream<Path> walk = Files.walk(builtIn)) {
            listedByTheRule = walk.filter(Files::isRegularFile)
                    .map(file -> builtIn.relativize(file).toString().replace('\\', '/'))
                    .filter(file -> file.endsWith(".yaml") || isPayloadOrTemplate(file) || file.endsWith("/README.md"))
                    .toList();
        }
        assertThat(listedByTheRule)
                .as("files the listing rule admits in the built-in pack")
                .isNotEmpty();
        assertThat(verdict.files()).extracting(f -> f.file()).containsExactlyInAnyOrderElementsOf(listedByTheRule);
        assertThat(verdict.files())
                .as("every listed file carries a kind or an exclusion class, and payloads and templates their kind")
                .allSatisfy(f -> {
                    assertThat(f.kind().isPresent() || f.exclusionClass().isPresent())
                            .as(f.file())
                            .isTrue();
                    if (f.file().matches("assets/.*\\.asset\\.yaml")) {
                        assertThat(f.kind()).as(f.file()).contains("asset");
                    } else if (isPayloadOrTemplate(f.file())) {
                        assertThat(f.kind())
                                .as(f.file())
                                .contains(f.file().startsWith("assets/") ? "asset-payload" : "template");
                    }
                });
        assertThat(verdict.files())
                .filteredOn(f -> f.file().equals("assets/docs_structural_lint.config.yaml"))
                .containsExactly(classified("assets/docs_structural_lint.config.yaml", "asset-payload"));
        assertThat(verdict.files())
                .as("no file is excluded by the retired rule")
                .noneMatch(f ->
                        f.exclusionClass().filter("**/*.config.yaml"::equals).isPresent());
    }

    /**
     * Whether a pack-relative path is a payload or template file the validate-mode listing holds whatever its
     * extension: under {@code assets/}, {@code templates/} or {@code missions/<type>/templates/}, and neither an
     * orientation {@code README.md} nor a hidden file.
     */
    private static boolean isPayloadOrTemplate(String file) {
        Path path = Path.of(file);
        String name = path.getFileName().toString();
        boolean underPayloadRoot = file.startsWith("assets/")
                || file.startsWith("templates/")
                || (path.getNameCount() > 3
                        && path.getName(0).toString().equals("missions")
                        && path.getName(2).toString().equals("templates"));
        return underPayloadRoot && !name.equals("README.md") && !name.startsWith(".");
    }

    /** Failing control for the check above: the built-in example with one asset payload deleted is not clean. */
    @Test
    void validateModeOverTheBuiltInExampleWithADeletedPayloadFindsIt(@TempDir Path scratch) throws IOException {
        Path copy = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, scratch);
        Files.delete(copy.resolve("assets/carrier_lint.py"));
        PackRoot root = new PackRoot("built-in", Optional.of(PackLevel.BUILT_IN), copy.toString());

        PackReadout verdict = INSPECTOR.read(root);

        assertThat(verdict.rationale().failures()).isNotEmpty();
    }

    // ---- asset containment ---------------------------------------------------------------------------------------

    @Test
    void aPathInsideTheAssetsRootIsContainedWhetherOrNotItExists(@TempDir Path dir) throws IOException {
        Path assets = Files.createDirectories(dir.resolve("assets"));
        Files.writeString(assets.resolve("there.png"), "x");

        assertThat(FileSystemPackInspector.containedInAssetsRoot(assets, "there.png"))
                .isTrue();
        assertThat(FileSystemPackInspector.containedInAssetsRoot(assets, "not/there/yet.png"))
                .isTrue();
        assertThat(FileSystemPackInspector.containedInAssetsRoot(dir.resolve("no-assets-dir"), "x.png"))
                .isTrue();
    }

    @Test
    void aSymlinkInsideTheAssetsRootThatLeavesItIsAnEscape(@TempDir Path dir) throws IOException {
        Path assets = Files.createDirectories(dir.resolve("assets"));
        Path outside = Files.createDirectories(dir.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "x");
        Files.createSymbolicLink(assets.resolve("out"), outside);
        Files.createSymbolicLink(assets.resolve("file-out"), outside.resolve("secret.txt"));
        Files.createDirectories(assets.resolve("real"));
        Files.createSymbolicLink(assets.resolve("in"), assets.resolve("real"));
        Files.createSymbolicLink(assets.resolve("dangling-out"), dir.resolve("no/such/place"));

        assertThat(FileSystemPackInspector.containedInAssetsRoot(assets, "out/secret.txt"))
                .isFalse();
        assertThat(FileSystemPackInspector.containedInAssetsRoot(assets, "out/new/file.txt"))
                .isFalse();
        assertThat(FileSystemPackInspector.containedInAssetsRoot(assets, "file-out"))
                .isFalse();
        assertThat(FileSystemPackInspector.containedInAssetsRoot(assets, "dangling-out"))
                .as("a dangling link is followed to where it points, as pathlib's resolve does")
                .isFalse();
        assertThat(FileSystemPackInspector.containedInAssetsRoot(assets, "in/file.png"))
                .isTrue();
    }

    @Test
    void anAssetsRootThatIsItselfASymlinkIsJudgedByWhereItPoints(@TempDir Path dir) throws IOException {
        Path elsewhere = Files.createDirectories(dir.resolve("elsewhere/assets-store"));
        Path assets = Files.createSymbolicLink(dir.resolve("assets"), elsewhere);
        Files.createSymbolicLink(elsewhere.resolve("sibling"), dir.resolve("elsewhere"));

        assertThat(FileSystemPackInspector.containedInAssetsRoot(assets, "a/b.png"))
                .isTrue();
        assertThat(FileSystemPackInspector.containedInAssetsRoot(assets, "sibling/x.png"))
                .isFalse();
    }

    @Test
    void aSymlinkLoopAndAPathThatCannotBeResolvedAreAnEscapeNotAnException(@TempDir Path dir) throws IOException {
        Path assets = Files.createDirectories(dir.resolve("assets"));
        Files.createSymbolicLink(assets.resolve("a"), Path.of("b"));
        Files.createSymbolicLink(assets.resolve("b"), Path.of("a"));
        Files.createSymbolicLink(assets.resolve("self"), Path.of("self"));

        assertThat(FileSystemPackInspector.containedInAssetsRoot(assets, "a")).isFalse();
        assertThat(FileSystemPackInspector.containedInAssetsRoot(assets, "a/x.png"))
                .isFalse();
        assertThat(FileSystemPackInspector.containedInAssetsRoot(assets, "self"))
                .isFalse();
        assertThat(FileSystemPackInspector.containedInAssetsRoot(assets, "ok\0nul.png"))
                .as("core raises ValueError for a NUL; here it is refused as not contained, never thrown")
                .isFalse();
    }

    @Test
    void aSymlinkThatClimbsOutThroughDotDotInItsTargetIsAnEscape(@TempDir Path dir) throws IOException {
        Path assets = Files.createDirectories(dir.resolve("assets"));
        Files.createSymbolicLink(assets.resolve("up"), Path.of("../.."));
        Files.createSymbolicLink(assets.resolve("stay"), Path.of("sub/.."));

        assertThat(FileSystemPackInspector.containedInAssetsRoot(assets, "up/x.png"))
                .isFalse();
        assertThat(FileSystemPackInspector.containedInAssetsRoot(assets, "stay/x.png"))
                .isTrue();
    }

    @Test
    void aSymlinkedAssetSidecarIsNeverReadAndIsRefusedAsALink(@TempDir Path dir) throws IOException {
        // D6 (#597): a link is never read through, wherever it leads; the storage rules refuse it.
        Path outside = Files.createDirectories(dir.resolve("outside"));
        Files.writeString(outside.resolve("m.asset.yaml"), ASSET.formatted("m", "image/png", "m.png"));
        Path pack = Files.createDirectories(dir.resolve("pack/assets"));
        Files.createSymbolicLink(pack.resolve("m.asset.yaml"), outside.resolve("m.asset.yaml"));
        TreeRead read = full(dir.resolve("pack"));

        assertThat(read.contents().assets()).isEmpty();
        assertThat(StorageRulesets.verdict(read).details())
                .filteredOn(reason -> StorageCodes.PACK_ENTRY_LINKED.equals(reason.code()))
                .extracting(reason -> reason.location().file())
                .containsExactly("assets/m.asset.yaml");
    }

    // ---- the listing of shards ---------------------------------------------------------------------

    @Test
    void theGraphShardsOfAListingAreItsGraphYamlFilesSortedByName() {
        assertThat(PackFileSet.graphShards(
                        Stream.of(Path.of("qa/b.graph.yaml"), Path.of("qa/pack.yaml"), Path.of("qa/a.graph.yaml")),
                        "qa",
                        p -> true))
                .containsExactly(Path.of("qa/a.graph.yaml"), Path.of("qa/b.graph.yaml"));
    }

    @Test
    void theGraphShardsAreSortedByTheFileNameAsAStringNotByPathCaseRules() {
        assertThat(PackFileSet.graphShards(
                        Stream.of(Path.of("qa/a.graph.yaml"), Path.of("qa/B.graph.yaml")), "qa", p -> true))
                .containsExactly(Path.of("qa/B.graph.yaml"), Path.of("qa/a.graph.yaml"));
    }

    @Test
    void aListingThatFailsWhileItIsReadIsRefusedAsAnUnreadablePack() {
        IOException cause = new IOException("gone");
        Stream<Path> failing = Stream.of(Path.of("qa/a.graph.yaml")).peek(p -> {
            throw new UncheckedIOException(cause);
        });

        assertThatThrownBy(() -> PackFileSet.graphShards(failing, "qa", p -> true))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessage("pack qa cannot be listed")
                .hasCause(cause);
    }

    @Test
    void aDirectoryThatEndsInGraphYamlIsNotAShard(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("odd.graph.yaml"));
        write(dir, "real.graph.yaml", "nodes: []\n");

        assertThat(full(dir).contents().shards()).extracting(FileRead::file).containsExactly("real.graph.yaml");
    }

    @Test
    void anInspectedRootThatCannotBeListedIsRefused(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("a-file");
        Files.writeString(file, "x");

        assertThat(full(file).readFaults()).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(StorageCodes.PACK_UNREADABLE);
            assertThat(fault.location().file()).isEqualTo(FileSystemPackInspector.ROOT);
            assertThat(fault.detail()).isEqualTo("pack qa is not a directory");
        });
    }

    /**
     * #195 (ADR-0042): a preset in the sanitized shape whose id is not its file stem is refused in validate mode, as
     * {@code artifact_schema_error} on its file, and by the load, on the same fixture; its sibling, whose id is its
     * stem, passes both.
     */
    @Test
    void aPresetWhoseIdIsNotItsStemIsRefusedInValidateModeAndByTheLoad(@TempDir Path dir) throws IOException {
        write(
                dir,
                "pack.yaml",
                "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");
        String preset = "schema_version: '1.0'\nid: minimal\ndescription: d\n";
        write(dir, "presets/minimal.yaml", preset);
        write(dir, "presets/other.yaml", preset);

        List<Reason> findings = verdict(dir);
        WholePackStore.LoadedPack loaded = new WholePackStore(new YamlSerializationStrategy()).load(dir);

        // The org-level root also finds its missing fragment; only the preset findings are this test's.
        assertThat(findings)
                .filteredOn(finding -> finding.location().file().startsWith(PresetFiles.PRESETS))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(PackCodes.ARTIFACT_SCHEMA_ERROR);
                    assertThat(finding.location().file()).isEqualTo("presets/other.yaml");
                    assertThat(finding.detail())
                            .endsWith("presets/other.yaml: preset id 'minimal' is not its file stem 'other'");
                });
        assertThat(loaded.pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo("presets/other.yaml");
            assertThat(violation.reason()).isEqualTo("preset id 'minimal' is not its file stem 'other'");
        });
        assertThat(loaded.pack().presets()).extracting(Preset::id).containsExactly("minimal");
    }

    // ---- step enhancements and prompt fragments in validate mode (FR-004 to FR-006) ------------------------------

    private static List<Reason> promptFragmentFindings(Path dir) {
        return verdict(dir).stream()
                .filter(finding -> PackCodes.PROMPT_FRAGMENT_INVALID.equals(finding.code()))
                .toList();
    }

    private static String yaml(String id) {
        return ENHANCEMENT_DIR + id + ".step-enhancement.yaml";
    }

    private static String fragment(String name) {
        return ENHANCEMENT_DIR + name + ".prompt-fragment.md";
    }

    /** A valid pair beside its single qualified enhances edge: the fixture each case below breaks in one line. */
    private static void validPair(Path dir) throws IOException {
        write(dir, ENHANCEMENT_GRAPH, SPDD_EDGE);
        write(dir, yaml("spdd-specify"), SPDD_ENHANCEMENT);
        write(dir, fragment("spdd-specify"), "## REASONS guidance\n\nState the reasons.\n");
    }

    @Test
    void aValidPairWithItsEdgeAnswersNoPromptFragmentInvalid(@TempDir Path dir) throws IOException {
        validPair(dir);

        assertThat(promptFragmentFindings(dir)).isEmpty();
    }

    @Test
    void aSlotMarkerInAFragmentIsPromptFragmentInvalidAtTheFragment(@TempDir Path dir) throws IOException {
        validPair(dir);
        write(dir, fragment("spdd-specify"), "## Guidance\n\n<!-- other:slot:start -->\nText.\n");

        assertThat(promptFragmentFindings(dir))
                .singleElement()
                .satisfies(finding -> assertThat(finding.location().file()).isEqualTo(fragment("spdd-specify")));
    }

    @Test
    void aBlankCompanionThatAnEntryNamesIsPromptFragmentInvalidAtTheFragmentAndItsNonBlankTwinIsNot(@TempDir Path dir)
            throws IOException {
        write(dir, ENHANCEMENT_GRAPH, SPDD_EDGE);
        write(dir, yaml("spdd-specify"), SPDD_ENHANCEMENT);
        write(dir, fragment("spdd-specify"), "\n");

        // A blank body breaks the structure schema three ways (no title, a preamble that is not text, no section):
        // every finding is at the fragment, none at the enhancement or as an orphan.
        assertThat(promptFragmentFindings(dir))
                .isNotEmpty()
                .allSatisfy(finding -> assertThat(finding.location().file()).isEqualTo(fragment("spdd-specify")));

        write(dir, fragment("spdd-specify"), "## REASONS guidance\n\nState the reasons.\n");

        assertThat(promptFragmentFindings(dir)).isEmpty();
    }

    @Test
    void aFragmentOfOnlySpacesAndTabsIsBlankAndPromptFragmentInvalidAtTheFragment(@TempDir Path dir)
            throws IOException {
        write(dir, ENHANCEMENT_GRAPH, SPDD_EDGE);
        write(dir, yaml("spdd-specify"), SPDD_ENHANCEMENT);
        write(dir, fragment("spdd-specify"), "   \t \n\t\t\n  \n");

        assertThat(promptFragmentFindings(dir))
                .isNotEmpty()
                .allSatisfy(finding -> assertThat(finding.location().file()).isEqualTo(fragment("spdd-specify")));
    }

    @Test
    void aNamedCompanionThatIsMissingIsPromptFragmentInvalidAtTheYaml(@TempDir Path dir) throws IOException {
        write(dir, ENHANCEMENT_GRAPH, SPDD_EDGE);
        write(dir, yaml("spdd-specify"), SPDD_ENHANCEMENT);

        assertThat(promptFragmentFindings(dir))
                .singleElement()
                .satisfies(finding -> assertThat(finding.location().file()).isEqualTo(yaml("spdd-specify")));
    }

    @Test
    void anOrphanFragmentBesideAValidPairIsPromptFragmentInvalidAtTheOrphan(@TempDir Path dir) throws IOException {
        validPair(dir);
        write(dir, fragment("stray"), "## Stray\n\nNobody names it.\n");

        assertThat(promptFragmentFindings(dir))
                .singleElement()
                .satisfies(finding -> assertThat(finding.location().file()).isEqualTo(fragment("stray")));
    }

    @Test
    void anOrphanBesideAnEnhancementWithARuleFaultIsJudgedBesideTheFault(@TempDir Path dir) throws IOException {
        // The enhancement is held, with a rule fault (no enhances edge), so it excuses no directory (fifth analysis
        // A5).
        write(dir, yaml("spdd-specify"), SPDD_ENHANCEMENT);
        write(dir, fragment("spdd-specify"), "## REASONS guidance\n\nState the reasons.\n");
        write(dir, fragment("stray"), "## Stray\n\nNobody names it.\n");

        assertThat(promptFragmentFindings(dir))
                .extracting(reason -> reason.location().file())
                .containsExactlyInAnyOrder(yaml("spdd-specify"), fragment("stray"));
    }

    @Test
    void aFragmentBesideAnEnhancementTheStoreWouldRefuseIsNoOrphan(@TempDir Path dir) throws IOException {
        // The named companion is missing, so the store refuses the enhancement and leaves every file beside it
        // unmodelled.
        write(dir, ENHANCEMENT_GRAPH, SPDD_EDGE);
        write(dir, yaml("spdd-specify"), SPDD_ENHANCEMENT);
        write(dir, fragment("stray"), "## Stray\n\nMaybe it was meant.\n");

        assertThat(promptFragmentFindings(dir))
                .singleElement()
                .satisfies(finding -> assertThat(finding.location().file()).isEqualTo(yaml("spdd-specify")));
    }

    @Test
    void aFragmentBesideAnEnhancementThatBreaksItsSchemaIsNoOrphan(@TempDir Path dir) throws IOException {
        write(dir, yaml("spdd-specify"), "schema_version: '1.0'\nid: spdd-specify\nprompt_fragments: []\n");
        write(dir, fragment("stray"), "## Stray\n\nMaybe it was meant.\n");

        assertThat(promptFragmentFindings(dir)).isEmpty();
        assertThat(verdict(dir)).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo(PackCodes.ARTIFACT_SCHEMA_ERROR);
            assertThat(finding.location().file()).isEqualTo(yaml("spdd-specify"));
        });
    }

    @Test
    void anEnhancementThatOverridesIsPromptFragmentInvalidAndItsTwinWithOnlyTheEnhancesEdgeIsNot(@TempDir Path dir)
            throws IOException {
        validPair(dir);
        assertThat(promptFragmentFindings(dir)).isEmpty();

        write(
                dir,
                ENHANCEMENT_GRAPH,
                SPDD_EDGE + "- source: step_enhancement:spdd-specify\n  target: mission_type:software-dev\n"
                        + "  relation: overrides\n");

        assertThat(promptFragmentFindings(dir))
                .singleElement()
                .satisfies(finding -> assertThat(finding.location().file()).isEqualTo(yaml("spdd-specify")));

        // the same overrides edge, its source qualified with the pack's own name: the same enhancement (#460 R-OVR)
        write(
                dir,
                ENHANCEMENT_GRAPH,
                SPDD_EDGE + "- source: qa::step_enhancement:spdd-specify\n  target: mission_type:software-dev\n"
                        + "  relation: overrides\n");

        assertThat(promptFragmentFindings(dir))
                .singleElement()
                .satisfies(finding -> assertThat(finding.location().file()).isEqualTo(yaml("spdd-specify")));

        // an overrides edge from another pack's enhancement is that pack's edge, not this pack's
        write(
                dir,
                ENHANCEMENT_GRAPH,
                SPDD_EDGE + "- source: other::step_enhancement:spdd-specify\n  target: mission_type:software-dev\n"
                        + "  relation: overrides\n");

        assertThat(promptFragmentFindings(dir)).isEmpty();
    }

    /**
     * An enhances edge whose target qualifier is no pack id (#582): the schema accepts a 64-character qualifier, the
     * binding refuses it, and that is the one fault. The step-enhancement edge rules do not judge it a second time; with
     * a qualifier of 63 the pack holds no fault.
     */
    @Test
    void anEnhancesTargetWhoseQualifierIsNoPackIdIsOneFaultNamingThePackIdGrammar(@TempDir Path dir)
            throws IOException {
        validPair(dir);
        String edge = SPDD_EDGE.replace("base::action:software-dev/specify", "%s");

        write(
                dir,
                ENHANCEMENT_GRAPH,
                edge.formatted("p".repeat(PackIdentifier.MAX_LENGTH + 1) + "::action:software-dev/specify"));
        // The fixture holds no pack.yaml, a fault of its own at pack.yaml in both cases; the graph's are the rest.
        assertThat(faultsBesidesTheDescriptor(dir)).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackCodes.DRG_SCHEMA_ERROR);
            assertThat(finding.detail()).contains("is not a reference").contains(PackIdentifier.GRAMMAR_DETAIL);
        });

        write(
                dir,
                ENHANCEMENT_GRAPH,
                edge.formatted("p".repeat(PackIdentifier.MAX_LENGTH) + "::action:software-dev/specify"));
        assertThat(promptFragmentFindings(dir)).isEmpty();
        assertThat(faultsBesidesTheDescriptor(dir)).isEmpty();
    }

    /**
     * The malformed qualifier leaves only its own enhancement unjudged (#582): a genuine fault of another enhancement in
     * the same graph document still stands. spdd-review has no enhances edge, so it is "0 enhances edges".
     */
    @Test
    void aMalformedQualifierLeavesAnotherEnhancementsFaultInTheSameDocumentStanding(@TempDir Path dir)
            throws IOException {
        validPair(dir);
        reviewEnhancement(dir);
        String overCap = "p".repeat(PackIdentifier.MAX_LENGTH + 1);
        write(dir, ENHANCEMENT_GRAPH, SPDD_EDGE.replace("base::", overCap + "::"));

        assertThat(faultsBesidesTheDescriptor(dir))
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file())
                .containsExactlyInAnyOrder(
                        tuple(PackCodes.DRG_SCHEMA_ERROR.value(), ENHANCEMENT_GRAPH),
                        tuple(PackCodes.PROMPT_FRAGMENT_INVALID.value(), yaml("spdd-review")));
        assertThat(promptFragmentFindings(dir))
                .singleElement()
                .satisfies(finding -> assertThat(finding.detail()).contains("0 enhances edges"));
    }

    /**
     * The same when the genuine fault comes from another graph document: spdd-review's enhances edge, in its own shard,
     * targets a tactic, while spdd-specify's malformed edge sits in the enhancements shard.
     */
    @Test
    void aMalformedQualifierLeavesAnotherEnhancementsFaultInAnotherDocumentStanding(@TempDir Path dir)
            throws IOException {
        validPair(dir);
        reviewEnhancement(dir);
        String overCap = "p".repeat(PackIdentifier.MAX_LENGTH + 1);
        write(dir, ENHANCEMENT_GRAPH, SPDD_EDGE.replace("base::", overCap + "::"));
        write(
                dir,
                "review.graph.yaml",
                SPDD_EDGE
                        .replace("spdd-specify", "spdd-review")
                        .replace("base::action:software-dev/specify", "base::tactic:x"));

        assertThat(faultsBesidesTheDescriptor(dir))
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file())
                .containsExactlyInAnyOrder(
                        tuple(PackCodes.DRG_SCHEMA_ERROR.value(), ENHANCEMENT_GRAPH),
                        tuple(PackCodes.PROMPT_FRAGMENT_INVALID.value(), yaml("spdd-review")));
        assertThat(promptFragmentFindings(dir))
                .singleElement()
                .satisfies(finding -> assertThat(finding.detail()).contains("not an action:<type>/<step>"));
    }

    /**
     * Only an enhances or overrides edge the step-enhancement rules read leaves its enhancement unjudged: a malformed
     * suggests edge from spdd-specify does not, so spdd-specify's genuine "0 enhances edges" still stands.
     */
    @Test
    void aMalformedSuggestsEdgeLeavesItsEnhancementJudged(@TempDir Path dir) throws IOException {
        validPair(dir);
        String overCap = "p".repeat(PackIdentifier.MAX_LENGTH + 1);
        write(
                dir,
                ENHANCEMENT_GRAPH,
                SPDD_EDGE
                        .replace("base::action:software-dev/specify", overCap + "::directive:D1")
                        .replace("relation: enhances", "relation: suggests"));

        assertThat(faultsBesidesTheDescriptor(dir))
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file())
                .containsExactlyInAnyOrder(
                        tuple(PackCodes.DRG_SCHEMA_ERROR.value(), ENHANCEMENT_GRAPH),
                        tuple(PackCodes.PROMPT_FRAGMENT_INVALID.value(), yaml("spdd-specify")));
        assertThat(promptFragmentFindings(dir))
                .singleElement()
                .satisfies(finding -> assertThat(finding.detail()).contains("0 enhances edges"));
    }

    /**
     * A malformed edge from another pack's enhancement of the same id leaves this pack's spdd-specify judged: its
     * genuine "0 enhances edges" still stands.
     */
    @Test
    void aMalformedEdgeFromAnotherPacksEnhancementLeavesTheLocalOneJudged(@TempDir Path dir) throws IOException {
        validPair(dir);
        String overCap = "p".repeat(PackIdentifier.MAX_LENGTH + 1);
        write(
                dir,
                ENHANCEMENT_GRAPH,
                SPDD_EDGE
                        .replace("source: step_enhancement:", "source: other::step_enhancement:")
                        .replace("base::", overCap + "::"));

        assertThat(faultsBesidesTheDescriptor(dir))
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file())
                .containsExactlyInAnyOrder(
                        tuple(PackCodes.DRG_SCHEMA_ERROR.value(), ENHANCEMENT_GRAPH),
                        tuple(PackCodes.PROMPT_FRAGMENT_INVALID.value(), yaml("spdd-specify")));
    }

    private static void reviewEnhancement(Path dir) throws IOException {
        write(dir, yaml("spdd-review"), SPDD_ENHANCEMENT.replace("spdd-specify", "spdd-review"));
        write(dir, fragment("spdd-review"), "## Review guidance\n\nCheck the reasons.\n");
    }

    private static List<Reason> faultsBesidesTheDescriptor(Path dir) {
        return verdict(dir).stream()
                .filter(finding ->
                        !PackFileSet.PACK_YAML.equals(finding.location().file()))
                .toList();
    }

    @Test
    void anEnhancesEdgeToANonStepAndAnAbsentEdgeAndAMismatchedDirectoryAreEachPromptFragmentInvalidAtTheYaml(
            @TempDir Path dir) throws IOException {
        validPair(dir);
        String edge = SPDD_EDGE.replace("base::action:software-dev/specify", "%s");

        write(dir, ENHANCEMENT_GRAPH, edge.formatted("base::tactic:x"));
        assertThat(promptFragmentFindings(dir)).singleElement().satisfies(finding -> {
            assertThat(finding.location().file()).isEqualTo(yaml("spdd-specify"));
            assertThat(finding.detail()).contains("not an action:<type>/<step>");
        });

        write(dir, ENHANCEMENT_GRAPH, edge.formatted("base::action:research/specify"));
        assertThat(promptFragmentFindings(dir)).singleElement().satisfies(finding -> {
            assertThat(finding.location().file()).isEqualTo(yaml("spdd-specify"));
            assertThat(finding.detail()).contains("research");
        });

        write(dir, ENHANCEMENT_GRAPH, "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges: []\n");
        assertThat(promptFragmentFindings(dir)).singleElement().satisfies(finding -> {
            assertThat(finding.location().file()).isEqualTo(yaml("spdd-specify"));
            assertThat(finding.detail()).contains("0 enhances edges");
        });

        // a second enhances edge from the pack-qualified source counts like a bare one
        write(
                dir,
                ENHANCEMENT_GRAPH,
                SPDD_EDGE + "- source: qa::step_enhancement:spdd-specify\n"
                        + "  target: base::action:software-dev/plan\n  relation: enhances\n");
        assertThat(promptFragmentFindings(dir)).singleElement().satisfies(finding -> {
            assertThat(finding.location().file()).isEqualTo(yaml("spdd-specify"));
            assertThat(finding.detail()).contains("2 enhances edges");
        });

        // a sole enhances edge from the pack-qualified source is the enhancement's one edge
        write(dir, ENHANCEMENT_GRAPH, SPDD_EDGE.replace("source: step_enhancement:", "source: qa::step_enhancement:"));
        assertThat(promptFragmentFindings(dir)).isEmpty();
    }

    @Test
    void aGraphDocumentThatCannotBeReadIsRefusedOnItsOwnAndJudgesNoEdgeRule(@TempDir Path dir) throws IOException {
        write(dir, yaml("spdd-specify"), SPDD_ENHANCEMENT);
        write(dir, fragment("spdd-specify"), "## REASONS guidance\n\nState the reasons.\n");
        write(dir, ENHANCEMENT_GRAPH, "nodes: [\n");

        assertThat(promptFragmentFindings(dir)).isEmpty();
        assertThat(verdict(dir))
                .anySatisfy(finding -> assertThat(finding.code()).isEqualTo(PackCodes.DRG_SCHEMA_ERROR));
    }

    @Test
    void aRepeatedEnhancementIdAnswersOneFindingAtTheSecondFileAndNoDuplicateArtifactId(@TempDir Path dir)
            throws IOException {
        String research = "missions/research/enhancements/spdd-plan";
        String software = "missions/software-dev/enhancements/spdd-plan";
        String enhancement = """
                schema_version: '1.0'
                id: spdd-plan
                prompt_fragments:
                  - slot: spdd:reasons-block
                    path: spdd-plan.prompt-fragment.md
                """;
        write(dir, research + ".step-enhancement.yaml", enhancement);
        write(dir, research + ".prompt-fragment.md", "## Research\n\nResearch guidance.\n");
        write(dir, software + ".step-enhancement.yaml", enhancement);
        write(dir, software + ".prompt-fragment.md", "## Software\n\nSoftware guidance.\n");
        write(dir, ENHANCEMENT_GRAPH, """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - source: step_enhancement:spdd-plan
                  target: base::action:research/plan
                  relation: enhances
                """);

        List<Reason> findings = verdict(dir);

        assertThat(findings)
                .filteredOn(finding -> finding.location().file().endsWith(".step-enhancement.yaml"))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(PackCodes.PROMPT_FRAGMENT_INVALID);
                    assertThat(finding.location().file()).isEqualTo(software + ".step-enhancement.yaml");
                });
        assertThat(findings).extracting(Reason::code).doesNotContain(PackCodes.DUPLICATE_ARTIFACT_ID);
    }

    @Test
    void theGenericDuplicateIdRuleStillFiresForTwoTacticsOfOneId(@TempDir Path dir) throws IOException {
        String tactic = "schema_version: '1.0'\nid: same\nname: Same\nsteps:\n  - title: Only step\n";
        write(dir, "tactics/same.tactic.yaml", tactic);
        write(dir, "more/same.tactic.yaml", tactic);

        assertThat(verdict(dir))
                .filteredOn(finding -> PackCodes.DUPLICATE_ARTIFACT_ID.equals(finding.code()))
                .singleElement()
                .satisfies(finding -> assertThat(finding.location().file()).isEqualTo("tactics/same.tactic.yaml"));
    }

    @Test
    void anIdThatDiffersFromItsFileStemIsASchemaViolationAtTheIdAndNeverPromptFragmentInvalid(@TempDir Path dir)
            throws IOException {
        validPair(dir);
        write(dir, yaml("spdd-specify"), SPDD_ENHANCEMENT.replace("id: spdd-specify", "id: spdd-other"));

        assertThat(promptFragmentFindings(dir)).isEmpty();
        assertThat(verdict(dir))
                .filteredOn(finding -> finding.location().file().equals(yaml("spdd-specify")))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(PackCodes.ARTIFACT_SCHEMA_ERROR);
                    assertThat(finding.location().pointer()).isEqualTo("/id");
                });
    }

    @Test
    void anEmptyPromptFragmentsListIsASchemaViolationAndAnAbsentKeyIsPromptFragmentInvalid(@TempDir Path dir)
            throws IOException {
        write(dir, ENHANCEMENT_GRAPH, SPDD_EDGE);

        write(dir, yaml("spdd-specify"), "schema_version: '1.0'\nid: spdd-specify\nprompt_fragments: []\n");
        assertThat(promptFragmentFindings(dir)).isEmpty();
        assertThat(verdict(dir)).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo(PackCodes.ARTIFACT_SCHEMA_ERROR);
            assertThat(finding.location().file()).isEqualTo(yaml("spdd-specify"));
        });

        write(dir, yaml("spdd-specify"), "schema_version: '1.0'\nid: spdd-specify\n");
        assertThat(promptFragmentFindings(dir))
                .singleElement()
                .satisfies(finding -> assertThat(finding.location().file()).isEqualTo(yaml("spdd-specify")));
    }

    @Test
    void aSlotThatBreaksTheGrammarIsASchemaViolationAndARepeatedSlotIsPromptFragmentInvalid(@TempDir Path dir)
            throws IOException {
        validPair(dir);
        write(dir, fragment("second"), "## Second\n\nAnother.\n");

        write(dir, yaml("spdd-specify"), SPDD_ENHANCEMENT + "  - slot: Bad:Id\n    path: second.prompt-fragment.md\n");
        assertThat(promptFragmentFindings(dir)).isEmpty();
        assertThat(verdict(dir)).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo(PackCodes.ARTIFACT_SCHEMA_ERROR);
            assertThat(finding.location().file()).isEqualTo(yaml("spdd-specify"));
        });

        write(
                dir,
                yaml("spdd-specify"),
                SPDD_ENHANCEMENT + "  - slot: spdd:reasons-block\n    path: second.prompt-fragment.md\n");
        assertThat(promptFragmentFindings(dir))
                .singleElement()
                .satisfies(finding -> assertThat(finding.location().file()).isEqualTo(yaml("spdd-specify")));
    }

    @Test
    void aFragmentThatBreaksTheCodecAndHoldsAMarkerAnswersTheCodecFaultOnly(@TempDir Path dir) throws IOException {
        validPair(dir);
        write(dir, fragment("spdd-specify"), "## Guidance\r\n\r\n<!-- other:slot:start -->\r\n");

        assertThat(promptFragmentFindings(dir)).singleElement().satisfies(finding -> {
            assertThat(finding.location().file()).isEqualTo(fragment("spdd-specify"));
            assertThat(finding.detail()).contains("a carriage return is not admitted");
        });
    }

    // ---- the asset MIME precheck (S2, R2 row 14) ---------------------------------------------------------------

    private static final String SIDECAR = "assets/logo.asset.yaml";

    @TempDir
    static Path mimePacks;

    /** The MIME precheck of one planted asset, as the one read's faults word it. */
    private static List<Reason> mime(String path, String mime) {
        try {
            Path dir = Files.createTempDirectory(mimePacks, "pack");
            write(dir, SIDECAR, "id: logo\nmime: '%s'\npath: '%s'\n".formatted(mime, path));
            return full(dir).readFaults().stream()
                    .filter(fault -> StorageCodes.ASSET_MIME_INVALID.equals(fault.code()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "/png", "image/", "/"})
    void aMimeWithoutTwoSidesIsNotWellFormed(String mime) {
        List<Reason> findings = mime("logo.zzz", mime);
        assertThat(findings)
                .containsExactly(Findings.at(
                        StorageCodes.ASSET_MIME_INVALID,
                        SIDECAR,
                        List.of(),
                        SIDECAR + ": asset mime '" + mime + "' is not a well-formed 'type/subtype' value"));
        assertThat(findings.get(0).severity()).isEqualTo(Severity.VIOLATION);
    }

    @Test
    void aMimeThatDisagreesWithTheExtensionTableIsInvalidWithThePinnedText() {
        assertThat(mime("img/logo.png", "text/plain"))
                .containsExactly(Findings.at(
                        StorageCodes.ASSET_MIME_INVALID,
                        SIDECAR,
                        List.of(),
                        SIDECAR + ": asset mime 'text/plain' does not match the '.png' extension (image/png)"));
    }

    static Stream<Arguments> agreeingOrUnjudgedPaths() {
        return Stream.of(
                Arguments.of("logo.png", "image/png"),
                Arguments.of("LOGO.PNG", "image/png"),
                Arguments.of("logo.JSON", "application/json"),
                Arguments.of("logo.zzz", "application/x-anything"),
                Arguments.of("logo", "application/x-anything"),
                Arguments.of(".png", "application/x-anything"),
                Arguments.of("a.b/logo", "application/x-anything"),
                Arguments.of("a/b/c.d/", "application/x-anything"),
                Arguments.of("x.tgz", "application/x-tar"),
                Arguments.of("x.TGZ", "application/x-tar"),
                Arguments.of("x.tar.gz", "application/x-tar"),
                Arguments.of("x.json.gz", "application/json"),
                Arguments.of("x.svgz", "image/svg+xml"),
                Arguments.of("x.json.Z", "application/json"),
                Arguments.of("x.md", "text/markdown"),
                Arguments.of("logo.zzz", "a/b/c"));
    }

    @ParameterizedTest
    @MethodSource("agreeingOrUnjudgedPaths")
    void anAgreeingOrUnknownExtensionPasses(String path, String mime) {
        assertThat(mime(path, mime)).isEmpty();
    }

    static Stream<Arguments> disagreeingPaths() {
        return Stream.of(
                Arguments.of("x.tgz", "application/gzip", ".tar", "application/x-tar"),
                Arguments.of("x.json.gz", "text/plain", ".json", "application/json"),
                Arguments.of("x.JPG", "image/png", ".jpg", "image/jpeg"),
                Arguments.of("dir.d/x.css", "text/plain", ".css", "text/css"),
                Arguments.of("x.json.Z", "text/plain", ".json", "application/json"));
    }

    @ParameterizedTest
    @MethodSource("disagreeingPaths")
    void aDisagreementNamesTheEffectiveExtensionAndTheExpectedType(
            String path, String mime, String extension, String expected) {
        assertThat(mime(path, mime))
                .extracting(Reason::detail)
                .containsExactly(SIDECAR + ": asset mime '" + mime + "' does not match the '" + extension
                        + "' extension (" + expected + ")");
    }

    @Test
    void theMimeTypeIsComparedExactly() {
        assertThat(mime("logo.png", "IMAGE/PNG"))
                .extracting(Reason::code)
                .containsExactly(StorageCodes.ASSET_MIME_INVALID);
    }

    // ---- the one read: the root, the census and the prechecks (#554, T025, T026, T030) -------------------------

    @Test
    void aDirectoryThatCannotBeListedIsOneReadFaultAtThePack(@TempDir Path dir) throws IOException {
        write(dir, "pack.yaml", "name: qa\n");
        PackFileKind table = PackFileKind.bundled();
        FileSystemPackInspector unlistable = new FileSystemPackInspector(
                table,
                new YamlSerializationStrategy(),
                KindBindings.all(),
                new Companions.Rules(table, PackSchemas::bundled),
                (directory, name) -> {
                    throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + name + " cannot be listed");
                });

        TreeRead read = unlistable.readTree(root(dir));

        assertThat(read.readFaults()).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(StorageCodes.PACK_UNREADABLE);
            assertThat(fault.location().file()).isEqualTo(FileSystemPackInspector.ROOT);
            assertThat(fault.detail()).isEqualTo("pack qa cannot be listed");
        });
        assertThat(read.census()).isEqualTo(TreeCensus.empty());
        assertThat(unlistable.read(root(dir)).rationale().isFailing())
                .as("the port answers the fault; it never throws")
                .isTrue();
    }

    @Test
    void aRootThatIsNoDirectoryIsOneReadFaultAtThePackAsARegistrationPlacesIt(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("qa"), "not a directory\n");
        for (Path missing : List.of(dir.resolve("missing"), file)) {
            TreeRead read = INSPECTOR.readTree(root(missing));

            assertThat(read.readFaults()).singleElement().satisfies(fault -> {
                assertThat(fault.code()).isEqualTo(StorageCodes.PACK_UNREADABLE);
                assertThat(fault.location().file()).isEqualTo(FileSystemPackInspector.ROOT);
                assertThat(fault.detail()).isEqualTo("pack qa is not a directory");
            });
            assertThat(read.census()).isEqualTo(TreeCensus.empty());
            assertThat(INSPECTOR.read(root(missing)).rationale().isFailing())
                    .as("the port answers the fault; it never throws")
                    .isTrue();
            assertThatThrownBy(() -> StagedReads.contents(new WholePackStore(new YamlSerializationStrategy()), missing))
                    .as("the registration answers the same root as a 422 at pack (F4)")
                    .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                        assertThat(refusal.violations())
                                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                                .containsExactly(tuple("pack", StorageCodes.PACK_UNREADABLE.value()));
                    });
        }
    }

    @Test
    void theCensusReadsTheManifestAndTheReservedShardWithoutFollowingALink(@TempDir Path host) throws IOException {
        SymbolicLinks.requireSupport(host);
        Path dir = host.resolve("qa");
        write(
                dir,
                "pack.yaml",
                "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");
        write(host, "outside/pack-manifest.yaml", "schema_version: '1.0'\nconstituents: []\npresets: []\n");
        SymbolicLinks.create(dir.resolve("pack-manifest.yaml"), host.resolve("outside/pack-manifest.yaml"));
        write(host, "outside/notes.txt", "a linked author aid\n");
        SymbolicLinks.create(dir.resolve("notes.txt"), host.resolve("outside/notes.txt"));

        TreeCensus census = INSPECTOR.readTree(root(dir)).census();

        assertThat(census.manifest().present()).isTrue();
        assertThat(census.manifest().regular())
                .as("a link in the manifest's place is not a regular file (#110)")
                .isFalse();
        assertThat(census.notHeld())
                .as("a file that is itself a link is neither held nor listed")
                .extracting(NotHeldFile::path)
                .doesNotContain("notes.txt");
        assertThat(census.reservedShard()).isEqualTo(TreeCensus.ReservedShard.ABSENT);
    }

    @Test
    void theCensusStampsTheReservedShardAsAFactNotAnUnreadableGraph(@TempDir Path dir) throws IOException {
        write(
                dir,
                "pack.yaml",
                "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");
        write(dir, RelationshipShard.SHARD_PATH, RelationshipShard.write(List.of()));
        assertThat(full(dir).census().reservedShard()).isEqualTo(TreeCensus.ReservedShard.STAMPED);

        write(dir, RelationshipShard.SHARD_PATH, "schema_version: '1.0'\ngenerated_at: S\nnodes: []\nedges: []\n");
        assertThat(full(dir).census().reservedShard()).isEqualTo(TreeCensus.ReservedShard.UNSTAMPED);
    }

    @Test
    void theCensusHoldsTheManifestsListsBesideTheDerivedOnes(@TempDir Path host) throws IOException {
        Path dir = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        PackRoot root = new PackRoot("built-in", Optional.of(PackLevel.BUILT_IN), dir.toString());

        TreeCensus.Manifest manifest = INSPECTOR.readTree(root).census().manifest();

        assertThat(manifest.present()).isTrue();
        assertThat(manifest.regular()).isTrue();
        assertThat(manifest.listed()).isPresent();
        assertThat(manifest.derived())
                .as("the reader derives the held lists through PackManifestDerivation (T024)")
                .contains(ManifestMembership.entries(PackManifestDerivation.derivedAt(
                        new PackIdentifier(ExamplePacks.BUILT_IN), dir, new YamlSerializationStrategy())));
        assertThat(ManifestRules.mismatch(
                        manifest.listed().orElseThrow(), manifest.derived().orElseThrow()))
                .as("the example pack's manifest lists what it holds")
                .isEmpty();

        Path tactic;
        try (var tactics = Files.list(dir.resolve("tactics"))) {
            tactic = tactics.sorted().findFirst().orElseThrow();
        }
        Files.delete(tactic);
        TreeCensus.Manifest short1 = INSPECTOR.readTree(root).census().manifest();
        assertThat(ManifestRules.mismatch(
                        short1.listed().orElseThrow(), short1.derived().orElseThrow()))
                .as("a listed file the pack no longer holds is a mismatch of the two facts")
                .singleElement()
                .asString()
                .startsWith("lists constituent tactic:");
    }

    @Test
    void theManifestsReadFaultAndTheNamedPolicysFaultTrailOnceEachTheManifestFirst(@TempDir Path dir)
            throws IOException {
        write(dir, "pack.yaml", """
                pack_id: 01ARWG13C00000000000001ABC
                pack_version: 1.0.0
                name: qa
                pack_level: org
                mutability_policy: policy.yaml
                """);
        write(dir, "pack-manifest.yaml", "constituents: [\n");
        write(dir, "tactics/t.tactic.yaml", "id: t\nschema_version: '1.0'\nname: T\nsteps:\n- title: Do\n");

        // WP07 review MINOR-3 and R2 row 32: the read faults at the manifest and the store's fault at the named policy
        // file follow the pack's other faults, the manifest's first, and each is stated once. Read unsorted, in the
        // order the registration answers it: the helper verdict() sorts by place, which would hide the order.
        assertThat(StorageRulesets.verdict(full(dir)).details())
                .extracting(finding -> finding.location().file())
                .filteredOn(file -> file.equals("pack-manifest.yaml") || file.equals("policy.yaml"))
                .containsExactly("pack-manifest.yaml", "policy.yaml");
    }

    @Test
    void aPackWithNoManifestHasNoManifestFacts(@TempDir Path dir) throws IOException {
        write(
                dir,
                "pack.yaml",
                "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");

        assertThat(full(dir).census().manifest()).isEqualTo(TreeCensus.Manifest.ABSENT);
    }

    @Test
    void theDescriptorFormatIsAPrecheckOfTheReadWithItsStorageCode(@TempDir Path dir) throws IOException {
        write(dir, "pack.yaml", """
                pack_id: 01ARWG13C00000000000001ABC
                pack_version: not-pep440
                name: qa
                pack_level: org
                requires_core: banana
                """);

        assertThat(full(dir).readFaults())
                .extracting(Reason::code, fault -> fault.location().pointer())
                .containsExactly(
                        tuple(StorageCodes.REQUIRES_CORE_INVALID, "/requires_core"),
                        tuple(StorageCodes.PACK_VERSION_INVALID, "/pack_version"));
    }

    @Test
    void theAssetMimeIsAPrecheckOfTheReadAfterEveryOtherFault(@TempDir Path dir) throws IOException {
        write(dir, "assets/a.asset.yaml", ASSET.formatted("a", "text/plain", "a.png"));
        write(dir, "assets/a.png", "png");
        write(dir, "tactics/bad.tactic.yaml", "id: [\n");

        assertThat(full(dir).readFaults())
                .extracting(Reason::code, fault -> fault.location().file())
                .containsExactly(
                        tuple(StorageCodes.UNREADABLE, "tactics/bad.tactic.yaml"),
                        tuple(StorageCodes.ASSET_MIME_INVALID, "assets/a.asset.yaml"));
    }

    @Test
    void aReadFaultIsAKernelReasonWhosePointerIsItsPlace(@TempDir Path dir) throws IOException {
        write(dir, "procedures/misnamed.procedure.yaml", PROCEDURE.formatted("other"));

        assertThat(full(dir).readFaults().getFirst().location().pointer()).isEqualTo("/id");
    }

    // ---- the A-M1 checklist: the load's faults are facts of the one read (#554, T025, T028) ---------------------

    private static PackContents.Assembly.Assembled assembled(TreeRead read) {
        if (read.contents().assembly() instanceof PackContents.Assembly.Assembled held) {
            return held;
        }
        throw new AssertionError(
                "expected the read's assembly input but was " + read.contents().assembly());
    }

    private static TreeRead builtIn(Path pack) {
        return INSPECTOR.readTree(new PackRoot("built-in", Optional.of(PackLevel.BUILT_IN), pack.toString()));
    }

    @Test
    void theReadCarriesTheWholeAssemblyInputAndTheAggregateIsBuiltFromItAlone(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);

        PackContents.Assembly.Assembled held = assembled(builtIn(pack));
        Pack built = PackAssembly.build(held);

        assertThat(held.violations()).isEmpty();
        assertThat(held.facts()).isPresent();
        assertThat(held.policyText()).isPresent();
        assertThat(built.artifacts())
                .containsExactlyElementsOf(held.artifacts().stream()
                        .map(PackContents.Assembly.Placed::artifact)
                        .toList());
        assertThat(built.id().value()).as("the id the root is read under").isEqualTo("built-in");
        assertThat(built.descriptor().packId()).isEqualTo("01ARWG13C000000000000000FG");
        assertThat(held.artifacts())
                .allSatisfy(placed -> assertThat(pack.resolve(placed.file())).exists());
    }

    @Test
    void aValueObjectBreachIsAFaultOfTheReadAtItsFile(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        write(pack, "presets/dup.yaml", """
                schema_version: '1.0'
                id: dup
                description: D.
                activated_directives:
                - id: DIRECTIVE_001
                - id: DIRECTIVE_001
                  must_have: true
                """);

        assertThat(assembled(builtIn(pack)).violations()).singleElement().satisfies(fault -> {
            assertThat(fault.file()).isEqualTo("presets/dup.yaml");
            assertThat(fault.kind()).isEqualTo(PackCodes.ARTIFACT_SCHEMA_ERROR);
            assertThat(fault.reason()).startsWith("Constraint breached: Preset 'dup'");
        });
    }

    @Test
    void aReferenceThatDoesNotCloseIsAFaultOfTheReadAtItsFile(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        Files.delete(pack.resolve("missions/software-dev/templates/research-template.md"));

        assertThat(assembled(builtIn(pack)).violations())
                .extracting(PackElementViolation::file, PackElementViolation::kind)
                .contains(
                        tuple("missions/software-dev/software-dev.mission-type.yaml", PackCodes.ARTIFACT_SCHEMA_ERROR));
    }

    @Test
    void aMissingDescriptorIsTheReadsRefusalAtTheDescriptor(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        Files.delete(pack.resolve("pack.yaml"));

        assertThat(builtIn(pack).contents().assembly())
                .isInstanceOfSatisfying(PackContents.Assembly.Refused.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                    assertThat(refused.atDescriptor()).isTrue();
                    assertThat(refused.graphFaults()).isEmpty();
                });
    }

    @Test
    void anUnreadableNamedPolicyFileIsAFaultOfTheReadAfterTheAssemblys(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        write(pack, "policy.yaml", "schema_version: 1\npins: []\n");

        PackContents.Assembly.Assembled held = assembled(builtIn(pack));

        assertThat(held.violations()).singleElement().satisfies(fault -> {
            assertThat(fault.file()).isEqualTo("policy.yaml");
            assertThat(fault.kind()).isEqualTo(StorageCodes.UNREADABLE);
            assertThat(fault.reason()).startsWith("named by mutability_policy, the file ");
        });
        assertThat(held.assembled()).as("it follows the assembly's own faults").isZero();
        assertThat(held.policyText()).isEmpty();
        assertThat(held.policyFault()).isPresent();
    }

    @Test
    void anUnreadableGraphIsAFaultOfTheReadAndTheFactsAreNotBuilt(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        write(pack, "broken.graph.yaml", "nodes: [\n");

        PackContents.Assembly.Assembled held = assembled(builtIn(pack));

        assertThat(held.graphFaults()).extracting(PackElementViolation::file).containsExactly("broken.graph.yaml");
        assertThat(held.facts()).isEmpty();
    }

    @Test
    void anAggregateBreachIsTheReadsRefusalAsAWholeAfterTheGraphFaults() {
        // A-M1 row 3: no planted pack reaches the aggregate's build() breach, so the fact is built directly.
        PackElementViolation graphFault =
                new PackElementViolation("a.graph.yaml", PackCodes.DRG_SCHEMA_ERROR, "not valid YAML");

        StoreRead read =
                StoreRead.breached(List.of(graphFault), "A pack must not hold two tactics with the same identifier.");

        assertThat(read.assembly()).isInstanceOfSatisfying(PackContents.Assembly.Refused.class, refused -> {
            assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
            assertThat(refused.message()).isEqualTo("A pack must not hold two tactics with the same identifier.");
            assertThat(refused.atDescriptor())
                    .as("placed at pack, not pack.yaml")
                    .isFalse();
            assertThat(refused.graphFaults()).containsExactly(graphFault);
        });
        assertThat(read.graphFaults()).containsExactly(graphFault);
        assertThat(read.graphs()).isEmpty();
    }
}
