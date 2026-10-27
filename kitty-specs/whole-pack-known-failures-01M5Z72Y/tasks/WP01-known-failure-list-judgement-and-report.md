---
work_package_id: WP01
title: Known-failure list, judgement and report
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-005
- FR-006
- FR-007
- FR-008
- NFR-001
- NFR-002
- NFR-003
- C-001
- C-002
- C-003
- SC-001
- SC-002
- SC-003
planning_base_branch: feat/whole-pack-known-failures
merge_target_branch: feat/whole-pack-known-failures
branch_strategy: Planning artifacts for this mission were generated on feat/whole-pack-known-failures. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/whole-pack-known-failures unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-whole-pack-known-failures-01M5Z72Y
base_commit: 352c5f56dc4f401d89c7f6c53f63010a3acb5b45
created_at: '2026-10-27T18:12:57.657120+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
- T007
phase: Phase 1 - Gate harness
history:
- at: '2026-10-27T18:10:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/test/resources/whole-pack/known-failures.yaml
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectation.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackReport.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectationTest.java
- service/packs/src/test/resources/whole-pack/known-failures.yaml
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 – Known-failure list, judgement and report

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter (or any user-defined profile), and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## ⚠️ IMPORTANT: Review Feedback

**Read this first if you are implementing this task!**

- **Has review feedback?**: Check the `review_ref` field in the event log (via `spec-kitty agent tasks status`).
- **You must address all feedback** before your work is complete. Feedback items are your implementation TODO list.
- **Report progress**: As you address each feedback item, record what you changed with `spec-kitty agent tasks add-history <WPID> --note "..."` (stored in the status event log).

---

## Review Feedback

*[If this WP was returned from review, the reviewer feedback reference appears in the status event log.]*

---

## Markdown Formatting

Wrap HTML/XML tags in backticks: `` `<div>` ``, `` `<script>` ``
Use language identifiers in code blocks: ````java`, ````bash`

---

## Objectives & Success Criteria

The whole-pack gate gains a third checked-in list, `whole-pack/known-failures.yaml`, for pack files that fail the
round trip because of a pack inconsistency tracked by an open-packs issue (ADR-0036 decision 5, open question 1).

Done when:

- A listed file that fails at its pinned `at` is `KNOWN_FAILURE` and raises no problem (FR-002).
- A listed file that round-trips raises `UNEXPECTEDLY_GREEN … remove it from whole-pack/known-failures.yaml` (FR-003).
- An entry whose `issue` is missing or not `spec-kitty/spec-kitty-open-packs#<n>` fails to load (FR-004).
- An unlisted red file still raises `UNEXPECTED_RED` (FR-005).
- A listed file whose differences changed raises `CHANGED_RED`; for a refused file the whole refusal must match (FR-006).
- Stale, excluded, unmodelled-kind and double-listed entries raise their problems; repeats and malformed entries fail to load (FR-007).
- The report counts known failures in the totals line and in a last per-kind column (FR-008).
- Tests assert exact codes and paths (NFR-001); an untagged test loads the checked-in list (NFR-002); the report stays deterministic (NFR-003).

## Context & Constraints

