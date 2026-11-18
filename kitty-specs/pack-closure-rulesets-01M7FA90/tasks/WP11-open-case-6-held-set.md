---
work_package_id: WP11
title: Open case 6, validate mode judges only what registration holds
dependencies:
- WP02
requirement_refs:
- FR-010
- FR-013
- FR-014
- SC-003
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts for this mission were generated on slice-5-554-pack-closure-rulesets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-5-554-pack-closure-rulesets unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-closure-rulesets-01M7FA90
base_commit: cd353b217728557112832cf5dc950330035c55c3
created_at: '2026-11-16T19:47:08.224375+00:00'
subtasks:
- T064
- T065
- T066
- T067
- T068
history:
- at: '2026-11-16T12:00:00Z'
  actor: planner-priti
  action: Prompt generated for the S1 to S3 replan (squad-shaping)
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileKind.java
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/HeldSetAgreementTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/NotHeldValidateTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileKind.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackCensus.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/HeldSetAgreementTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/NotHeldValidateTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileKindTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetTest.java
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP11 - Open case 6, validate mode judges only what registration holds

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else.

Then read, in `kitty-specs/pack-closure-rulesets-01M7FA90/`:

- `spec.md` (the Intent Summary's rulings table, S1 to S3 included);
- `plan.md` (Decisions, and the IC named below);
- `data-model.md` (placement, the one reader, the compositions);
- `research/squad-shaping.md` (the S1 to S3 rulings, their recorded readings and the target shape);
- `research/design-r3.md` (§1 to §7, read with §8, which lists what S1 to S3 and S6 superseded) and `research/squad-plan.md`;
- `research/squad-similar-surfaces.md` (file:line map, as of before slice F; re-check each reference).

Charter rules that bind every subtask:

- The charter's model is canonical. There is no legacy reader and no shim.
- Tests carry only the canonical shape, apart from error-path fixtures.

## Branch Strategy

- Planning base and final merge target: `slice-5-554-pack-closure-rulesets`. The mission branch is `kitty/mission-pack-closure-rulesets-01M7FA90`.
- The execution worktree is allocated per computed lane from `lanes.json`; this mission has one lane.
- Start with `spec-kitty agent action implement WP11 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.
- An edit to a file another work package owns is an **out-of-map edit**: one purpose, recorded in the review log.

## Objective

Settle **open case 6** on today's pipeline, before the pipeline moves: validate mode and registration agree on what a
pack holds. Validate mode today judges `charter.yaml` and a nested `drg/<kind>.graph.yaml`, which registration (WP02)
warns `file_not_held` and does not hold. After this work package validate mode reports the same `file_not_held`
warning and judges nothing in a file that is not held, and the packs contract says what the store holds. Spec FR-010,
FR-013 and FR-014, US3 scenario 4; plan IC-10; squad findings 1 (two owners of "held"), Pr6 and Pr9; the WP02
review's LOW findings L1 to L3; the WP01 review's routing of open case 8.

This work package runs on today's pipeline (`PackPolicy`, `FileSystemPackInspector`, `WholePackStore`). WP05 then
makes the one census; here the two derivations are made to agree and an agreement test pins them.

## Coordination

- `WholePackStore.java` is WP12's (T049): T065's explicit not-held kind and the retired-file exclusion are out-of-map
  edits there, recorded. `WholePackWriteStore.java` stays WP02's (out-of-map, recorded).

## Subtasks

### T064: Narrow the table to what the store holds

- **`contracts/packs/file-kinds.yaml`** (WP02's file; out-of-map, recorded): the `**/charter.yaml` rule (`:~179`) and
  the depth of `**/*.graph.yaml` (`:~142`) say what the service models: the root `*.graph.yaml` shards and
  `drg/fragment.yaml`, and no `charter.yaml` (the compose read never reads either, and the golden records it
  `readable`). The rule's own comment, "so that a stray one is validated, not silently ignored", is replaced: under Q1
  a stray file is not silent, it is a `file_not_held` warning that names it.
- **The classifier cases** (`conformance/classifier-cases.yaml:42,57`; out-of-map): `core/graph/a.graph.yaml` and
  `project/charter.yaml` stop being `mapped`, with the outcome the narrowed table gives.
- **The packs `CHANGELOG.md`** (out-of-map): a `### Breaking` line naming the narrowed globs and the reclassified cases.
  `README.md` follows if it describes them.
- **The census gate (Pr6):** narrowing re-kinds files in `WholePackCensus`. Run `:pack-validation:wholePackRoundTrip`
  and the drift test; update a baseline list only where the example packs hold such a file, and record it.
- **Wording for mapped kinds (WP02 review L3):** with the narrowed table, a file the table maps but the store does not
  hold no longer exists; confirm no `file_not_held` reason names a mapped kind.

### T065: An explicit not-held kind, and retired files off the list (WP02 review L1, L2)

- **L1:** `ExcludedFile.isUnmodelled()` compares the reason with a string (`WholePackWriteStore.java:63-65`). Give
  `ExcludedFile` an explicit kind (`EXCLUDED` or `UNMODELLED`) set where the file is classified
  (`WholePackStore.java:~926-931`), and switch on it. `WholePackWriteStore` is WP02's and `WholePackStore` WP12's;
  out-of-map, recorded. WP05's
  `TreeCensus` carries the same kind.
- **L2:** a retired file is refused as `retired` and is also listed as not held with the unmodelled reason. Leave a
  retired file out of the not-held list: it is a refusal, not a warning.
- **Wire check:** the `file_not_held` details do not change for any other file; the golden diff shows only the
  retired files' duplicate warnings gone, each listed, plus the excluded-file warnings of T066.

### T066: Validate mode judges nothing in a file that is not held

- **The inspector** (`FileSystemPackInspector`, WP05's file; out-of-map) classifies through the narrowed table, so a
  nested shard or a `charter.yaml` is a not-held file with the unmodelled reason, and its contents are not read.
- **`PackPolicy`** (WP13's file; out-of-map) reports one `file_not_held` warning per not-held file, with the
  registration's detail (`<file>: not held (<reason>)`), and runs no rule over its contents. The
  `drg_schema_error` and `artifact_schema_error` findings at those files go (R2 row 33, V).
- **`NotHeldValidateTest`:** for each of the 12 golden subjects of open case 6, validate mode's verdict is valid with
  one `file_not_held` warning beside the descriptor findings (US3 scenarios 2 and 4).

### T067: The held-set agreement test

- **`HeldSetAgreementTest`:** over every planted and example pack, plus a synthetic pack holding every class of file
  (scenario packs are built inside Cucumber steps and none plants a nested shard, a `charter.yaml` or an excluded file;
  review cycle 1), the files validate mode reports not held
  equal the store's `notHeld()`, file for file, with the same kind and reason. This pins the two derivations until
  WP05 replaces them with one census; WP05 then anchors the test to the golden's `file_not_held` diagnostics
  (`golden/surfaces.json`), because one derivation compared with itself proves nothing (FR-016).

### T068: The golden, the drafts and the routing

- **The golden snapshot:** regenerate with `-Dgolden.update=true`. Every diff row is either a validate row of one of
  open case 6's 12 subjects (a judged finding replaced by one `file_not_held` warning) or a retired file's dropped
  duplicate warning (T065, NFR-002's "WP02 review L2" entry), or one of the two excluded-file validate warnings
  (`example-packs/built-in`, `example-packs/org-base`; validate mode warns for EXCLUDED and UNMODELLED files alike,
  NFR-002's excluded-files entry, review cycle 1). Record the counts.
- **The open-packs draft** `TBD-unmodelled-not-held`: hand the reclassification (the narrowed globs and cases) to the
  orchestrator, who writes it in the planning pass.
- **The note** `docs/development/notes/pack-code-unification.md` (WP01's file; out-of-map): open case 6's "Validate
  mode (WP05 and WP08, open)" becomes "settled by WP11", and open case 8 (the clean control's missing
  `assets/doc.txt`) is routed to WP05, as the WP01 review asked.

## Definition of Done

- Validate mode and registration report the same not-held files, with the same kind, reason and detail.
- No rule judges a file that is not held.
- The packs contract, its classifier cases and its `### Breaking` line agree with the store.
- `ExcludedFile` has an explicit kind; no reason is compared as text; retired files are not on the not-held list.
- `verifyAll` is green, including `:pack-validation:wholePackRoundTrip` and `driftTest`.
- The golden diff lists only the rows named in T068.

## Risks

- **The census gate and the drift test** compare kinds; narrowing changes them (Pr6).
- **Ownership.** Most edits are to files WP02, WP05, WP13 and WP01 own. Keep each to its purpose and record it.

## Reviewer guidance

- Is any nested shard or `charter.yaml` still judged by validate mode?
- Does the held-set agreement test cover every planted and example pack and the synthetic every-class pack?
- Is the not-held kind explicit everywhere it is read?
