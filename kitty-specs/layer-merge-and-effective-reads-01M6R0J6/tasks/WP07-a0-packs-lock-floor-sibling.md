---
work_package_id: WP07
title: 'A0 · packs rulings: lock scope, own policy, held floor, sibling settlement'
dependencies: []
requirement_refs:
- FR-028
- FR-033
- FR-035
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T029
- T030
- T031
- T032
- T033
phase: PR A0
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/PackMutabilityPolicy.java
create_intent: []
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackMutabilityPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/RequiredFloor.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackMutabilityPolicyClosureTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackMutabilityPolicyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/RequiredFloorTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCompositionResolverTest.java
role: implementer
tags: []
tracker_refs: []
---
# WP07: A0 · packs rulings: lock scope, own policy, held floor, sibling settlement

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Apply four rulings to `packs` behaviour #399 ships:
- **A15 (FR-033):** a pack's policy locks and enforces only artifacts of the pack and of the packs it builds on
  (`depends_on` and lineage ancestors), not unrelated packs that share a closure.
- **B8 (FR-033):** a pack's own policy never binds the pack itself; it protects its artifacts from other packs (C25
  aligned with C21).
- **A10 (FR-028):** the floor holds only ids some pack of the closure holds; unheld must-have items are dropped from
  the floor and reported.
- **A14 (FR-035):** `enhances` is not a sibling conflict; a settlement names exactly one winner.

Implementation command:
`spec-kitty agent action implement WP07 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A0 (own PR, stacked on #399 `issue-291-activation-writes`, before PR A; decision DM-01M6RFGB9NVD9MDWZ6AP0PBYH7).
- **Concern:** IC-19 (rows A15, B8, A10, A14). **Requirements:** FR-028, FR-033, FR-035.
- **Rulings:** A15 `DM-01M6RA97220XBR7RR447FE2JXD`, B8 `DM-01M6RAA6Y7NVVGKVKQV05GZYES`, A10
  `DM-01M6RA8MG55V8C2V9PXWDTRNQS`, A14 `DM-01M6RA92BS6GGCYTRCFQDYYY5S`.
- **Code areas:** `PackMutabilityPolicy.authorize` (line 157), `ClosureView` (locked set), `RequiredFloor`,
  `PackCompositionResolver` (sibling conflict).
- **Ruleset:** C14, C15, C21–C25, C29–C32, E3.

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


### Files shared with other work packages

These files are owned by another WP (the no-overlap rule) but this WP must make the small edit named. Make only that edit, and record a one-line rationale in the commit message (`out-of-map: <file> — <why>`):

- `service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java` — the locked set and the floor are computed with the scoped policy and the held-ids filter (owned by WP21)
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureQueries.java` — pass the held ids to `RequiredFloor` and surface the unheld report (owned by WP12)


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
- At closeout the mission branch is cut into the stacked PRs #399 → A0 → A → B → C; keep this WP's commits inside its PR letter (A0).

## Subtasks

### T029: Lock scope (A15)

- Tests first: project pack `p` with `kind_defaults: {directive: locked}` composed with an unrelated org pack `q`
  → `q`'s directives are not locked; a pack `e` depending on `b` locks `b`'s directive (enterprise enforcement).
- `μ_p(u)` restricted to `holds(p,u)` ∪ artifacts of `p`'s `depends_on`/lineage ancestors.

### T030: Own policy does not bind self (B8)

- Test: a pack whose own policy locks `x` can re-register `x` with a per-artifact PUT (today `409`).
- `PackMutabilityPolicy.authorize` skips the writing pack's own policy entries.

### T031: Floor holds held ids only (A10)

- `RequiredFloor` takes the held-id set; must-have items no pack holds are excluded and returned as an
  `unheld` report (a value the reads surface in PR B).
- Test: preset must-have `directive:Z` that no pack holds → not in the floor, reported.

### T032: Sibling settlement (A14)

- `PackCompositionResolver`: two siblings both `enhances` T is not a conflict; a settlement must name exactly one
  winner (a settlement naming two answers the existing conflict refusal).
- Tests for both.

### T033: Closure tests

- `PackMutabilityPolicyClosureTest` covers the scoped lock across a three-level closure, and the own-policy
  exemption under a whole-pack replace (prepares WP14).

## Test strategy

`PackMutabilityPolicyTest`, `PackMutabilityPolicyClosureTest`, `RequiredFloorTest`, `PackCompositionResolverTest`.

## Risks

- A15 narrows locks: an activation that relied on an unrelated pack's lock now sees fewer locks in force; record it for the A0 changelog (hand-off to WP08's fragment).

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
