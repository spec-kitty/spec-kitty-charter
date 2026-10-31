package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Classification of pack-relative paths against the bundled {@code file-kinds.yaml}. */
class PackFileKindTest {

    private static final PackFileKind KINDS = PackFileKind.bundled();

    /** One sample path for each {@code files} rule, with the schema it must map to. */
    private static final Map<String, String> MAPPED = Map.ofEntries(
            Map.entry("core/pack.yaml", "pack-descriptor"),
            Map.entry("core/graph/a.graph.yaml", "graph-shard"),
            Map.entry("org/drg/fragment.yaml", "org-fragment"),
            Map.entry("org/org-charter.yaml", "org-charter"),
            Map.entry("core/directives/a.directive.yaml", "directive"),
            Map.entry("core/tactics/a.tactic.yaml", "tactic"),
            Map.entry("core/styleguides/a.styleguide.yaml", "styleguide"),
            Map.entry("core/toolguides/a.toolguide.yaml", "toolguide"),
            Map.entry("core/toolguides/a.guide.md", "toolguide-guide"),
            Map.entry("core/missions/plan/plan.mission-type.yaml", "mission-type"),
            Map.entry("core/missions/plan/steps/specify.prompt.md", "mission-step-prompt"),
            Map.entry("core/missions/plan/steps/specify.guidelines.md", "mission-step-guidelines"),
            Map.entry("core/paradigms/a.paradigm.yaml", "paradigm"),
            Map.entry("core/procedures/a.procedure.yaml", "procedure"),
            Map.entry("core/agent-profiles/a.agent.yaml", "agent-profile"),
            Map.entry("core/step-contracts/a.step-contract.yaml", "mission-step-contract"),
            Map.entry("core/assets/a.asset.yaml", "asset"),
            Map.entry("core/glossary/a.glossary-pack.yaml", "glossary-pack"),
            Map.entry("core/skills/a.skill.yaml", "skill"),
            Map.entry("project/charter.yaml", "charter"),
            Map.entry("core/presets/minimal.yaml", "preset"),
            Map.entry("core/pack-manifest.yaml", "pack-manifest"),
            Map.entry("core/pack.md", "pack-document"));

    /** One sample path for each exclusion rule. */
    private static final List<String> EXCLUDED = List.of(
            "built-in.source.yaml",
            "core/missions/software-dev/templates/plan-field-declaration.yaml",
            "built-in/assets/README.md",
            "built-in/assets/audiences/README.md",
            "built-in/assets/.DS_Store",
            "internal/assets/debrief/fonts/.gitkeep",
            "built-in/templates/.pack-write-123.tmp",
            "built-in/templates/writing/.gitkeep",
            "built-in/missions/plan/templates/.gitkeep",
            "built-in/missions/README.md",
            "built-in/missions/plan/.pack-write-1.tmp",
            "built-in/missions/plan/steps/.pack-write-2.tmp",
            "built-in/missions/plan/README.md",
            "built-in/missions/documentation/templates/README.md");

    /** Schema-less payload samples, nested on purpose (a trailing `**` would match one segment only), with their kind. */
    private static final Map<String, String> PAYLOADS = Map.ofEntries(
            Map.entry("built-in/assets/audiences/line_manager.md", "asset-payload"),
            Map.entry("internal/assets/debrief/fonts/FallingSky-Bold.otf", "asset-payload"),
            Map.entry("qa/assets/qa_carrier_lint.py", "asset-payload"),
            Map.entry("built-in/assets/docs_structural_lint.config.yaml", "asset-payload"),
            Map.entry("built-in/templates/writing/meeting-minutes.md", "template"),
            Map.entry("built-in/missions/documentation/templates/divio/tutorial-template.md", "template"),
            Map.entry("qa/missions/qa/templates/test-report-template.md", "template"));

    /** A table text whose only rules are {@code %s}, written as YAML flow mappings. */
    private static final String TABLE = "node_kinds: []\nfragment_kinds: {}\n%s\n";

