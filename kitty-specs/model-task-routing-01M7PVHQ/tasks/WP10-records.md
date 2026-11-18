---
work_package_id: WP10
title: Records and closeout docs
dependencies:
- WP08
- WP09
requirement_refs:
- FR-015
- SC-004
- NFR-004
- C-004
planning_base_branch: feat/108-model-task-routing
merge_target_branch: feat/108-model-task-routing
branch_strategy: Planning artifacts for this mission were generated on feat/108-model-task-routing. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/108-model-task-routing unless the human explicitly redirects the landing branch.
subtasks:
- T037
- T038
- T039
- T040
phase: Phase 4 - Records
history:
- at: '2026-11-18T08:55:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- contracts/charter/changes/2026-11-18-routing-catalog-and-model-recommendation.md
execution_mode: code_change
owned_files:
- contracts/charter/changes/2026-11-18-routing-catalog-and-model-recommendation.md
- docs/api/service-contract.md
- docs/conformance/divergence-register.md
- docs/conformance/divergences.yaml
- docs/architecture/logical-model.md
- docs/guides/compose-packs.md
role: documentarian
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP10 – Records and closeout docs

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according to
its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `documentarian`
- **Agent/tool**: `claude`

---

## ⚠️ Review Feedback

Check the `review_ref` field in the event log (`spec-kitty agent tasks status`). Address every feedback item, and record
each change with `spec-kitty agent tasks add-history WP10 --note "..."`.

## Branch Strategy

- **Planning base branch**: `feat/108-model-task-routing`
- **Merge target branch**: `feat/108-model-task-routing`
- The execution worktree is allocated per computed lane from `lanes.json`; never pick a base branch by hand.

```bash
spec-kitty agent action implement WP10 --agent claude --mission model-task-routing-01M7PVHQ
```

## Before you start (every WP)

1. **#355 must be merged.** No code starts before #355 (PR #599) has merged and this branch is rebased onto that main
   (C-001). If it has not merged, stop and report.
2. **Recheck every path below on the merged main.** This prompt was written against main `8284bd36` with #599 open. #599
   rewrites `PackContents`, `PackRulesets`, `PackAssembly`, `StoreRead`, `StorageRulesets` and
   `ReferenceFacts`/`ReferenceClosure`, and deletes `MissionTemplates` and `GuardClosure` (research R1). Follow the
   merged code, not this text, where they differ, and record the difference in the WP history.
3. **Read the authority:**
   - `docs/adr/0080-the-routing-catalog-kind-and-the-model-recommendation.md` (accepted);
   - `kitty-specs/model-task-routing-01M7PVHQ/{plan.md,data-model.md,research.md,contracts/operations.md}`;
   - the charter companion `.kittify/charter/charter.md`.

   Core's `/opt/spec-kitty/src/charter/offering/model_task_routing/` is evidence only (ADR-0015).
4. **Tests use the canonical shape only**, except tests of invalid content (charter companion; ADR-0070). Never exclude
   a field from a parity or round-trip check to make a test pass.
5. **Commits:** signed (`git commit -S`), ending with the `Claude-Session:` trailer, with no model names and no
   `Co-Authored-By`.

## Objective

Make the decision visible where clients and reviewers look, and prepare the closeout of #108 and #94 (FR-015, SC-004,
NFR-004; plan IC-06; squad finding C3).

## Subtasks

### T037 – The contract changelog fragment

- **Write** `contracts/charter/changes/2026-11-18-routing-catalog-and-model-recommendation.md` under the rules in
  `contracts/charter/changes/README.md`. It has one block, `### Breaking` (ADR-0022, 0.x line: a closed enum gaining
  values is breaking for a generated client, CHANGELOG precedent near line 2330).
- **It lists:**
  - the new kind in `ActivatableKind`, `ArtifactKindToken`, `NodeKind`, `TaxonomyKind` and `CascadeKind`;
  - the selection and preset properties;
  - the four catalog reads and `getModelRecommendation`;
  - the three refusal codes;
  - `MissionStep.modelTier`;
  - the link relations.
- **It names core's seam:** regenerate the DTOs, then handle the new kind, the codes and the read that replaces
  `charter.model_routing.load`/`evaluate`.
- **Check:** `ChangelogFragmentsTest` passes.

### T038 – Service contract

In `docs/api/service-contract.md`:

- **The R14 row** (`grep -n "R14\|#108"`): decided and built. It names the five operations, ADR-0080 and the
  divergences DIV-701 to DIV-707, and records that core's `load`/`evaluate` is replaced by the activation read.
- **The operation rows:** add the five operations where the skill reads are listed (lines 74, 86 and 102–111 region).
- **#94's decided-reads table:** every row has a shape, and no row lacks one. Check #94's done-when ("lists the decided
  reads, with no row lacking a shape"), and say in the WP history whether it now holds.

### T039 – Divergence register

- **Move DIV-701 to DIV-707 from `proposed` to `open`** in both forms: `docs/conformance/divergences.yaml` and
  `divergence-register.md`. The block intro says the entries are open since the reads are built.
- **Replace each `evidence` list's ADR-only line** with the scenarios that pin it:
  - DIV-704: R7 rows 4–6;
  - DIV-705: row 2;
  - DIV-707: the refusal scenarios.
- **Check:** `DivergenceRegisterTest` passes (the two forms list the same ids).

### T040 – Logical model and the pack-author guide

- **`docs/architecture/logical-model.md`:** add the kind row (near line 299, beside skill) and its activation facts.
- **`docs/guides/compose-packs.md`:** a short section on shipping a routing catalog, covering:
  - the file and its placement;
  - layering by the same id, and `open` and `locked`;
  - selecting one catalog per activation, and the `409` on two;
  - a step's `model_tier`;
  - that the answer is advice.

  Link ADR-0080.
- **Docs checks:** run every `check_docs.py` check, markdownlint and cspell on the changed files; no new finding.

## Definition of Done

- The fragment, the service contract, the register and the docs are updated, and the docs checks are clean.
- `./gradlew verifyAll` passes on Java 25 for the modules this WP touches, with no new style-gate finding.
- Every subtask's status is recorded with `spec-kitty agent tasks mark-status`.
- Nothing outside `owned_files` changed, except a recorded edit outside the map, named in the WP history with its
  reason.

## Reviewer Guidance

- Check the R14 row and #94's table against the merged operations.
- Check that every DIV entry cites a scenario that exists.
- **Independent reviewer.** Review is by a separate reviewer session, never by the implementer.
