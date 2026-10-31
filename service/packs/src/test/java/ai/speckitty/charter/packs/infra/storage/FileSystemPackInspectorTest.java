package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
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
        write(dir, "pack.yaml", "pack_id: 01ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");
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
        write(dir, "pack.yaml", "pack_id: 01ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");
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
    void theFullScopeReadsThePackFilesOfTheLoadPathAsTheLoadPathDoes(@TempDir Path dir) throws IOException {
        write(dir, "pack.yaml", "pack_id: 01ABC\npack_version: 1.0.0\nname: qa\npack_level: org\nmystery: 1\n");
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
        write(dir, "pack.yaml", "pack_id: 01ABC\npack_version: 1.0.0\nname: qa\n");
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

    @Test
    void aSchemaLessPayloadIsListedWithItsKindAndNotReadWhileItsSidecarsFactsStand(@TempDir Path dir)
            throws IOException {
        write(dir, "assets/x.md.asset.yaml", ASSET.formatted("x", "text/markdown", "x.md"));
        write(dir, "assets/x.md", "# a payload\n");
        // No payload is YAML a schema would pass: were one read and validated, it would be unreadable. Validate mode
        // walks *.yaml files only, so the two Markdown payloads are not listed at all.
        write(dir, "assets/tools/x.config.yaml", "rules: [\n");
        write(dir, "templates/writing/t.md", "---\ntitle: [\n---\n");
        write(dir, "templates/writing/t.yaml", "a: [\n");

        PackContents contents = full(dir);

        assertThat(contents.unreadableFiles()).isEmpty();
        assertThat(contents.files())
                .containsExactly(
                        classified("assets/tools/x.config.yaml", "asset-payload"),
                        classified("assets/x.md.asset.yaml", "asset"),
                        classified("templates/writing/t.yaml", "template"));
        assertThat(contents.assets())
                .as("the sidecar is read and judged as it is without payload rules")
                .containsExactly(
                        new PackContents.AssetFacts("assets/x.md.asset.yaml", "x", "x.md", "text/markdown", true));
        assertThat(contents.artifacts())
                .containsExactly(new PackContents.ArtifactFacts("assets/x.md.asset.yaml", "asset", "x"));
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

        // #195: main at 90de8f9 found nothing. The pinned presets and manifest are now schema-mapped, and their shape
        // is not the sanitized one (ADR-0042): each is reported, as an artifact schema error (ADR-0036 decision 3),
        // until
        // open-packs fixes them (OP-1, OP-2). Every other file still has no finding.
        assertThat(verdict.findings())
                .allSatisfy(f -> assertThat(f.code()).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR))
                .extracting(PackFinding::file)
                .containsOnly("pack-manifest.yaml", "presets/default.yaml", "presets/minimal.yaml");
        assertThat(verdict.findings())
                .filteredOn(f -> f.place().isEmpty() && !f.detail().contains("provenance_path"))
                .extracting(PackFinding::detail)
                .containsExactlyInAnyOrder(
                        "built-in/pack-manifest.yaml: unknown key 'charter'",
                        "built-in/pack-manifest.yaml: unknown key 'fetched_at'",
                        "built-in/pack-manifest.yaml: unknown key 'generated_at'",
                        "built-in/pack-manifest.yaml: unknown key 'generated_by'",
                        "built-in/pack-manifest.yaml: unknown key 'source_type'",
                        "built-in/pack-manifest.yaml: unknown key 'source_url'",
                        "built-in/presets/default.yaml: missing 'id'",
                        "built-in/presets/default.yaml: missing 'schema_version'",
                        "built-in/presets/default.yaml: unknown key 'mission_type_activations'",
                        "built-in/presets/default.yaml: unknown key 'name'",
                        "built-in/presets/minimal.yaml: missing 'id'",
                        "built-in/presets/minimal.yaml: missing 'schema_version'",
                        "built-in/presets/minimal.yaml: unknown key 'mission_type_activations'",
                        "built-in/presets/minimal.yaml: unknown key 'name'");
        assertThat(verdict.findings())
                .filteredOn(
                        f -> f.file().equals("pack-manifest.yaml") && !f.place().isEmpty())
                .as("schema_version '1', provenance_path on every constituent, and name on every preset entry")
                .extracting(PackFinding::detail)
                .containsOnly(
                        "built-in/pack-manifest.yaml: 'schema_version' has an unsupported value '1'",
                        "built-in/pack-manifest.yaml: unknown key 'provenance_path'",
                        "built-in/pack-manifest.yaml: unknown key 'name'",
                        "built-in/pack-manifest.yaml: missing 'id'");
        assertThat(verdict.findings())
                .filteredOn(f ->
                        f.file().equals("presets/minimal.yaml") && !f.place().isEmpty())
                .as("minimal.yaml's items are bare strings, not {id} objects")
                .extracting(PackFinding::detail)
                .containsExactlyInAnyOrder(
                        "built-in/presets/minimal.yaml: '/activated_directives/0' breaks schema keyword 'type'",
                        "built-in/presets/minimal.yaml: '/activated_directives/1' breaks schema keyword 'type'",
                        "built-in/presets/minimal.yaml: '/activated_directives/2' breaks schema keyword 'type'",
                        "built-in/presets/minimal.yaml: '/activated_directives/3' breaks schema keyword 'type'",
                        "built-in/presets/minimal.yaml: '/activated_directives/4' breaks schema keyword 'type'",
                        "built-in/presets/minimal.yaml: '/activated_tactics/0' breaks schema keyword 'type'");
        assertThat(verdict.findings())
                .filteredOn(f -> f.detail().endsWith("unknown key 'provenance_path'"))
                .as("one per constituent of the pinned manifest")
                .hasSize(272);
        assertThat(verdict.findings())
                .as("283 for the manifest (272 provenance_path, six root keys, schema_version, two preset entries keyed"
                        + " name without id), 10 for minimal.yaml, 4 for default.yaml")
                .hasSize(297);
        // 386 at the previous pin, 5df92a5d; the mission-type migration (#194, open-packs#95) retired 74 YAML files of
        // the pack and added its 4 mission-type files.
        assertThat(verdict.files()).hasSize(386 - 74 + 4);
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
        write(dir, "pack.yaml", "pack_id: 01ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n");
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
}
