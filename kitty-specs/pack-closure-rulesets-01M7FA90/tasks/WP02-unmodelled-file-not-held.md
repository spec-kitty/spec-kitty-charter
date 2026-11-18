---
work_package_id: WP02
title: Q1, an unmodelled file is warned about and not held
dependencies:
- WP01
requirement_refs:
- FR-010
- FR-014
- SC-003
- FR-013
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts for this mission were generated on slice-5-554-pack-closure-rulesets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-5-554-pack-closure-rulesets unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-closure-rulesets-01M7FA90
base_commit: 2182c8854746148e5b163fdad8ef3ffb8ca6265b
created_at: '2026-11-16T10:13:38.990443+00:00'
subtasks:
- T006
- T007
- T008
- T009
- T010
- T011
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/WholePackWriteStore.java
create_intent:
- contracts/charter/changes/2026-11-16-unmodelled-files-are-not-held.md
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/WholePackWriteStore.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ExcludedFilesAgreementTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ExamplePacksTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/examples/ExamplePackCoverageTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackStoreTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/roundtrip/WholePackDrift.java
- service/pack-validation/src/driftTest/java/ai/speckitty/charter/packvalidation/roundtrip/OpenPacksDriftTest.java
- contracts/charter/scenarios/registration/whole-pack-registration.feature
- contracts/charter/scenarios/registration/retired-files.feature
- contracts/charter/scenarios/prompt-fragment-refusals.feature
- contracts/charter/changes/2026-11-11-excluded-files-are-not-held.md
- contracts/charter/changes/2026-11-16-unmodelled-files-are-not-held.md
- contracts/charter/schemas/DiagnosticCode.yaml
- contracts/charter/paths/packs_packId.yaml
- contracts/packs/file-kinds.yaml
- contracts/packs/README.md
- contracts/packs/conformance/classifier-cases.yaml
- contracts/packs/CHANGELOG.md
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP02 - Q1, an unmodelled file is warned about and not held

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
- Start with `spec-kitty agent action implement WP02 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.

## Objective

Deliver the operator's Q1 ruling on today's pipeline, before the pipeline moves. A file that no pack file kind models
becomes a `file_not_held` warning. Registration accepts it, does not store it, and the round trip never sees it.
Spec FR-010, FR-013, FR-014 and SC-003; plan IC-02; squad findings C-9 and C-10.

## Subtasks

### T006: `WholePackStore`, unmodelled files are not held

- **Today:**
  - `loadStaged` (`WholePackStore.java:~360-404`) refuses each unmodelled file with violation `unmodelled`.
  - The excluded files (`:~955`) become `file_not_held` warnings and are removed before the commit (#451).
- **Change:**
  - The unmodelled list joins the not-held list. Replace `LoadedPack.unmodelled()` with `notHeld()`, one path-sorted
    list that carries each file's reason.
  - An unmodelled file's reason is `no pack file kind models this file`. Its detail follows `ExcludedFile.notHeld`:
    `"<file>: not held (no pack file kind models this file)"`.
  - The removal before the commit takes the whole list, so the stored pack and its revision equal the pack without
    these files.
- **What stays:** `unreportedFiles` keeps its dedup. Orphan fragments keep `prompt_fragment_invalid`, and retired
  files keep `retired`.
- **Diagnostics order (C-9):** the load's own warnings first, then one path-sorted list of files not held. Unmodelled
  and excluded files are merged into it, not appended one after the other.
- **New behaviour:** the manifest-membership gate now fires on a pack whose only other fault was an unmodelled file.
  The fragment says so.

### T007: The callers of `unmodelled()`

Repoint each caller to `notHeld()`, or to the `file_not_held` findings:

- `pack-validation`'s `roundtrip/WholePackDrift.java` and `driftTest/.../OpenPacksDriftTest.java`. The drift test
  still treats every load warning as a problem (ADR-0070), as #555 confirmed.
- `ExcludedFilesAgreementTest`. Add an unmodelled `notes.txt` case.
- `ExamplePacksTest`, `ExamplePackCoverageTest`, `WholePackStoreTest`.
- The rest: `GuardAndLifecycleLoadTest`, `ManifestMembershipTest`, `MarkdownCompanionTest` and
  `MissionTypeBindingTest`. Each is an **out-of-map edit**, recorded in the review log as "Q1: `unmodelled()`
  renamed".

### T008: Rewrite the scenarios (C-10)

All in `whole-pack-registration.feature` unless named otherwise:

- **`:~340`:** the `notes.txt` row of the outline becomes a retired `PROVENANCE.md`.
- **`:~522-534`:** the scenario now succeeds:
  - outcome `created`;
  - exactly 1 `file_not_held` diagnostic, whose detail contains `"notes.txt: not held"`;
  - the revision equals `v1`'s.

  Rename it to "A text file the pack model does not know is not held".
- **`:~539-575`:** swap `notes.txt` for `PROVENANCE.md` (`retired`). That keeps exactly 3 violations, with the
  non-graph fault after the graph faults.
- **Titles and comments only:** `:403-404`, `:428`, `retired-files.feature:31` and
  `prompt-fragment-refusals.feature:14-16`.

### T009: Contract records

- **The new fragment,** `contracts/charter/changes/2026-11-16-unmodelled-files-are-not-held.md`:
  - exactly one `### Breaking` block, per `ChangelogFragments.java:43-45`;
  - core's seam: a 422 becomes a 2xx with a warning;
  - the new manifest-membership exposure.
- **Amend the unreleased 2026-11-11 fragment.** It says `notes.txt` "is still refused as `unmodelled`".
- **`DiagnosticCode.yaml`:** the `file_not_held` text covers any file no rule models, not only Markdown.
- **`packs_packId.yaml:35`:** remove "an unmodelled file" from the list of 422 answers.

### T010: Packs contract wording

- `file-kinds.yaml:~79-80`, `README.md:~44` and `conformance/classifier-cases.yaml:~17` say "refused as
  `unmodelled`". The classifier outcome stays `unmodelled`, because it is a classification, not a verdict. The wording
  becomes "warned as `file_not_held` and not held".
- Add a packs `CHANGELOG.md` line.
- The classifier cases' outcomes do not change.

### T011: The open-packs draft and the golden snapshot

- **The draft:** add `research/open-packs-drafts/TBD-unmodelled-not-held.md` to the mission. Its expected-code
  fixtures that expect `unmodelled` change, and `validate_pack_schemas.py` follows. Hand the file to the orchestrator.
  Under `kitty-specs/`, this is written in the planning pass, not in this WP's code commits.
- **The golden snapshot:** regenerate with `-Dgolden.update=true`. Check that every diff row is an unmodelled file
  moving from a violation to a diagnostic. Record the count.

## Definition of Done

- The rewritten scenarios pass in `:deployable:test`.
- The golden diff lists only Q1 rows.
- `verifyAll` is green, including `:pack-validation:wholePackRoundTrip` and `driftTest` compiling.
- `git grep -n 'unmodelled()'` returns nothing in `service/`.

## Risks

- **#578's harness compares warnings.** Make sure an example pack holds no unmodelled file, so the gate stays as it
  is.

## Reviewer guidance

- Is the stored tree equal with and without `notes.txt`?
- Is the diagnostics order one path-sorted list?
- Is the fragment format valid?
