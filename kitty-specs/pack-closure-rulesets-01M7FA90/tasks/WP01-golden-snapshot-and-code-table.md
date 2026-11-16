---
work_package_id: WP01
title: The golden snapshot and the R2 code table
dependencies: []
requirement_refs:
- NFR-002
- SC-002
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts were generated on slice-5-554-pack-closure-rulesets (via the mission branch kitty/mission-pack-closure-rulesets-01M7FA90). Completed changes merge back into slice-5-554-pack-closure-rulesets, then into main by one pull request (R4).
subtasks:
- T001
- T002
- T003
- T004
- T005
agent: claude
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/golden/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/golden/SurfaceGoldenTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/golden/SurfaceAnswers.java
- service/packs/src/test/java/ai/speckitty/charter/packs/golden/CompositionGoldenTest.java
- service/packs/src/test/resources/golden/surfaces.json
- service/packs/src/test/resources/golden/composition.json
- service/packs/src/testFixtures/resources/planted/artifact-not-utf8/expected.json
- docs/development/notes/pack-code-unification.md
execution_mode: code_change
owned_files:
- service/packs/src/test/resources/planted/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GlossaryPlantedRefusalsTest.java
- scripts/packs/core_verdicts.py
- docs/development/notes/pack-code-unification.md
- service/packs/src/test/java/ai/speckitty/charter/packs/golden/SurfaceGoldenTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/golden/SurfaceAnswers.java
- service/packs/src/test/java/ai/speckitty/charter/packs/golden/CompositionGoldenTest.java
- service/packs/src/test/resources/golden/surfaces.json
- service/packs/src/test/resources/golden/composition.json
- service/packs/src/testFixtures/resources/planted/artifact-not-utf8/expected.json
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP01 - The golden snapshot and the R2 code table

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else.

Then read, in `kitty-specs/pack-closure-rulesets-01M7FA90/`:

- `spec.md` (the Intent Summary's rulings table);
- `plan.md` (Decisions, and the IC named below);
- `data-model.md`;
- `research/design-r3.md` (with its §7 folds) and `research/squad-plan.md`;
- `research/squad-similar-surfaces.md` (file:line map, as of before slice F; re-check each reference).

Charter rules that bind every subtask:

- The charter's model is canonical. There is no legacy reader and no shim.
- Tests carry only the canonical shape, apart from error-path fixtures.

## Branch Strategy

- Planning base and final merge target: `slice-5-554-pack-closure-rulesets`. The mission branch is `kitty/mission-pack-closure-rulesets-01M7FA90`.
- The execution worktree is allocated per computed lane from `lanes.json`; this mission has one lane.
- Start with `spec-kitty agent action implement WP01 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.

## Objective

Before any behaviour changes, record what every pack-judging surface answers today. Then fill the R2 table, which gives
each fault its one code and wording. Every later work package compares against this snapshot. A difference is allowed
only when it cites Q1 or an R2 row (NFR-002, SC-002). Plan IC-01; squad findings M-2 and M-8.

## Subtasks

### T001: The golden harness (`golden/SurfaceAnswers`)

- **Purpose:** one function per surface that returns that surface's whole answer as a stable, ordered value.
- **Surfaces:**
  - **validate:** `new PackValidation(new FileSystemPackInspector()).validate(root)`. Record every finding as code,
    severity, file, place and detail, in order.
  - **registration:** stage the pack as `PackRegistrationService` and `PackSourceStagers` do, then call
    `WholePackStore.loadStaged`.
    - On a refusal, record the problem code, the problem detail and the violations (pointer, code, detail), in order.
    - On success, record the diagnostics (code, detail), in order.
    - Reuse the staging the existing `WholePackStoreTest` uses. Do not add a Spring context.
  - **compose read:** `DomainRepositoryService` over the pack as a stored, on-disk pack. Record the first violation's
    `pack_unreadable` detail, as `readable` (`:812-827` before F; re-check) builds it, or "readable".
- **Serialization:** JSON, with one stable key order. Use a test-only dependency that is already present (Jackson is
  on the `:packs` classpath).

### T002: Move `planted/` into testFixtures

- Move `service/packs/src/test/resources/planted/**` (76 directories) to
  `service/packs/src/testFixtures/resources/planted/**` with `git mv`, so that `:pack-validation` tests can read
  them too (M-8).
- Repoint `GlossaryPlantedRefusalsTest` and any other reader (`git grep -n '"planted'`), and the path in
  `scripts/packs/core_verdicts.py`.
- Run `:packs:test --tests '*Planted*'` and the script's own self-check, if it has one.

### T003: The golden files and the comparison test

- **The file:** `service/packs/src/test/resources/golden/surfaces.json` holds one entry per planted pack and per
  example pack under `testFixtures/resources/example-packs/**`.
- **`SurfaceGoldenTest`:** compares `SurfaceAnswers` to the file.
  - With `-Dgolden.update=true` it rewrites the file instead. That is the only way the file is written, and a
    reviewer reads the diff.
  - Prove it bites: alter one detail in a fixture locally and watch the test fail. Note the run in the WP's review
    log.
- **Not covered here:** the contract scenarios build their archives inside Cucumber steps. They stay their own wire
  oracle, through `:deployable:test`.

### T004: The R2 code table

- **Where:** `docs/development/notes/pack-code-unification.md`.
- **One row per fault kind** that either surface answers today, with these columns:
  - fault;
  - validate code and wording;
  - registration code, pointer and wording;
  - **kept** code and wording;
  - the surface that changes;
  - the `### Breaking` fragment that will carry the change.
- **The rule (R2, design-r3 §7 F3):** the specific code wins over a catch-all. The catch-alls are `domain`,
  `reference`, `invalid`, the aggregate `{pack, validation_failed, message}`, the refusal at pointer `pack`, and
  `schema` given once per violation.
  - Where registration already answers a specific code (`retired`, `manifest_membership`, `reserved_shard`, `asset`,
    `prompt_fragment_invalid`, `relationship_endpoint_not_allowed`), validate mode adopts it.
  - Where only validate mode judges a fault (level, org fragment, asset mime), its code stays. R1 keeps it off the
    registration surface.
- **Fill it from the golden snapshot,** pairing faults by file and place. Mark every row "checked against golden".
- **List the open cases** at the end, for example a fault that differs in count or pointer. Send them to the
  orchestrator only if a row cannot be decided by the rule.

### T005: The offering's golden snapshot

- **`CompositionGoldenTest`:** for each refusal case in `PackCompositionResolverTest`, and for at least 5
  multi-fault sets you construct (cycle with unknown dependency; name conflict with sibling contest; preset contest
  with an unqualified reference; duplicate id with out-of-range), record what the offering answers today. That is the
  refusal code, its detail and its `RefusalPack` list.
- **Store it in `golden/composition.json`.** WP08 must reproduce it byte for byte.

## Definition of Done

- `SurfaceGoldenTest` and `CompositionGoldenTest` pass on today's code, and both are shown to bite.
- `planted/` lives in testFixtures, and every reader is repointed.
- The R2 table is complete, with every row checked against the golden snapshot.
- `./gradlew verifyAll --max-workers=1` is green. Record the counts in the review log.

## Risks

- **Non-determinism** (map order, temporary paths in details). Normalise only the temporary staging path, and say so
  in a comment.
- **Size.** If `surfaces.json` passes about 2 MB, split it per pack directory.

## Reviewer guidance

- Is the snapshot taken on unmodified main behaviour?
- Does a wilful detail change fail the test?
- Does each R2 row follow the rule, or carry a stated reason?
