---
work_package_id: WP15
title: A · Registration impact check, refusals and version history
dependencies: [WP14]
requirement_refs:
- FR-002
- FR-025
- FR-026
- FR-027
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts were generated on issue-10-layer-merge-effective-reads; execution worktrees come from lanes.json and completed changes merge back into issue-10-layer-merge-effective-reads. The four PRs (A0, A, B, C) are cut from it at closeout.
subtasks:
- T070
- T071
- T072
- T073
- T074
- T075
phase: PR A
assignee: ''
agent: claude
shell_pid: ''
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationImpact.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationImpact.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ActivationImpact.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackVersionHistory.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/VersionHistoryStore.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationImpactTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackVersionHistoryTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/VersionHistoryStoreTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationImpact.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ActivationImpact.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackVersionHistory.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/VersionHistoryStore.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationImpactTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackVersionHistoryTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/VersionHistoryStoreTest.java
role: implementer
---
# WP15: A · Registration impact check, refusals and version history

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

- **Impact check (FR-025, IC-15):** with the staged pack in place, compose **every closure that contains it** (found
  from the reverse `depends_on` index), not one global compose, and refuse `422` naming the closure root and the packs
  involved on: a compose failure (C17–C21), a mutability violation with the A15 lock scope, an `overrides` edge onto a
  target that is not `open` (A6, `override_not_permitted`), an `enhances` cycle or an enhancer with no route to a
  non-enhancer (A8, `enhances_cycle`), an artifact that both enhances and overrides one target (A11,
  `conflicting_relations`), an `overrides` edge that resolves to its own source after layer re-pointing (A12,
  `self_override`), an `overrides` cycle (C28, `override_cycle`).
- **Notices (FR-027, B4):** on success list the must-have items and locks added and removed. The affected
  activations come through an `ActivationImpact` port that `packs` declares and `activations.infra` implements in PR B
  (here: a no-op default bean).
- **History (FR-026, B1, IC-16):** `<config-root>/packs/.history/<packId>.json`, a list of `(pack_version, revision,
  registeredAt)` written in the same swap as the pack; a version seen with another revision → `409
  pack_version_conflict` whether or not it is the stored one; `DELETE` keeps the history.

Implementation command:
`spec-kitty agent action implement WP15 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A (stacked on PR A0).
- **Concerns:** IC-15, IC-16. **Requirements:** FR-002, FR-025, FR-026, FR-027.
- **Rulings:** A5 `DM-01M6RA8225EXG6GAT1MM2R9T6C`, A6 `DM-01M6RA86QPBM1VE5QZTB9AZ195`, A8
  `DM-01M6RA8G0RGRWSFW4DERH8W319`, A11/A12 `DM-01M6RA8S9G57W4BW55SNVHBQ10`, B4 `DM-01M6RA9SAKCBSKGX165JM3CAGS`, B1.
- **Read side:** stored packs registered before the rule are not re-judged; the startup census logs them.

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


### Files shared with other work packages

These files are owned by another WP (the no-overlap rule) but this WP must make the small edit named. Make only that edit, and record a one-line rationale in the commit message (`out-of-map: <file> — <why>`):

- `service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRegistrationService.java` — call the impact check as step 9 and the history check in step 5 (owned by WP14)
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackCommitStore.java` — write the history file inside the same swap (owned by WP14)


## Project constraints (apply to every subtask)

- **TDD, contract first (C-001).** Write the failing test or `@contract` scenario first, watch it fail for the right
  reason, then write the code. A contract change lands with its `contracts/charter/changes/` fragment before the code
  that serves it.
- **No disabled tests, no new suppressions (C-006, NFR-001).** No `@Disabled`, no `@WIP` left on a scenario this WP
  makes green, no `@SuppressWarnings`/Checkstyle/PMD/SpotBugs suppression added, and the suppression register does not
  grow. Methods stay within CC ≤ 15, NPath ≤ 1000, ≤ 60 lines and nested-if depth ≤ 1.
- **Hexagonal slices, no shadow logic (C-002).** Merges and walks live in `packs`; `activations` reaches `packs` only
  through the `AvailablePacks` port and `activations.infra.packs`. `HexagonalArchitectureTest` must stay green.
- **No core changes (C-003).** Nothing in spec-kitty core is edited, and no PR or issue is opened in core.
- **git is a host binary (C-008).** Only the system `git` ≥ 2.50.1, never JGit, never a work-tree checkout.
- **Gradle.** Run module tasks, not the full suite (`NO_FULL_HEAVY_SUITES_IN_MISSION`):
  `./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none <module>:check`.
  On a Maven Central `429`, wait and re-run the same command; never turn dependency verification off.
- **Divergences (C-007).** Every behaviour that differs from core gets a divergence-register entry in the PR that
  ships it (the docs WP of that PR writes it; tell it what you changed in your hand-off note).
- **Commits** end with the session's attribution lines; no model id in commits or PR text.

## Branch Strategy

- Planning branch: `issue-10-layer-merge-effective-reads`; final merge target: `issue-10-layer-merge-effective-reads`.
- Execution worktrees are allocated per computed lane from `lanes.json`; do not pick a base branch by hand. Dependencies are a claim gate: this WP can be claimed only when its dependencies are approved or done.
- At closeout the mission branch is cut into the stacked PRs #399 → A0 → A → B → C; keep this WP's commits inside its PR letter (A).

## Subtasks

### T070: Closures containing the pack

A reverse `depends_on`/lineage index over the configured packs plus the staged one; the set of closure roots to compose. Test with a diamond and an unrelated pack (not composed).

### T071: Compose and mutability per closure

Run `PackCompositionResolver` per closure with the staged pack; map each refusal to `422` with the closure root and packs.

### T072: Relation refusals (A6, A8, A11, A12, C28)

Pure checks over the staged pack's closure edges, one test per refusal code plus a passing near-miss for each (e.g. an `enhances` chain grounded in a non-enhancer passes).

### T073: Floor and lock notices (B4)

Diff the floor and lock sets before/after per closure; return them in the outcome; the `ActivationImpact` port with a no-op default bean.

### T074: Version history (B1)

`VersionHistoryStore` reads/writes the JSON; `PackVersionHistory` judges; tests for a re-registered old version with other content (`409`), the same content (accepted), and `DELETE` keeping the file.

### T075: Census of stored packs

At startup, run the relation checks over stored packs and log (WARN) each pack that would now be refused; no refusal at read beyond today's `500 pack_unreadable`.

## Test strategy

`RegistrationImpactTest`, `PackVersionHistoryTest`, `VersionHistoryStoreTest`, and the IC-15 scenarios in `whole-pack-registration.feature`.

## Risks

- The check composes several closures under the registry lock: NFR-009's 5 s budget bounds it; measure the 10-pack fixture and note the time.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
