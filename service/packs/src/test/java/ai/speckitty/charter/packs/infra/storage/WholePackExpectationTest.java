package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
                    "spec-kitty/spec-kitty-charter#TBD-preset-shape"
                })
        void anEntryWithoutAnOpenPacksIssueFailsToLoad(String issue) {
            String yaml = known(A, "steps[2].title", issue);

            assertThatThrownBy(() -> WholePackExpectation.load(MATCHING_RED, MATCHING_EXCLUSIONS, yaml))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("has issue '" + issue + "'")
                    .hasMessageContaining("expected spec-kitty/spec-kitty-open-packs#<number>");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "spec-kitty/spec-kitty-open-packs#12",
                    "spec-kitty/spec-kitty-open-packs#TBD-preset-shape",
                    "spec-kitty/spec-kitty-open-packs#TBD-manifest-shape"
                })
        void anOpenPacksIssueOrOneOfThePendingDraftsLoads(String issue) {
            String yaml = known(A, "steps[2].title", issue);

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
        void theBundledListPinsExactlyTheSixBuiltInSidecarsTheTwoBuiltInPresetsAndTheManifestAsKnownFailures() {
            WholePackExpectation bundled = WholePackExpectation.fromClasspath();

            assertThat(bundled.knownFailures())
                    .filteredOn(failure -> failure.path().startsWith("assets/"))
                    .extracting(WholePackExpectation.KnownFailure::path, WholePackExpectation.KnownFailure::at)
                    .containsExactlyInAnyOrder(
                            sidecar("assets/docs_structural_lint.py"),
                            sidecar("assets/audiences/agentic-framework-core-team.md"),
                            sidecar("assets/audiences/automation_agent.md"),
                            sidecar("assets/audiences/line_manager.md"),
                            sidecar("assets/audiences/nontech_educator.md"),
                            sidecar("assets/audiences/software_engineer.md"))
                    .hasSize(6);
            assertThat(bundled.knownFailures())
                    .filteredOn(failure -> failure.path().startsWith("assets/"))
                    .allSatisfy(
                            failure -> assertThat(failure.issue()).isEqualTo("spec-kitty/spec-kitty-open-packs#75"));
            // #195: the two built-in presets and the manifest are not in the sanitized shapes until the pending
            // upstream drafts land.
            assertThat(bundled.knownFailures())
                    .filteredOn(failure -> failure.path().startsWith("presets/"))
                    .allSatisfy(failure -> {
                        assertThat(failure.at()).startsWith("refused: ");
                        assertThat(failure.issue()).isEqualTo("spec-kitty/spec-kitty-open-packs#TBD-preset-shape");
                    })
                    .extracting(WholePackExpectation.KnownFailure::path)
                    .containsExactly("presets/default.yaml", "presets/minimal.yaml");
            assertThat(bundled.knownFailures())
                    .filteredOn(failure -> !failure.path().startsWith("assets/")
                            && !failure.path().startsWith("presets/"))
                    .singleElement()
                    .satisfies(failure -> {
                        assertThat(failure.path()).isEqualTo("pack-manifest.yaml");
                        assertThat(failure.at()).startsWith("refused: const violated at /schema_version; ");
                        assertThat(failure.issue()).isEqualTo("spec-kitty/spec-kitty-open-packs#TBD-manifest-shape");
                    });
        }

        private static org.assertj.core.groups.Tuple sidecar(String payload) {
            return org.assertj.core.groups.Tuple.tuple(
                    payload + ".asset.yaml", "refused: payload assets/" + payload + " does not exist");
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
                            "pack.md", "pack-document",
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
            assertThat(WholePackReport.kindTable(evaluation)).contains("| tactic | 2 | 1 | 0 | 0 | 1 | 0 |");
        }
    }
}
