package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.abort;

import ai.speckitty.charter.packs.infra.storage.WholePackExpectation.Evaluation;
import ai.speckitty.charter.packs.infra.storage.WholePackExpectation.FileResult;
import ai.speckitty.charter.packs.infra.storage.WholePackExpectation.Status;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole-pack gate's census, expectation and two-way ratchet, by example. Each case states the pack, what the round
 * trip observed and what the checked-in lists say, and names the problem the gate must raise; a do-nothing gate
 * (empty pack, nothing observed, everything baselined) must not pass.
 */
class WholePackExpectationTest {

    private static final Set<String> MODELLED = Set.of("tactic", "pack-descriptor");
    private static final String NO_EXCLUSIONS = "exclusions: []\n";

    private static SortedMap<String, String> census(String... pathsAndKinds) {
        SortedMap<String, String> census = new TreeMap<>();
        for (int i = 0; i < pathsAndKinds.length; i += 2) {
            census.put(pathsAndKinds[i], pathsAndKinds[i + 1]);
        }
        return census;
    }

    private static final SortedMap<String, String> PACK = census(
            "pack.yaml", "pack-descriptor",
            "tactics/a.tactic.yaml", "tactic",
            "tactics/b.tactic.yaml", "tactic",
            "procedures/p.procedure.yaml", "procedure",
            "README.md", "other:README.md");

    private static final Map<String, Optional<String>> ALL_GREEN = Map.of(
            "pack.yaml", Optional.empty(),
            "tactics/a.tactic.yaml", Optional.empty(),
            "tactics/b.tactic.yaml", Optional.empty());

    private static final String MATCHING_RED = """
            kinds:
              - kind: procedure
                reason: Procedures are not modelled.
                issue: "#194"
            files: []
            """;
    private static final String EMPTY_DIRECTIVE = """
            empty_kinds:
              - kind: directive
                reason: The pack has no directive.
                issue: "#192"
            """;
    private static final String MATCHING_EXCLUSIONS = """
            exclusions:
              - match: "**/README.md"
                reason: Notes for pack authors.
            """;

    private static Evaluation evaluate(String red, String exclusions, Map<String, Optional<String>> observed) {
        return WholePackExpectation.load(red, exclusions).evaluate(PACK, MODELLED, observed);
    }

    private static Map<String, Optional<String>> withRed(String path, String detail) {
        Map<String, Optional<String>> observed = new TreeMap<>(ALL_GREEN);
        observed.put(path, Optional.of(detail));
        return observed;
    }

    private static Map<String, Status> statuses(Evaluation evaluation) {
        return evaluation.files().stream().collect(Collectors.toMap(FileResult::path, FileResult::status));
    }

    @Nested
    class TheRatchet {

        @Test
        void aMatchingExpectationPassesAndLabelsEveryFile() {
            Evaluation evaluation = evaluate(MATCHING_RED, MATCHING_EXCLUSIONS, ALL_GREEN);

            assertThat(evaluation.problems()).isEmpty();
            assertThat(statuses(evaluation))
                    .containsExactlyInAnyOrderEntriesOf(Map.of(
                            "pack.yaml", Status.GREEN,
                            "tactics/a.tactic.yaml", Status.GREEN,
                            "tactics/b.tactic.yaml", Status.GREEN,
                            "procedures/p.procedure.yaml", Status.EXPECTED_RED,
                            "README.md", Status.EXCLUDED));
        }

        @Test
        void anUnlistedRedFailsAndNamesTheFileAndTheDifference() {
            Evaluation evaluation =
                    evaluate(MATCHING_RED, MATCHING_EXCLUSIONS, withRed("tactics/a.tactic.yaml", "notes: x, <absent>"));

            assertThat(evaluation.problems())
                    .containsExactly("UNEXPECTED_RED tactics/a.tactic.yaml (tactic): notes: x, <absent>");
        }

        @Test
        void aListedRedFilePassesWithItsReasonAndIssue() {
            String red = MATCHING_RED.replace("files: []", """
                    files:
                      - path: tactics/a.tactic.yaml
                        at: l
                        reason: The writer drops the empty list.
                        issue: "#187"
                    """);

            Evaluation evaluation =
                    evaluate(red, MATCHING_EXCLUSIONS, withRed("tactics/a.tactic.yaml", "l: [], <absent>"));

            assertThat(evaluation.problems()).isEmpty();
            assertThat(evaluation.files())
                    .filteredOn(file -> file.path().equals("tactics/a.tactic.yaml"))
                    .singleElement()
                    .satisfies(file -> {
                        assertThat(file.status()).isEqualTo(Status.EXPECTED_RED);
                        assertThat(file.detail())
                                .contains("l: []")
                                .contains("drops the empty list")
                                .contains("#187");
                    });
        }

