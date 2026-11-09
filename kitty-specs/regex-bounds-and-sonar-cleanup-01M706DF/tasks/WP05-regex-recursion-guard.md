---
work_package_id: WP05
title: Regression guard against recursive patterns
dependencies:
- WP01
- WP02
- WP04
requirement_refs:
- FR-011
- SC-004
- NFR-003
- C-001
planning_base_branch: fix/regex-bounds-and-sonar-cleanup
merge_target_branch: fix/regex-bounds-and-sonar-cleanup
branch_strategy: Planning artifacts for this mission were generated on fix/regex-bounds-and-sonar-cleanup. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into fix/regex-bounds-and-sonar-cleanup unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-regex-bounds-and-sonar-cleanup-01M706DF
base_commit: fce8cc7a2f6c297ee3c0c9ca73bec92b60884885
created_at: '2026-11-09T16:03:17.059886+00:00'
subtasks:
- T017
- T018
- T019
history:
- timestamp: '2026-11-09T13:20:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: build-logic/src/test/kotlin/
create_intent:
- build-logic/src/test/kotlin/RegexRecursionGuardTest.kt
execution_mode: code_change
model: ''
owned_files:
- build-logic/src/test/kotlin/RegexRecursionGuardTest.kt
role: implementer
tags: []
tracker_refs: []
---

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else, then load
`spec-kitty charter context --action implement`.

## Objective

The build flags main code that compiles a regex with a repeated group not followed by a possessive `+`. FR-011. The
guard is heuristic, says so in its KDoc, and carries an allow-list for patterns it cannot judge.

## Context

- build-logic already holds repository-reading guard tests, e.g. `build-logic/src/test/kotlin/SonarWorkflowGuardTest.kt`
  (reads `.github/workflows`) and `CheckstyleRegisterTest.kt`. Follow their style for locating the repo root and
  running them (`./gradlew -p build-logic test --tests 'RegexRecursionGuardTest'`, or however build-logic tests
  run; check `build-logic/build.gradle.kts` and how CI runs them).
- A repeated group is `)` followed by `*`, `+` or `{n,}`. It is possessive when the quantifier is followed by
  another `+` (`)*+`, `)++`, `){n,}+`). An atomic group `(?>…)` is also safe.

## Subtasks

### T017: The scan

`RegexRecursionGuardTest.kt`: walk `service/*/src/main/java/**/*.java`, find string literals passed to
`Pattern.compile(` (including literals in a `static final String` that is passed later, by a simple same-file
lookup), and report every literal with a repeated group whose quantifier is not possessive. Ignore patterns with a
bounded quantifier (`{0,2}`), because a bounded repeat cannot recurse without limit. The failure message names the
file and line, and says to make the quantifier possessive or add an allow-list entry with a reason.

### T018: Fixture tests

Unit-test the detector on in-memory sources: `Pattern.compile("^[a-z]+(-[a-z]+)*$")` is flagged;
`Pattern.compile("^[a-z]++(?:-[a-z]++)*+$")` is not; `(?>-[a-z]+)*` is not; `[0-9]{0,2}` is not; a
concatenated or constant-built pattern is reported as "cannot judge" so it surfaces for the allow-list.

### T019: Allow-list

A small in-file list of `file:pattern-name` entries with a reason each. For example, `GitSourceRules` HOST and
`GuideAllowlist` HOST are bounded by a `(?=.{1,253}$)` lookahead, and `ContainerProperties.HOST_ENTRY` is bounded
by its lookahead. Run the guard on real main code after WP01, WP02 and WP04: it must pass, with each remaining hit
either rewritten or allow-listed with a true reason. A hit outside this mission's sites is reported in the commit
body and allow-listed with "bounded: <why>" only if it is truly bounded. Otherwise stop and report it.

## Definition of Done

- Fixture tests green; the scan of real main code green.
- KDoc states the guard is heuristic (literal-based, same-file constants only) and that SonarCloud S5998 is the
  backstop.

Implement command: `spec-kitty agent action implement WP05 --agent claude --mission regex-bounds-and-sonar-cleanup-01M706DF`
