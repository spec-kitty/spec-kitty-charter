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
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackInspector.Scope;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackVerdict;
import ai.speckitty.charter.packs.domain.Preset;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The load-path inspector reads {@code pack.yaml}, the root shards and the fragment as facts, and nothing else. */
class FileSystemPackInspectorTest {

    private static final FileSystemPackInspector INSPECTOR = new FileSystemPackInspector();

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

        PackContents contents = INSPECTOR.inspect(root(dir), Scope.LOAD_PATH);

        assertThat(contents.root()).isEqualTo(root(dir));
        assertThat(read(contents.descriptor()).file()).isEqualTo("pack.yaml");
        assertThat(read(contents.descriptor()).value().declaredLevel()).contains(PackLevel.ORG);
        assertThat(contents.shards()).extracting(FileRead::file).containsExactly("a.graph.yaml", "b.graph.yaml");
        assertThat(contents.shards()).allSatisfy(s -> assertThat(s).isInstanceOf(FileRead.Read.class));
        assertThat(read(contents.fragment()).file()).isEqualTo("drg/fragment.yaml");
        assertThat(read(contents.fragment()).value().shape()).isEqualTo(RawGraph.Shape.FRAGMENT);
        assertThat(contents.files()).isEmpty();
        assertThat(contents.stepContracts()).isEmpty();
        assertThat(contents.artifacts()).isEmpty();
        assertThat(contents.assets()).isEmpty();
    }

    @Test
    void aSourceShardNamedLikeTheRegistrationShardIsRefusedButTheStoresOwnIsRead(@TempDir Path dir) throws IOException {
        write(
                dir,
                "pack.yaml",
                "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");
        write(
                dir,
                "registered.graph.yaml",
                "schema_version: '1.0'\ngenerated_at: STATIC\nnodes:\n- {urn: 'directive:A', kind: directive}\nedges: []\n");

        FileRead.Unreadable<?> source = unreadable(
                INSPECTOR.inspect(root(dir), Scope.LOAD_PATH).shards().get(0));

        assertThat(source.file()).isEqualTo("registered.graph.yaml");
        assertThat(source.parse().firstLine()).contains("reserved for registered relationships");

        write(dir, "registered.graph.yaml", RelationshipShard.write(java.util.List.of()));

        read(INSPECTOR.inspect(root(dir), Scope.LOAD_PATH).shards().get(0));
    }

    @Test
    void aFileThatIsNotThereIsAbsent(@TempDir Path dir) {
        PackContents contents = INSPECTOR.inspect(root(dir), Scope.LOAD_PATH);

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

        PackContents contents = INSPECTOR.inspect(root(dir), Scope.LOAD_PATH);

        assertThat(unreadable(contents.descriptor()).parse()).isNotNull();
        assertThat(unreadable(contents.shards().get(0)).file()).isEqualTo("a.graph.yaml");
        assertThat(unreadable(contents.shards().get(0)).parse()).isNotNull();
        assertThat(unreadable(contents.shards().get(1)).file()).isEqualTo("b.graph.yaml");
        assertThat(unreadable(contents.shards().get(1)).violations()).isNotEmpty();
        assertThat(unreadable(contents.fragment()).violations()).isNotEmpty();
    }

    @Test
    void aRootThatIsNoDirectoryIsRefusedAsUnreadable(@TempDir Path dir) {
        assertThatThrownBy(() -> INSPECTOR.inspect(root(dir.resolve("missing")), Scope.LOAD_PATH))
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                    assertThat(r.getMessage()).isEqualTo("pack qa is not a directory");
                });
    }

    @Test
    void aFileThatIsNotUtf8IsNotYamlAndTheCodeFollowsItsKind(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("pack.yaml"), new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00});
        Files.write(dir.resolve("tactic.graph.yaml"), new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00});
        Files.createDirectories(dir.resolve("drg"));
        Files.write(dir.resolve("drg/fragment.yaml"), new byte[] {'a', ':', ' ', (byte) 0xC3});

        for (Scope scope : Scope.values()) {
            PackContents contents = INSPECTOR.inspect(root(dir), scope);

            assertThat(unreadable(contents.descriptor()).parse())
                    .isEqualTo(new PackContents.ParseFailure("pack.yaml", "the file is not valid UTF-8"));
            assertThat(unreadable(contents.shards().get(0)).parse())
                    .isEqualTo(new PackContents.ParseFailure("tactic.graph.yaml", "the file is not valid UTF-8"));
            assertThat(unreadable(contents.fragment()).parse())
                    .isEqualTo(new PackContents.ParseFailure("drg/fragment.yaml", "the file is not valid UTF-8"));
        }
    }

    /**
     * A {@code pack.yaml} with 217 unknown keys, {@code N} and {@code Nx} mixed, once made the segment order a cycle, and
     * both the load and the judge threw TimSort's "Comparison method violates its general contract!". Each key is now a
     * schema refusal at the root (ADR-0036 decision 3), so every finding has the same place and this fixture no longer
     * exercises the segment order: {@code PackPolicyOrderTest} guards the comparator. This test keeps the fixture as a
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

        for (Scope scope : Scope.values()) {
            PackContents contents = INSPECTOR.inspect(project, scope);
            // The closed descriptor schema (ADR-0036 decision 3) refuses each key at the root, naming it in the detail.
            assertThat(PackPolicy.judge(contents).findings())
                    .extracting(f -> f.detail()
                            .substring(f.detail().indexOf('\'') + 1, f.detail().lastIndexOf('\'')))
                    .as("every unknown key refused once")
                    .hasSize(217)
                    .doesNotHaveDuplicates();
        }
    }

    // ---- the full scope ------------------------------------------------------------------------------------------

    private static final String TACTIC = "id: %s\nschema_version: '1.0'\nname: T\nsteps:\n- title: s\n";
    private static final String ASSET = "id: %s\nmime: %s\npath: %s\n";

    private static PackContents full(Path dir) {
        return INSPECTOR.inspect(root(dir), Scope.FULL);
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

        PackContents linked = full(link);
        PackContents direct = full(real);

        assertThat(linked.files())
                .as("validate mode walks a linked root from its real path (#284)")
                .extracting(PackContents.ClassifiedFile::file)
                .containsExactly("pack.yaml", "tactics/x.tactic.yaml");
        assertThat(linked.files()).isEqualTo(direct.files());
        assertThat(linked.artifacts()).isEqualTo(direct.artifacts()).isNotEmpty();
        assertThat(linked.unreadableFiles()).isEqualTo(direct.unreadableFiles());
    }

    @Test
    void theFullScopeReadsThePackFilesOfTheLoadPathAsTheLoadPathDoes(@TempDir Path dir) throws IOException {
        write(
                dir,
                "pack.yaml",
                "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\nmystery: 1\n");
        write(dir, "a.graph.yaml", "schema_version: '1.0'\ngenerated_at: S\nnodes: []\nedges: []\n");
        write(dir, "b.graph.yaml", "nodes: [\n");
        write(dir, "drg/fragment.yaml", "nodes: []\nedges: []\n");
        write(dir, "tactics/x.tactic.yaml", TACTIC.formatted("x"));

        PackContents load = INSPECTOR.inspect(root(dir), Scope.LOAD_PATH);
        PackContents full = full(dir);

        assertThat(full.root()).isEqualTo(load.root());
        assertThat(full.descriptor()).isEqualTo(load.descriptor());
        assertThat(full.shards()).isEqualTo(load.shards());
        assertThat(full.fragment()).isEqualTo(load.fragment());
        assertThat(full.unreadableFiles())
                .as("the shard is a load-path file, reported once")
                .isEmpty();
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

        assertThat(full(dir).files())
                .containsExactly(
                        classified("a.graph.yaml", "graph-shard"),
                        classified("drg/fragment.yaml", "org-fragment"),
                        // A retired file (#194) is refused by name, never listed; an excluded one is listed.
                        excluded(
                                "missions/m/templates/plan-field-declaration.yaml",
                                "**/templates/plan-field-declaration.yaml"),
                        classified("org-charter.yaml", "org-charter"),
                        classified("pack.yaml", "pack-descriptor"),
                        classified("tactics/x.tactic.yaml", "tactic"),
                        classified("tactics-b/y.tactic.yaml", "tactic"));
    }

    private static PackContents.ClassifiedFile classified(String file, String kind) {
        return new PackContents.ClassifiedFile(file, Optional.of(kind), Optional.empty());
    }

    private static PackContents.ClassifiedFile excluded(String file, String exclusionClass) {
        return new PackContents.ClassifiedFile(file, Optional.empty(), Optional.of(exclusionClass));
    }

    @Test
    void aMappedFileThatIsNotYamlOrBreaksItsSchemaIsUnreadableNotAnException(@TempDir Path dir) throws IOException {
        write(dir, "tactics/bad.tactic.yaml", "id: [\n");
        write(dir, "tactics/short.tactic.yaml", "id: only\n");
        write(dir, "org-charter.yaml", "schema_version: two\n");
        write(dir, "project/charter.yaml", "governance: 3\n");
        write(dir, "directives/bytes.directive.yaml", "id: !!binary AAAA\n");
        write(dir, "paradigms/utf.paradigm.yaml", "a: b\n");
        Files.write(dir.resolve("paradigms/utf.paradigm.yaml"), new byte[] {(byte) 0xC3, 0x28});

        List<FileRead.Unreadable<?>> unreadable = full(dir).unreadableFiles();

        assertThat(unreadable)
                .extracting(FileRead::file)
                .containsExactly(
                        "directives/bytes.directive.yaml",
                        "org-charter.yaml",
                        "paradigms/utf.paradigm.yaml",
                        "project/charter.yaml",
                        "tactics/bad.tactic.yaml",
                        "tactics/short.tactic.yaml");
        assertThat(unreadable.get(0).parse()).isNotNull();
        assertThat(unreadable.get(1).violations())
                .extracting(PackContents.SchemaViolation::keyword)
                .containsExactly("type");
        assertThat(unreadable.get(2).parse())
                .isEqualTo(new PackContents.ParseFailure("paradigms/utf.paradigm.yaml", "the file is not valid UTF-8"));
        assertThat(unreadable.get(4).parse()).isNotNull();
        assertThat(unreadable.get(5).violations()).isNotEmpty();
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

        List<FileRead.Unreadable<?>> unreadable = full(dir).unreadableFiles();

        assertThat(unreadable)
                .extracting(FileRead::file)
                .containsExactlyInAnyOrder(
                        "toolguides/preamble.guide.md",
                        "toolguides/no-overview.guide.md",
                        "toolguides/no-h1.guide.md",
                        "toolguides/fenced.guide.md");
        assertThat(unreadable)
                .filteredOn(file -> file.file().equals("toolguides/preamble.guide.md"))
                .singleElement()
                .satisfies(file -> assertThat(file.violations())
                        .extracting(PackContents.SchemaViolation::keyword)
                        .containsExactly("const"));
        assertThat(unreadable)
                .filteredOn(file -> file.file().equals("toolguides/no-overview.guide.md"))
                .singleElement()
                .satisfies(file -> assertThat(file.parse())
                        .isEqualTo(new PackContents.ParseFailure(
                                "toolguides/no-overview.guide.md", "the guide has no ## Overview")));
        assertThat(unreadable)
                .filteredOn(file -> file.file().equals("toolguides/no-h1.guide.md"))
                .singleElement()
                .satisfies(file -> assertThat(String.valueOf(file.parse())).contains("has no H1"));
        assertThat(PackPolicy.judge(full(dir)).findings())
                .extracting(PackFinding::detail)
                .anySatisfy(detail -> assertThat(detail)
                        .isEqualTo("qa/toolguides/no-overview.guide.md is not valid Markdown: the guide has no ##"
                                + " Overview"));
        assertThat(full(dir).files())
                .as("a Markdown file is read, not listed in the YAML classification")
                .isEmpty();
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
        PackContents contents = full(dir);

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
        assertThat(contents.unreadableFiles()).isEmpty();
        assertThat(contents.files())
                .extracting(PackContents.ClassifiedFile::file)
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

        assertThat(full(dir).unreadableFiles())
                .extracting(FileRead::file)
                .containsExactlyInAnyOrder(
                        ENHANCEMENT_DIR + "spdd-specify.prompt-fragment.md",
                        ENHANCEMENT_DIR + "blank.prompt-fragment.md");
        // OD-10 row 4: validate mode answers prompt_fragment_invalid, at the fragment, for the frontmatter fragment and
        // for the blank one (an orphan: the staged load refuses it as well, StepEnhancementLoadChecksTest).
        assertThat(promptFragmentFindings(dir).stream().map(PackFinding::file).distinct())
                .containsExactlyInAnyOrder(
                        ENHANCEMENT_DIR + "spdd-specify.prompt-fragment.md",
                        ENHANCEMENT_DIR + "blank.prompt-fragment.md");
    }

    @Test
    void theTwinOfThePromptFragmentThatBreaksItsStructureIsReadableAndAnswersNoPromptFragmentInvalid(@TempDir Path dir)
            throws IOException {
        write(dir, ENHANCEMENT_GRAPH, SPDD_EDGE);
        write(dir, ENHANCEMENT_DIR + "spdd-specify.step-enhancement.yaml", SPDD_ENHANCEMENT);
        write(dir, ENHANCEMENT_DIR + "spdd-specify.prompt-fragment.md", "## REASONS guidance\n\nState the reasons.\n");

        assertThat(full(dir).unreadableFiles()).isEmpty();
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

        PackContents contents = full(dir);

        assertThat(contents.unreadableFiles()).singleElement().satisfies(file -> {
            assertThat(file.file()).isEqualTo("procedures/misnamed.procedure.yaml");
            assertThat(file.violations()).singleElement().satisfies(violation -> {
                assertThat(violation.keyword()).isEqualTo("const");
                assertThat(violation.pointer()).containsExactly("id");
            });
        });
        assertThat(contents.artifacts())
                .extracting(PackContents.ArtifactFacts::id)
                .containsExactly("named");
    }

    @Test
    void aFileThatIsReadCleanlyIsNotListedAsUnreadable(@TempDir Path dir) throws IOException {
        write(dir, "tactics/x.tactic.yaml", TACTIC.formatted("x"));
        write(dir, "org-charter.yaml", "schema_version: 2\norg_name: acme\n");

        assertThat(full(dir).unreadableFiles()).isEmpty();
    }

    @Test
    void stepIdsAndArtifactIdsAreFactsOfTheFilesThatPassedTheirSchema(@TempDir Path dir) throws IOException {
        write(dir, "contracts/a.step-contract.yaml", """
                id: c1
                schema_version: '1.0'
                action: plan
                mission: m
                steps:
                - id: s1
                  description: d
                - id: s2
                  description: d
                - id: s1
                  description: d
                """);
        write(dir, "contracts/broken.step-contract.yaml", "id: c2\n");
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
                roles: [implementer]
                purpose: x
                specialization:
                  primary-focus: f
                """);

        PackContents contents = full(dir);

        assertThat(contents.stepContracts())
                .containsExactly(new PackContents.StepContractFacts(
                        "contracts/a.step-contract.yaml", java.util.List.of("s1", "s2", "s1")));
        assertThat(contents.artifacts())
                .as("a file that failed its schema owns no id; an agent profile has no id, as in core")
                .containsExactly(
                        new PackContents.ArtifactFacts("contracts/a.step-contract.yaml", "mission_step_contract", "c1"),
                        new PackContents.ArtifactFacts("directives/d.directive.yaml", "directive", "DIRECTIVE_001"),
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

        assertThat(full(dir).assets())
                .as("core checks the first manifest of a repeated id, in path order, and only schema-valid ones")
                .containsExactly(
                        new PackContents.AssetFacts("assets/a.asset.yaml", "logo", "logo.png", "image/png", true),
                        new PackContents.AssetFacts("assets/d.asset.yaml", "doc", "doc.txt", "text/plain", true));
        assertThat(full(dir).artifacts())
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

        assertThat(full(dir).assets())
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

        PackContents contents = full(dir);

        assertThat(contents.unreadableFiles()).isEmpty();
        assertThat(contents.files())
                .containsExactly(
                        classified("assets/logo.png", "asset-payload"),
                        classified("assets/tools/x.config.yaml", "asset-payload"),
                        classified("assets/x.md", "asset-payload"),
                        classified("assets/x.md.asset.yaml", "asset"),
                        classified("templates/writing/t.md", "template"),
                        classified("templates/writing/t.yaml", "template"));
        assertThat(contents.assets())
                .as("the sidecar is read, and its payload is held")
                .containsExactly(
                        new PackContents.AssetFacts("assets/x.md.asset.yaml", "x", "x.md", "text/markdown", true));
        assertThat(contents.artifacts())
                .containsExactly(new PackContents.ArtifactFacts("assets/x.md.asset.yaml", "asset", "x"));
    }

    /**
     * #251: validate mode refuses the sidecars the store's load refuses, with the load's words: a payload the pack does
     * not hold (missing, spelled in another case, or outside {@code assets/}), and a payload an earlier sidecar named.
     * The sidecar's facts stand, so the asset rules still judge it.
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
        List<Tuple> refused = List.of(
                tuple("assets/b.asset.yaml", "payload assets/b.md does not exist"),
                tuple("assets/c.asset.yaml", "payload assets/Logo.png does not exist"),
                tuple("assets/d.asset.yaml", "payload assets/a.md is already named by asset a"),
                tuple("assets/e.asset.yaml", "payload assets/../../escape.png does not exist"));

        PackContents contents = full(dir);
        FilesystemPackStore.PackRead load =
                new FilesystemPackStore(host, new YamlSerializationStrategy()).read(new PackIdentifier("qa"));

        assertThat(contents.unreadableFiles())
                .extracting(
                        FileRead::file,
                        bad -> bad.parse() == null
                                ? null
                                : bad.parse().refusal().orElse(null))
                .containsExactlyElementsOf(refused);
        assertThat(load.violations())
                .as("the load refuses the same sidecars, in the same words")
                .extracting(PackElementViolation::file, PackElementViolation::reason)
                .containsExactlyElementsOf(refused);
        assertThat(contents.assets())
                .extracting(PackContents.AssetFacts::file)
                .containsExactly(
                        "assets/a.asset.yaml",
                        "assets/b.asset.yaml",
                        "assets/c.asset.yaml",
                        "assets/d.asset.yaml",
                        "assets/e.asset.yaml");
    }

    /**
     * #251: an asset file the store's read does not hold (here, a link that leaves the pack) is refused with the load's
     * reason, and a sidecar naming it names no payload, in validate mode as in the load.
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
        List<Tuple> refused = List.of(
                tuple("assets/out.png", "the file leaves the pack"),
                tuple("assets/f.asset.yaml", "payload assets/out.png does not exist"));

        PackContents contents = full(dir);
        FilesystemPackStore.PackRead load =
                new FilesystemPackStore(host, new YamlSerializationStrategy()).read(new PackIdentifier("qa"));

        assertThat(contents.unreadableFiles())
                .extracting(
                        FileRead::file,
                        bad -> bad.parse() == null
                                ? null
                                : bad.parse().refusal().orElse(null))
                .containsExactlyElementsOf(refused);
        assertThat(load.violations())
                .extracting(PackElementViolation::file, PackElementViolation::reason)
                .containsExactlyElementsOf(refused);
    }

    /**
     * Acceptance check 12: validate mode over the pinned built-in pack reports no exception for its sidecar-less asset
     * payload {@code assets/docs_structural_lint.config.yaml}, and its findings are those of main at 90de8f9.
     *
     * <p>The expected findings were captured, not recomputed: on 90de8f9 (open-packs at 01edb4e) a throwaway
     * {@code @Tag("open-packs")} test printed {@code PackPolicy.judge(inspect(built-in, FULL)).findings()} and the
     * size of {@code files()}, run with {@code ./gradlew :packs:openPacksRoundTrip --tests '<that test>'
     * -PopenPacksDir=<checkout>}. It printed no findings and 386 classified files. The one expected difference in the
     * file list is the config payload: excluded by {@code **}{@code /*.config.yaml} then, kind {@code asset-payload}
     * now.
     */
    @Test
    @Tag("open-packs")
    void validateModeOverTheBuiltInPackFindsWhatMainFoundAndListsTheConfigPayload() throws IOException {
        OpenPacksCorpus.assertPinned();
        Path builtIn = OpenPacksCorpus.packsDirectory().resolve("built-in");
        PackRoot root = new PackRoot("built-in", Optional.of(PackLevel.BUILT_IN), builtIn.toString());

        PackVerdict verdict = PackPolicy.judge(INSPECTOR.inspect(root, Scope.FULL));

        // OP-1, OP-2 and open-packs#75 landed: the presets, the manifest and the six sidecars are all in the shapes the
        // store reads (ADR-0042, ADR-0040 decision 6), so main at 90de8f9's "no findings" is back, but for the five
        // software-dev step prompts whose slot still holds legacy text under an unversioned descriptor: five warnings
        // (ADR-0066 version gate). The slots return to empty when open-packs#175 empties them (#435).
        assertThat(verdict.findings())
                .extracting(PackFinding::code, PackFinding::severity, PackFinding::file)
                .containsExactlyInAnyOrder(
                        tuple(
                                PackFindingCode.PROMPT_SLOT_NOT_EMPTY_UNVERSIONED,
                                PackFindingCode.Severity.WARNING,
                                "missions/software-dev/steps/implement.prompt.md"),
                        tuple(
                                PackFindingCode.PROMPT_SLOT_NOT_EMPTY_UNVERSIONED,
                                PackFindingCode.Severity.WARNING,
                                "missions/software-dev/steps/plan.prompt.md"),
                        tuple(
                                PackFindingCode.PROMPT_SLOT_NOT_EMPTY_UNVERSIONED,
                                PackFindingCode.Severity.WARNING,
                                "missions/software-dev/steps/review.prompt.md"),
                        tuple(
                                PackFindingCode.PROMPT_SLOT_NOT_EMPTY_UNVERSIONED,
                                PackFindingCode.Severity.WARNING,
                                "missions/software-dev/steps/specify.prompt.md"),
                        tuple(
                                PackFindingCode.PROMPT_SLOT_NOT_EMPTY_UNVERSIONED,
                                PackFindingCode.Severity.WARNING,
                                "missions/software-dev/steps/tasks.prompt.md"));

        // 386 at 5df92a5d, less the 74 YAML files the mission-type migration (#194,
        // open-packs#95) retired, plus its 4 mission-type files, plus the payloads and templates #251 lists whatever
        // their extension (the 7 asset payloads and 27 templates the whole-pack census counts, less the YAML payload
        // this walk listed already). The pin f85cf23 adds four mission step prompts (open-packs 7963b62), all under
        // missions/<type>/steps/, which this walk does not list: no step prompt or guideline file is classified. The
        // #240 migration adds the 43 built-in procedures its directives' steps became.
        assertThat(verdict.files()).hasSize(386 - 74 + 4 + (7 + 27 - 1) + 43);
        assertThat(verdict.files())
                .filteredOn(f -> f.file().equals("assets/docs_structural_lint.config.yaml"))
                .containsExactly(classified("assets/docs_structural_lint.config.yaml", "asset-payload"));
        assertThat(verdict.files())
                .as("no file is excluded by the retired rule")
                .noneMatch(f ->
                        f.exclusionClass().filter("**/*.config.yaml"::equals).isPresent());
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
    void aSymlinkedAssetManifestThatLeavesThePackIsStillOnlyReadAsAFile(@TempDir Path dir) throws IOException {
        Path outside = Files.createDirectories(dir.resolve("outside"));
        Files.writeString(outside.resolve("m.asset.yaml"), ASSET.formatted("m", "image/png", "m.png"));
        Path pack = Files.createDirectories(dir.resolve("pack/assets"));
        Files.createSymbolicLink(pack.resolve("m.asset.yaml"), outside.resolve("m.asset.yaml"));

        assertThat(INSPECTOR.inspect(root(dir.resolve("pack")), Scope.FULL).assets())
                .extracting(PackContents.AssetFacts::id)
                .containsExactly("m");
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

        assertThat(INSPECTOR.inspect(root(dir), Scope.LOAD_PATH).shards())
                .extracting(FileRead::file)
                .containsExactly("real.graph.yaml");
    }

    @Test
    void anInspectedRootThatCannotBeListedIsRefused(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("a-file");
        Files.writeString(file, "x");

        assertThatThrownBy(() -> INSPECTOR.inspect(root(file), Scope.LOAD_PATH))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.getMessage()).isEqualTo("pack qa is not a directory"));
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

        List<PackFinding> findings = PackPolicy.judge(full(dir)).findings();
        WholePackStore.LoadedPack loaded = new WholePackStore(new YamlSerializationStrategy()).load(dir);

        // The org-level root also finds its missing fragment; only the preset findings are this test's.
        assertThat(findings)
                .filteredOn(finding -> finding.file().startsWith(PresetFiles.PRESETS))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR);
                    assertThat(finding.file()).isEqualTo("presets/other.yaml");
                    assertThat(finding.detail())
                            .endsWith("presets/other.yaml: 'id' has an unsupported value 'minimal'");
                });
        assertThat(loaded.pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo("presets/other.yaml");
            assertThat(violation.reason()).isEqualTo("preset id 'minimal' is not its file stem 'other'");
        });
        assertThat(loaded.pack().presets()).extracting(Preset::id).containsExactly("minimal");
    }

    // ---- step enhancements and prompt fragments in validate mode (FR-004 to FR-006) ------------------------------

    private static List<PackFinding> promptFragmentFindings(Path dir) {
        return PackPolicy.judge(full(dir)).findings().stream()
                .filter(finding -> finding.code() == PackFindingCode.PROMPT_FRAGMENT_INVALID)
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
                .satisfies(finding -> assertThat(finding.file()).isEqualTo(fragment("spdd-specify")));
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
                .allSatisfy(finding -> assertThat(finding.file()).isEqualTo(fragment("spdd-specify")));

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
                .allSatisfy(finding -> assertThat(finding.file()).isEqualTo(fragment("spdd-specify")));
    }

    @Test
    void aNamedCompanionThatIsMissingIsPromptFragmentInvalidAtTheYaml(@TempDir Path dir) throws IOException {
        write(dir, ENHANCEMENT_GRAPH, SPDD_EDGE);
        write(dir, yaml("spdd-specify"), SPDD_ENHANCEMENT);

        assertThat(promptFragmentFindings(dir))
                .singleElement()
                .satisfies(finding -> assertThat(finding.file()).isEqualTo(yaml("spdd-specify")));
    }

    @Test
    void anOrphanFragmentBesideAValidPairIsPromptFragmentInvalidAtTheOrphan(@TempDir Path dir) throws IOException {
        validPair(dir);
        write(dir, fragment("stray"), "## Stray\n\nNobody names it.\n");

        assertThat(promptFragmentFindings(dir))
                .singleElement()
                .satisfies(finding -> assertThat(finding.file()).isEqualTo(fragment("stray")));
    }

    @Test
    void anOrphanBesideAnEnhancementWithARuleFaultIsJudgedBesideTheFault(@TempDir Path dir) throws IOException {
        // The enhancement is held, with a rule fault (no enhances edge), so it excuses no directory (fifth analysis
        // A5).
        write(dir, yaml("spdd-specify"), SPDD_ENHANCEMENT);
        write(dir, fragment("spdd-specify"), "## REASONS guidance\n\nState the reasons.\n");
        write(dir, fragment("stray"), "## Stray\n\nNobody names it.\n");

        assertThat(promptFragmentFindings(dir))
                .extracting(PackFinding::file)
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
                .satisfies(finding -> assertThat(finding.file()).isEqualTo(yaml("spdd-specify")));
    }

    @Test
    void aFragmentBesideAnEnhancementThatBreaksItsSchemaIsNoOrphan(@TempDir Path dir) throws IOException {
        write(dir, yaml("spdd-specify"), "schema_version: '1.0'\nid: spdd-specify\nprompt_fragments: []\n");
        write(dir, fragment("stray"), "## Stray\n\nMaybe it was meant.\n");

        assertThat(promptFragmentFindings(dir)).isEmpty();
        assertThat(PackPolicy.judge(full(dir)).findings()).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR);
            assertThat(finding.file()).isEqualTo(yaml("spdd-specify"));
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
                .satisfies(finding -> assertThat(finding.file()).isEqualTo(yaml("spdd-specify")));
    }

    @Test
    void anEnhancesEdgeToANonStepAndAnAbsentEdgeAndAMismatchedDirectoryAreEachPromptFragmentInvalidAtTheYaml(
            @TempDir Path dir) throws IOException {
        validPair(dir);
        String edge = SPDD_EDGE.replace("base::action:software-dev/specify", "%s");

        write(dir, ENHANCEMENT_GRAPH, edge.formatted("base::tactic:x"));
        assertThat(promptFragmentFindings(dir)).singleElement().satisfies(finding -> {
            assertThat(finding.file()).isEqualTo(yaml("spdd-specify"));
            assertThat(finding.detail()).contains("not an action:<type>/<step>");
        });

        write(dir, ENHANCEMENT_GRAPH, edge.formatted("base::action:research/specify"));
        assertThat(promptFragmentFindings(dir)).singleElement().satisfies(finding -> {
            assertThat(finding.file()).isEqualTo(yaml("spdd-specify"));
            assertThat(finding.detail()).contains("research");
        });

        write(dir, ENHANCEMENT_GRAPH, "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges: []\n");
        assertThat(promptFragmentFindings(dir)).singleElement().satisfies(finding -> {
            assertThat(finding.file()).isEqualTo(yaml("spdd-specify"));
            assertThat(finding.detail()).contains("0 enhances edges");
        });
    }

    @Test
    void aGraphDocumentThatCannotBeReadIsRefusedOnItsOwnAndJudgesNoEdgeRule(@TempDir Path dir) throws IOException {
        write(dir, yaml("spdd-specify"), SPDD_ENHANCEMENT);
        write(dir, fragment("spdd-specify"), "## REASONS guidance\n\nState the reasons.\n");
        write(dir, ENHANCEMENT_GRAPH, "nodes: [\n");

        assertThat(promptFragmentFindings(dir)).isEmpty();
        assertThat(PackPolicy.judge(full(dir)).findings())
                .anySatisfy(finding -> assertThat(finding.code()).isEqualTo(PackFindingCode.DRG_SCHEMA_ERROR));
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

        List<PackFinding> findings = PackPolicy.judge(full(dir)).findings();

        assertThat(findings)
                .filteredOn(finding -> finding.file().endsWith(".step-enhancement.yaml"))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(PackFindingCode.PROMPT_FRAGMENT_INVALID);
                    assertThat(finding.file()).isEqualTo(software + ".step-enhancement.yaml");
                });
        assertThat(findings).extracting(PackFinding::code).doesNotContain(PackFindingCode.DUPLICATE_ARTIFACT_ID);
    }

    @Test
    void theGenericDuplicateIdRuleStillFiresForTwoTacticsOfOneId(@TempDir Path dir) throws IOException {
        String tactic = "schema_version: '1.0'\nid: same\nname: Same\nsteps:\n  - title: Only step\n";
        write(dir, "tactics/same.tactic.yaml", tactic);
        write(dir, "more/same.tactic.yaml", tactic);

        assertThat(PackPolicy.judge(full(dir)).findings())
                .filteredOn(finding -> finding.code() == PackFindingCode.DUPLICATE_ARTIFACT_ID)
                .singleElement()
                .satisfies(finding -> assertThat(finding.file()).isEqualTo("tactics/same.tactic.yaml"));
    }

    @Test
    void anIdThatDiffersFromItsFileStemIsASchemaViolationAtTheIdAndNeverPromptFragmentInvalid(@TempDir Path dir)
            throws IOException {
        validPair(dir);
        write(dir, yaml("spdd-specify"), SPDD_ENHANCEMENT.replace("id: spdd-specify", "id: spdd-other"));

        assertThat(promptFragmentFindings(dir)).isEmpty();
        assertThat(PackPolicy.judge(full(dir)).findings())
                .filteredOn(finding -> finding.file().equals(yaml("spdd-specify")))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR);
                    assertThat(finding.place()).containsExactly("id");
                });
    }

    @Test
    void anEmptyPromptFragmentsListIsASchemaViolationAndAnAbsentKeyIsPromptFragmentInvalid(@TempDir Path dir)
            throws IOException {
        write(dir, ENHANCEMENT_GRAPH, SPDD_EDGE);

        write(dir, yaml("spdd-specify"), "schema_version: '1.0'\nid: spdd-specify\nprompt_fragments: []\n");
        assertThat(promptFragmentFindings(dir)).isEmpty();
        assertThat(PackPolicy.judge(full(dir)).findings()).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR);
            assertThat(finding.file()).isEqualTo(yaml("spdd-specify"));
        });

        write(dir, yaml("spdd-specify"), "schema_version: '1.0'\nid: spdd-specify\n");
        assertThat(promptFragmentFindings(dir))
                .singleElement()
                .satisfies(finding -> assertThat(finding.file()).isEqualTo(yaml("spdd-specify")));
    }

    @Test
    void aSlotThatBreaksTheGrammarIsASchemaViolationAndARepeatedSlotIsPromptFragmentInvalid(@TempDir Path dir)
            throws IOException {
        validPair(dir);
        write(dir, fragment("second"), "## Second\n\nAnother.\n");

        write(dir, yaml("spdd-specify"), SPDD_ENHANCEMENT + "  - slot: Bad:Id\n    path: second.prompt-fragment.md\n");
        assertThat(promptFragmentFindings(dir)).isEmpty();
        assertThat(PackPolicy.judge(full(dir)).findings()).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR);
            assertThat(finding.file()).isEqualTo(yaml("spdd-specify"));
        });

        write(
                dir,
                yaml("spdd-specify"),
                SPDD_ENHANCEMENT + "  - slot: spdd:reasons-block\n    path: second.prompt-fragment.md\n");
        assertThat(promptFragmentFindings(dir))
                .singleElement()
                .satisfies(finding -> assertThat(finding.file()).isEqualTo(yaml("spdd-specify")));
    }

    @Test
    void aFragmentThatBreaksTheCodecAndHoldsAMarkerAnswersTheCodecFaultOnly(@TempDir Path dir) throws IOException {
        validPair(dir);
        write(dir, fragment("spdd-specify"), "## Guidance\r\n\r\n<!-- other:slot:start -->\r\n");

        assertThat(promptFragmentFindings(dir)).singleElement().satisfies(finding -> {
            assertThat(finding.file()).isEqualTo(fragment("spdd-specify"));
            assertThat(finding.detail()).contains("not valid Markdown");
        });
    }
}
