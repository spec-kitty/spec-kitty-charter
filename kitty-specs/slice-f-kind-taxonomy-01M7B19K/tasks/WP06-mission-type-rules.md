---
work_package_id: WP06
title: Mission-type rules and template_set
dependencies: [WP05]
requirement_refs:
- FR-009
- FR-010
- FR-011
planning_base_branch: slice-f-537-kind-taxonomy
merge_target_branch: slice-f-537-kind-taxonomy
branch_strategy: Planning artifacts for this mission were generated on slice-f-537-kind-taxonomy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-f-537-kind-taxonomy unless the human explicitly redirects the landing branch.
subtasks:
- T026
- T027
- T028
- T029
agent: claude
history:
- at: '2026-11-13T20:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MissionTypeFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MissionStepLinkRule.java
- service/packs/src/test/resources/planted/r32-mission-step-unlinked/expected.json
- service/packs/src/test/resources/planted/r33-authored-action-sequence/expected.json
- service/packs/src/test/resources/planted/r34-authored-template-set/expected.json
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MissionTypeFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackContents.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MissionStepLinkRule.java
- contracts/packs/schemas/charter.schema.json
- contracts/charter/scenarios/mission-types.feature
- service/packs/src/test/resources/planted/r32-*/**
- service/packs/src/test/resources/planted/r33-*/**
- service/packs/src/test/resources/planted/r34-*/**
- service/packs/src/test/resources/planted/charter-valid/**
role: implementer
---

# Work Package Prompt: WP06 - Mission-type rules and template_set

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/` and adopt it.

Then read these files in `kitty-specs/slice-f-kind-taxonomy-01M7B19K/`:

- `spec.md`: its Intent Summary and the requirements this work package maps to.
- `plan.md`: concern IC-02.
- `data-model.md`.
- `research.md`: R6, R14 and R15.
- `research/squad-post-plan.md`.

## Objective

Every step and on-demand step of a mission type has its authored `mission_type -requires-> action` edge (operator ruling `DM-01M7B1YQR5ZKQPDW6DX64KPNRN`). It is refused per pack at load and at registration, and on the winning type at compose. An authored `action_sequence` or `template_set` is proven refused. `template_set` leaves the charter schema.

## Context

- WP01 and WP02 already added the edges to every valid-path pack.
- `PackPolicy` is in `packs.domain` and cannot depend on `missions.domain` (`PACKS_DOMAIN_IS_PURE`), so the rule works over plain-data `MissionTypeFacts` held in `PackContents`.
- Squad: D-M3, D-M6, T-B3.

## Subtasks

### T026: mission_step_unlinked per pack

- `MissionTypeFacts`: per mission type, its id and its step ids (on-demand included), read by the existing mission-type codec into `PackContents`. Plain records, with no missions-domain types.
- `MissionStepLinkRule.judge(facts, edges)` returns one finding per step with no `requires` edge from its type, and one per `requires` edge from the type to an action that is not one of its steps.
- Wire it into `PackPolicy`'s judgement and the load path, as `PackFindingCode.MISSION_STEP_UNLINKED` (blocking), mapped in `violationKind`. Add the row in `CharterRefusalCode.yaml` (an out-of-map edit; WP05 owns that file).
- Registration refuses it with `422 validation_failed`.

### T027: Compose-time check on the winning type

- At compose, `EdgeIndex` repoints a losing type's `requires` edges to the winner (`EdgeIndex.java:193-206`).
- Check the winning type: every step has an edge, and no edge names a step the winner lacks. A failure is `pack_unreadable` with `mission_step_unlinked`.
- Add a composition scenario where an org pack's override of a mission type drops a step and leaves its edge. It is refused.

### T028: action_sequence and template_set

- Add planted packs `r33-authored-action-sequence` and `r34-authored-template-set`. Each is a mission type with that one field, refused as an unknown field by name (the closed schema already does this).
- Add a `@contract` scenario in `mission-types.feature`: whole-pack registration of such a pack is refused, and the violation names the field.

### T029: template_set and the javadoc

- Remove `template_set` from `charter.schema.json` (`CharterCatalog` lines ~84 and ~113, `GovernanceCharterConfig` ~357, and its `required` lists).
- Update `ProjectCharterYamlSchemaTest:38` and `planted/charter-valid/files/charter.yaml:3`.
- Add an invalid charter fixture that carries `template_set`, refused as an unknown field.
- `RelationType.ENHANCES`'s javadoc no longer mentions "action sequence".

## Validation

- `:packs:test :deployable:test` are green.
- Each new planted pack is refused with its single fault.

## Definition of Done

- T026 to T029 are done, and the codes are registered and covered by examples.
- Every commit is signed, carries no `Co-Authored-By` trailer and names no model.
- The tests this work package touches are green. Gradle runs in the foreground with `--max-workers=1`, one run at a time.
- Out-of-map edits are recorded with a one-line rationale in the commit message.

## Risks and reviewer guidance

- **A mission type that is extended across packs.** Step enhancements add no steps (`step-enhancement.schema.json:5`), so they need no edges.

## Branch Strategy

Planning happened on `slice-f-537-kind-taxonomy`, which is also the final merge target. `finalize-tasks` computes the
execution lanes into `lanes.json`, and each lane gets its own worktree. Do not choose a base branch by hand.

```bash
spec-kitty agent action implement WP06 --agent claude --mission slice-f-kind-taxonomy-01M7B19K
```

## Coordination

- **Slice C** is already on `main` (PR #566), and this branch is based on it.
- **#554 and #556 (epic #549).** Until #554 lands, the read check stays in `PackPolicy`, and whichever lands second rehomes it. Check the open PRs before each WP.
- **#551.** It changes the `kernel.validation` API; the second of the two to land rebases.
- **#106.** It renames `glossary_pack` to `glossary`; the second to land rebases.
- **#552.** It drops `MissionStepContractIdentifier` from its list.
- **Slice D close-out (#367, #371).** It runs in parallel, so rebase or merge `main` often. Never force-push a pushed branch.
