package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackInspector.Scope;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRoot;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Retired mission files are refused by name (#194, ADR-0044 decision 6, FR-008): classified {@code Retired} by the
 * {@code retired} section of the file-kinds table, refused by the store's load and by validate mode alike (YAML and
 * Markdown), never a companion, bucketed {@code retired:} by the gate census. Proved on the bundled table, over
 * {@link RetiredFileFixture}'s samples.
 */
@DisplayName("Retired mission files are refused by name (#194)")
class RetiredFilesTest {

    private static final PackFileKind TABLE = PackFileKind.bundled();
    private static final String TABLE_HEAD = "node_kinds: []\nfragment_kinds: {}\nfiles: []\n";

    @Nested
    @DisplayName("the retired section of the table")
    class TheTable {

        @Test
        @DisplayName("each family's sample, at either layout, is classified Retired with its rule's match and reason")
        void everyFamilyIsClassifiedRetired() {
            RetiredFileFixture.bothLayouts()
                    .forEach((path, match) -> assertThat(TABLE.classify("qa/" + path))
                            .as(path)
                            .isEqualTo(new PackFileKind.Retired(match, RetiredFileFixture.RULES.get(match))));
            assertThat(TABLE.retiredRules())
                    .extracting(PackFileKind.RetiredRule::match)
                    .containsExactlyElementsOf(RetiredFileFixture.RULES.keySet());
        }

        @Test
        @DisplayName("the retired rules replace the mission exclusions: no exclusion matches a retired sample")
        void theRetiredRulesReplaceTheMissionExclusions() {
            for (String path : RetiredFileFixture.bothLayouts().keySet()) {
                assertThat(TABLE.exclusionRules())
                        .as(path)
                        .noneMatch(rule -> TABLE.matchingRules("qa/" + path).contains(rule.match()));
            }
        }

        @Test
        @DisplayName(
                "a README under a retired directory is retired, not excluded; one beside a mission type is excluded")
        void aReadmeUnderARetiredDirectoryIsRetired() {
            assertThat(TABLE.classify("qa/mission-steps/qa/specify/README.md"))
                    .isInstanceOf(PackFileKind.Retired.class);
            assertThat(TABLE.classify("qa/missions/plan/command-templates/README.md"))
                    .isInstanceOf(PackFileKind.Retired.class);
            assertThat(TABLE.classify("qa/missions/plan/README.md")).isInstanceOf(PackFileKind.Excluded.class);
            assertThat(TABLE.classify("qa/missions/plan/templates/divio/README.md"))
                    .isInstanceOf(PackFileKind.Excluded.class);
            assertThat(TABLE.matchingRules("qa/mission-steps/qa/specify/README.md"))
                    .containsExactly("*/mission-steps/**/*");
            assertThat(TABLE.classify("built-in/missions/mission-steps/plan/specify/README.md"))
                    .isInstanceOf(PackFileKind.Retired.class);
            // The one overlap: a README directly in the old built-in missions/mission-steps/ is read as one beside a
            // mission type named mission-steps. Exclusions come first, so it is excluded, never read either way.
            assertThat(TABLE.matchingRules("built-in/missions/mission-steps/README.md"))
                    .containsExactly("*/missions/*/README.md", "*/missions/mission-steps/**/*");
            assertThat(TABLE.classify("built-in/missions/mission-steps/README.md"))
                    .isInstanceOf(PackFileKind.Excluded.class);
        }

        @Test
        @DisplayName("a payload that only shares a name with a retired family is never retired: the rules are anchored")
        void aNamesakeIsNotRetired() {
            for (String path : RetiredFileFixture.NAMESAKES) {
                assertThat(TABLE.classify("qa/" + path)).as(path).isInstanceOf(PackFileKind.Mapped.class);
                assertThat(TABLE.matchingRules("qa/" + path)).as(path).isEmpty();
            }
        }

        @Test
        @DisplayName("a mission-type file, its companions and its templates stay mapped, never retired")
        void theNewShapeIsNotRetired() {
            assertThat(TABLE.classify("qa/missions/plan/plan.mission-type.yaml"))
                    .isInstanceOf(PackFileKind.Mapped.class);
            assertThat(TABLE.classify("qa/missions/plan/steps/specify.prompt.md"))
                    .isInstanceOf(PackFileKind.Mapped.class);
            assertThat(TABLE.classify("qa/missions/plan/templates/spec.md"))
                    .isEqualTo(new PackFileKind.Mapped("template", Optional.empty(), ContentForm.BYTES));
        }

