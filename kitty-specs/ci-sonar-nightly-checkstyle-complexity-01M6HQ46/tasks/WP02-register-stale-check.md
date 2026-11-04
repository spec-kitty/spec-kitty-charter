---
work_package_id: WP02
title: Stale-entry and register-format check
dependencies:
- WP01
requirement_refs:
- FR-002
- NFR-001
- C-001
- C-003
- SC-002
planning_base_branch: ci-sonar-nightly-checkstyle-complexity
merge_target_branch: ci-sonar-nightly-checkstyle-complexity
branch_strategy: Planning artifacts for this mission were generated on ci-sonar-nightly-checkstyle-complexity. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into ci-sonar-nightly-checkstyle-complexity unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-ci-sonar-nightly-checkstyle-complexity-01M6HQ46
base_commit: 9c2643b4eb0cb1908f579d6a7fa4abce37fa4bbf
created_at: '2026-11-04T11:01:01.352042+00:00'
subtasks:
- T005
- T006
- T007
- T008
- T009
agent: claude
history: []
agent_profile: implementer-ivan
authoritative_surface: build-logic/
create_intent:
- build-logic/src/main/kotlin/CheckstyleRegister.kt
- build-logic/src/test/kotlin/CheckstyleRegisterTest.kt
execution_mode: code_change
model: ''
owned_files:
- build-logic/src/main/kotlin/CheckstyleRegister.kt
- build-logic/src/main/kotlin/charter.hand-written.gradle.kts
- build-logic/src/test/kotlin/CheckstyleRegisterTest.kt
role: implementer
tags: []
tracker_refs: []
---

# WP02: Stale-entry and register-format check

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `implementer-ivan`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Make the exception register in `config/checkstyle/suppressions.xml` genuinely shrink-only. Checkstyle
never reports an unused suppression, so this WP adds a check, part of every hand-written module's
`check` and therefore of `verifyAll` and the PR gate. It fails when:

- an entry lists a rule that no longer fires on its method (stale or too wide);
- an entry names a file that does not exist;
- an entry lacks `rule:`, `file:`, `reason:` or `sunset:`;
- the header's `Size: N entries` disagrees with the number of entries.

Implementation command:
`spec-kitty agent action implement WP02 --agent claude --mission ci-sonar-nightly-checkstyle-complexity-01M6HQ46`

## Branch Strategy

- Planning branch: `ci-sonar-nightly-checkstyle-complexity`.
- Final merge target: `ci-sonar-nightly-checkstyle-complexity`, which reaches `main` by pull request.
- This WP depends on WP01, which defines the register format. Its lane is computed from `lanes.json`.

## Context

- **Spec:** FR-002, SC-002, NFR-001, C-001 (no new plugin or library), C-003.
- **Decision:** `01M6JHQW6K8TH51XPHZK5VNJNB`.
- **Research:** R3. **Data model:** `data-model.md` (register entry, probe report, invariants).
- **Precedent for a pure, unit-tested helper in build-logic:** `ServiceReleaseGate.kt` with
  `ServiceReleaseGateTest.kt`, and `OpenPacksCheckout.kt`. Follow their style: an `internal` object or
  functions, no Gradle API in the pure part, and AssertJ tests.
- **Constraints in `charter.hand-written.gradle.kts`:**
  - configuration-cache safe: no `project` access at execution time;
  - no reaching into other projects;
  - inputs and outputs declared.

  Gradle's built-in `Checkstyle` task type is the only Checkstyle runner allowed (C-001).

### Subtask T005: Pure register parser

**Purpose:** read the register into data.

**Steps:**
1. In `CheckstyleRegister.kt`, define an `Entry`:
   - `rules: Set<String>`, split from `checks` on `|`;
   - `fileRegex: String`;
   - `method: String?`, from `@text='…'` in `query`;
   - `commentFields: Map<String, String>`, with the `rule`, `file`, `reason` and `sunset` lines of the
     comment directly above;
   - `line: Int`, for messages.
2. Parse with the JDK's XML parser, with DTD loading disabled
   (`http://apache.org/xml/features/nonvalidating/load-external-dtd` = false): the build must not fetch
   `checkstyle.org`. Keep comments, so that you can read each entry's preceding comment, and read the
   header comment's `Size:` line.
3. Read both `suppress` and `suppress-xpath` elements. A `suppress` entry, one with no method, is
   allowed for non-complexity rules but must still carry the comment fields.

### Subtask T006: Pure comparator

**Purpose:** decide liveness without Gradle.

