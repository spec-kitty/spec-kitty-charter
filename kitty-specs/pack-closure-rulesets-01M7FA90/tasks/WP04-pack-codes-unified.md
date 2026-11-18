---
work_package_id: WP04
title: PackCodes and the unified codes (R2)
dependencies:
- WP03
- WP11
requirement_refs:
- FR-009
- FR-013
- C-005
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts for this mission were generated on slice-5-554-pack-closure-rulesets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-5-554-pack-closure-rulesets unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-closure-rulesets-01M7FA90
base_commit: 0e2346c268c79d9cc5e238bccab71d54e6ebd089
created_at: '2026-11-16T22:01:06.588562+00:00'
subtasks:
- T017
- T018
- T019
- T020
- T021
- T022
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
- at: '2026-11-16T12:00:00Z'
  actor: planner-priti
  action: Replanned for the S1 to S3 rulings (squad-shaping)
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCodes.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCodes.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCodesTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/StorageCodes.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/package-info.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/rules/StorageCodesTest.java
- contracts/charter/changes/2026-11-16-registration-names-the-fault.md
- contracts/charter/changes/2026-11-16-validate-mode-speaks-the-wire-codes.md
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCodes.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/StorageCodes.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/package-info.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/rules/StorageCodesTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackElementViolation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Diagnostic.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCodesTest.java
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
- `research/squad-shaping.md` (S1 to S3 and S6) and `research/design-r3.md` (with its §7 folds and §8, what S1 to S3
  and S6 superseded), and `research/squad-plan.md`;
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

Give the slice one typed code table per owner, `PackCodes` in the domain and `StorageCodes` in the storage edge (S3),
and make every surface speak them, per the R2 table from WP01. One fault answers one code and one wording everywhere,
and each token is built exactly once. Spec FR-009 and FR-013; ruling 4; S3; plan IC-04. WP11 has already narrowed
the held set and given the not-held list an explicit kind; the open case 6 rows are already in the golden.

## Coordination

- `PackFinding.java`, `PackFindingCode.java` and `PackFindingTest.java` are WP13's (it deletes them): T018 retypes
  `PackFinding.code` and narrows `PackFindingCode` out-of-map; each edit is recorded.
- `WholePackStore.java` and `DomainRepositoryService.java` are WP12's: T018's sites there (2 and 7) and the `invalid`
  fallback's deletion are out-of-map, one line each, recorded.

## Subtasks

### T017: `PackCodes` and `StorageCodes`

- **What each code is:** a kernel `ReasonCode(value, severity)` plus its wording function (the detail template). Together
  they cover every row of the R2 table and the `CLOSURE` codes; the pack-level (placement) codes are WP09's
  `PlacementCodes`.
- **Who builds which token** (`data-model.md`, "PackCodes and StorageCodes"):
  - `PackCodes` (`packs.domain`): every token a core rule emits and the `CLOSURE` tokens. The storage edge names these
    constants where it reports the same fault (for example `asset` for a missing payload).
  - **Not here: the placement tokens** (`pack_level_missing`, `pack_level_mismatch`, `org_fragment_missing`). WP09
    builds them once, in `pack-validation`'s `PlacementCodes` (squad-shaping Q7), the only module that emits them.
  - `StorageCodes` (new package `packs.infra.storage.rules`, with its `package-info`): the tokens only the storage edge
    emits: `file_not_held`, `reserved_shard`, `manifest_membership`, the read faults (`pack_unreadable`, `unreadable`,
    `artifact_schema_error`, `retired`, `companion`) and S2's prechecks (`requires_core_invalid`,
    `pack_version_invalid`, `dependency_range_invalid`, `asset_mime_invalid`).
  - No token is built in both. `StorageCodes` names only the kernel, `packs.domain` and `java.*`.
- **Source of tokens:** where a kernel `RefusalCode` exists with the same token (`pack_unreadable`, `drg_schema_error`
  and the composition codes), build the token from `RefusalCode.token()`, so there is one source (A-m1).
- **`prompt_slot_not_empty`:** two constants, `VIOLATION` and `WARNING`, with one token. Mappers switch on the
  constant, never on `value()` (C-12).
- **`PackCodesTest` and `StorageCodesTest`:** every token matches `[a-z][a-z0-9_]*`. No two constants share a value and
  a severity, across both tables. Every `Diagnostic.Code` has a constant in one of them, apart from the three placement
  tokens, which `PackFindingCode` carries until WP09 (T018).

### T018: Typed fields

- **The retyping:** `PackFinding.code` (today `PackFindingCode`) and `PackElementViolation.kind` (today a `String`,
  set at about 24 `new PackElementViolation(` sites) become `ReasonCode` from `PackCodes`.
- **`FilesystemPackStore`'s 12 sites** are owned here. The sites in `DomainRepositoryService` (7), `WholePackStore`
  (2), `PackPolicy` (2) and `StepEnhancementRules` / `RelationshipEndpointRule` are out-of-map edits: one line each,
  mechanical, recorded.
- **Delete `WholePackStore.violationOf`'s `invalid` fallback** (`:758-763`).
- **`PackFindingCode`** stays as a thin view over `PackCodes` and `StorageCodes`, plus the three placement tokens that
  today's `PackPolicy` still emits, until WP13 deletes it. This is **staging inside the single pull request (R4 b),
  never shipped**: no new caller names it, it never reaches `main`, and no legacy reader is kept beside the canonical
  codes. WP09 moves the placement tokens to `PlacementCodes`; WP13's `git grep` pins its deletion.
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
  WP13; record any change here as out-of-map.

### T022: The planted packs and `core_verdicts.py`

- **`planted/*/expected.json`:** the codes follow the table, and `"severity": "blocking"` becomes `"violation"`
  (canonical shape, M-8).
- **`scripts/packs/core_verdicts.py`:** reads the new tokens. These are out-of-map edits to WP01's files.
- **Run the script's check, if there is one,** and `GlossaryPlantedRefusalsTest`.
- **Carried from WP11's review (cycle 2):**
  - DIV-027 in `docs/conformance/divergences.yaml` and its register page: name both of core's refusals (a schema
    refusal at `drg/<kind>.graph.yaml`, and `drg_root_graph_missing` at `<pack>` for `r15-glossary-urn-in-a-shard`),
    and note that core's runtime reads the same shards the service holds (root shards and `drg/fragment.yaml`).
  - Rename `ExamplePacksTest.everyExamplePackValidatesWithNoFinding` to say it expects only `file_not_held` warnings,
    and reflow the javadoc line at `NotHeldValidateTest.java:27`.
- **Landing item (not WP04's to run if core's pinned venv is absent):** regenerate core's raw verdicts for
  `c4-shard-urn-prefix-differs-from-kind` and `c16-shard-edge-provenance` against the pinned core (`9554e7b2`), whose
  shard moved to the pack root in WP11, and confirm `ok: true` (DIV-023). If the venv is absent, record it in the
  review log so the landing checklist carries it.

## Definition of Done

- One code table per owner exists, and no token is built twice.
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