- Read first: `kitty-specs/whole-pack-known-failures-01M5Z72Y/spec.md`, `plan.md`, `data-model.md` (the exact problem texts), `research.md` (squad findings).
- Charter: `.kittify/charter/charter.md` — test first, no fake green, outcome assertions; Java style gates in `verifyAll`.
- ADR: `docs/adr/0036-round-trip-fidelity-is-equality-of-meaning.md`, decision 5 and open question 1.
- **Additive only (C-001)**: do not edit `service/packs/src/test/resources/whole-pack/expected-red.yaml`, `exclusions.yaml`, `WholePackRoundTripTest.java`, kind registrations or any production source. Four Wave 1 sessions (#190–#193) edit the gate in parallel. Keep existing method signatures; add, don't rework.
- **No production code (C-002)**: everything lives under `service/packs/src/test/`.
- The red detail format is set by `WholePackRoundTripTest.redDetail`: `"<path1> ; <path2>: <first difference>"`, or `"refused: <reason>"` for a refused file. `WholePackExpectation.atOf(red)` returns the text before the first `": "` (so `refused` for every refusal).

## Branch Strategy

- **Strategy**: populated by finalize-tasks
- **Planning base branch**: `feat/whole-pack-known-failures`
- **Merge target branch**: `feat/whole-pack-known-failures` (the mission then lands on `main` by PR)

> Execution worktrees are allocated per computed lane from `lanes.json`. Implement with
> `spec-kitty agent action implement WP01 --agent claude --mission whole-pack-known-failures-01M5Z72Y`.

## Subtasks & Detailed Guidance

### Subtask T001 – Ship `known-failures.yaml`

- **Purpose**: The list the gate reads; it ships empty (no pack inconsistency is known at the current pin).
- **Steps**: Create `service/packs/src/test/resources/whole-pack/known-failures.yaml`:

  ```yaml
  # The whole-pack gate's known-failure list (#247, ADR-0036 decision 5 and open question 1): files of a modelled kind
  # that fail the round trip because the pack's own data is inconsistent, while the fix is pending in
  # spec-kitty-open-packs. Not for service defects (those are expected red, in expected-red.yaml) and not for files the
  # gate does not compare (exclusions.yaml).
  #
  # Each entry: path (pack-relative), at (where the file differs, exactly as the report's detail shows it before ": ";
  # for a refused file, the whole detail "refused: ..."), reason (one line) and issue
  # (spec-kitty/spec-kitty-open-packs#<number>). One entry per file.
  #
  # Self-expiring: a listed file that round-trips fails the gate (UNEXPECTEDLY_GREEN). Remove its entry in the change
  # that moves openPacksRef to the commit with the fix.
  known-failures: []
  ```
- **Files**: the resource above (new).
- **Notes**: Keep the header wording consistent with the docs WP02 writes.

### Subtask T002 – Failing tests: the four acceptance cases, moved difference, refusal match

- **Purpose**: Specification by example; these must fail before T004–T006.
- **Steps**: In `WholePackExpectationTest`, add a `@Nested class KnownFailures` (after `TheRatchet`, before `TheReport`). Add a helper next to the existing `evaluate(...)`:

  ```java
  private static Evaluation evaluate(
          String red, String exclusions, String known, Map<String, Optional<String>> observed) {
      return WholePackExpectation.load(red, exclusions, known).evaluate(PACK, MODELLED, observed);
  }
  ```

  and a fixture builder for one entry, e.g.

  ```java
  private static String known(String path, String at, String issue) {
      return """
              known-failures:
                - path: %s
                  at: "%s"
                  reason: The pack file holds two YAML documents.
                  issue: %s
              """.formatted(path, at, issue);
  }
  ```

  Tests (each asserts exact problem strings with `containsExactly` and, where relevant, the file's status via `statuses(...)` or `filteredOn(...).singleElement()`):
  1. `aListedFileThatFailsWhereItIsPinnedIsAKnownFailureAndPasses` — `withRed("tactics/a.tactic.yaml", "steps[2].title: x, <absent>")`, entry `at: steps[2].title` → `problems()` empty; status `KNOWN_FAILURE`; detail contains the red detail, the reason and `spec-kitty/spec-kitty-open-packs#123`.
  2. `aListedFileThatNowRoundTripsFailsWithRemoveIt` — `ALL_GREEN`, same entry → exactly `"UNEXPECTEDLY_GREEN tactics/a.tactic.yaml: it round-trips now; remove it from whole-pack/known-failures.yaml"`; status `GREEN`.
  3. `anUnlistedFailureStillFails` — red file, empty known list (`"known-failures: []\n"`) → exactly `"UNEXPECTED_RED tactics/a.tactic.yaml (tactic): …"`.
  4. `anEntryWithoutAnOpenPacksIssueFailsToLoad` — parameterize over `"#12"`, `"TODO"`, `"spec-kitty/spec-kitty-charter#12"`, `"spec-kitty/spec-kitty-open-packs#0"`, and an entry with the `issue` line removed → `assertThatThrownBy(() -> WholePackExpectation.load(MATCHING_RED, MATCHING_EXCLUSIONS, yaml)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(...)` naming the field or the expected format. Use `@ParameterizedTest @ValueSource` if `junit-jupiter-params` is on the test classpath (check `service/packs/build.gradle.kts` / the version catalog); otherwise one test per value.
  5. `aListedFileWhoseDifferencesChangedFails` — entry `at: steps[2].title`, red `"notes: x, <absent>"` → exactly `"CHANGED_RED tactics/a.tactic.yaml (tactic): expected the first difference at 'steps[2].title', now: notes: x, <absent>"`; status `RED`.
  6. `aRefusedFileMustMatchTheWholeRefusal` — entry `at: "refused: a.tactic.yaml holds two documents"`; observed `"refused: a.tactic.yaml holds two documents"` → passes, `KNOWN_FAILURE`; observed `"refused: a.tactic.yaml has an unknown field x"` → `CHANGED_RED …`.
- **Files**: `WholePackExpectationTest.java`.
- **Parallel?**: No (same file as T003).
- **Notes**: Positive control for NFR-001: case 1 and case 3 use the same fixture except the entry, so a do-nothing change fails case 1.

### Subtask T003 – Failing tests: load refusals, misplaced entries, the real list

- **Steps** (same nested class):
  1. `aRepeatedPathFailsToLoad` — two entries with the same path → `IllegalArgumentException` containing `"lists 'tactics/a.tactic.yaml' twice"`.
  2. `anUnknownFieldOrMissingFieldFailsToLoad` — an entry with `kind: tactic`, and entries missing `at` / `reason` → `IllegalArgumentException`.
  3. `anUnknownTopLevelKeyFailsToLoad` — `files: []` in the known list → `IllegalArgumentException` containing `unknown key 'files'`.
  4. `anEntryForAFileNotInThePackIsStale` — path `tactics/z.tactic.yaml` → exactly `"STALE_KNOWN_FAILURE tactics/z.tactic.yaml: no such file; remove it from whole-pack/known-failures.yaml"`.
  5. `anEntryForAnExcludedFileIsStale` — path `README.md` with `MATCHING_EXCLUSIONS` → contains `"STALE_KNOWN_FAILURE README.md: the file is excluded; remove it from whole-pack/known-failures.yaml"`.
  6. `anEntryForAnUnmodelledKindCanNeverExpire` — path `procedures/p.procedure.yaml` → contains `"KNOWN_FAILURE_UNMODELLED procedures/p.procedure.yaml (procedure): no adapter models this kind, so the entry can never expire"`; the file keeps its `EXPECTED_RED` status from the kind entry.
  7. `aFileOnBothListsIsAmbiguous` — the same path in `expected-red.yaml` `files:` and the known list → contains `"AMBIGUOUS_ENTRY tactics/a.tactic.yaml: listed in both whole-pack/expected-red.yaml and whole-pack/known-failures.yaml"`; the file's status is `RED` (not expected red, not known failure).
  8. `theCheckedInListLoads` — `WholePackExpectation.fromClasspath()` does not throw (NFR-002). This class is untagged, so it runs in `:packs:test` and `verifyAll`. Add a one-line assertion that the loaded expectation judges an empty pack's census without a known-failure problem, or simply `assertThatCode(WholePackExpectation::fromClasspath).doesNotThrowAnyException()`.
- **Files**: `WholePackExpectationTest.java`.

### Subtask T004 – `KnownFailure`, the 3-arg `load`, the issue check

- **Steps** in `WholePackExpectation.java`:
  1. Constant `static final String KNOWN_FAILURES_RESOURCE = "whole-pack/known-failures.yaml";` next to the other two.
  2. Record with Javadoc in the style of `FileEntry`:

     ```java
     /**
      * A file of a modelled kind that fails because the pack's own data is inconsistent, while the fix is pending in
      * spec-kitty-open-packs (ADR-0036 decision 5). It expires itself: once the file round-trips, the gate fails.
      *
      * @param path the pack-relative path
      * @param at where the file differs (as {@link #atOf}), or the whole red detail for a refused file
      * @param reason why it fails
      * @param issue the open-packs issue, {@code spec-kitty/spec-kitty-open-packs#<number>}
      */
     record KnownFailure(String path, String at, String reason, String issue) {}
     ```
  3. A field `private final List<KnownFailure> knownFailures;` set in the constructor (`List.copyOf`).
  4. `load(String expectedRedYaml, String exclusionsYaml)` delegates to `load(expectedRedYaml, exclusionsYaml, "known-failures: []\n")`; add the 3-arg `load` with Javadoc (`@throws IllegalArgumentException` also for a malformed issue). Parse with `document(knownYaml, KNOWN_FAILURES_RESOURCE, Set.of("known-failures"))` and `entries(known.get("known-failures"), "known-failures", List.of("path", "at", "reason", "issue"))`.
  5. Issue check: `private static final Pattern OPEN_PACKS_ISSUE = Pattern.compile("spec-kitty/spec-kitty-open-packs#[1-9]\\d*");` and, per entry, `if (!OPEN_PACKS_ISSUE.matcher(issue).matches()) throw new IllegalArgumentException("known-failures entry " + path + " has issue '" + issue + "'; expected spec-kitty/spec-kitty-open-packs#<number> (ADR-0036 decision 5)");`.
  6. `fromClasspath()` passes `resource(KNOWN_FAILURES_RESOURCE)` as the third argument.
  7. Update the class Javadoc: three lists, one sentence on the known-failure list.

### Subtask T005 – `KNOWN_FAILURE` and its judgement

- **Steps**:
  1. Append `KNOWN_FAILURE` to `Status` **after `RED`**, with Javadoc ("Differs because of a pack inconsistency tracked upstream, and is listed as a known failure"). Appending keeps `byKind()` ordering and existing tables stable (decision `01M5Z7T5QET0XX0GDP5GE1ZNSH`).
  2. In `evaluate`, build `Map<String, KnownFailure> knownEntries` and `Set<String> ambiguous` = paths in both `fileEntries` and `knownEntries`.
  3. Per file (after the exclusion branch, which stays first):
     - Green branch: if `knownEntries` has the path and it is not ambiguous, add `"UNEXPECTEDLY_GREEN " + path + ": it round-trips now; remove it from " + KNOWN_FAILURES_RESOURCE`.
     - Red branch: if the path is ambiguous → `Status.RED`, detail = red, no per-file problem (the whole-list check reports it). Else if modelled and a known entry exists → compare `matches(red, entry.at())`: on match `KNOWN_FAILURE` with detail `red + " — known failure: " + reason + " (" + issue + ")"`; otherwise `RED` plus the existing `CHANGED_RED` text. Keep the existing `fileEntry` / `kindEntry` / `UNEXPECTED_RED` branches untouched for every other case. A known entry on an unmodelled kind is ignored per file.
     - `matches`: `"refused".equals(atOf(red)) ? red.equals(at) : atOf(red).equals(at)`. Applies to known failures only (the `files:` behaviour is unchanged).
  4. Whole-list checks, after the existing `files` loop:
     - not in census → `STALE_KNOWN_FAILURE <path>: no such file; remove it from whole-pack/known-failures.yaml`;
     - excluded → `STALE_KNOWN_FAILURE <path>: the file is excluded; remove it from whole-pack/known-failures.yaml`;
     - census kind not modelled → `KNOWN_FAILURE_UNMODELLED <path> (<kind>): no adapter models this kind, so the entry can never expire`;
     - ambiguous → `AMBIGUOUS_ENTRY <path>: listed in both whole-pack/expected-red.yaml and whole-pack/known-failures.yaml`.
  5. Keep `evaluate` readable: extract the known-failure branch into a private method if the lambda grows past ~15 lines (Checkstyle and Error Prone run in `verifyAll`). `@Nullable` from `org.jspecify` for lookups, as the existing code does (NullAway).

### Subtask T006 – The report

- **Steps** in `WholePackReport.java`:
  1. `label`: `case KNOWN_FAILURE -> "known failure";`. The totals line loops over `Status.values()`, so it now ends `, known failure N`.
  2. `kindTable`: header `| kind | files | green | expected red | excluded | red | known failure |`, separator gains `| ---: |`, each row appends `.append(" | ").append(counts.getOrDefault(Status.KNOWN_FAILURE, 0))` before `" |\n"`.
  3. Update the class Javadoc if it names the columns.
- **Tests** (in `TheReport` or `KnownFailures`): render an evaluation with one known failure and assert the rendered text `contains("known failure 1")` in the totals line, the kind table `contains("| tactic | 2 | 1 | 0 | 0 | 0 | 1 |")`, and the file row contains the issue. The existing `contains("| tactic | 2 | 1 | 0 | 0 | 1 |")` assertion must stay as it is and still pass (substring of the longer row). Determinism (NFR-003) is covered by the existing byte-identical test; extend it with a known failure if cheap.

### Subtask T007 – Verify

- **Steps**:

  ```bash
  export JAVA_HOME=/usr/lib/jvm/temurin-25
  ./gradlew :packs:test --tests '*WholePackExpectationTest*'
  ./gradlew :packs:wholePackRoundTrip      # needs ../spec-kitty-open-packs at openPacksRef; report shows "known failure 0"
  ./gradlew verifyAll                       # compiler (Error Prone, NullAway), Checkstyle, Spotless, all tests
  ```

  If Maven Central answers 429, wait and retry (docs/development/cloud-session-setup.md). Run `./gradlew spotlessApply` to fix formatting. Record the commands and pass counts with `spec-kitty agent tasks add-history WP01 --note "..."`.
- **Notes**: Before finishing, temporarily revert the `KNOWN_FAILURE` branch in `evaluate` and confirm tests 1, 2 and 6 fail, then restore (mutation check for NFR-001; do not commit the revert).

## Test Strategy

- Tests are required (NFR-001, NFR-002). They live in `WholePackExpectationTest` (untagged, so in `:packs:test` and `verifyAll`).
- The compiler check is part of the gate: `./gradlew :packs:compileTestJava` must pass with Error Prone and NullAway; a green test run is not enough.

## Risks & Mitigations

- **Sibling collisions**: siblings may add tests to `WholePackExpectationTest` and remove `expected-red.yaml` entries. Keep your additions in the new nested class and new methods; rebase on `main` before review.
- **Exhaustive switch**: adding the enum value breaks `WholePackReport.label` until T006; do T005 and T006 together.
- **Masking**: a too-loose match would hide new losses. The refused-file rule compares the whole message.

## Review Guidance

- Each of the four #247 cases has a test asserting the exact problem string or status, and case 1 fails with the new branch removed.
- `expected-red.yaml`, `exclusions.yaml`, `WholePackRoundTripTest.java` and production sources are untouched.
- The new column and status are last; the old kind-table assertion is unchanged.
- The checked-in list is loaded by an untagged test.
- Compiler diagnostics (Error Prone, NullAway) and Checkstyle passed, not just the tests.

## Progress & Status

Progress, history, and status all live in the status event log (`status.events.jsonl`) — never in this prompt file.

- **Record a progress note**: `spec-kitty agent tasks add-history WP01 --note "<what you did>"`
- **Change WP status**: `spec-kitty agent tasks move-task WP01 --to <status>`
- **View history and status**: `spec-kitty agent tasks status`