    @Test
    void everyFilesRuleMapsItsSamplePathToItsSchema() {
        Map<String, String> byPath = new LinkedHashMap<>();
        MAPPED.forEach((path, schema) -> {
            PackFileKind.Classification c = KINDS.classify(path);
            assertThat(c).as(path).isInstanceOf(PackFileKind.Mapped.class);
            PackFileKind.Mapped mapped = (PackFileKind.Mapped) c;
            assertThat(mapped.kind()).as(path).isEqualTo(schema);
            assertThat(mapped.schema()).as(path).contains("packs/schemas/" + schema + ".schema.json");
            assertThat(mapped.contentForm())
                    .as(path)
                    .isEqualTo(path.endsWith(".md") ? ContentForm.MARKDOWN : ContentForm.YAML);
            byPath.put(path, mapped.kind());
        });
        assertThat(KINDS.fileRules())
                .extracting(PackFileKind.FileRule::schema)
                .containsExactlyInAnyOrderElementsOf(byPath.values());
    }

    @Test
    void everyExclusionClassMatchesItsSamplePath() {
        Set<String> classes = new LinkedHashSet<>();
        for (String path : EXCLUDED) {
            PackFileKind.Classification c = KINDS.classify(path);
            assertThat(c).as(path).isInstanceOf(PackFileKind.Excluded.class);
            PackFileKind.Excluded excluded = (PackFileKind.Excluded) c;
            assertThat(excluded.reason()).as(path).isNotBlank();
            classes.add(excluded.exclusionClass());
        }
        assertThat(classes)
                .containsExactlyInAnyOrderElementsOf(KINDS.exclusionRules().stream()
                        .map(PackFileKind.ExclusionRule::match)
                        .toList());
    }

    @Test
    void aSchemaLessPayloadIsMappedToItsKindWithNoSchemaAsBytes() {
        PAYLOADS.forEach((path, kind) -> assertThat(KINDS.classify(path))
                .as(path)
                .isEqualTo(new PackFileKind.Mapped(kind, Optional.empty(), ContentForm.BYTES)));
        assertThat(KINDS.payloadRules())
                .containsExactly(
                        new PackFileKind.PayloadRule("*/assets/**/*", "asset-payload", ContentForm.BYTES),
                        new PackFileKind.PayloadRule("*/templates/**/*", "template", ContentForm.BYTES),
                        new PackFileKind.PayloadRule("*/missions/*/templates/**/*", "template", ContentForm.BYTES));
    }

    @Test
    void aSidecarUnderAssetsIsMappedByItsFilesRuleBeforeThePayloadRule() {
        assertThat(KINDS.classify("built-in/assets/audiences/line_manager.md.asset.yaml"))
                .isEqualTo(new PackFileKind.Mapped(
                        "asset", Optional.of("packs/schemas/asset.schema.json"), ContentForm.YAML));
        assertThat(KINDS.matchingPayloadRules("built-in/assets/audiences/line_manager.md.asset.yaml"))
                .as("the overlap with the payload rule is by design; the files rule is consulted first")
                .containsExactly("*/assets/**/*");
    }

    @Test
    void noSamplePathIsMatchedByTwoPayloadRules() {
        List<String> all = new ArrayList<>(PAYLOADS.keySet());
        all.addAll(MAPPED.keySet());
        all.addAll(EXCLUDED);
        for (String path : all) {
            assertThat(KINDS.matchingPayloadRules(path)).as(path).hasSizeLessThanOrEqualTo(1);
        }
        PAYLOADS.keySet()
                .forEach(path ->
                        assertThat(KINDS.matchingPayloadRules(path)).as(path).hasSize(1));
    }

    @Test
    void thePayloadGlobsSpanNestedFilesAndTheTemplateRulesCoverTopLevelAndMissionTypeTemplates() {
        assertThat(KINDS.matchingPayloadRules("x/assets/a/b/c/d.bin"))
                .as("depth 3 under assets/")
                .containsExactly("*/assets/**/*");
        assertThat(KINDS.matchingPayloadRules("x/missions/m/templates/a.md"))
                .as("a mission type's template (#194, ADR-0044)")
                .containsExactly("*/missions/*/templates/**/*");
        assertThat(KINDS.matchingPayloadRules("x/missions/templates/a.md")).isEmpty();
        assertThat(KINDS.matchingPayloadRules("x/templates/a.md")).containsExactly("*/templates/**/*");
    }