        @Test
        @DisplayName("the overlap check sees a retired rule that overlaps a files or exclusions rule")
        void theOverlapCheckCoversRetiredRules() {
            PackFileKind overlapping = new PackFileKind(TABLE_HEAD
                    + "exclusions: [{match: '**/actions/*/index.yaml', reason: r}]\n"
                    + "retired: [{match: '**/actions/**/*', reason: gone}]\n");
            PackFileKind withFiles = new PackFileKind("""
                    node_kinds: []
                    fragment_kinds: {}
                    files: [{match: '**/*.tactic.yaml', schema: tactic}]
                    exclusions: []
                    retired: [{match: '**/old/*.yaml', reason: gone}]
                    """);

            assertThat(overlapping.matchingRules("qa/actions/a/index.yaml"))
                    .containsExactly("**/actions/*/index.yaml", "**/actions/**/*");
            assertThat(withFiles.matchingRules("qa/old/a.tactic.yaml"))
                    .containsExactly("**/*.tactic.yaml", "**/old/*.yaml");
            for (String path : RetiredFileFixture.bothLayouts().keySet()) {
                assertThat(TABLE.matchingRules("qa/" + path)).as(path).hasSize(1);
            }
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "retired: [{match: a}]",
                    "retired: [{reason: r}]",
                    "retired: [{match: a, reason: ' '}]",
                    "retired: [{match: a, reason: r, issue: x}]",
                    "retired: {match: a}"
                })
        @DisplayName("a retired rule without its match or reason, or with another key, refuses the table")
        void aMalformedRetiredRuleRefusesTheTable(String section) {
            assertThatThrownBy(() -> new PackFileKind(TABLE_HEAD + "exclusions: []\n" + section + "\n"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("packs/file-kinds.yaml");
        }

        @Test
        @DisplayName("an unknown top-level key refuses the table, naming it; the known ones load")
        void anUnknownTopLevelKeyRefusesTheTable() {
            assertThatThrownBy(() -> new PackFileKind(TABLE_HEAD + "exclusions: []\nretire: []\n"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("packs/file-kinds.yaml: the unknown top-level key 'retire'; the table takes only"
                            + " [artifact_kinds, exclusions, files, fragment_kinds, node_kinds, payloads, retired]");
            assertThat(new PackFileKind(TABLE_HEAD + "artifact_kinds: []\nexclusions: []\npayloads: []\nretired: []\n")
                            .retiredRules())
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("the store's load")
    class TheLoad {

        @Test
        @DisplayName("refuses every retired file by name with its rule's reason, at the root and the missions layout")
        void everyRetiredFileIsAViolationNamingItsReason(@TempDir Path host) {
            Map<String, String> files = RetiredFileFixture.bothLayouts();
            RetiredFileFixture.plant(host.resolve("qa"), files.keySet());
            FilesystemPackStore store = new FilesystemPackStore(host, new YamlSerializationStrategy());

            List<PackElementViolation> violations =
                    store.read(new PackIdentifier("qa")).violations();

            assertThat(violations)
                    .containsExactlyInAnyOrderElementsOf(files.entrySet().stream()
                            .map(e -> new PackElementViolation(
                                    e.getKey(), "retired", RetiredFileFixture.RULES.get(e.getValue())))
                            .toList());
        }
    }

    @Nested
    @DisplayName("validate mode")
    class ValidateMode {

        @Test
        @DisplayName(
                "reports every retired file, YAML and Markdown alike, as an artifact schema error naming the reason")
        void everyRetiredFileIsAFinding(@TempDir Path host) {
            SequencedMap<String, String> files = new java.util.LinkedHashMap<>(RetiredFileFixture.bothLayouts());
            Path pack = RetiredFileFixture.plant(host.resolve("qa"), files.keySet());
            PackRoot root = new PackRoot("qa", Optional.of(PackLevel.BUILT_IN), pack.toString());

            PackContents contents = new FileSystemPackInspector().inspect(root, Scope.FULL);

            assertThat(contents.unreadableFiles())
                    .extracting(FileRead.Unreadable::file)
                    .containsExactlyInAnyOrderElementsOf(files.keySet());
            List<PackFinding> findings = contents.unreadableFiles().stream()
                    .flatMap(bad -> PackPolicy.unreadable("qa", bad).stream())
                    .toList();
            assertThat(findings).extracting(PackFinding::code).containsOnly(PackFindingCode.ARTIFACT_SCHEMA_ERROR);
            assertThat(findings)
                    .filteredOn(f -> f.file().equals("missions/plan/mission-runtime.yaml"))
                    .singleElement()
                    .extracting(PackFinding::detail)
                    .asString()
                    .isEqualTo("qa/missions/plan/mission-runtime.yaml is retired: folded into the mission-type file"
                            + " (ADR-0044)");
            assertThat(findings)
                    .filteredOn(f -> f.file().equals("mission-steps/qa/review/prompt.md"))
                    .singleElement()
                    .extracting(PackFinding::detail)
                    .asString()
                    .contains("is retired: folded into the mission-type steps and their companions");
        }
    }

    @Nested
    @DisplayName("companions and the gate census")
    class CompanionsAndCensus {

        @Test
        @DisplayName("a companion path at a retired file is refused, naming the rule and its reason")
        void aRetiredPathIsNoCompanion(@TempDir Path host) {
            Path pack = host.resolve("qa").toAbsolutePath().normalize();
            KindBinding.Companion companion = new KindBinding.Companion(
                    "../mission-steps/qa/review/prompt.md", Optional.empty(), ContentForm.TEXT, Optional.empty());

            assertThat(Companions.resolve(
                            new Companions.Rules(TABLE, PackSchemas::bundled),
                            pack,
                            "toolguides/x.toolguide.yaml",
                            companion))
                    .isEqualTo(
                            new Companions.Refused(
                                    "is retired by file-kinds rule '*/mission-steps/**/*'"
                                            + " (folded into the mission-type steps and their companions (ADR-0044)), not a companion"));
        }

        @Test
        @DisplayName("the census buckets each retired file as retired:<rule>, never other:")
        void theCensusBucketsRetiredFiles() {
            RetiredFileFixture.bothLayouts()
                    .forEach((path, match) -> assertThat(WholePackCensus.kindOf("qa", path))
                            .as(path)
                            .isEqualTo(WholePackCensus.RETIRED + match));
        }
    }
}
