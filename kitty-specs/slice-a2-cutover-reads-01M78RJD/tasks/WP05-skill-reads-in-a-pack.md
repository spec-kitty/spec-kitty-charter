---
work_package_id: WP05
title: Skill reads in a pack (#107)
dependencies:
- WP02
- WP03
- WP04
requirement_refs:
- FR-007
- FR-008
- FR-010
planning_base_branch: slice-a2-536-cutover-reads
merge_target_branch: slice-a2-536-cutover-reads
branch_strategy: Planning artifacts for this mission were generated on slice-a2-536-cutover-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-a2-536-cutover-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-slice-a2-cutover-reads-01M78RJD
base_commit: ebb13c23b45dabf2f18ed815a0596e9c72bea2ed
created_at: '2026-11-12T23:55:25.664587+00:00'
subtasks:
- T022
- T023
- T024
- T025
phase: ''
history:
- at: '2026-11-12T22:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/
create_intent:
- contracts/charter/paths/packs_packId_artifacts_skill.yaml
- contracts/charter/paths/packs_packId_artifacts_skill_artifactId.yaml
- contracts/charter/schemas/SkillSummary.yaml
- contracts/charter/schemas/SkillList.yaml
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/SkillReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/SkillView.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/PacksSkills.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/SkillsController.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/SkillBodies.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/SkillsControllerTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/SkillBodiesTest.java
- contracts/charter/scenarios/skills.feature
- contracts/charter/changes/2026-11-12-skill-reads.md
execution_mode: code_change
owned_files:
- contracts/charter/paths/packs_packId_artifacts_skill.yaml
- contracts/charter/paths/packs_packId_artifacts_skill_artifactId.yaml
- contracts/charter/schemas/SkillSummary.yaml
- contracts/charter/schemas/SkillList.yaml
- contracts/charter/examples/SkillList*.yaml
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/SkillReads.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/application/SkillView.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/packs/PacksSkills.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/SkillsController.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/SkillBodies.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/SkillsControllerTest.java
- service/artifacts-edge/src/test/java/ai/speckitty/charter/artifacts/infra/api/SkillBodiesTest.java
- contracts/charter/scenarios/skills.feature
- contracts/charter/changes/2026-11-12-skill-reads.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP05 - Skill reads in a pack (#107)

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/slice-a2-cutover-reads-01M78RJD/`:

- `spec.md`
- `plan.md`, its concern for this WP
- `research.md`
- `research/squad-post-plan.md`

## Objective

Serve a pack's skills (#107; ADR-0074 decisions 4, 9 and 10; research.md R8; FR-007, FR-008 and FR-010).

| Request | Answer |
|---|---|
| `GET /packs/{p}/artifacts/skill` (`listPackSkills`, tag `Skills`) | `200 SkillList` of `SkillSummary{id, title, description, form, _links.self}`, id order, unpaged |
| `GET /packs/{p}/artifacts/skill/{artifactId}` (`getPackSkill`) | `200 Artifact` with member `skill` (R8 fields); `body` exactly as held for a prompt skill, absent for a wrapper |
| unknown pack / absent skill | `404 pack_not_found` / `404 artifact_not_found` |

## Context

- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/Skill.java`: `title()`, `description()`, `triggers()`, `form()`, `body()` (:189, present exactly for a prompt skill), `parameters()`, `invocation()`, `expandsTo()` and `tools()`.
- The body is the `text` companion (`SkillBinding.java:45-60`).
- Riding `ForeignArtifactRead.read` and `held` (WP04); the WP04 module patterns: `AssetReads`, `PacksAssets`, `AssetBodies`.
- Fixture example: `activation-kind-model.feature:125-137` (`skills/land-pr.skill.yaml` plus `land-pr.skill.md`).

## Subtasks

### T022: Contract

- Write the two path files, the `Skills` tag, `SkillSummary`, `SkillList` and the examples.
- The description says the body is answered verbatim, and that `bodyPath`, `overrides` and `enhances` are not answered (R8: content, not paths; edges through references).

### T023: `SkillBodies`

- Map `SkillView` to the `Skill` DTO (WP02's schema), field by field, with optional members omitted when empty.
- `SkillBodiesTest` checks every field, a body that is byte-identical (with a trailing newline and unicode), and a wrapper skill with no body.

### T024: Controller

- `SkillReads`, `PacksSkills` and `SkillsController implements SkillsApi`. The item answers the `Artifact` envelope (`kind: skill`), with `_links` `self` and `collection`; the ETag is the pack revision.

### T025: Scenarios

- Write `contracts/charter/scenarios/skills.feature`: the list with a prompt skill and a wrapper skill, both items, and the 404s.
- Add the registry rows and the fragment.

**Shared registries (out-of-map rows, recorded).** Add rows for this WP's operations to the files WP01 owns:

- `contracts/charter/openapi.yaml`
- `contracts/charter/scenarios/switch-gate.yaml` (`ready`)
- `every-operation.feature`, `unexpected-failure.feature` and `read-refusals.feature`
- the relation table and the lint enum, only for relations this WP introduces

Record each edit in the commit message as "out-of-map: registry rows for <operationIds>". Keep each file's existing order: path order in openapi.yaml, and the table orders.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :contracts:check :artifacts-edge:test
./gradlew --max-workers=1 :deployable:test   # contract scenarios, switch gate, link checks, ArchUnit
```
Run Gradle in the foreground, one run at a time; never `--stop`.

## Definition of Done

- Both operations are `ready`.
- The body is verbatim.
- A wrapper skill has no body member.

## Risks and reviewer guidance

- The closed schema must accept every field a canonical skill file can hold. Check against `contracts/packs/schemas/skill.schema.json`.

## Branch Strategy

- **Planning base and merge target:** `slice-a2-536-cutover-reads`.
- **Mission branch:** `kitty/mission-slice-a2-cutover-reads-01M78RJD`.
- **Worktrees:** allocated per computed lane (`lanes.json`).
- **Implement with:** `spec-kitty agent action implement WP05 --agent claude --mission slice-a2-cutover-reads-01M78RJD`.
- **Commits:** sign them if signing works. Add no model or `Co-Authored-By` trailer. Reference the issue, for example `(#107)`.

## Coordination

Slice C (#467) is in flight and changes:

- the `prompt` path and link relation;
- four schemas;
- `mission_type_not_in_force` in `CharterRefusalCode`;
- `ClosureQueries.closureFingerprint` ordering.

Do not use or change those names. Do not pin fingerprint values in tests.
