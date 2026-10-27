---
work_package_id: WP09
title: Reconcile with the sibling merges on main
dependencies:
- WP05
- WP06
- WP07
requirement_refs:
- FR-001
- FR-003
- FR-011
- NFR-002
- NFR-003
- C-001
- C-002
planning_base_branch: feat/asset-template-round-trip
merge_target_branch: feat/asset-template-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/asset-template-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/asset-template-round-trip unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-asset-template-round-trip-01M5YY7G
base_commit: 36e247d85c613852af508485ecbd8628f32d2167
created_at: '2026-10-27T18:44:10.665308+00:00'
subtasks:
- T036
- T037
- T038
phase: Phase 3 - Proofs, gate and records
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/Companions.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CompanionContent.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/Companions.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CompanionContent.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBinding.java
role: implementer
tags: []
task_type: implement
---
# Work Package Prompt: WP09 — Reconcile with the sibling merges on main

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Bring this mission onto `origin/main` after the sibling Wave 1 merges (#190 agent profiles, #191 mission step contracts,
#192 glossary packs and skills with ADR-0039 "a kind binding can carry companion files"), resolving every conflict and
unifying the seams so there is **one** low-level non-YAML path, not two (operator ruling, decision
01M5ZAMP0R5V5YF5KM09YXY0NS):

- **Keep** this mission's pack-level `AssetFile` and the `Template` artifact: ADR-0039 companions are artifact-owned and
  resolve against the artifact file's directory; they cannot express a sidecar-less payload (R1), a template that is an
  artifact of its own (R2), or a payload resolved against `assets/`.
- **Share** the low-level pieces with ADR-0039: one path/containment rule owner (`Companions` on main, or a common helper
  both use), one bytes read and one bytes write routine, one fingerprint ordering (companions right after their artifact;
  asset files then templates last), one revision-over-bytes (WP03's `computeOverBytes`; ADR-0039 decision 2 says the commit
  path's revision digests "move to bytes with #193" — this mission did that).
- Every sibling's behaviour and tests on main stay exactly as merged; every behaviour and test of WP01–WP07 stays.

## Ownership note

`owned_files` lists only the files this WP may change on its own account. Resolving the merge necessarily edits files
owned by WP01, WP03–WP07 and by sibling missions; those edits are the merge resolution itself and are recorded in the
handoff file by file (out-of-map with rationale "merge resolution"). Nothing beyond what the conflicts and T037 require.

## Inputs

- The mission branch with all approved lanes (WP01–WP07) merged; `origin/main` (fetch it). A dry run merging lanes f+g
  with `origin/main` conflicted in 19 files: `contracts/packs/README.md`, `contracts/packs/file-kinds.yaml`,
  `DomainRepositoryService`, `FilesystemPackStore`, `KindBindings`, `PackFileSet`, `WholePackStore`, `YamlFormats`,
  `RelationshipRegistrationServiceTest`, `DomainRoundTrip`, `DomainRoundTripTest`, `GeneratedPackTree`,
  `KindBindingsTest`, `PackFileKindTest`, `PackFileSetExactnessTest`, `WholePackExpectation`, `WholePackRoundTripTest`,
  `WholePackStoreTest`, `whole-pack/expected-red.yaml`.
- Read on main first: `docs/adr/0039-a-kind-binding-can-carry-companion-files.md`, `Companions.java`,
  `CompanionContent.java`, `KindBinding` (companions/withCompanions/hasCompanions), `PackFileSet`, `FilesystemPackStore`,
  `WholePackStore`, `DomainRoundTrip`, `WholePackExpectation`, `expected-red.yaml`, `file-kinds.yaml`.

## Branch Strategy

Planning branch and final merge target: `feat/asset-template-round-trip`. Execution worktrees are allocated per
computed lane from `lanes.json`; do not pick a base branch by hand. Implement with:

```bash
spec-kitty agent action implement WP09 --agent claude --mission asset-template-round-trip-01M5YY7G
```

## Validation discipline

Run the targeted tests for the files you touch and the owning module's fast tier (`./gradlew :packs:test
:packs:check`; `:deployable:test` when named). Never run a whole-repo heavy sweep (`verifyAll` is CI's,
directive NO_FULL_HEAVY_SUITES_IN_MISSION); record the exact commands and pass/fail counts in the review handoff.
Java 25 via `JAVA_HOME=/usr/lib/jvm/temurin-25`; Maven Central HTTP 429 → wait and retry.

## Subtasks

### T036 — Merge `origin/main` into the lane

- In this WP's lane worktree (which already contains WP01–WP07 via its dependencies), `git fetch origin main` and
  `git merge origin/main` (a merge commit; never rebase or force). Resolve every conflict keeping both sides' intent:
  - Append-only lists (`KindBindings.ALL`, `YamlFormats`, `REQUIRED_KINDS`, `ARTEFACT_KINDS`, `MODELLED`, file-kinds rows,
    `expected-red.yaml` entries): keep every sibling entry and ours; order: main's order, then ours appended.
  - `file-kinds.yaml`: keep main's new `files` rows (glossary-pack, skill) and removed exclusions; keep our header text,
    `content_form`, `payloads` section, README exclusion and the retirement of `**/*.config.yaml`.
  - `WholePackExpectation`/`expected-red.yaml`: keep both sides' entry shapes (ours: `refused`, `pack`); if main added
    fields too, the union.
- Commit the merge with a message naming the siblings and #193.

### T037 — Unify the low-level seams

- One path rule: asset-file and template paths go through the same validation/containment owner as companions (extend
  `Companions` with a resolution base, or extract a shared helper both call). Keep each side's refusal messages as
  pinned by their tests (ours: `payload assets/<path> does not exist`; theirs: the ADR-0039 list).
- One bytes I/O: companions' text read/write and our payload/template byte read/write share one routine (text = UTF-8
  view over the same bytes); no second `writeAtomically`.
- `CompanionContent`: add the `Bytes` case only if it is used; ADR-0039 expects it, but this mission does not model asset
  payloads as companions — if unused, do not add it, and WP08 records why in ADR-0040.
- Fingerprint: `PackFileSet.digested` keeps main's companion placement and our asset files + templates last; the
  generated oracle (`GeneratedPackTree`, `PackFileSetExactnessTest`) carries both sides' shape rows.

### T038 — Gates and proofs green on the merged code

- `./gradlew :packs:test :packs:check :deployable:test :packs:wholePackRoundTrip -PopenPacksDir=...` all green; paste the
  gate kind table (siblings' kinds and ours). Literal pins (fingerprints, revisions, validate findings) must still hold;
  if a sibling's change legitimately moves one, re-derive it from the right base and say which and why.
- Re-run the reviewer guidance proofs of WP05 and WP07 on the merged code (revert missing-payload refusal → red; revert
  bytes branch → red); restore.

## Definition of Done

- The lane merges cleanly into `origin/main` (no conflicts left); all gates above green; no sibling test weakened or
  removed; one path rule owner and one bytes I/O routine (reviewer greps for duplicates).
- No change to `contracts/charter/openapi.yaml`.

## Risks / reviewer guidance

- Reviewer: diff the merge commit against both parents for dropped hunks; check sibling tests are byte-identical to main
  unless a conflict forced an edit (and then the edit keeps their assertions).