        @Test
        void aListedFileThatNowRoundTripsFailsAsUnexpectedlyGreen() {
            String red = MATCHING_RED.replace("files: []", """
                    files:
                      - path: tactics/a.tactic.yaml
                        at: l
                        reason: The writer drops the empty list.
                        issue: "#187"
                    """);

            Evaluation evaluation = evaluate(red, MATCHING_EXCLUSIONS, ALL_GREEN);

            assertThat(evaluation.problems())
                    .singleElement()
                    .asString()
                    .startsWith("UNEXPECTEDLY_GREEN tactics/a.tactic.yaml")
                    .contains("remove it");
        }

        @Test
        void aKindEntryWhoseFilesAreAllExcludedFailsAsUnexpectedlyGreen() {
            String withReadme = MATCHING_RED.replace("kinds:\n", """
                    kinds:
                      - kind: other:README.md
                        reason: Was red.
                        issue: "#187"
                    """);

            Evaluation evaluation = evaluate(withReadme, MATCHING_EXCLUSIONS, ALL_GREEN);

            assertThat(evaluation.problems())
                    .containsExactly(
                            "UNEXPECTEDLY_GREEN kind other:README.md: no file of it is red now; remove it from "
                                    + WholePackExpectation.EXPECTED_RED_RESOURCE);
        }

        @Test
        void aModelledKindCanNeverBeExpectedRedAsAWhole() {
            String tacticKind = MATCHING_RED.replace("kind: procedure", "kind: tactic");

            Evaluation evaluation =
                    evaluate(tacticKind, MATCHING_EXCLUSIONS, withRed("tactics/a.tactic.yaml", "steps: dropped"));

            assertThat(evaluation.problems())
                    .anySatisfy(problem -> assertThat(problem).startsWith("BASELINED_MODELLED_KIND tactic"))
                    .anySatisfy(problem -> assertThat(problem).startsWith("UNEXPECTED_RED tactics/a.tactic.yaml"));
        }

        @Test
        void aListedFileWhoseFirstDifferenceMovedIsANewLoss() {
            String red = MATCHING_RED.replace("files: []", """
                    files:
                      - path: tactics/a.tactic.yaml
                        at: l
                        reason: The writer drops the empty list.
                        issue: "#187"
                    """);

            Evaluation evaluation = evaluate(
                    red, MATCHING_EXCLUSIONS, withRed("tactics/a.tactic.yaml", "steps: source [x], written <absent>"));

            assertThat(evaluation.problems())
                    .singleElement()
                    .asString()
                    .startsWith("CHANGED_RED tactics/a.tactic.yaml (tactic): expected the first difference at 'l'");
        }

        @Test
        void anUnmodelledKindWithoutAnEntryIsAnUnexpectedRed() {
            Evaluation evaluation = evaluate("kinds: []\nfiles: []\n", MATCHING_EXCLUSIONS, ALL_GREEN);

            assertThat(evaluation.problems())
                    .containsExactly(
                            "UNEXPECTED_RED procedures/p.procedure.yaml (procedure): no adapter models kind procedure");
        }

        @Test
        void aModelledFileWithNoOutcomeIsRedNotGreen() {
            Map<String, Optional<String>> observed = new TreeMap<>(ALL_GREEN);
            observed.remove("tactics/b.tactic.yaml");

            Evaluation evaluation = evaluate(MATCHING_RED, MATCHING_EXCLUSIONS, observed);

            assertThat(evaluation.problems())
                    .singleElement()
                    .asString()
                    .startsWith("UNEXPECTED_RED tactics/b.tactic.yaml");
        }
    }

    @Nested
    class StaleAndVacuousEntries {

        @Test
        void aBaselinedPathThatDoesNotExistFails() {
            String red = MATCHING_RED.replace("files: []", """
                    files:
                      - path: tactics/gone.tactic.yaml
                        at: l
                        reason: Was red.
                        issue: "#187"
                    """);

            assertThat(evaluate(red, MATCHING_EXCLUSIONS, ALL_GREEN).problems())
                    .singleElement()
                    .asString()
                    .startsWith("STALE_BASELINE tactics/gone.tactic.yaml");
        }