    /**
     * F5: a hidden file under a pack's {@code assets/} or top-level {@code templates/} is excluded, not a payload,
     * while the same name without its leading dot, beside it, is one; a mission type's templates follow the same rule.
     */
    @Test
    void aHiddenFileUnderAssetsOrTemplatesIsExcludedWhileItsUnhiddenTwinIsAPayload() {
        assertThat(KINDS.classify("qa/assets/a/.DS_Store"))
                .isEqualTo(new PackFileKind.Excluded(
                        "*/assets/**/.*",
                        KINDS.exclusionRules().stream()
                                .filter(r -> r.match().equals("*/assets/**/.*"))
                                .findFirst()
                                .orElseThrow()
                                .reason()));
        assertThat(KINDS.classify("qa/assets/a/DS_Store"))
                .isEqualTo(new PackFileKind.Mapped("asset-payload", Optional.empty(), ContentForm.BYTES));
        assertThat(KINDS.classify("qa/templates/.gitkeep")).isInstanceOf(PackFileKind.Excluded.class);
        assertThat(KINDS.classify("qa/templates/gitkeep"))
                .isEqualTo(new PackFileKind.Mapped("template", Optional.empty(), ContentForm.BYTES));
        assertThat(KINDS.classify("built-in/missions/plan/templates/.gitkeep"))
                .isInstanceOf(PackFileKind.Excluded.class);
        assertThat(KINDS.classify("built-in/missions/plan/templates/gitkeep"))
                .isEqualTo(new PackFileKind.Mapped("template", Optional.empty(), ContentForm.BYTES));
    }

    @ParameterizedTest
    @CsvSource({
        "foo/bar.yaml",
        "core/notes.yaml",
        "core/a.unknown.yaml",
        "pack.yaml",
        "core/deep/er/pack.yaml",
        "core/docs/pack.md",
        "built-in/missions/templates/spec-template.md"
    })
    void anUnknownFileIsUnclassified(String path) {
        assertThat(KINDS.classify(path)).isEqualTo(PackFileKind.Unclassified.INSTANCE);
    }

    @Test
    void noSamplePathIsMatchedByTwoRules() {
        List<String> all = new java.util.ArrayList<>(MAPPED.keySet());
        all.addAll(EXCLUDED);
        for (String path : all) {
            assertThat(KINDS.matchingRules(path)).as(path).hasSize(1);
        }
    }

    @Test
    void aSingleStarStaysInOneSegmentAndDoubleStarSpansSegments() {
        assertThat(KINDS.classify("a/b/pack.yaml")).isEqualTo(PackFileKind.Unclassified.INSTANCE);
        assertThat(KINDS.classify("x.graph.yaml")).isInstanceOf(PackFileKind.Mapped.class);
        assertThat(KINDS.classify("a/b/c/d.graph.yaml")).isInstanceOf(PackFileKind.Mapped.class);
        assertThat(KINDS.classify("a/xdirective.yaml")).isEqualTo(PackFileKind.Unclassified.INSTANCE);
    }

    @Test
    void theVocabularyListsAreExposed() {
        assertThat(KINDS.nodeKinds()).hasSize(15).contains("directive", "mission_type", "action");
        assertThat(KINDS.fragmentKinds())
                .hasSize(15)
                .containsEntry("directives", "directive")
                .containsEntry("mission_types", "mission_type");
        assertThat(KINDS.fileRules()).hasSize(23);
        assertThat(KINDS.fileRules())
                .as("every files rule keeps the default form but the Markdown ones: the companions' (#194) and"
                        + " pack.md's (#260)")
                .filteredOn(rule -> !Set.of(
                                "toolguide-guide", "pack-document", "mission-step-prompt", "mission-step-guidelines")
                        .contains(rule.schema()))
                .extracting(PackFileKind.FileRule::contentForm)
                .containsOnly(ContentForm.YAML);
    }

    // ---- table load --------------------------------------------------------------------------------

    @Test
    void aPayloadRuleThatNamesABuiltFormLoads() {
        PackFileKind table = new PackFileKind(
                TABLE.formatted(
                        "files: []\nexclusions: []\npayloads: [{match: '*/assets/**/*', kind: asset-payload, content_form: bytes}]"));

        assertThat(table.classify("p/assets/a.bin"))
                .isEqualTo(new PackFileKind.Mapped("asset-payload", Optional.empty(), ContentForm.BYTES));
    }