**Inputs:**
- the parsed register;
- the probe violations, as `(check simple name, absolute file, line)`;
- a `sourceLine(file, n)` reader;
- the root directory.

**Rules:**
- **Method of a violation.** For `CyclomaticComplexity`, `NPathComplexity` and `MethodLength`, the
  reported line is the declaration: take the identifier before the first `(` on that line, skipping
  annotations. For `NestedIfDepth`, the line is the nested `if`: walk upwards to the nearest
  method or constructor declaration (a line matching a declaration pattern at class-member
  indentation). Unit-test both.
- **Live entry.** An entry is live when, for **each** rule it lists, some violation has that check, a
  file whose name matches `fileRegex`, and that method.
- **Findings,** each naming the entry's line and what to do:
  - `STALE`: a rule no longer fires; narrow `checks` or delete the entry;
  - `MISSING_FILE`: the comment's `file:` does not exist under the root;
  - `MISSING_FIELD`: one of `rule`, `file`, `reason`, `sunset` is absent or blank, or `sunset` is not
    an ISO date;
  - `SIZE_MISMATCH`: the stated Size differs from the number of entries.
- **Scope per module.** A module's verifier judges liveness only for the entries whose `file:` path lies
  under that module's directory. Every module checks fields, Size and file existence. That repetition is
  cheap and keeps each task self-contained.

### Subtask T007: Unit tests

**Cases**, with in-memory registers and violation lists:
- a matching entry is live;
- an entry listing two rules where only one fires is STALE;
- an entry whose method no longer violates is STALE;
- a missing `reason` gives MISSING_FIELD, and a bad sunset date gives MISSING_FIELD;
- `Size: 3` with two entries gives SIZE_MISMATCH;
- a `file:` that does not exist gives MISSING_FILE;
- NestedIfDepth maps to the enclosing method;
- an annotated method declaration maps correctly;
- DTD loading is off: a register whose DOCTYPE points to an unreachable URL still parses.

### Subtask T008: Gradle wiring

**Steps,** in `charter.hand-written.gradle.kts`:
1. **Probe config.** A task `checkstyleRegisterProbeConfig` writes
   `build/checkstyle-register/probe.xml`. It is `config/checkstyle/checkstyle.xml` with the
   `SuppressionFilter` and `SuppressionXpathFilter` modules removed (a regex over the text is fine;
   assert both were found, and fail clearly otherwise). Inputs: the config file. Output: the probe file.
2. **Probe runs.** For each source set (`main`, `test`), register a `Checkstyle` task
   `checkstyleRegisterProbe<SourceSet>` with:
   - `configFile` set to the probe;
   - `configDirectory` the same as the real task, so `${config_loc}` still resolves;
   - `source` set to the source set's Java files, filtered to the names the register's `files`
     patterns match (read the register at configuration time through a provider, or filter in a
     `PatternFilterable` spec);
   - `isIgnoreFailures = true`, `maxWarnings` and `maxErrors` unbounded;
   - the XML report on, and the HTML report off.

   If no register file falls in the module, the task has `NO-SOURCE`. That is fine.
3. **Verifier.** A task `checkCheckstyleRegister` reads the probe XML reports and the register, calls the
   pure comparator, and fails with the findings listed one per line. Wire it into `check`.
4. **Cost.** The probe runs only over the register's few files. Measure the module timings and keep the
   total added to `verifyAll` under 30 s (NFR-001).

### Subtask T009: End-to-end proof

1. `./gradlew verifyAll` passes with WP01's register.
2. **Planted stale entry:** add a temporary entry for a method that does not break the rule. `verifyAll`
   fails with STALE naming it. Remove it, and the build passes.
3. **Planted Size error:** change `Size`, see SIZE_MISMATCH, then revert.
4. Paste both failure outputs into the review evidence.

## Definition of Done

- The parser, the comparator and the unit tests are in build-logic. `./gradlew -p build-logic check`
  passes.
- `checkCheckstyleRegister` runs in each hand-written module's `check`, and `verifyAll` passes.
- The planted stale and Size failures are shown, then reverted.
- No new plugin or library, and no network access during the build.

## Risks and reviewer guidance

- **The reviewer checks:**
  - liveness requires every listed rule, not any;
  - DTD fetching is off;
  - the configuration cache stays valid. Run `./gradlew verifyAll --configuration-cache` twice; the
    second run must reuse the entry.
- **Name mapping is textual.** Keep the declaration regexes simple and well-tested. If a register
  method cannot be mapped, the verifier fails loudly; it never passes silently.
