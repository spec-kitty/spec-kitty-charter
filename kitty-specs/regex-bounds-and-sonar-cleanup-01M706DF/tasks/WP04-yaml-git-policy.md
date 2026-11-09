---
work_package_id: WP04
title: YAML resolver, git source path and pack policy message
dependencies: []
requirement_refs:
- FR-008
- FR-009
- FR-010
- FR-012
- C-003
planning_base_branch: fix/regex-bounds-and-sonar-cleanup
merge_target_branch: fix/regex-bounds-and-sonar-cleanup
branch_strategy: Planning artifacts for this mission were generated on fix/regex-bounds-and-sonar-cleanup. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into fix/regex-bounds-and-sonar-cleanup unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-regex-bounds-and-sonar-cleanup-01M706DF
base_commit: 95e51699166b8264448084814175900b18df38f0
created_at: '2026-11-09T13:16:33.391252+00:00'
subtasks:
- T013
- T014
- T015
- T016
history:
- timestamp: '2026-11-09T13:20:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/YamlResolverBoundsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/git/GitSourceRulesBoundsTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/Yaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/git/GitSourceRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/YamlResolverBoundsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/git/GitSourceRulesBoundsTest.java
role: implementer
tags: []
tracker_refs: []
---

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else, then load
`spec-kitty charter context --action implement`.

## Objective

- The YAML int and float resolver cannot recurse on a long sexagesimal scalar, and short scalars resolve exactly as
  before (FR-008, FR-009, #441).
- The git source URL path pattern cannot recurse (FR-010).
- The pack policy fragment message resolves the kind once, with the same text (FR-012, #442).

## Subtasks

### T013: Red test, YAML

Create `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/YamlResolverBoundsTest.java`. Reading
YAML that holds a plain scalar `"1" + ":5".repeat(10_000)` (and a float form, e.g. `"1" + ":5".repeat(10_000) + ".5"`
if the FLOAT pattern accepts that shape; read `Yaml.java:66-73` first) must complete without an `Error`. Assert the
value it resolves to: whatever the post-fix resolver yields, as a deliberate expectation, not "no exception". Today
it throws `StackOverflowError`; record the red run. Use the public reading entry point of `Yaml` (read the class).

Add an equivalence table for short scalars: `1`, `-1`, `1_000`, `0x1F`, `1:30`, `190:20:30`, `1.5`, `1:30.5`,
`.inf`, `abc`. Pin today's resolved types and values, run against the old code first.

### T014: Possessive rewrite, YAML

At lines ~66-67 and ~72-73, change `(?::[0-5]?[0-9])+` to `(?::[0-5]?[0-9])++` and make the leading digit runs
possessive where safe (`[1-9][0-9_]*+`). Keep every alternative and anchor. The research squad fuzzed the INT form
with 0 differences; FLOAT was not fuzzed, so the T013 table must cover it.

### T015: GitSourceRules.PATH

`infra/fetch/git/GitSourceRules.java:41`: change `PATH = (/[A-Za-z0-9._~-]+)+` to `(?:/[A-Za-z0-9._~-]++)++`, only
if no `group(n)` reads it (check :103 and every use). Create `GitSourceRulesBoundsTest.java`:

- a path at the 2048-character URL cap (`MAX_URL_CHARS`) still matches exactly as before (positive control);
- a direct match of `PATH` on `"/a".repeat(10_000)` does not throw. Make the pattern package-visible only if a test
  needs it, and prefer testing through the public validation method.

### T016: PackPolicy

`domain/PackPolicy.java:570-573`. Replace the `isPresent()` / `.get()` pair in the FRAGMENT case with:

```java
NodeKind.fromPlural(node.kind())
        .map(kind -> origin + ": " + NOT_AUTHORED + "'" + new Urn(kind, node.ref()) + "'")
        .orElseGet(() -> origin + ": node " + node.ref() + " has unknown kind '" + node.kind() + "'")
```

(match today's exact strings; read the file first; line 556 shows the house form). Confirm a test covers both
branches (`grep -rn 'has unknown kind' service/packs/src/test`); if one is missing, add it to the closest existing
PackPolicy test (record the out-of-map edit) before refactoring.

## Definition of Done

- Red run recorded for T013; `./gradlew :packs:test --tests '*YamlResolverBoundsTest' --tests '*GitSourceRules*' --tests '*PackPolicy*' --tests '*Yaml*'` green.
- The infra.storage (85) and infra.fetch (70) floors not put at risk.
- spotless and checkstyle clean for `:packs`.

Implement command: `spec-kitty agent action implement WP04 --agent claude --mission regex-bounds-and-sonar-cleanup-01M706DF`
