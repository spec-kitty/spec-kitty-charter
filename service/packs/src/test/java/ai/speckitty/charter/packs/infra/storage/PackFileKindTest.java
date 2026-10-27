package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
            Map.entry("core/paradigms/a.paradigm.yaml", "paradigm"),
            Map.entry("core/procedures/a.procedure.yaml", "procedure"),
            Map.entry("core/agent-profiles/a.agent.yaml", "agent-profile"),
            Map.entry("core/step-contracts/a.step-contract.yaml", "mission-step-contract"),
            Map.entry("core/assets/a.asset.yaml", "asset"),
            Map.entry("core/glossary/a.glossary-pack.yaml", "glossary-pack"),
            Map.entry("core/skills/a.skill.yaml", "skill"),
            Map.entry("project/charter.yaml", "charter"));

    /** One sample path for each exclusion rule. */
    private static final List<String> EXCLUDED = List.of(
            "core/missions/software-dev/mission-steps/specify/step.yaml",
            "core/actions/plan/index.yaml",
            "core/missions/software-dev/mission.yaml",
            "core/missions/software-dev/mission-runtime.yaml",
            "core/missions/software-dev/expected-artifacts.yaml",
            "core/missions/software-dev/governance-profile.yaml",
            "core/mission_types/software-dev.yaml",
            "core/presets/minimal.yaml",
            "core/pack-manifest.yaml",
            "built-in.source.yaml",
            "core/missions/software-dev/templates/plan-field-declaration.yaml",
            "built-in/assets/README.md",
            "built-in/assets/audiences/README.md",
            "built-in/assets/.DS_Store",
            "internal/assets/debrief/fonts/.gitkeep",
            "built-in/templates/.pack-write-123.tmp",
            "built-in/templates/writing/.gitkeep");

    /** Schema-less payload samples, nested on purpose (a trailing `**` would match one segment only), with their kind. */
    private static final Map<String, String> PAYLOADS = Map.ofEntries(
            Map.entry("built-in/assets/audiences/line_manager.md", "asset-payload"),
            Map.entry("internal/assets/debrief/fonts/FallingSky-Bold.otf", "asset-payload"),
            Map.entry("qa/assets/qa_carrier_lint.py", "asset-payload"),
            Map.entry("built-in/assets/docs_structural_lint.config.yaml", "asset-payload"),
            Map.entry("built-in/templates/writing/meeting-minutes.md", "template"));

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
            assertThat(mapped.contentForm()).as(path).isEqualTo(ContentForm.YAML);
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
                        new PackFileKind.PayloadRule("*/templates/**/*", "template", ContentForm.BYTES));
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
    void thePayloadGlobsSpanNestedFilesAndTheTemplatesRuleCoversOnlyAPacksTopLevelTemplates() {
        assertThat(KINDS.matchingPayloadRules("x/assets/a/b/c/d.bin"))
                .as("depth 3 under assets/")
                .containsExactly("*/assets/**/*");
        assertThat(KINDS.matchingPayloadRules("x/missions/m/templates/a.md")).isEmpty();
        assertThat(KINDS.matchingPayloadRules("x/templates/a.md")).containsExactly("*/templates/**/*");
    }

    /**
     * F5: a hidden file under a pack's {@code assets/} or top-level {@code templates/} is excluded, not a payload,
     * while the same name without its leading dot, beside it, is one; a hidden mission template stays unclassified.
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
                .isEqualTo(PackFileKind.Unclassified.INSTANCE);
    }

    @ParameterizedTest
    @CsvSource({
        "foo/bar.yaml",
        "core/notes.yaml",
        "core/a.unknown.yaml",
        "pack.yaml",
        "core/deep/er/pack.yaml",
        "qa/missions/qa/templates/test-report-template.md",
        "built-in/missions/software-dev/templates/spec-template.md"
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
        assertThat(KINDS.fileRules()).hasSize(16);
        assertThat(KINDS.fileRules())
                .as("every files rule keeps the default form")
                .extracting(PackFileKind.FileRule::contentForm)
                .containsOnly(ContentForm.YAML);
    }

    // ---- table load --------------------------------------------------------------------------------

    @Test
    void aPayloadRuleThatNamesABuiltFormLoads() {
        PackFileKind table = new PackFileKind(TABLE.formatted(
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
                        "content_form 'markdown' is declared but not built yet (the mission that first needs it builds it)"),
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
                        "files: [{match: a, schema: s, content_form: markdown}]\nexclusions: []",
                        "content_form 'markdown' is declared but not built yet"));

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
                        + " files rule has a schema and its files are yaml");
        PackFileKind table = new PackFileKind(TABLE.formatted(rule.formatted("yaml")));
        assertThat(table.classify("p/a.bin"))
                .isEqualTo(new PackFileKind.Mapped("s", Optional.of("packs/schemas/s.schema.json"), ContentForm.YAML));
        assertThat(table.payloadRules()).isEmpty();
    }
}