    /**
     * F1: the store holds a {@code bytes} file only as an asset file or a template, so a {@code bytes} rule of any other
     * kind is refused when the table loads, naming the rule; the same rule with a kind the store holds loads.
     */
    @Test
    void aBytesRuleOfAKindTheStoreDoesNotHoldFailsToLoadNamingTheRule() {
        String rule = "files: []\nexclusions: []\npayloads: [{match: '*/blobs/**/*', kind: %s, content_form: bytes}]";

        assertThatThrownBy(() -> new PackFileKind(TABLE.formatted(rule.formatted("blob"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("packs/file-kinds.yaml: the payloads rule for '*/blobs/**/*' declares content_form 'bytes'"
                        + " for kind 'blob', which the store does not hold; a bytes file is one of"
                        + " [asset-payload, template]");
        assertThat(new PackFileKind(TABLE.formatted(rule.formatted("template"))).classify("p/blobs/a.bin"))
                .isEqualTo(new PackFileKind.Mapped("template", Optional.empty(), ContentForm.BYTES));
        assertThat(PackFileKind.TEMPLATE).isEqualTo(ArtifactKind.TEMPLATE.token());
        assertThat(PackFileKind.BYTES_KINDS)
                .as("every bundled bytes rule names a kind the store holds")
                .containsAll(KINDS.payloadRules().stream()
                        .filter(r -> r.contentForm() == ContentForm.BYTES)
                        .map(PackFileKind.PayloadRule::kind)
                        .toList());
    }

    /**
     * #193: the store holds a schema-less file only as {@code bytes}, so a {@code payloads} rule declaring another form
     * would hold its files nowhere; it is refused when the table loads, naming the rule and the form, and the same rule
     * declaring {@code bytes} loads.
     */
    @Test
    void aPayloadsRuleThatDeclaresAFormOtherThanBytesFailsToLoadNamingTheRuleAndForm() {
        String rule =
                "files: []\nexclusions: []\npayloads: [{match: '*/assets/**/*', kind: asset-payload, content_form: %s}]";

        assertThatThrownBy(() -> new PackFileKind(TABLE.formatted(rule.formatted("yaml"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("packs/file-kinds.yaml: the payloads rule for '*/assets/**/*' declares content_form 'yaml';"
                        + " a payloads rule has no schema and its files are bytes");
        assertThat(new PackFileKind(TABLE.formatted(rule.formatted("bytes"))).classify("p/assets/a.bin"))
                .isEqualTo(new PackFileKind.Mapped("asset-payload", Optional.empty(), ContentForm.BYTES));
    }

    @Test
    void aRuleThatIsNotWellFormedFailsToLoadNamingTheProblem() {
        String payload = "files: []\nexclusions: []\npayloads: [{match: '*/assets/**/*', %s}]";
        Map<String, String> problems = Map.ofEntries(
                Map.entry(payload.formatted("kind: k, content_form: bytes, mime: x"), "unknown key 'mime'"),
                Map.entry(payload.formatted("content_form: bytes"), "non-blank 'kind'"),
                Map.entry(payload.formatted("kind: k"), "non-blank 'content_form'"),
                Map.entry(payload.formatted("kind: k, content_form: html"), "unknown content_form 'html'"),
                Map.entry(
                        payload.formatted("kind: k, content_form: markdown"),
                        "declares content_form 'markdown'; a payloads rule has no schema and its files are bytes"),
                Map.entry(
                        payload.formatted("kind: k, content_form: text"),
                        "content_form 'text' is declared but not built yet"),
                Map.entry(
                        "files: [{match: a, schema: s, mode: x}]\nexclusions: []",
                        "a rule in files has the unknown key 'mode'"),
                Map.entry(
                        "files: []\nexclusions: [{match: a, reason: r, note: x}]",
                        "a rule in exclusions has the unknown key 'note'"),
                Map.entry(
                        "files: [{match: a, schema: s, content_form: text}]\nexclusions: []",
                        "content_form 'text' is declared but not built yet (the mission that first needs it builds it)"),
                Map.entry(
                        "files: [{match: a, schema: s, content_form: markdown}]\nexclusions: []",
                        "the files rule for 'a' declares content_form 'markdown' with schema 's', which does not load"));

        problems.forEach((rules, problem) -> assertThatThrownBy(() -> new PackFileKind(TABLE.formatted(rules)))
                .as(rules)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("packs/file-kinds.yaml")
                .hasMessageContaining(problem));
    }

    /**
     * F1: a {@code files} rule has a schema, which validates a parsed YAML document, so one declaring another form is
     * refused when the table loads, naming the rule; the same rule declaring {@code yaml} loads.
     */
    @Test
    void aFilesRuleThatDeclaresAFormOtherThanYamlFailsToLoadAndATableWithoutPayloadsHasNone() {
        String rule = "files: [{match: '*/a.bin', schema: s, content_form: %s}]\nexclusions: []";

        assertThatThrownBy(() -> new PackFileKind(TABLE.formatted(rule.formatted("bytes"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("packs/file-kinds.yaml: the files rule for '*/a.bin' declares content_form 'bytes'; a"
                        + " files rule has a schema and its files are yaml or markdown");
        PackFileKind table = new PackFileKind(TABLE.formatted(rule.formatted("yaml")));
        assertThat(table.classify("p/a.bin"))
                .isEqualTo(new PackFileKind.Mapped("s", Optional.of("packs/schemas/s.schema.json"), ContentForm.YAML));
        assertThat(table.payloadRules()).isEmpty();
    }

    /**
     * #195: a kind file under {@code presets/} matches both its kind's rule and the preset rule. The kind's rule comes
     * first in the table, and the first match wins, so it stays a file of its kind; a plain preset stays a preset.
     */
    @Test
    void aKindFileUnderPresetsStaysAFileOfItsKind() {
        assertThat(KINDS.classify("core/presets/a.tactic.yaml"))
                .isInstanceOfSatisfying(
                        PackFileKind.Mapped.class,
                        mapped -> assertThat(mapped.kind()).isEqualTo("tactic"));
        assertThat(KINDS.classify("core/presets/a.yaml"))
                .isInstanceOfSatisfying(
                        PackFileKind.Mapped.class,
                        mapped -> assertThat(mapped.kind()).isEqualTo("preset"));
    }

    // ---- the markdown form (#194) ----------------------------------------------------------------------

    /** The directory of the test-only structure schemas a {@code markdown} rule names here. */
    private static final String STRUCTURE_SCHEMAS = "markdown-rule-fixtures/";

    /**
     * FR-005: {@code markdown} is built, so a {@code files} rule may declare it with a structure schema; the file maps
     * to that schema and the {@code markdown} form. {@code text} stays refused, on a {@code files} and a
     * {@code payloads} rule alike.
     */
    @Test
    void aMarkdownFilesRuleLoadsAndTextIsStillRefused() {
        String rule = "files: [{match: '*/notes/*.md', schema: notes, content_form: %s}]\nexclusions: []";

        PackFileKind table = new PackFileKind(TABLE.formatted(rule.formatted("markdown")), STRUCTURE_SCHEMAS);

        assertThat(table.classify("p/notes/a.md"))
                .isEqualTo(new PackFileKind.Mapped(
                        "notes", Optional.of(STRUCTURE_SCHEMAS + "notes.schema.json"), ContentForm.MARKDOWN));
        assertThat(table.fileRules())
                .singleElement()
                .isEqualTo(new PackFileKind.FileRule("*/notes/*.md", "notes", ContentForm.MARKDOWN));
        assertThatThrownBy(() -> new PackFileKind(TABLE.formatted(rule.formatted("text")), STRUCTURE_SCHEMAS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("packs/file-kinds.yaml: content_form 'text' is declared but not built yet (the mission"
                        + " that first needs it builds it); the rule for '*/notes/*.md' cannot be read");
    }

    /**
     * A {@code markdown} rule's structure schema must load as strictly as the bundled ones; one that is missing or uses
     * a keyword outside 2020-12 is refused when the table loads, naming the rule and the schema. The same rule with
     * {@code yaml} keeps today's behaviour: its schema is not loaded with the table.
     */
    @Test
    void aMarkdownRuleWhoseSchemaDoesNotLoadIsRefusedWhenTheTableLoads() {
        String rule = "files: [{match: '*/notes/*.md', schema: notes, content_form: %s}]\nexclusions: []";

        assertThatThrownBy(() -> new PackFileKind(TABLE.formatted(rule.formatted("markdown"))))
                .as("no notes schema among the bundled ones")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("packs/file-kinds.yaml: the files rule for '*/notes/*.md' declares content_form"
                        + " 'markdown' with schema 'notes', which does not load: notes.schema.json is missing");
        assertThatThrownBy(() -> new PackFileKind(
                        TABLE.formatted(rule.formatted("markdown")), STRUCTURE_SCHEMAS + "unloadable/"))
                .as("an x- keyword")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("packs/file-kinds.yaml: the files rule for '*/notes/*.md' declares content_form"
                        + " 'markdown' with schema 'notes', which does not load: notes.schema.json cannot be compiled");
        assertThat(new PackFileKind(TABLE.formatted(rule.formatted("yaml"))).classify("p/notes/a.md"))
                .isEqualTo(new PackFileKind.Mapped(
                        "notes", Optional.of("packs/schemas/notes.schema.json"), ContentForm.YAML));
    }

    /**
     * A {@code markdown} rule is a {@code files} rule, so it outranks every exclusion and payload rule: a rule such as
     * {@code **}{@code /*.md} would turn an excluded assets README and every Markdown template or asset into a
     * schema'd Markdown file. The table load checks no overlap, so this does: no bundled {@code markdown} rule (none at
     * the pin) may match a path an exclusion or a payload rule matches, over the sample paths here and every file of
     * the vendored packs. It fails as soon as an overlapping {@code markdown} rule is added (ADR-0040, 2026-10-29).
     */
    @Test
    void noBundledMarkdownRuleMatchesAPathAnExclusionOrAPayloadRuleMatches() throws IOException {
        assertThat(markdownOverlaps(KINDS, samplePaths())).isEmpty();
    }

    /** The guard above is not vacuous: an overlapping rule is caught, a disjoint one is not. */
    @Test
    void theMarkdownOverlapGuardCatchesAnOverlappingRuleAndPassesADisjointOne() throws IOException {
        List<String> samples = samplePaths();

        assertThat(markdownOverlaps(withMarkdownRule("**/*.md"), samples))
                .contains(
                        "**/*.md matches built-in/assets/README.md, excluded by */assets/**/README.md",
                        "**/*.md matches built-in/assets/audiences/line_manager.md, a payload of */assets/**/*",
                        "**/*.md matches built-in/templates/writing/meeting-minutes.md, a payload of */templates/**/*");
        assertThat(markdownOverlaps(withMarkdownRule("*/notes/*.md"), samples)).isEmpty();
    }

    /** Every sample exclusion and payload path, then every file of the vendored packs, relative to {@code packs/}. */
    private static List<String> samplePaths() throws IOException {
        List<String> paths = new ArrayList<>(EXCLUDED);
        paths.addAll(PAYLOADS.keySet());
        String root = System.getProperty("charter.repositoryRoot");
        assertThat(root).as("charter.repositoryRoot, set by the build").isNotNull();
        Path packs = Path.of(root, "packs");
        try (Stream<Path> walk = Files.walk(packs)) {
            walk.filter(Files::isRegularFile)
                    .map(p -> packs.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .forEach(paths::add);
        }
        return paths;
    }

    /** Each {@code markdown} rule and path it matches that an exclusion or a payload rule also matches. */
    private static List<String> markdownOverlaps(PackFileKind table, List<String> paths) {
        Set<String> markdownRules = new LinkedHashSet<>();
        Set<String> exclusionRules = new LinkedHashSet<>();
        table.fileRules().stream()
                .filter(rule -> rule.contentForm() == ContentForm.MARKDOWN)
                .forEach(rule -> markdownRules.add(rule.match()));
        table.exclusionRules().forEach(rule -> exclusionRules.add(rule.match()));
        List<String> overlaps = new ArrayList<>();
        for (String path : paths) {
            List<String> matching = table.matchingRules(path);
            for (String markdown : matching) {
                if (!markdownRules.contains(markdown)) {
                    continue;
                }
                matching.stream()
                        .filter(exclusionRules::contains)
                        .forEach(exclusion ->
                                overlaps.add(markdown + " matches " + path + ", excluded by " + exclusion));
                table.matchingPayloadRules(path)
                        .forEach(payload -> overlaps.add(markdown + " matches " + path + ", a payload of " + payload));
            }
        }
        return overlaps;
    }

    /** The bundled table with one {@code markdown} rule first among its {@code files} rules. */
    private static PackFileKind withMarkdownRule(String match) throws IOException {
        String table;
        try (InputStream in = PackFileKindTest.class.getClassLoader().getResourceAsStream(PackFileKind.RESOURCE)) {
            assertThat(in).isNotNull();
            table = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String files = "\nfiles:\n";
        assertThat(table).contains(files);
        String rule = "  - match: \"" + match + "\"\n    schema: notes\n    content_form: markdown\n";
        return new PackFileKind(table.replace(files, files + rule), STRUCTURE_SCHEMAS);
    }
}