        @Test
        void aBaselinedKindWithNoFileFails() {
            String red = MATCHING_RED.replace("files: []", "") + """
                      - kind: agent-profile
                        reason: Not modelled.
                        issue: "#190"
                    files: []
                    """;

            assertThat(evaluate(red, MATCHING_EXCLUSIONS, ALL_GREEN).problems())
                    .singleElement()
                    .asString()
                    .startsWith("STALE_BASELINE kind agent-profile");
        }

        @Test
        void aBaselinedFileThatIsExcludedFails() {
            String red = MATCHING_RED.replace("files: []", """
                    files:
                      - path: README.md
                        at: x
                        reason: Was red.
                        issue: "#187"
                    """);

            assertThat(evaluate(red, MATCHING_EXCLUSIONS, ALL_GREEN).problems())
                    .singleElement()
                    .asString()
                    .startsWith("STALE_BASELINE README.md: the file is excluded");
        }

        @Test
        void anExclusionThatMatchesNothingFails() {
            String exclusions = MATCHING_EXCLUSIONS + """
                      - match: "**/.gitkeep"
                        reason: Keeps an empty directory.
                    """;

            assertThat(evaluate(MATCHING_RED, exclusions, ALL_GREEN).problems())
                    .containsExactly("STALE_EXCLUSION **/.gitkeep: matches no file");
        }

        @Test
        void anExclusionOverAModelledFileFails() {
            String exclusions = MATCHING_EXCLUSIONS + """
                      - match: "tactics/b.tactic.yaml"
                        reason: Trying to hide a red file.
                    """;

            assertThat(evaluate(MATCHING_RED, exclusions, ALL_GREEN).problems())
                    .singleElement()
                    .asString()
                    .startsWith("EXCLUDED_MODELLED tactics/b.tactic.yaml");
        }

        @Test
        void aModelledKindWithNoFilesFails() {
            Evaluation evaluation = WholePackExpectation.load(MATCHING_RED, MATCHING_EXCLUSIONS)
                    .evaluate(PACK, Set.of("tactic", "pack-descriptor", "directive"), ALL_GREEN);

            assertThat(evaluation.problems())
                    .containsExactly("EMPTY_MODELLED_KIND directive: the pack has no file of this modelled kind");
        }

        @Test
        void aListedEmptyModelledKindPasses() {
            Evaluation evaluation = WholePackExpectation.load(MATCHING_RED + EMPTY_DIRECTIVE, MATCHING_EXCLUSIONS)
                    .evaluate(PACK, Set.of("tactic", "pack-descriptor", "directive"), ALL_GREEN);

            assertThat(evaluation.problems()).isEmpty();
        }

        @Test
        void aListedEmptyKindDoesNotExcuseAnotherEmptyKind() {
            Evaluation evaluation = WholePackExpectation.load(MATCHING_RED + EMPTY_DIRECTIVE, MATCHING_EXCLUSIONS)
                    .evaluate(PACK, Set.of("tactic", "pack-descriptor", "directive", "skill"), ALL_GREEN);

            assertThat(evaluation.problems())
                    .containsExactly("EMPTY_MODELLED_KIND skill: the pack has no file of this modelled kind");
        }

        @Test
        void aListedEmptyKindThatGainsAFileIsStale() {
            Evaluation evaluation = WholePackExpectation.load(
                            MATCHING_RED + EMPTY_DIRECTIVE.replace("directive", "tactic"), MATCHING_EXCLUSIONS)
                    .evaluate(PACK, MODELLED, ALL_GREEN);

            assertThat(evaluation.problems())
                    .containsExactly("STALE_BASELINE empty kind tactic: the pack has a file of it now; remove it from "
                            + WholePackExpectation.EXPECTED_RED_RESOURCE);
        }

        @Test
        void aListedEmptyKindThatIsNotModelledIsStale() {
            Evaluation evaluation = WholePackExpectation.load(MATCHING_RED + EMPTY_DIRECTIVE, MATCHING_EXCLUSIONS)
                    .evaluate(PACK, MODELLED, ALL_GREEN);

            assertThat(evaluation.problems())
                    .containsExactly("STALE_BASELINE empty kind directive: not a modelled kind; remove it from "
                            + WholePackExpectation.EXPECTED_RED_RESOURCE);
        }

