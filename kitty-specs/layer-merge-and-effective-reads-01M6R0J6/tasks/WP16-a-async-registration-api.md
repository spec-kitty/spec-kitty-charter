---
work_package_id: WP16
title: A · Asynchronous registration API, access, flags and audit
dependencies:
- WP15
requirement_refs:
- FR-001
- FR-004
- FR-024
- FR-037
- FR-039
- NFR-004
- NFR-008
- NFR-009
- NFR-010
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T076
- T077
- T078
- T079
- T080
- T081
- T082
phase: PR A
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationController.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationJobs.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/AuditLog.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArchiveBodyAdvice.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationMapper.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RegistrationProperties.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationJobsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PackRegistrationControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ArchiveBodyAdviceTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/PackRegistrationSteps.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/RegistrationWiring.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationJobs.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/AuditLog.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArchiveBodyAdvice.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRegistrationMapper.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RegistrationProperties.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationJobsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PackRegistrationControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ArchiveBodyAdviceTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/PackRegistrationSteps.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/RegistrationWiring.java
role: implementer
tags: []
tracker_refs: []
---
# WP16: A · Asynchronous registration API, access, flags and audit

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Serve `PUT /packs/{packId}`, `POST /packs` (handing a source to the fetchers of WP17/WP18), `GET /packs/{packId}`
and `GET /pack-registrations/{registrationId}` asynchronously (FR-024): synchronous checks, then `202` + status URI;
a bounded executor runs the registration job; the client polls (no outbound callback: SSRF).

Implementation command:
`spec-kitty agent action implement WP16 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A (stacked on PR A0).
- **Concerns:** IC-14, IC-18, IC-02 (controller). **Requirements:** FR-001, FR-004, FR-024, FR-037, FR-039, NFR-004,
  NFR-008, NFR-009, NFR-010.
- **Synchronous checks:** access (`401` without an authenticated principal), `charter.registration.enabled` (default
  `false`) and `charter.pack-sources.<kind>.enabled` (`503 pack_source_unavailable`), the packId grammar, body size
  (`413` from `Content-Length` before Spring buffers), media type and magic bytes (`415`), `If-Match`/`If-None-Match: *`.
- **Executor:** 2 running, 8 waiting; the next answers `503` with `Retry-After`. `If-Match` re-checked under the lock;
  moved → `refused` `412`. Status kept 1 h after it ends, at most 1,000 entries (one replica). Identical re-send ends
  `succeeded` with outcome `unchanged`.
- **Audit (NFR-010):** one record per attempt through an `AuditLog` port (structured log default): principal,
  `packId`, source kind and locator with the query cut, outcome, revision before/after.

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


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

### T076: ArchiveBodyAdvice

Reads `Content-Length` and wraps the stream before binding: `413` over 32 MiB without reading; magic bytes vs `Content-Type` (`415`); a chunked body is counted while streaming.

### T077: RegistrationJobs

Bounded executor, job states, retention (1 h, 1,000), `503 Retry-After` when full; tests with a fake clock.

### T078: Controller and mapper

The four operations on the generated interface; `Location` and `_links`; `PackRegistrationMapper` to the contract shapes; `GET /packs/{packId}` reads the stored summary.

### T079: Access, flags and properties

`RegistrationProperties` (`charter.registration.*`, `charter.pack-sources.*`); `401`/`503` paths; `RegistrationWiring` in deployable.

### T080: Audit

`AuditLog` port + structured-log adapter; a test asserts one record for each accepted, refused and failed attempt.

### T081: Scenario glue and NFR-008

`PackRegistrationSteps`: build `tar.gz`/`zip` from the inline fixture packs, poll the status URI; make `whole-pack-registration.feature` green for the `PUT` paths. NFR-008: `202` within 1 s of the last byte at p95 over 50 runs of a 32 MiB archive (a `timing`-tagged test, report-only).

### T082: Native upload test

Add one `tar.gz` and one `zip` upload to the nightly native test set (ADR-0060), with any hint beside `ActivationRuntimeHints`' pattern.

## Test strategy

Unit tests per class; `whole-pack-registration.feature` (PUT paths) green; the timing test reported.

## Risks

- A restart forgets pending jobs (their URI answers `404`); clients re-send, which the version rule makes idempotent — document in WP20.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
