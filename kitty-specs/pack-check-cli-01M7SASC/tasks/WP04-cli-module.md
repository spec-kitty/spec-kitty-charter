---
work_package_id: WP04
title: 'The CLI module: main, arguments, problems, report, exit codes'
dependencies:
- WP03
requirement_refs:
- FR-001
- FR-005
- FR-006
- FR-009
- NFR-005
planning_base_branch: feat/478-pack-check
merge_target_branch: feat/478-pack-check
branch_strategy: Planning artifacts for this mission were generated on feat/478-pack-check. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/478-pack-check unless the human explicitly redirects the landing branch.
subtasks:
- T014
- T015
- T016
- T017
- T018
phase: Phase 3 - CLI
history:
- at: '2026-11-19T08:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/pack-check/src/main/
create_intent:
- service/pack-check/src/main/java/ai/speckitty/charter/packcheck/PackCheckMain.java
- service/pack-check/src/main/java/ai/speckitty/charter/packcheck/Arguments.java
- service/pack-check/src/main/java/ai/speckitty/charter/packcheck/ConfigRoot.java
- service/pack-check/src/main/java/ai/speckitty/charter/packcheck/Problems.java
- service/pack-check/src/main/java/ai/speckitty/charter/packcheck/ReportWriter.java
- service/pack-check/src/test/java/ai/speckitty/charter/packcheck/PackCheckMainTest.java
- service/pack-check/src/test/java/ai/speckitty/charter/packcheck/ArgumentsTest.java
execution_mode: code_change
owned_files:
- service/pack-check/build.gradle.kts
- service/pack-check/src/main/**
- service/pack-check/src/test/java/ai/speckitty/charter/packcheck/*.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java
- service/deployable/src/test/java/ai/speckitty/charter/packcheck/badfixture/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP04 — The CLI module: main, arguments, problems, report, exit codes

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

`service/pack-check` gets its `main`: the composition root and what only the CLI needs.

- arguments and the config-root discovery;
- the mapping from verdicts to contract problems;
- the JSON report on standard output, warnings on standard error;
- the exit codes `0`, `1` and `2`;
- the module's gates.

It holds no check (FR-009).

## Context

- Spec: FR-001, FR-005, FR-006, FR-009, edge cases, User Stories 1 and 2. data-model.md (the report, the problem, the
  warnings, the exit codes). The draft schema is `contracts/PackCheckReport.yaml` in the mission directory.
  research.md R6 and squad S3, S5, S11, S12, S14, S15 and S18.
- WP03 gives `PackSetCheck`/`PackSetVerdict` and the `RoundTripping` port; WP02 gives the shared `PackRefusalMapping`.
- How the API renders a problem (`service/web-transport/.../RefusalHandler.java`):
  - **title:** the code as a sentence (`title(code)`, about line 492);
  - **status:** `statusOf(code, "getPackRegistration")`, from the contract's `x-refusal`;
  - **detail:** the refusal's message plus `leftOut(count)` when there are more than 100 violations (about line 309);
  - **packs:** `RefusalPack` DTOs;
  - **violations:** `{pointer, code, detail}`, at most 100.

  The CLI may not name `web-transport` (`PACK_VALIDATION_IS_FRAMEWORK_FREE`), so it holds a small status table and
  the same title and left-out wording. WP05 pins them equal.
- Config root layout (`application.yaml`, `charter.config-root`): `<root>/packs/<name>/`, each at the level its
  `pack.yaml` declares.

## Branch Strategy

Planning base `feat/478-pack-check`, merge target `feat/478-pack-check`; worktrees per lane from `lanes.json`. Run
`spec-kitty agent action implement WP04 --agent <name> --mission pack-check-cli-01M7SASC`.

## Subtasks

### T014 — `Arguments` (`ai.speckitty.charter.packcheck.Arguments`)

- **Grammar:** `--config-root <dir>` | (`--pack <dir>[:<level>]`)+, then optionally `--round-trip`.
  - Levels are the wire tokens `built-in`, `org` and `project` (`PackLevel` tokens).
  - The level separator is the **last** `:` whose suffix is a level token, so `C:\packs\x` on Windows and paths
    holding `:` still parse.
- **Usage faults** (exit 2, usage on stderr, nothing on stdout):
  - no input;
  - both `--config-root` and `--pack`;
  - an unknown option, a missing value or an unknown level;
  - a missing directory;
  - an empty or absent `<root>/packs`;
  - two packs with the same real path (`toRealPath`);
  - two packs with the same name.
- `--help` prints usage and exits 0.
- A parse result is a value: `Arguments.parse(String[])` returns a sealed `Parsed` or `UsageFault(message)`. Nothing
  throws for a user error.
- **Tests:** one per fault, plus the happy paths (a repeated `--pack` with and without a level, a Windows-style path
  with a level).

### T015 — `ConfigRoot`

- `ConfigRoot.packs(Path root)`: the directories directly under `root/packs`, in name order. A directory reached
  through a link is allowed (#174). Plain files are ignored. Every pack gets a `PackRoot` with no configured level, so
  the declared level applies (data-model.md).
- **Tests:** the five example packs copied under `@TempDir/packs/` give five roots in name order. A link to a pack
  directory is accepted. A file under `packs/` is ignored.

### T016 — `Problems`

Each pack's problems are emitted before the composition problems (data-model.md order).

- **A pack with violations:** `PackRefusalMapping.of(rationale-of-violations)` (WP02), rendered as a `Problem`:
  - `title` "Validation failed", `status` 422, `code` `validation_failed`;
  - `detail`: the mapping's message plus the left-out sentence when more than 100;
  - `instance`: the pack's path as given;
  - `violations`: the first 100.
- **An `Unreadable` pack whose verdict has no violation (D3):** one problem with the refusal's code, the status table's
  status, the refusal's detail and `instance`. Never two problems for one pack.
- **Round-trip reasons:** a `validation_failed` problem for that pack, with the `round_trip_*` violations at their
  files.
- **Each `CompositionFault`:**
  - `code` from the fault, `status` from the table, the title rule, `detail` the fault's;
  - `packs`: `[{descriptorPackId, packId, name}]` from its `RefusalPack`s.
- **The status table** lists every code the CLI can emit: `validation_failed`, `pack_unreadable`, the load's codes the
  builder can raise, and every composition and preset code `CompositionRules.codeOf` can return. Each maps to the
  `getPackRegistration` status of its `x-refusal` entry in `contracts/charter/schemas/CharterRefusalCode.yaml`. WP05
  pins it against `RefusalHandler`.
- **Tests:** each branch; the 100 cap with 101 violations, and its left-out wording; titles.

### T017 — `ReportWriter` and `PackCheckMain`

- **`ReportWriter`** writes the report with Jackson 3 (`tools.jackson.databind`, allowlisted): `checks`, `packs`
  (`name`, optional `level`, `path`), optional `skipped`, then `problems`. Nulls are omitted; key order is stable.
- **Warnings** go to stderr, one line each: `warning: <pack>: <code> <file>: <detail>`, for every `WARNING` of every
  verdict.
- **`PackCheckMain`:**
  - `public static void main(String[] args)` calls `System.exit(run(args, System.out, System.err))`;
  - `static int run(String[] args, PrintStream out, PrintStream err)` is testable in-process.
  - **The composition root:** it names `FileSystemPackInspector` (the `PackReading`) and `PackRoundTrip`, for the
    `RoundTripping` lambda. The lambda creates a temporary directory per pack under one run directory, which is
    deleted in a `finally`.
  - **Exit codes:** `2` on a usage fault or an I/O failure outside a pack read (writing stdout, the temporary
    directory); `1` when `problems` is non-empty; else `0`. Never a partial report with exit `0`.
- **Tests** (in-process `run`):
  - the five example packs as a config root, and as `--pack`: exit 0 with the documented report;
  - `project` alone: exit 1, two `pack_dependency_unknown` problems with `packs`;
  - a planted subject: exit 1, `validation_failed` with its codes;
  - a planted link (once WP01 lands, or with a plant made in the test): `pack_entry_linked`;
  - `--round-trip` over the example packs: exit 0 and `ROUND_TRIP` in `checks`;
  - a `file_not_held` pack: exit 0 with the warning line on stderr;
  - a usage fault: exit 2, empty stdout.

### T018 — Module gates

- **`service/pack-check/build.gradle.kts`:**
  - the `ModuleDependencyAllowlist` gains `:packs`, named by the main;
  - add `testImplementation(testFixtures(project(":packs")))` for the example packs and plants;
  - remove the javadoc skip block, now that the module holds types;
  - apply `charter.mutation-conventions` with a `pack-check` group measured on the new code (`floor5(score - 3)`, as
    the other modules document it).

  WP06 adds the jar through a build-logic convention plugin. Leave room: no jar task here.
- **`HexagonalArchitectureTest`** (`service/deployable/src/test/java/ai/speckitty/charter/deployable/`):
  - `PackCheckMain`, named by its fully qualified class name, may name `FileSystemPackInspector` and `PackRoundTrip`,
    in `PACK_VALIDATION_IS_FRAMEWORK_FREE`'s direct half and in `ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS`;
  - a known-good control: a fixture class in `ai.speckitty.charter.packcheck.badfixture` that names
    `FileSystemPackInspector` is still refused (`assertBites`);
  - the transitive half walks from `PackCheckMain` and passes;
  - update the rule javadocs that say "until #478 adds its main".
- **FR-009 by inventory:** list in the PR body every `pack-validation` main type and who uses it, showing none is
  CLI-only. No code moves.

## Definition of Done

- `./gradlew verifyAll` is green, with the ArchUnit gate, the module's mutation floor and the style gates.
- `PackCheckMain.run` answers every scenario above.
- The report matches the draft schema; WP05 makes that a contract test.

## Risks and reviewer guidance

- **Status drift.** The table must list every code; WP05's test enumerates `CompositionRules`' codes to catch a
  missing one.
- **Leaked temporary directories.** A test asserts the run directory is gone after `run`.
- **No check in the CLI.** Any `if` that judges pack content belongs in `packs` or `pack-validation`, not here.