        @Test
        void aListedEmptyKindIsReportedAsNotProved() {
            Evaluation evaluation = WholePackExpectation.load(MATCHING_RED + EMPTY_DIRECTIVE, MATCHING_EXCLUSIONS)
                    .evaluate(PACK, Set.of("tactic", "pack-descriptor", "directive"), ALL_GREEN);

            assertThat(evaluation.emptyKinds())
                    .extracting(WholePackExpectation.EmptyKindEntry::kind)
                    .containsExactly("directive");
            assertThat(WholePackReport.render(evaluation, "p", "pin", Map.of()))
                    .contains("## Modelled kinds with no file")
                    .contains("- directive: not proved here. The pack has no directive. (#192)");
        }

        @Test
        void anEmptyKindEntryWithoutAReasonIsRefused() {
            assertThatThrownBy(() -> WholePackExpectation.load(
                            MATCHING_RED + EMPTY_DIRECTIVE.replace("    reason: The pack has no directive.\n", ""),
                            MATCHING_EXCLUSIONS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty_kinds entry")
                    .hasMessageContaining("has no reason");
        }

        @Test
        void anEmptyKindEntryWithoutAnIssueIsRefused() {
            assertThatThrownBy(() -> WholePackExpectation.load(
                            MATCHING_RED + EMPTY_DIRECTIVE.replace("    issue: \"#192\"\n", ""), MATCHING_EXCLUSIONS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty_kinds entry")
                    .hasMessageContaining("has no issue");
        }

        @Test
        void anEmptyPackCannotPass() {
            Evaluation evaluation =
                    WholePackExpectation.load("{}", NO_EXCLUSIONS).evaluate(new TreeMap<>(), MODELLED, Map.of());

            assertThat(evaluation.problems())
                    .hasSize(2)
                    .allSatisfy(p -> assertThat(p).startsWith("EMPTY_MODELLED_KIND"));
        }

        @Test
        void everyFileIsAccountedForExactlyOnce() {
            Evaluation evaluation = evaluate(MATCHING_RED, MATCHING_EXCLUSIONS, ALL_GREEN);

            assertThat(evaluation.files()).extracting(FileResult::path).containsExactlyElementsOf(PACK.keySet());
            assertThat(evaluation.byKind().values().stream()
                            .flatMap(counts -> counts.values().stream())
                            .mapToInt(Integer::intValue)
                            .sum())
                    .isEqualTo(PACK.size());
        }
    }

    @Nested
    class Loading {

        @Test
        void anEntryWithoutAnIssueIsRefused() {
            assertThatThrownBy(() -> WholePackExpectation.load(
                            "kinds:\n  - kind: procedure\n    reason: Not modelled.\n", NO_EXCLUSIONS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("has no issue");
        }

        @Test
        void aBlankReasonIsRefused() {
            assertThatThrownBy(() -> WholePackExpectation.load(
                            NO_EXCLUSIONS.replace("exclusions: []", "kinds: []"),
                            "exclusions:\n  - match: x\n    reason: ' '\n"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("has no reason");
        }

        @Test
        void anUnknownKeyIsRefused() {
            assertThatThrownBy(() -> WholePackExpectation.load(
                            "kinds:\n  - kind: procedure\n    reason: r\n    issue: i\n    owner: me\n", NO_EXCLUSIONS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown key 'owner'");
        }

        @Test
        void aRepeatedEntryIsRefused() {
            String twice =
                    "kinds:\n  - {kind: procedure, reason: r, issue: i}\n  - {kind: procedure, reason: r, issue: i}\n";

            assertThatThrownBy(() -> WholePackExpectation.load(twice, NO_EXCLUSIONS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("twice");
        }

        @Test
        void aNonTextValueIsRefused() {
            assertThatThrownBy(() -> WholePackExpectation.load(
                            "kinds:\n  - kind: procedure\n    reason: [a, b]\n    issue: i\n", NO_EXCLUSIONS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-text value");
        }

        @Test
        void globsMatchAtTheRootAndAcrossSegmentsButStarStaysInASegment() {
            assertThat(WholePackExpectation.glob("**/README.md")
                            .matcher("README.md")
                            .matches())
                    .isTrue();
            assertThat(WholePackExpectation.glob("**/README.md")
                            .matcher("a/b/README.md")
                            .matches())
                    .isTrue();
            assertThat(WholePackExpectation.glob("assets/*.py")
                            .matcher("assets/x.py")
                            .matches())
                    .isTrue();
            assertThat(WholePackExpectation.glob("assets/*.py")
                            .matcher("assets/sub/x.py")
                            .matches())
                    .isFalse();
            assertThat(WholePackExpectation.glob("a.b").matcher("axb").matches())
                    .isFalse();
        }
    }

    @Nested
    class PinnedRefusals {

        private static final String REASON = "payload assets/assets/x.md does not exist";
        private static final String REFUSED_A = MATCHING_RED.replace("files: []", """
                files:
                  - path: tactics/a.tactic.yaml
                    refused: "payload assets/assets/x.md does not exist"
                    reason: The sidecar anchors path at the pack root.
                    issue: "#193"
                """);

        @Test
        void aRefusalWithTheExactReasonPasses() {
            Evaluation evaluation =
                    evaluate(REFUSED_A, MATCHING_EXCLUSIONS, withRed("tactics/a.tactic.yaml", "refused: " + REASON));

            assertThat(evaluation.problems()).isEmpty();
            assertThat(statuses(evaluation)).containsEntry("tactics/a.tactic.yaml", Status.EXPECTED_RED);
        }

        @Test
        void aRefusalWithADifferentReasonIsANewRed() {
            Evaluation evaluation = evaluate(
                    REFUSED_A,
                    MATCHING_EXCLUSIONS,
                    withRed("tactics/a.tactic.yaml", "refused: payload assets/x.md does not exist"));

            assertThat(evaluation.problems())
                    .singleElement()
                    .asString()
                    .startsWith("CHANGED_RED tactics/a.tactic.yaml (tactic): expected refused: " + REASON)
                    .endsWith("now: refused: payload assets/x.md does not exist");
            assertThat(statuses(evaluation)).containsEntry("tactics/a.tactic.yaml", Status.RED);
        }

        @Test
        void aDifferenceOtherThanTheRefusalIsANewRed() {
            Evaluation evaluation = evaluate(
                    REFUSED_A, MATCHING_EXCLUSIONS, withRed("tactics/a.tactic.yaml", "refused: " + REASON + "!"));

            assertThat(evaluation.problems())
                    .singleElement()
                    .asString()
                    .startsWith("CHANGED_RED tactics/a.tactic.yaml");
        }

        @Test
        void aPinnedFileThatIsNowWrittenAndGreenFailsAsUnexpectedlyGreen() {
            Evaluation evaluation = evaluate(REFUSED_A, MATCHING_EXCLUSIONS, ALL_GREEN);

            assertThat(evaluation.problems())
                    .singleElement()
                    .asString()
                    .startsWith("UNEXPECTEDLY_GREEN tactics/a.tactic.yaml")
                    .contains("remove it");
        }

        @Test
        void anEntryForAnotherPackNeitherMatchesNorGoesStaleHere() {
            String otherPack = MATCHING_RED.replace("files: []", """
                    files:
                      - path: tactics/a.tactic.yaml
                        pack: other
                        refused: "payload assets/assets/x.md does not exist"
                        reason: Another pack's sidecar.
                        issue: "#193"
                      - path: tactics/only-in-other.tactic.yaml
                        pack: other
                        at: l
                        reason: Another pack's file.
                        issue: "#193"
                    """);

            Evaluation evaluation =
                    evaluate(otherPack, MATCHING_EXCLUSIONS, withRed("tactics/a.tactic.yaml", "refused: " + REASON));

            assertThat(evaluation.problems())
                    .containsExactly("UNEXPECTED_RED tactics/a.tactic.yaml (tactic): refused: " + REASON);
        }

        @Test
        void theEvaluatedPackPicksItsOwnEntries() {
            String otherPack = REFUSED_A.replace("    refused:", "    pack: other\n    refused:");

            Evaluation evaluation = WholePackExpectation.load(otherPack, MATCHING_EXCLUSIONS)
                    .evaluate("other", PACK, MODELLED, withRed("tactics/a.tactic.yaml", "refused: " + REASON));

            assertThat(evaluation.problems()).isEmpty();
        }

        @Test
        void refusedAndAtTogetherAreRefused() {
            String both = REFUSED_A.replace("    refused:", "    at: refused\n    refused:");

            assertThatThrownBy(() -> WholePackExpectation.load(both, NO_EXCLUSIONS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("both at and refused");
        }

        @Test
        void anEntryWithNeitherAtNorRefusedIsRefused() {
            String neither = REFUSED_A.replace("    refused: \"" + REASON + "\"\n", "");

            assertThatThrownBy(() -> WholePackExpectation.load(neither, NO_EXCLUSIONS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("has no at or refused");
        }

        @Test
        void anUnknownFileKeyIsRefused() {
            String unknown = REFUSED_A.replace("    refused:", "    owner: me\n    refused:");

            assertThatThrownBy(() -> WholePackExpectation.load(unknown, NO_EXCLUSIONS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown key 'owner'");
        }

        @Test
        void theRefusalsOfAPackAreReadableByOtherRatchets() {
            WholePackExpectation expectation = WholePackExpectation.load(REFUSED_A, NO_EXCLUSIONS);

            assertThat(expectation.refusals("built-in"))
                    .containsExactly(new WholePackExpectation.PinnedRefusal(
                            "built-in",
                            "tactics/a.tactic.yaml",
                            REASON,
                            "The sidecar anchors path at the pack root.",
                            "#193"));
            assertThat(expectation.refusals("other")).isEmpty();
        }

        @Test
        void theBundledListPinsExactlyTheSixBuiltInSidecars() {
            WholePackExpectation bundled = WholePackExpectation.fromClasspath();

            assertThat(bundled.refusals("built-in"))
                    .extracting(WholePackExpectation.PinnedRefusal::path, WholePackExpectation.PinnedRefusal::refused)
                    .containsExactlyInAnyOrder(
                            sidecar("assets/docs_structural_lint.py"),
                            sidecar("assets/audiences/agentic-framework-core-team.md"),
                            sidecar("assets/audiences/automation_agent.md"),
                            sidecar("assets/audiences/line_manager.md"),
                            sidecar("assets/audiences/nontech_educator.md"),
                            sidecar("assets/audiences/software_engineer.md"));
            assertThat(bundled.refusals("built-in"))
                    .allSatisfy(refusal -> assertThat(refusal.issue()).isEqualTo("#193"));
            assertThat(bundled.refusals("other")).isEmpty();
        }

        private static org.assertj.core.groups.Tuple sidecar(String payload) {
            return org.assertj.core.groups.Tuple.tuple(
                    payload + ".asset.yaml", "payload assets/" + payload + " does not exist");
        }
    }

    @Nested
    class TheCensus {

        @TempDir
        Path temp;

        @Test
        void filesTakeTheirSchemaKindOrWhereTheyLive() throws IOException {
            Path pack = temp.resolve("built-in");
            for (String path : List.of(
                    "pack.yaml",
                    "pack.md",
                    "tactic.graph.yaml",
                    "tactics/x/a.tactic.yaml",
                    "missions/m/mission.yaml",
                    "toolguides/GUIDE.md")) {
                Files.createDirectories(pack.resolve(path).getParent());
                Files.writeString(pack.resolve(path), "x");
            }

            assertThat(WholePackCensus.of(pack))
                    .containsExactlyEntriesOf(new TreeMap<>(Map.of(
                            "missions/m/mission.yaml", "other:missions",
                            "pack.md", "other:pack.md",
                            "pack.yaml", "pack-descriptor",
                            "tactic.graph.yaml", "graph-shard",
                            "tactics/x/a.tactic.yaml", "tactic",
                            "toolguides/GUIDE.md", "other:toolguides")));
        }

        @Test
        void aSymbolicLinkIsCountedNeverFollowed() throws IOException {
            Path pack = temp.resolve("built-in");
            Files.createDirectories(pack.resolve("tactics"));
            Files.writeString(pack.resolve("tactics/a.tactic.yaml"), "x");
            try {
                Files.createSymbolicLink(pack.resolve("link.tactic.yaml"), pack.resolve("tactics/a.tactic.yaml"));
            } catch (UnsupportedOperationException | IOException noLinks) {
                abort("the filesystem cannot create symbolic links");
            }

            assertThat(WholePackCensus.of(pack))
                    .containsEntry("link.tactic.yaml", WholePackCensus.SYMLINK)
                    .containsEntry("tactics/a.tactic.yaml", "tactic");
        }
    }

    @Nested
    class TheReport {

        @Test
        void itIsByteIdenticalAcrossRunsAndEscapesPipes() {
            Evaluation evaluation =
                    evaluate(MATCHING_RED, MATCHING_EXCLUSIONS, withRed("tactics/a.tactic.yaml", "a | b"));

            String first = WholePackReport.render(evaluation, "built-in", "abc", Map.of());
            String second = WholePackReport.render(evaluation, "built-in", "abc", Map.of());

            assertThat(first)
                    .isEqualTo(second)
                    .contains("a \\| b")
                    .contains("## By kind")
                    .contains("UNEXPECTED_RED");
            assertThat(WholePackReport.kindTable(evaluation)).contains("| tactic | 2 | 1 | 0 | 0 | 1 |");
        }
    }
}
