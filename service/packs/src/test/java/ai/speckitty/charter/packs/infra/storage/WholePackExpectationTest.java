package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    private static final String MATCHING_EXCLUSIONS = """
            exclusions:
              - match: "**/README.md"
                reason: Notes for pack authors.
            """;

    private static Evaluation evaluate(String red, String exclusions, Map<String, Optional<String>> observed) {
        return WholePackExpectation.load(red, exclusions).evaluate(PACK, MODELLED, observed);
    }

    private static Evaluation evaluate(
            String red, String exclusions, String known, Map<String, Optional<String>> observed) {
        return WholePackExpectation.load(red, exclusions, known).evaluate(PACK, MODELLED, observed);
    }

    private static final String NO_KNOWN_FAILURES = "known_failures: []\n";
    private static final String OPEN_PACKS_ISSUE = "spec-kitty/spec-kitty-open-packs#123";

    /** A known-failure list with one entry for {@code path}, pinned {@code at}, owned by {@code issue}. */
    private static String known(String path, String at, String issue) {
        return """
                known_failures:
                  - path: %s
                    at: "%s"
                    reason: The pack file holds two YAML documents.
                    issue: "%s"
                """.formatted(path, at, issue);
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
    class KnownFailures {

        private static final String A = "tactics/a.tactic.yaml";

        private FileResult resultOf(Evaluation evaluation, String path) {
            return evaluation.files().stream()
                    .filter(file -> file.path().equals(path))
                    .findFirst()
                    .orElseThrow();
        }

        @Test
        void aListedFileThatFailsWhereItIsPinnedIsAKnownFailureAndPasses() {
            Evaluation evaluation = evaluate(
                    MATCHING_RED,
                    MATCHING_EXCLUSIONS,
                    known(A, "steps[2].title", OPEN_PACKS_ISSUE),
                    withRed(A, "steps[2].title: x, <absent>"));

            assertThat(evaluation.problems()).isEmpty();
            assertThat(resultOf(evaluation, A)).satisfies(file -> {
                assertThat(file.status()).isEqualTo(Status.KNOWN_FAILURE);
                assertThat(file.detail())
                        .contains("steps[2].title: x, <absent>")
                        .contains("holds two YAML documents")
                        .contains(OPEN_PACKS_ISSUE);
            });
        }

        @Test
        void aListedFileThatNowRoundTripsFailsWithRemoveIt() {
            Evaluation evaluation = evaluate(
                    MATCHING_RED, MATCHING_EXCLUSIONS, known(A, "steps[2].title", OPEN_PACKS_ISSUE), ALL_GREEN);

            assertThat(evaluation.problems())
                    .containsExactly("UNEXPECTEDLY_GREEN tactics/a.tactic.yaml: it round-trips now; remove it from "
                            + "whole-pack/known-failures.yaml");
            assertThat(statuses(evaluation)).containsEntry(A, Status.GREEN);
        }

        @Test
        void anUnlistedFailureStillFails() {
            Evaluation evaluation = evaluate(
                    MATCHING_RED, MATCHING_EXCLUSIONS, NO_KNOWN_FAILURES, withRed(A, "steps[2].title: x, <absent>"));

            assertThat(evaluation.problems())
                    .containsExactly("UNEXPECTED_RED tactics/a.tactic.yaml (tactic): steps[2].title: x, <absent>");
            assertThat(statuses(evaluation)).containsEntry(A, Status.RED);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "#12",
                    "TODO",
                    "spec-kitty/spec-kitty-charter#12",
                    "spec-kitty/spec-kitty-open-packs#0",
                    "spec-kitty/spec-kitty-open-packs#12 and more",
                    "spec-kitty/spec-kitty-open-packs#TBD-other-shape",
                    "spec-kitty/spec-kitty-open-packs#TBD-",
                    "spec-kitty/spec-kitty-open-packs#TBD-preset-shape",
                    "spec-kitty/spec-kitty-open-packs#TBD-manifest-shape",
                    "spec-kitty/spec-kitty-charter#TBD-preset-shape"
                })
        void anEntryWithoutAnOpenPacksIssueFailsToLoad(String issue) {
            String yaml = known(A, "steps[2].title", issue);

            assertThatThrownBy(() -> WholePackExpectation.load(MATCHING_RED, MATCHING_EXCLUSIONS, yaml))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("has issue '" + issue + "'")
                    .hasMessageContaining("expected spec-kitty/spec-kitty-open-packs#<number>");
        }

        @Test
        void anOpenPacksIssueLoads() {
            // The #195 pending drafts are gone: OP-1 and OP-2 landed, so every entry names a filed issue.
            String yaml = known(A, "steps[2].title", OPEN_PACKS_ISSUE);

            assertThatCode(() -> WholePackExpectation.load(MATCHING_RED, MATCHING_EXCLUSIONS, yaml))
                    .doesNotThrowAnyException();
        }

        @Test
        void aListedFileWhoseDifferencesChangedFails() {
            Evaluation evaluation = evaluate(
                    MATCHING_RED,
                    MATCHING_EXCLUSIONS,
                    known(A, "steps[2].title", OPEN_PACKS_ISSUE),
                    withRed(A, "notes: x, <absent>"));

            assertThat(evaluation.problems())
                    .containsExactly("CHANGED_RED tactics/a.tactic.yaml (tactic): expected the first difference at "
                            + "'steps[2].title', now: notes: x, <absent>");
            assertThat(statuses(evaluation)).containsEntry(A, Status.RED);
        }

        @Test
        void aRefusedFileMustMatchTheWholeRefusal() {
            String yaml = known(A, "refused: a.tactic.yaml holds two documents", OPEN_PACKS_ISSUE);

            Evaluation same = evaluate(
                    MATCHING_RED, MATCHING_EXCLUSIONS, yaml, withRed(A, "refused: a.tactic.yaml holds two documents"));
            Evaluation other = evaluate(
                    MATCHING_RED,
                    MATCHING_EXCLUSIONS,
                    yaml,
                    withRed(A, "refused: a.tactic.yaml has an unknown field x"));

            assertThat(same.problems()).isEmpty();
            assertThat(statuses(same)).containsEntry(A, Status.KNOWN_FAILURE);
            assertThat(other.problems())
                    .containsExactly("CHANGED_RED tactics/a.tactic.yaml (tactic): expected the first difference at "
                            + "'refused: a.tactic.yaml holds two documents', now: "
                            + "refused: a.tactic.yaml has an unknown field x");
            assertThat(statuses(other)).containsEntry(A, Status.RED);
        }

        @Test
        void aRepeatedPathFailsToLoad() {
            String twice = known(A, "steps[2].title", OPEN_PACKS_ISSUE)
                    + known(A, "notes", OPEN_PACKS_ISSUE).replace("known_failures:\n", "");

            assertThatThrownBy(() -> WholePackExpectation.load(MATCHING_RED, MATCHING_EXCLUSIONS, twice))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("lists 'tactics/a.tactic.yaml' twice");
        }

        @Test
        void aRoundTripOnlyEntryLoadsFlaggedAndAnUnflaggedOneDefaultsToFalse() {
            String flagged = known(A, "refused: a.tactic.yaml holds two documents", OPEN_PACKS_ISSUE)
                    .replace("    reason:", "    round_trip_only: true\n    reason:");

            assertThat(WholePackExpectation.load(MATCHING_RED, MATCHING_EXCLUSIONS, flagged)
                            .knownFailures())
                    .containsExactly(new WholePackExpectation.KnownFailure(
                            A,
                            "refused: a.tactic.yaml holds two documents",
                            "The pack file holds two YAML documents.",
                            OPEN_PACKS_ISSUE,
                            true));
            assertThat(WholePackExpectation.load(
                                    MATCHING_RED, MATCHING_EXCLUSIONS, known(A, "steps[2].title", OPEN_PACKS_ISSUE))
                            .knownFailures())
                    .first()
                    .satisfies(failure -> assertThat(failure.roundTripOnly()).isFalse());
        }

        @Test
        void aRoundTripOnlyFlagThatIsNeitherTrueNorFalseIsRefused() {
            String yaml = known(A, "steps[2].title", OPEN_PACKS_ISSUE)
                    .replace("    reason:", "    round_trip_only: maybe\n    reason:");

            assertThatThrownBy(() -> WholePackExpectation.load(MATCHING_RED, MATCHING_EXCLUSIONS, yaml))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("round_trip_only 'maybe'");
        }

        @ParameterizedTest
        @ValueSource(strings = {"unknown", "path", "at", "reason", "issue"})
        void anUnknownFieldOrMissingFieldFailsToLoad(String change) {
            String entry = known(A, "steps[2].title", OPEN_PACKS_ISSUE);
            String yaml = switch (change) {
                case "unknown" -> entry + "    kind: tactic\n";
                case "path" -> entry.replace("  - path: " + A + "\n    at:", "  - at:");
                case "at" -> entry.replace("    at: \"steps[2].title\"\n", "");
                case "issue" -> entry.replace("    issue: \"" + OPEN_PACKS_ISSUE + "\"\n", "");
                default -> entry.replace("    reason: The pack file holds two YAML documents.\n", "");
            };
            String message = "unknown".equals(change) ? "unknown key 'kind'" : "has no " + change;

            assertThatThrownBy(() -> WholePackExpectation.load(MATCHING_RED, MATCHING_EXCLUSIONS, yaml))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(message);
        }

        @Test
        void anUnknownTopLevelKeyFailsToLoad() {
            assertThatThrownBy(() -> WholePackExpectation.load(MATCHING_RED, MATCHING_EXCLUSIONS, "files: []\n"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown key 'files'");
        }

        @Test
        void anEntryForAFileNotInThePackIsStale() {
            Evaluation evaluation = evaluate(
                    MATCHING_RED,
                    MATCHING_EXCLUSIONS,
                    known("tactics/z.tactic.yaml", "steps[2].title", OPEN_PACKS_ISSUE),
                    ALL_GREEN);

            assertThat(evaluation.problems())
                    .containsExactly("STALE_KNOWN_FAILURE tactics/z.tactic.yaml: no such file; remove it from "
                            + "whole-pack/known-failures.yaml");
        }

        @Test
        void anEntryForAnExcludedFileIsStale() {
            Evaluation evaluation =
                    evaluate(MATCHING_RED, MATCHING_EXCLUSIONS, known("README.md", "x", OPEN_PACKS_ISSUE), ALL_GREEN);

            assertThat(evaluation.problems())
                    .containsExactly("STALE_KNOWN_FAILURE README.md: the file is excluded; remove it from "
                            + "whole-pack/known-failures.yaml");
            assertThat(statuses(evaluation)).containsEntry("README.md", Status.EXCLUDED);
        }

        @Test
        void anEntryForAnUnmodelledKindCanNeverExpire() {
            String path = "procedures/p.procedure.yaml";

            Evaluation evaluation = evaluate(
                    MATCHING_RED,
                    MATCHING_EXCLUSIONS,
                    known(path, "no adapter models kind procedure", OPEN_PACKS_ISSUE),
                    ALL_GREEN);

            assertThat(evaluation.problems())
                    .containsExactly(
                            "KNOWN_FAILURE_UNMODELLED procedures/p.procedure.yaml (procedure): no adapter models "
                                    + "this kind, so the entry can never expire");
            assertThat(statuses(evaluation)).containsEntry(path, Status.EXPECTED_RED);
        }

        @Test
        void aFileOnBothListsIsAmbiguous() {
            String red = MATCHING_RED.replace("files: []", """
                    files:
                      - path: tactics/a.tactic.yaml
                        at: steps[2].title
                        reason: The writer drops the title.
                        issue: "#187"
                    """);

            Evaluation evaluation = evaluate(
                    red,
                    MATCHING_EXCLUSIONS,
                    known(A, "steps[2].title", OPEN_PACKS_ISSUE),
                    withRed(A, "steps[2].title: x, <absent>"));

            assertThat(evaluation.problems())
                    .containsExactly(
                            "AMBIGUOUS_ENTRY tactics/a.tactic.yaml: listed in both whole-pack/expected-red.yaml "
                                    + "and whole-pack/known-failures.yaml");
            assertThat(resultOf(evaluation, A)).satisfies(file -> {
                assertThat(file.status()).isEqualTo(Status.RED);
                assertThat(file.detail()).isEqualTo("steps[2].title: x, <absent>");
            });
        }

        @Test
        void aFileOnBothListsThatRoundTripsFailsOnceForEachList() {
            String red = MATCHING_RED.replace("files: []", """
                    files:
                      - path: tactics/a.tactic.yaml
                        at: steps[2].title
                        reason: The writer drops the title.
                        issue: "#187"
                    """);

            Evaluation evaluation =
                    evaluate(red, MATCHING_EXCLUSIONS, known(A, "steps[2].title", OPEN_PACKS_ISSUE), ALL_GREEN);

            assertThat(evaluation.problems())
                    .containsExactly(
                            "UNEXPECTEDLY_GREEN tactics/a.tactic.yaml: it round-trips now; remove it from "
                                    + "whole-pack/expected-red.yaml",
                            "AMBIGUOUS_ENTRY tactics/a.tactic.yaml: listed in both whole-pack/expected-red.yaml "
                                    + "and whole-pack/known-failures.yaml");
            assertThat(statuses(evaluation)).containsEntry(A, Status.GREEN);
        }

        @Test
        void theCheckedInListLoads() {
            assertThatCode(WholePackExpectation::fromClasspath).doesNotThrowAnyException();
        }

        @Test
        void theReportCountsKnownFailuresLast() {
            Evaluation evaluation = evaluate(
                    MATCHING_RED,
                    MATCHING_EXCLUSIONS,
                    known(A, "steps[2].title", OPEN_PACKS_ISSUE),
                    withRed(A, "steps[2].title: x, <absent>"));

            String report = WholePackReport.render(evaluation, "built-in", "abc", Map.of());

            assertThat(report)
                    .contains("- Files: 5, green 2, expected red 1, excluded 1, red 0, known failure 1\n")
                    .contains("| kind | files | green | expected red | excluded | red | known failure |")
                    .contains("| tactics/a.tactic.yaml | tactic | known failure | steps[2].title: x, <absent> — known "
                            + "failure: The pack file holds two YAML documents. (" + OPEN_PACKS_ISSUE + ") |\n");
            assertThat(WholePackReport.kindTable(evaluation)).contains("| tactic | 2 | 1 | 0 | 0 | 0 | 1 |");
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
        void aKindEntryNamesASchemaSoAPackWithNoFileOfItPasses() {
            String red = MATCHING_RED.replace("files: []", "") + """
                      - kind: agent-profile
                        reason: Not modelled.
                        issue: "#190"
                    files: []
                    """;

            assertThat(evaluate(red, MATCHING_EXCLUSIONS, ALL_GREEN).problems()).isEmpty();
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
        void anEmptyPackCannotPass() {
            Evaluation evaluation =
                    WholePackExpectation.load("{}", NO_EXCLUSIONS).evaluate(new TreeMap<>(), MODELLED, Map.of());

            assertThat(evaluation.problems())
                    .containsExactly("EMPTY_PACK: the census has no file, so the run proves nothing");
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

    /** #547: one gate run per example pack, and what only the packs together, or the file-kind table, can prove. */
    @Nested
    class AcrossTheExamplePacks {

        private static final String TWO_PACKS = """
                kinds: []
                files:
                  - path: built-in/tactics/a.tactic.yaml
                    at: l
                    reason: The writer drops the empty list.
                    issue: "#187"
                  - path: org-base/tactics/gone.tactic.yaml
                    at: l
                    reason: Only org-base has it.
                    issue: "#187"
                """;

        private static final Set<String> WITH_PROCEDURE = Set.of("tactic", "pack-descriptor", "procedure");

        @Test
        void aPacksRunIsJudgedByItsOwnEntriesAlone() {
            WholePackExpectation expectation = WholePackExpectation.load(TWO_PACKS, NO_EXCLUSIONS);
            SortedMap<String, String> pack = new TreeMap<>(PACK);
            pack.remove("procedures/p.procedure.yaml");
            pack.remove("README.md");
            Map<String, Optional<String>> red = withRed("tactics/a.tactic.yaml", "l: [], <absent>");

            assertThat(expectation
                            .forPack("built-in")
                            .evaluate(pack, MODELLED, red)
                            .problems())
                    .as("built-in: its entry pins the red, and org-base's entry is not stale here")
                    .isEmpty();
            assertThat(expectation
                            .forPack("org-base")
                            .evaluate(pack, MODELLED, red)
                            .problems())
                    .containsExactly(
                            "UNEXPECTED_RED tactics/a.tactic.yaml (tactic): l: [], <absent>",
                            "STALE_BASELINE tactics/gone.tactic.yaml: no such file; remove it from "
                                    + WholePackExpectation.EXPECTED_RED_RESOURCE);
        }

        @Test
        void anEntryThatNamesNoExamplePackIsUnscoped() {
            WholePackExpectation expectation = WholePackExpectation.load(
                    TWO_PACKS.replace("org-base/", "org/"),
                    MATCHING_EXCLUSIONS,
                    known("project/x.tactic.yaml", "x", OPEN_PACKS_ISSUE));

            assertThat(expectation.unscopedEntries(List.of("built-in", "org-base", "project")))
                    .containsExactly(
                            "STALE_BASELINE org/tactics/gone.tactic.yaml: names no example pack [built-in, org-base,"
                                    + " project]; fix it in " + WholePackExpectation.EXPECTED_RED_RESOURCE,
                            "STALE_EXCLUSION **/README.md: names no example pack [built-in, org-base, project]; fix it"
                                    + " in " + WholePackExpectation.EXCLUSIONS_RESOURCE);
        }

        @Test
        void everySchemaIsModelledOrListedAndEveryListedKindIsAnUnmodelledSchema() {
            WholePackExpectation expectation = WholePackExpectation.load(MATCHING_RED, NO_EXCLUSIONS);

            assertThat(expectation.schemaProblems(Set.of("tactic", "pack-descriptor", "procedure"), MODELLED))
                    .as("procedure is listed with a reason")
                    .isEmpty();
            assertThat(expectation.schemaProblems(Set.of("tactic", "procedure", "org-fragment", "guard"), MODELLED))
                    .containsExactly(
                            "UNMODELLED_SCHEMA guard: the round trip does not model this file-kinds.yaml schema, and "
                                    + WholePackExpectation.EXPECTED_RED_RESOURCE
                                    + " does not list it in kinds with a reason",
                            "UNMODELLED_SCHEMA org-fragment: the round trip does not model this file-kinds.yaml"
                                    + " schema, and " + WholePackExpectation.EXPECTED_RED_RESOURCE
                                    + " does not list it in kinds with a reason");
            assertThat(expectation.schemaProblems(Set.of("tactic", "pack-descriptor"), MODELLED))
                    .containsExactly("STALE_BASELINE kind procedure: not a file-kinds.yaml schema; remove it from "
                            + WholePackExpectation.EXPECTED_RED_RESOURCE);
        }

        @Test
        void aListedKindTheRoundTripModelsNowIsBaselinedModelled() {
            Evaluation evaluation = WholePackExpectation.load(MATCHING_RED, MATCHING_EXCLUSIONS)
                    .evaluate(
                            PACK,
                            WITH_PROCEDURE,
                            Map.of(
                                    "pack.yaml", Optional.empty(),
                                    "tactics/a.tactic.yaml", Optional.empty(),
                                    "tactics/b.tactic.yaml", Optional.empty(),
                                    "procedures/p.procedure.yaml", Optional.empty()));

            assertThat(evaluation.problems())
                    .singleElement()
                    .asString()
                    .startsWith("BASELINED_MODELLED_KIND procedure");
        }

        @Test
        void aModelledKindNoExamplePackHoldsFails() {
            SortedMap<String, String> org = census("drg/fragment.yaml", "org-fragment");
            Map<String, SortedMap<String, String>> byPack = Map.of("built-in", PACK, "org-base", org);

            assertThat(WholePackExpectation.emptyKindProblems(byPack, Set.of("tactic", "org-fragment")))
                    .as("each kind is held by one pack")
                    .isEmpty();
            assertThat(WholePackExpectation.emptyKindProblems(byPack, Set.of("tactic", "org-fragment", "guard")))
                    .containsExactly("EMPTY_MODELLED_KIND guard: no example pack has a file of this modelled kind");
        }

        @Test
        void withNoExpectationAFileOfAnUnmodelledKindFailsByKindAndPath() {
            Evaluation evaluation = WholePackExpectation.none().evaluate(PACK, MODELLED, ALL_GREEN);

            assertThat(evaluation.problems())
                    .containsExactly(
                            "UNEXPECTED_RED README.md (other:README.md): no adapter models kind other:README.md",
                            "UNEXPECTED_RED procedures/p.procedure.yaml (procedure): no adapter models kind procedure");
        }

        @Test
        void withNoExpectationAModelledFileTheRoundTripSkippedFails() {
            Map<String, Optional<String>> observed = new TreeMap<>(ALL_GREEN);
            observed.remove("tactics/b.tactic.yaml");

            assertThat(WholePackExpectation.none()
                            .evaluate(PACK, WITH_PROCEDURE, observed)
                            .problems())
                    .contains("UNEXPECTED_RED tactics/b.tactic.yaml (tactic): the round trip gave no outcome for this"
                            + " file");
        }

        @Test
        void aListedRefusedFileMustMatchTheWholeRefusal() {
            String red = MATCHING_RED.replace("files: []", """
                    files:
                      - path: tactics/a.tactic.yaml
                        at: "refused: read-back: the impacts edge is missing 'impacts'"
                        reason: The domain drops an impacts strength.
                        issue: "#477"
                    """);

            assertThat(evaluate(
                                    red,
                                    MATCHING_EXCLUSIONS,
                                    withRed(
                                            "tactics/a.tactic.yaml",
                                            "refused: read-back: the impacts edge is missing 'impacts'"))
                            .problems())
                    .isEmpty();
            assertThat(evaluate(
                                    red,
                                    MATCHING_EXCLUSIONS,
                                    withRed("tactics/a.tactic.yaml", "refused: read-back: an enhances edge is lost"))
                            .problems())
                    .singleElement()
                    .asString()
                    .startsWith("CHANGED_RED tactics/a.tactic.yaml (tactic)");
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

        private static final String SIDECAR = "tactics/a.tactic.yaml";
        private static final String REFUSAL = "refused: payload assets/assets/x.md does not exist";

        @Test
        void aRefusalThatOnlyExtendsThePinnedOneIsANewRed() {
            Evaluation evaluation = evaluate(
                    MATCHING_RED,
                    MATCHING_EXCLUSIONS,
                    known(SIDECAR, REFUSAL, OPEN_PACKS_ISSUE),
                    withRed(SIDECAR, REFUSAL + "!"));

            assertThat(evaluation.problems())
                    .singleElement()
                    .asString()
                    .startsWith("CHANGED_RED tactics/a.tactic.yaml (tactic)")
                    .endsWith("now: " + REFUSAL + "!");
            assertThat(statuses(evaluation)).containsEntry(SIDECAR, Status.RED);
        }

        @Test
        void theKnownFailuresAreReadableByOtherRatchets() {
            WholePackExpectation expectation =
                    WholePackExpectation.load(MATCHING_RED, NO_EXCLUSIONS, known(SIDECAR, REFUSAL, OPEN_PACKS_ISSUE));

            assertThat(expectation.knownFailures())
                    .containsExactly(new WholePackExpectation.KnownFailure(
                            SIDECAR, REFUSAL, "The pack file holds two YAML documents.", OPEN_PACKS_ISSUE));
        }

        @Test
        void theBundledListPinsNothingOnceOpenPacks75HasLanded() {
            assertThat(WholePackExpectation.fromClasspath().knownFailures()).isEmpty();
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
                    "README.md",
                    "tactic.graph.yaml",
                    "tactics/x/a.tactic.yaml",
                    "missions/m/mission.yaml",
                    "toolguides/GUIDE.md",
                    "toolguides/notes.txt")) {
                Files.createDirectories(pack.resolve(path).getParent());
                Files.writeString(pack.resolve(path), "x");
            }

            assertThat(WholePackCensus.of(pack))
                    .containsExactlyEntriesOf(new TreeMap<>(Map.of(
                            "missions/m/mission.yaml", "retired:*/missions/*/mission.yaml",
                            "README.md", "pack-document",
                            "pack.yaml", "pack-descriptor",
                            "tactic.graph.yaml", "graph-shard",
                            "tactics/x/a.tactic.yaml", "tactic",
                            "toolguides/GUIDE.md", "excluded:**/*.md",
                            "toolguides/notes.txt", "other:toolguides")));
        }

        @Test
        void aSymbolicLinkIsCountedNeverFollowed() throws IOException {
            Path pack = temp.resolve("built-in");
            Files.createDirectories(pack.resolve("tactics"));
            Files.writeString(pack.resolve("tactics/a.tactic.yaml"), "x");
            SymbolicLinks.create(pack.resolve("link.tactic.yaml"), pack.resolve("tactics/a.tactic.yaml"));

            assertThat(WholePackCensus.of(pack))
                    .containsEntry("link.tactic.yaml", WholePackCensus.SYMLINK)
                    .containsEntry("tactics/a.tactic.yaml", "tactic");
        }
    }

    /**
     * A file the table excludes is checked, not compared (#451): it is absent from the tree the store wrote, it is never
     * red and it needs no entry in a baseline list; a file nothing models that is not Markdown stays red.
     */
    @Nested
    class TheExcludedFiles {

        private static final String NOTE_KIND = WholePackCensus.EXCLUDED + "**/*.md";

        private final SortedMap<String, String> census = census(
                "pack.yaml", "pack-descriptor",
                "tactics/a.tactic.yaml", "tactic",
                "docs/notes.md", NOTE_KIND,
                "notes.txt", "other:notes.txt");

        private Evaluation judged(Map<String, Optional<String>> observed) {
            return WholePackExpectation.load("kinds: []\nfiles: []\n", NO_EXCLUSIONS)
                    .evaluate(census, MODELLED, observed);
        }

        @Test
        void theCensusTakesAnExcludedKindFromTheTable(@TempDir Path pack) throws IOException {
            Files.createDirectories(pack.resolve("docs"));
            Files.writeString(pack.resolve("docs/notes.md"), "# note\n");
            Files.writeString(pack.resolve("notes.txt"), "stray");
            Files.createDirectories(pack.resolve("missions/x/templates"));
            Files.writeString(pack.resolve("missions/x/templates/README.md"), "# t\n");

            assertThat(WholePackCensus.of(pack))
                    .containsEntry("docs/notes.md", NOTE_KIND)
                    .containsEntry(
                            "missions/x/templates/README.md",
                            WholePackCensus.EXCLUDED + "*/missions/*/templates/**/README.md")
                    .containsEntry("notes.txt", "other:notes.txt");
            assertThat(WholePackCensus.kindOf(pack.getFileName().toString(), "README.md"))
                    .as("the pack document is a file of the pack, not an excluded one")
                    .isEqualTo("pack-document");
        }

        @Test
        void anExcludedFileTheStoreDidNotWriteIsExcludedAndNeedsNoBaselineEntry() {
            Evaluation evaluation = judged(withNoteAbsent());

            assertThat(statuses(evaluation)).containsEntry("docs/notes.md", Status.EXCLUDED);
            assertThat(evaluation.files())
                    .filteredOn(file -> file.path().equals("docs/notes.md"))
                    .singleElement()
                    .satisfies(file ->
                            assertThat(file.detail()).contains("**/*.md").contains("not written"));
            assertThat(evaluation.problems())
                    .as("the note is judged apart; only the control is a problem")
                    .containsExactly(
                            "UNEXPECTED_RED notes.txt (other:notes.txt): no adapter models kind other:notes.txt");
        }

        @Test
        void aTextFileNothingModelsStaysRedWhileTheNoteIsExcluded() {
            Evaluation evaluation = judged(withNoteAbsent());

            assertThat(statuses(evaluation)).containsEntry("notes.txt", Status.RED);
        }

        @Test
        void anExcludedFileTheStoreWroteIsRed() {
            Map<String, Optional<String>> observed = withNoteAbsent();
            observed.put("docs/notes.md", Optional.of("the store wrote the excluded file docs/notes.md"));

            Evaluation evaluation = judged(observed);

            assertThat(statuses(evaluation)).containsEntry("docs/notes.md", Status.RED);
            assertThat(evaluation.problems())
                    .contains("EXCLUDED_WRITTEN docs/notes.md (" + NOTE_KIND
                            + "): the store wrote the excluded file docs/notes.md");
        }

        @Test
        void aRunThatNeverCheckedAnExcludedFileDoesNotPass() {
            Map<String, Optional<String>> observed = withNoteAbsent();
            observed.remove("docs/notes.md");

            assertThat(judged(observed).problems())
                    .anyMatch(problem ->
                            problem.startsWith("EXCLUDED_WRITTEN docs/notes.md") && problem.contains("did not check"));
        }

        @Test
        void aBaselineEntryOverAnExcludedFileIsStale() {
            Evaluation evaluation =
                    WholePackExpectation.load("""
                            kinds: []
                            files:
                              - path: docs/notes.md
                                at: refused
                                reason: Should never be listed.
                                issue: "#451"
                            """, NO_EXCLUSIONS).evaluate(census, MODELLED, withNoteAbsent());

            assertThat(evaluation.problems())
                    .contains("STALE_BASELINE docs/notes.md: the file is excluded; remove it from "
                            + WholePackExpectation.EXPECTED_RED_RESOURCE);
        }

        private Map<String, Optional<String>> withNoteAbsent() {
            Map<String, Optional<String>> observed = new TreeMap<>(ALL_GREEN);
            observed.remove("tactics/b.tactic.yaml");
            observed.put("docs/notes.md", Optional.empty());
            return observed;
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
            assertThat(WholePackReport.kindTable(evaluation)).contains("| tactic | 2 | 1 | 0 | 0 | 1 | 0 |");
        }
    }
}
