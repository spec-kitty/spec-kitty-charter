---
work_package_id: WP01
title: Packs identifier patterns cannot recurse and are capped
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- NFR-001
- NFR-002
- C-003
- SC-001
- SC-003
planning_base_branch: fix/regex-bounds-and-sonar-cleanup
merge_target_branch: fix/regex-bounds-and-sonar-cleanup
branch_strategy: Planning artifacts for this mission were generated on fix/regex-bounds-and-sonar-cleanup. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into fix/regex-bounds-and-sonar-cleanup unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-regex-bounds-and-sonar-cleanup-01M706DF
base_commit: 880b6e78866afb113d17a621cb19d06412d49718
created_at: '2026-11-09T13:09:57.890201+00:00'
subtasks:
- T001
- T002
- T003
- T004
history:
- timestamp: '2026-11-09T13:20:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/IdentifierBoundsTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GuardIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ProcedureIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ToolguideIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/GuardImplementation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Executable.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Preset.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/SkillIdentifier.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/IdentifierBoundsTest.java
role: implementer
tags: []
tracker_refs: []
---

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else, then load
`spec-kitty charter context --action implement`.

## Objective

Make the seven packs identifier types refuse an id of any length with their ordinary refusal
(`IllegalArgumentException`, or the type's existing exception), never a `StackOverflowError`. Kebab ids are capped
at 64 characters and `Executable` at 255, checked **before** the pattern match. The accepted language at or below
each cap must not change. Covers FR-001, FR-002, FR-003, FR-004 and issue #440.

## Context

- Java's regex engine recurses once per repetition of a group such as `(-[a-z0-9]+)*`. A 10 001-character id
  (`"a" + "-a".repeat(5_000)`) already overflows. Non-capturing `(?:…)*` alone still overflows; possessive `*+`/`++`
  does not (research.md R3).
- Possessive is language-preserving here because each repeated segment starts with a character (`-`, `/`) that the
  class before it cannot consume. The research squad fuzzed 300 000 strings per pattern with 0 differences.
- `Preset` (`MAX_ID_LENGTH=64`) and `SkillIdentifier` (`MAX_LENGTH=64`) already cap; confirm the length test runs
  before `matcher(...)` (left side of `&&` or an earlier `if`). If not, reorder.
- `GuardImplementation` reads `m.group(1..3)` (lines ~30-35). Keep the capturing groups; make only the quantifiers
  inside possessive.

| File | Today | Target |
|---|---|---|
| `domain/GuardIdentifier.java:15` | `^[a-z0-9]+(-[a-z0-9]+)*$` | `^[a-z0-9]++(?:-[a-z0-9]++)*+$`, cap 64 |
| `domain/ProcedureIdentifier.java:15` | kebab | same shape, cap 64 |
| `domain/ToolguideIdentifier.java:16` | letter-first kebab | `[a-z][a-z0-9]*+(?:-[a-z0-9]++)*+`, cap 64 |
| `domain/GuardImplementation.java:16` | groups 1-3 | quantifiers possessive, groups kept; pack-name and guard parts each ≤ 64 |
| `domain/Executable.java:29` | `…(/[A-Za-z0-9_][A-Za-z0-9_.-]*)*` | `[A-Za-z0-9_][A-Za-z0-9_.-]*+(?:/[A-Za-z0-9_][A-Za-z0-9_.-]*+)*+`, cap 255 |
| `domain/Preset.java:47` | kebab, cap exists | possessive, cap order confirmed |
| `domain/SkillIdentifier.java:15` | kebab, cap exists | possessive, cap order confirmed |

Read each file fully before editing: the refusal message style and the exception type must stay as they are. A new
cap refusal reuses the type's existing refusal path, with a message naming the cap, e.g.
`"guard id is longer than 64 characters"`.

## Subtasks

### T001: Red tests first

Create `service/packs/src/test/java/ai/speckitty/charter/packs/domain/IdentifierBoundsTest.java` (JUnit 5,
AssertJ, the style of the module's other domain tests). For each type:

- `"a" + "-a".repeat(10_000)` (and for Executable `"a" + "/a".repeat(10_000)`) is **refused** with the type's
  ordinary exception: `assertThatThrownBy(...).isInstanceOf(IllegalArgumentException.class)` (or the type's
  exception). Today this throws `StackOverflowError`, so the test is red for the defect's own reason.
- An id of exactly 64 (255 for Executable) valid characters is accepted; 65 (256) is refused. Today 65 is accepted
  for the uncapped types, which is the second red reason.

Run `./gradlew :packs:test --tests '*IdentifierBoundsTest'` (JAVA_HOME=/usr/lib/jvm/temurin-25) and record the
failing test names and the `StackOverflowError` or acceptance in the commit body.

### T002: Possessive rewrite

Rewrite each pattern as the table above. Keep `^`/`$` anchoring exactly as today, and `matches()` versus `find()`
as today.

### T003: Caps before matching

Add a `MAX_LENGTH` constant per type (64, or 255 for Executable) and check `value.length() > MAX_LENGTH` before the
matcher. For GuardImplementation, cap each captured part, or the whole string at 64 + separators + 64; choose the
per-part check and document it in the Javadoc.

### T004: Edge-string equivalence

In the same test class, a `@ParameterizedTest` table per type pins today's verdicts: `""`, `"-"`, `"a-"`, `"-a"`,
`"a--b"`, `"A"`, `"a-b"`, `"1a"` (where letter-first matters), and for Executable `"a/"`, `"/a"`, `"a//b"`,
`"bin/tool.sh"`. Compute expected verdicts from the current code before the rewrite (run the table against the old
code first; it must pass both before and after).

## Definition of Done

- Red run recorded for T001; all of `:packs:test --tests '*IdentifierBoundsTest'` green after.
- The existing tests of each type (GuardTest, ProcedureEntityTest, SkillTest, ToolguideTest, PresetTest, …) still
  pass: `./gradlew :packs:test --tests '*Guard*' --tests '*Procedure*' --tests '*Toolguide*' --tests '*Skill*' --tests '*Preset*' --tests '*Executable*'`.
- `./gradlew :packs:spotlessApply :packs:compileJava :packs:checkstyleMain :packs:checkstyleTest` clean.
- One commit per type or one commit for the WP, each with its test; no Co-Authored-By trailer.

## Reviewer guidance

- Check each rewritten pattern still accepts and refuses the same edge strings (T004 table ran green on the old code).
- Check the cap is tested at exactly the cap and one over (kills the boundary mutant; packs domain floor 85).
- Check no capturing group read by `group(n)` was renamed or removed.

Implement command: `spec-kitty agent action implement WP01 --agent claude --mission regex-bounds-and-sonar-cleanup-01M706DF`
