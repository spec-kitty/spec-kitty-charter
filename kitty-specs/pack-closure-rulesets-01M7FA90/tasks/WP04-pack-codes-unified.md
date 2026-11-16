---
work_package_id: WP04
title: PackCodes and the unified codes (R2)
dependencies:
- WP02
- WP03
requirement_refs:
- FR-009
- C-005
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts were generated on slice-5-554-pack-closure-rulesets (via the mission branch kitty/mission-pack-closure-rulesets-01M7FA90). Completed changes merge back into slice-5-554-pack-closure-rulesets, then into main by one pull request (R4).
subtasks:
- T017
- T018
- T019
- T020
- T021
- T022
agent: claude
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCodes.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCodes.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCodesTest.java
- contracts/charter/changes/2026-11-16-registration-names-the-fault.md
- contracts/charter/changes/2026-11-16-validate-mode-speaks-the-wire-codes.md
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCodes.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackFindingCode.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackFinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackElementViolation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Diagnostic.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCodesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackFindingTest.java
- contracts/charter/changes/2026-11-16-registration-names-the-fault.md
- contracts/charter/changes/2026-11-16-validate-mode-speaks-the-wire-codes.md
- contracts/charter/schemas/CharterRefusalCode.yaml
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP04 - PackCodes and the unified codes (R2)

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
- Start with `spec-kitty agent action implement WP04 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.

## Objective

Give the slice one typed code table, `PackCodes`, and make every surface speak it, per the R2 table from WP01. One
fault answers one code and one wording everywhere. Spec FR-009 and FR-013; ruling 4; plan IC-04.

## Subtasks

### T017: `PackCodes`

- **What each code is:** a kernel `ReasonCode(value, severity)` plus its wording function (the detail template). It
  covers every row of the R2 table, the pack-level codes and the `CLOSURE` codes.
- **Source of tokens:** where a kernel `RefusalCode` exists with the same token (`pack_unreadable`, `drg_schema_error`
  and the composition codes), build the token from `RefusalCode.token()`, so there is one source (A-m1).
- **`prompt_slot_not_empty`:** two constants, `VIOLATION` and `WARNING`, with one token. Mappers switch on the
  constant, never on `value()` (C-12).
- **`PackCodesTest`:** every token matches `[a-z][a-z0-9_]*`. No two constants share a value and a severity. Every
  `Diagnostic.Code` has a constant.

### T018: Typed fields

- **The retyping:** `PackFinding.code` (today `PackFindingCode`) and `PackElementViolation.kind` (today a `String`,
  set at about 24 `new PackElementViolation(` sites) become `ReasonCode` from `PackCodes`.
- **`FilesystemPackStore`'s 12 sites** are owned here. The sites in `DomainRepositoryService` (7), `WholePackStore`
  (2), `PackPolicy` (2) and `StepEnhancementRules` / `RelationshipEndpointRule` are out-of-map edits: one line each,
  mechanical, recorded.
- **Delete `WholePackStore.violationOf`'s `invalid` fallback** (`:758-763`).
- **`PackFindingCode`** becomes a thin view over `PackCodes`, or is deleted if nothing needs it. WP09 deletes it in
  any case.
- **The golden snapshot is unchanged after T018 alone.** Commit it separately.

### T019: R2 on the registration surface

- **Apply each row where registration changes:**
  - catch-alls become specific codes: `domain` and `reference` become the specific rule codes, the aggregate
    `{pack, validation_failed, message}` and the refusal at pointer `pack` become the specific fault, and `schema`
    given once per violation becomes one finding per file, if the table says so;
  - each row's wording is the kept wording.
- **Golden diff:** every changed registration row cites its R2 row.
- **Rewrite the scenarios that pin a changed registration answer.** Count them, and list them in the fragment.

### T020: R2 on the validate surface

- **Validate mode adopts registration's specific tokens:** for example `schema` and `artifact_schema_error` become the
  one kept code, `reserved_shard` is used in both places, and `asset` is used where the table keeps it.
- **Golden diff:** every changed validate row cites its R2 row.

### T021: The records

- **Two fragments,** each with exactly one `### Breaking` block:
  - `2026-11-16-registration-names-the-fault.md` (registration's rows);
  - `2026-11-16-validate-mode-speaks-the-wire-codes.md` (validate mode's rows).

  Each lists the old code and the new one.
- **`CharterRefusalCode.yaml`:** `validation_failed`'s list of violation codes (`:~380-391`) names every code
  registration can now answer.
- **Consistency checks:** keep the `raised` flags and `ReservedRefusalCodesTest` consistent. That test is owned by
  WP09; record any change here as out-of-map.

### T022: The planted packs and `core_verdicts.py`

- **`planted/*/expected.json`:** the codes follow the table, and `"severity": "blocking"` becomes `"violation"`
  (canonical shape, M-8).
- **`scripts/packs/core_verdicts.py`:** reads the new tokens. These are out-of-map edits to WP01's files.
- **Run the script's check, if there is one,** and `GlossaryPlantedRefusalsTest`.

## Definition of Done

- One code table exists.
- `git grep -n 'new PackElementViolation(.*"'` returns nothing.
- The golden diff is fully explained by R2 rows.
- The scenarios and fragments are updated.
- `verifyAll` is green.
- A narrowed PIT run is reported.

## Risks

- **Open-packs fixtures.** Registration's catch-all rows break open-packs' expected codes. Add them to WP02's open-packs
  draft, which the orchestrator files.

## Reviewer guidance

- Is the table applied row by row?
- Is no surface left with a private token?
