package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import ai.speckitty.charter.packs.infra.storage.rules.TreeRead;
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
            // mission type named mission-steps. Exclusions come first, so it is excluded: not held and, since #451, not
            // refused either (it is an orientation note, not a stale mission-step file).
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
        @DisplayName("a pack.md or PROVENANCE.md that is not at the pack root is not retired")
        void aPackLevelNamesakeBelowTheRootIsNotRetired() {
            for (String path : RetiredFileFixture.NOT_AT_THE_ROOT) {
                assertThat(TABLE.classify("qa/" + path)).as(path).isNotInstanceOf(PackFileKind.Retired.class);
                assertThat(TABLE.matchingRules("qa/" + path)).as(path).isEmpty();
            }
        }

        @Test
        @DisplayName(
                "the pack root's own files keep their exact results: README.md is the document, pack.md is retired")
        void theRootFilesKeepTheirExactResults() {
            assertThat(TABLE.classify("qa/README.md"))
                    .isEqualTo(new PackFileKind.Mapped(
                            "pack-document",
                            Optional.of("packs/schemas/pack-document.schema.json"),
                            ContentForm.MARKDOWN));
            assertThat(TABLE.classify("qa/pack.md"))
                    .isEqualTo(new PackFileKind.Retired("*/pack.md", RetiredFileFixture.RULES.get("*/pack.md")));
        }

        @Test
        @DisplayName(
                "a root file that differs only in letter case from a rule's file is refused naming that rule (FR-022)")
        void aCaseVariantIsRefusedByName() {
            RetiredFileFixture.CASE_VARIANTS.forEach((file, match) -> assertThat(TABLE.classify("qa/" + file))
                    .as(file)
                    .isEqualTo(new PackFileKind.Retired(
                            match,
                            "differs only in letter case from '" + match.substring(match.indexOf('/') + 1) + "'")));
        }

        @Test
        @DisplayName("a case variant below the pack root is not refused: only the root files are rule files")
        void aCaseVariantBelowTheRootIsNotRefused() {
            for (String path : List.of("docs/readme.md", "docs/Readme.md", "sub/PACK.md")) {
                assertThat(TABLE.classify("qa/" + path)).as(path).isNotInstanceOf(PackFileKind.Retired.class);
            }
        }

        @Test
        @DisplayName("a case variant of an asset or template payload is not a rule file and is not refused")
        void aPayloadCaseVariantIsNotRefused() {
            assertThat(TABLE.classify("qa/Assets/x.bin")).isNotInstanceOf(PackFileKind.Retired.class);
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
                if (path.endsWith(".graph.yaml")) {
                    // The one declared overlap with a files rule: the retired rule is consulted first and refuses it.
                    assertThat(TABLE.matchingRules("qa/" + path))
                            .as(path)
                            .containsExactly("*/*.graph.yaml", "**/mission_step_contract.graph.yaml");
                    assertThat(TABLE.classify("qa/" + path)).as(path).isInstanceOf(PackFileKind.Retired.class);
                } else {
                    assertThat(TABLE.matchingRules("qa/" + path)).as(path).hasSize(1);
                }
            }
            // The retired rules come before the files rules: a mission-type file in the old mission tier is refused by
            // its family's name, not read as a misplaced mission type.
            assertThat(TABLE.classify("qa/mission_types/research.mission-type.yaml"))
                    .isEqualTo(new PackFileKind.Retired(
                            "*/mission_types/*.yaml", RetiredFileFixture.RULES.get("*/mission_types/*.yaml")));
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
                    .hasMessage(
                            "packs/file-kinds.yaml: the unknown top-level key 'retire'; the table takes only"
                                    + " [artifact_kinds, exclusions, files, fragment_kinds, ignored, node_kinds, payloads, retired]");
            assertThat(new PackFileKind(TABLE_HEAD
                                    + "artifact_kinds: []\nexclusions: []\npayloads: []\nretired: []\nignored: []\n")
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
                    store.read(new PackDirectory("qa")).violations();

            assertThat(violations)
                    .containsExactlyInAnyOrderElementsOf(files.entrySet().stream()
                            .map(e -> new PackElementViolation(
                                    e.getKey(), StorageCodes.RETIRED, RetiredFileFixture.RULES.get(e.getValue())))
                            .toList());
        }
    }

    @Nested
    @DisplayName("the store's load of the pack-level retired files and case variants")
    class ThePackLevelFiles {

        @Test
        @DisplayName("a pack.md, a PROVENANCE.md and every case variant is a retired violation naming its reason")
        void theStoreRefusesThemByName(@TempDir Path host) {
            FilesystemPackStore store = new FilesystemPackStore(host, new YamlSerializationStrategy());
            Path exact = RetiredFileFixture.plant(host.resolve("qa"), List.of());
            write(exact, "pack.md");
            write(exact, "PROVENANCE.md");

            assertThat(store.read(new PackDirectory("qa")).violations())
                    .containsExactlyInAnyOrder(
                            new PackElementViolation(
                                    "pack.md", StorageCodes.RETIRED, RetiredFileFixture.RULES.get("*/pack.md")),
                            new PackElementViolation(
                                    "PROVENANCE.md",
                                    StorageCodes.RETIRED,
                                    RetiredFileFixture.RULES.get("*/PROVENANCE.md")));
            // Spellings that differ only in case are one file on a case-insensitive file system, so each variant is
            // planted alone in a pack of its own. The pack.yaml, policy.yaml and pack-manifest.yaml variants still
            // collide there with the files every planted pack holds, so this test assumes a case-sensitive file system.
            int n = 0;
            for (Map.Entry<String, String> variant : RetiredFileFixture.CASE_VARIANTS.entrySet()) {
                String name = "variant" + n++;
                write(RetiredFileFixture.plant(host.resolve(name), List.of()), variant.getKey());

                assertThat(store.read(new PackDirectory(name)).violations())
                        .as(variant.getKey())
                        .containsExactly(new PackElementViolation(
                                variant.getKey(),
                                StorageCodes.RETIRED,
                                "differs only in letter case from '"
                                        + variant.getValue()
                                                .substring(variant.getValue().indexOf('/') + 1) + "'"));
            }
        }

        private void write(Path pack, String file) {
            try {
                java.nio.file.Files.writeString(pack.resolve(file), "# T\n");
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
    }

    @Nested
    @DisplayName("validate mode")
    class ValidateMode {

        @Test
        @DisplayName("reports a pack.md and a case variant as a retired file naming the reason")
        void thePackLevelRetiredFilesAreFindings(@TempDir Path host) {
            Path pack = RetiredFileFixture.plant(host.resolve("qa"), List.of());
            for (String file : List.of("pack.md", "Readme.md", "PROVENANCE.md")) {
                try {
                    java.nio.file.Files.writeString(pack.resolve(file), "# T\n");
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
            PackRoot root = new PackRoot("qa", Optional.of(PackLevel.BUILT_IN), pack.toString());

            TreeRead contents = new FileSystemPackInspector().readTree(root);

            assertThat(contents.readFaults())
                    .extracting(fault -> fault.location().file())
                    .containsExactlyInAnyOrder("pack.md", "Readme.md", "PROVENANCE.md");
            assertThat(contents.readFaults().stream().map(Reason::detail))
                    .anySatisfy(d -> assertThat(d).contains("pack.md: the pack document is README.md at the pack root"))
                    .anySatisfy(d ->
                            assertThat(d).contains("PROVENANCE.md: folded into README.md as its provenance section"))
                    .anySatisfy(d -> assertThat(d).contains("Readme.md: differs only in letter case from 'README.md'"));
        }

        @Test
        @DisplayName(
                "reports every retired file, YAML and Markdown alike, as an artifact schema error naming the reason")
        void everyRetiredFileIsAFinding(@TempDir Path host) {
            SequencedMap<String, String> files = new java.util.LinkedHashMap<>(RetiredFileFixture.bothLayouts());
            Path pack = RetiredFileFixture.plant(host.resolve("qa"), files.keySet());
            PackRoot root = new PackRoot("qa", Optional.of(PackLevel.BUILT_IN), pack.toString());

            TreeRead contents = new FileSystemPackInspector().readTree(root);

            assertThat(contents.readFaults())
                    .extracting(fault -> fault.location().file())
                    .containsExactlyInAnyOrderElementsOf(files.keySet());
            List<Reason> findings = contents.readFaults();
            // A retired file is retired, whatever its kind, a graph document's included (#554, R2 row 11).
            assertThat(findings).extracting(Reason::code).containsOnly(StorageCodes.RETIRED);
            assertThat(findings)
                    .filteredOn(f -> f.location().file().equals("mission_step_contract.graph.yaml"))
                    .singleElement()
                    .satisfies(f -> {
                        assertThat(f.code()).isEqualTo(StorageCodes.RETIRED);
                        assertThat(f.detail())
                                .isEqualTo("mission_step_contract.graph.yaml: folded into the"
                                        + " mission-type steps' substeps (slice F)");
                    });
            assertThat(findings)
                    .filteredOn(f -> f.location().file().equals("mission_step_contracts/specify.step-contract.yaml"))
                    .singleElement()
                    .extracting(Reason::detail)
                    .asString()
                    .isEqualTo("mission_step_contracts/specify.step-contract.yaml: folded into the"
                            + " mission-type steps' substeps (slice F)");
            assertThat(findings)
                    .filteredOn(f -> f.location().file().equals("missions/plan/mission-runtime.yaml"))
                    .singleElement()
                    .extracting(Reason::detail)
                    .asString()
                    .isEqualTo("missions/plan/mission-runtime.yaml: folded into the mission-type file" + " (ADR-0044)");
            assertThat(findings)
                    .filteredOn(f -> f.location().file().equals("mission-steps/qa/review/prompt.md"))
                    .singleElement()
                    .extracting(Reason::detail)
                    .asString()
                    .contains(": folded into the mission-type steps and their companions");
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
