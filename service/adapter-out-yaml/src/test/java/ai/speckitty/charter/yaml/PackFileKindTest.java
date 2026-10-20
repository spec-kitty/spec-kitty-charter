package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
            "core/skills/a.skill.yaml",
            "core/glossary/a.glossary-pack.yaml",
            "core/assets/lint.config.yaml",
            "core/pack-manifest.yaml",
            "built-in.source.yaml",
            "core/missions/software-dev/templates/plan-field-declaration.yaml");

    @Test
    void everyFilesRuleMapsItsSamplePathToItsSchema() {
        Map<String, String> byPath = new LinkedHashMap<>();
        MAPPED.forEach((path, schema) -> {
            PackFileKind.Classification c = KINDS.classify(path);
            assertThat(c).as(path).isInstanceOf(PackFileKind.Mapped.class);
            PackFileKind.Mapped mapped = (PackFileKind.Mapped) c;
            assertThat(mapped.kind()).as(path).isEqualTo(schema);
            assertThat(mapped.schema()).as(path).isEqualTo("packs/schemas/" + schema + ".schema.json");
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

    @ParameterizedTest
    @CsvSource({"foo/bar.yaml", "core/notes.yaml", "core/a.unknown.yaml", "pack.yaml", "core/deep/er/pack.yaml"})
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
        assertThat(KINDS.fileRules()).hasSize(14);
    }
}
