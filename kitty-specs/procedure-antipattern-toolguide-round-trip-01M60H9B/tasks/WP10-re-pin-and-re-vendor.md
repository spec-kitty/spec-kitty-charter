---
work_package_id: WP10
title: Re-pin and re-vendor
dependencies:
- WP07
- WP09
requirement_refs:
- FR-002
- FR-004
- FR-006
- FR-007
- FR-009
- NFR-001
- NFR-002
- NFR-003
- SC-001
- SC-002
- SC-003
- SC-004
- C-002
- C-007
planning_base_branch: feat/194a-procedure-antipattern-toolguide
merge_target_branch: feat/194a-procedure-antipattern-toolguide
branch_strategy: Planning artifacts for this mission were generated on feat/194a-procedure-antipattern-toolguide. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/194a-procedure-antipattern-toolguide unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-procedure-antipattern-toolguide-round-trip-01M60H9B
base_commit: 9b0676d4c5596a1088b039bc4d24a9223861ee78
created_at: '2026-10-29T18:49:15.774662+00:00'
subtasks:
- T049
- T050
- T051
- T052
- T053
phase: PR-A2 - Migration
agent: claude
history:
- at: '2026-10-29T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/resources/whole-pack/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ProcedureAndGuideCorpusTest.java
execution_mode: code_change
owned_files:
- gradle.properties
- packs/internal/**
- packs/qa/**
- service/packs/src/test/resources/whole-pack/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/VendoredPacksTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackOpenPacksRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ProcedureAndGuideCorpusTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/OpenPacksCorpus.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP10 — Re-pin and re-vendor

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

**Precondition**: the open-packs PR from WP09 is merged (operator decision). Move `openPacksRef` to the merged commit,
re-vendor the internal and qa packs, remove this mission's red-baseline entries, add the procedure and guide corpus
tests, delete the harness, and prove the whole gate and `verifyAll` green at the new pin — in one commit-coherent
change (the red window closes here).

## Branch Strategy

Planning branch and merge target `feat/194a-procedure-antipattern-toolguide`; lanes from `lanes.json`.
`spec-kitty agent action implement WP10 --agent <name> --mission procedure-antipattern-toolguide-round-trip-01M60H9B`.
**PR-A2** opens after this WP.

**Out-of-map edit (recorded)**: delete `service/packs/src/test/java/ai/speckitty/charter/packs/migration/**` and
revert the `migration` tag/task in `service/packs/build.gradle.kts` (owned by WP09, approved before this WP).

## Context

- `gradle.properties` `openPacksRef` comment style (keep the explanatory comment current: name the open-packs PR);
  `service/packs/src/test/resources/whole-pack/expected-red.yaml` and `known-failures.yaml` (self-expiring, #247:
  a listed item that round-trips fails "remove it"; `at` strings must match exactly).
- `VendoredPacksTest` + `LOCAL_DELTA` (repo-local deltas such as `semi-auto-delivery.procedure.yaml`).
- `WholePackOpenPacksRoundTripTest` hard-coded counts (profiles/assets/templates) — add pinned procedure and guide
  counts; `OpenPacksCorpus.YAML_FILE_COUNT` may move.

## Subtasks

### T049 — Pin and vendored packs

1. `git -C ../spec-kitty-open-packs fetch origin main && git -C ../spec-kitty-open-packs checkout <merged-sha>`;
   set `openPacksRef=<merged-sha>` with an updated comment.
2. Re-vendor `packs/internal` and `packs/qa` from the pinned checkout (copy exactly; keep the repo-local delta files
   and re-apply the sanitized shape to any delta file that is a procedure or toolguide — migrate it with the harness
   before deleting it, T052); recompute `LOCAL_DELTA`.

### T050 — Baseline

Remove `kind: procedure` and `kind: other:toolguides` from `expected-red.yaml`; keep `anti_pattern.graph.yaml` (#236)
exactly; refresh any other entry whose `at` text the new pin changed (only if the gate reports it; never widen an
entry). `known-failures.yaml`: add an entry only for a residual divergence that the open-packs PR could not fix,
keyed `spec-kitty/spec-kitty-open-packs#<n>`; expected none.

### T051 — Corpus tests and counts

`ProcedureAndGuideCorpusTest` (tagged `open-packs`): all 41 procedures and 24 toolguides with guides across built-in,
internal and qa round-trip; counts asserted. Add pinned procedure and guide counts to `WholePackOpenPacksRoundTripTest`;
update `OpenPacksCorpus.YAML_FILE_COUNT` if it moved.

### T052 — Delete the harness

Delete the migration package and the `migration` tag/task; add an assertion (in `ProcedureAndGuideCorpusTest` or a
small test) that no class in package `..packs.migration..` exists on the test classpath.

### T053 — Full gate and `verifyAll`

`./gradlew :packs:test :packs:openPacksRoundTrip :packs:wholePackRoundTrip :deployable:test` and `./gradlew verifyAll`
(Java 25) green; docs checks green. Record outputs in the review handoff. Positive controls: temporarily break the
procedure writer (drop a field) and the Markdown writer (drop a section) locally and show the gate goes red — do not
commit the break; record the observation.

## Definition of Done

- Exit criterion E2 (plan.md) met except the docs WP; harness gone; gate green at the new pin.
- Tell the orchestrator the new `openPacksRef` SHA (the parent session needs it for #195).
- Mark T049–T053 done.

## Risks / reviewer guidance

- Reviewer: no baseline widening; `anti_pattern.graph.yaml` entry intact; vendored packs equal the pin except the
  recomputed delta; harness absent.
