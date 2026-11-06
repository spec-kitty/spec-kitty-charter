---
work_package_id: WP11
title: 'A · Contract A: registration, status, removal and their red scenarios'
dependencies: [WP10]
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-005
- FR-006
- FR-024
- FR-037
- FR-039
- NFR-004
- C-001
- SC-001
- SC-003
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts were generated on issue-10-layer-merge-effective-reads; execution worktrees come from lanes.json and completed changes merge back into issue-10-layer-merge-effective-reads. The four PRs (A0, A, B, C) are cut from it at closeout.
subtasks:
- T047
- T048
- T049
- T050
- T051
- T052
phase: PR A
assignee: ''
agent: claude
shell_pid: ''
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/
create_intent:
- contracts/charter/paths/packs.yaml
- contracts/charter/paths/packs_packId.yaml
- contracts/charter/paths/pack-registrations_registrationId.yaml
- contracts/charter/schemas/PackRegistration.yaml
- contracts/charter/schemas/PackRegistrationStatus.yaml
- contracts/charter/schemas/PackSource.yaml
- contracts/charter/parameters/PackIfMatch.yaml
- contracts/charter/parameters/PackIfMatchRequired.yaml
- contracts/charter/changes/2026-11-07-a-pack-registration.md
- contracts/charter/scenarios/registration/whole-pack-registration.feature
- contracts/charter/scenarios/pack-removal.feature
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/paths/packs.yaml
- contracts/charter/paths/packs_packId.yaml
- contracts/charter/paths/pack-registrations_registrationId.yaml
- contracts/charter/schemas/PackRegistration.yaml
- contracts/charter/schemas/PackRegistrationStatus.yaml
- contracts/charter/schemas/PackSource.yaml
- contracts/charter/parameters/PackIfMatch.yaml
- contracts/charter/parameters/PackIfMatchRequired.yaml
- contracts/charter/changes/2026-11-07-a-pack-registration.md
- contracts/charter/scenarios/registration/whole-pack-registration.feature
- contracts/charter/scenarios/pack-removal.feature
- contracts/charter/openapi.yaml
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/paths/packs_packId_relationships.yaml
- contracts/charter/paths/packs_packId_artifacts_kind_artifactId.yaml
- contracts/charter/scenarios/registration/register-pack.feature
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalCode.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedRefusalCodesTest.java
role: implementer
---
# WP11: A · Contract A: registration, status, removal and their red scenarios

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Declare PR A's operations and refusals, and write their failing scenarios: `PUT /packs/{packId}` (archive),
`POST /packs` (source), `GET /packs/{packId}` (FR-039), `GET /pack-registrations/{registrationId}` (FR-024), the
artifact `DELETE` and the relationship `DELETE`.

Implementation command:
`spec-kitty agent action implement WP11 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A (stacked on PR A0).
- **Concerns:** IC-02, IC-14 (contract), IC-18 (contract), and the post-plan contract squad list (plan, "Contract
  findings folded"). **Requirements:** FR-001–FR-006, FR-024, FR-037, FR-039, NFR-004, C-001, SC-001, SC-003.
- **Read:** `contracts/contract-delta.md` (the PR A block, exact member names), data-model.md (`PackRegistration`,
  `PackRegistrationStatus`, `PackSource`).

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

### T047: Paths and parameters

- `PUT /packs/{packId}`: body `application/gzip` (plus `application/x-gzip`, `application/x-tar+gzip`) or
  `application/zip`, raw bytes; `202` + `Location: /api/v1/pack-registrations/{id}` + `PackRegistrationStatus`;
  `PackIfMatch` (`required: false`) and `If-None-Match: *`; `401`, `413`, `415`, `428`?, `412`, `503`
  (`Retry-After`). Check the delta for each status.
- `POST /packs`: body `PackSource`; same answers.
- `GET /packs/{packId}` (`getPack`): the stored pack summary with `_links.self`.
- `GET /pack-registrations/{registrationId}`: `pending|running|succeeded|refused`, `404` once forgotten.
- Both `DELETE`s: `PackIfMatchRequired` (`428` absent, `412` stale), `204`, per-kind `404` codes
  (`pack_not_found`, `kind_not_found`, `artifact_not_found`, `relationship_not_found`), `409`.

### T048: Schemas

- `PackRegistration` (pack id, version, level, revision, outcome `created|replaced|unchanged`, `source` with the
  resolved `commit`, `notices` for FR-027: floor and lock items added/removed, affected activations), `_links`.
- `PackRegistrationStatus` (state, timestamps, `result` or `problem`).
- `PackSource`: one closed object, `kind` in snake case (`https_archive`, `git`, `filesystem`), `packId`, `url` or
  `path`, `ref`, optional `sha256` or `commit`.

### T049: Refusal codes

- `CharterRefusalCode.yaml` and `kernel RefusalCode`: `pack_version_conflict` (409), `artifact_referenced` (409,
  with `packs`/referrers), `relationship_not_found` (404), `pack_source_refused` (422), `pack_source_unavailable`
  (503), `override_not_permitted`, `enhances_cycle`, `conflicting_relations`, `self_override`, `override_cycle` (all
  422, IC-15), `kind_not_found` if not yet declared. Use the `x-refusal` table format (ADR-0056).
- `ReservedRefusalCodesTest` updated: these codes are declared and raised by PR A (not reserved).

### T050: Changelog fragment

`changes/2026-11-07-a-pack-registration.md`: `### Added` per operation, `### Breaking` for PR-PACK-REGISTER-003's rewrite (two sources claiming one id now replace), one block per fragment.

### T051: Red scenarios: registration

- `registration/whole-pack-registration.feature` replaces the `@WIP` `register-pack.feature` (#374; retire the old
  file's scenarios by rewriting them here, then delete the old file — this is a replacement, not a disabled test).
- US1 AS1–AS7 (create, replace with removals, identical re-send `unchanged`, same version other content `409`,
  stale `If-Match` `412`, invalid archive `422`, changed level `422`), the status resource, `401` without a
  principal, `503` when `charter.registration.enabled=false`, `POST` per source kind (with the test seams named in
  WP17/WP18), and the impact-check refusals of IC-15.

### T052: Red scenarios: removal

`pack-removal.feature`: US2 AS1–AS4 (delete, `404` per code, `409 artifact_referenced` naming the referrer, a preset item of another pack as referrer, `428`/`412`), relationship delete, negative controls. Confirm red.

## Test strategy

Contract lint and `OpenApiDocumentTest`; the two new feature files red; `ReservedRefusalCodesTest`.

## Risks

- PR #393's `ProblemHandler` edits may collide: rebase before review.
- The generated Spring interface for a binary body is `Resource`; the body limit is enforced before binding by WP16.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
