---
work_package_id: WP01
title: '`:missions` module and domain'
dependencies: [WP02]
requirement_refs:
- FR-005
- FR-006
planning_base_branch: issue-194-mission-type-consolidation
merge_target_branch: issue-194-mission-type-consolidation
branch_strategy: Planning artifacts for this mission were generated on issue-194-mission-type-consolidation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-194-mission-type-consolidation unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
history: []
agent_profile: java-jenny
authoritative_surface: service/missions/
create_intent: []
execution_mode: code_change
owned_files:
- service/missions/**
- settings.gradle.kts
- service/charter-reports/build.gradle.kts
- service/packs/build.gradle.kts
- service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java
- service/deployable/src/test/java/ai/speckitty/charter/missions/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---
# Work Package Prompt: WP01 — `:missions` module and domain

## Do This First: Load Agent Profile

Load the agent profile `java-jenny` (`spec-kitty agent profile show java-jenny`) and follow its guidance.

## Objective

Create the `:missions` Gradle module (`ai.speckitty.charter.missions.domain`, `@NullMarked`) as `:profiles` is built, and the domain of data-model.md: `MissionType` (a `CharterArtifact` of kind `MISSION_TYPE`, `relationships()` empty), `MissionTypeIdentifier`, `MissionStep`, `StepDocument`/`StepSection`, `ExpectedArtifact`/`ArtifactClass`, `MissionReference` (kernel `ArtifactKind`, restricted to the six guidance kinds), `PathConventions`. No file paths in the domain. Red-first: one accepted/refused pair per invariant (id grammar, blank fields, step id uniqueness across both lists, artifact key uniqueness and grammar, reference kind restriction, at least one step). Wire `settings.gradle.kts`, `charter-reports` `serviceModules`, `:packs` `api(project(":missions"))`, and `HexagonalArchitectureTest` (`SLICES`, `DOMAIN_RINGS`, `MISSIONS_DOMAIN_IS_PURE` with a known-bad fixture under `deployable` test sources). `ArtifactKind.MISSION_TYPE` comes from WP02, which runs first.

## Context

- ADR-0044 (`docs/adr/0044-mission-types-consolidated-into-one-sanitized-shape.md`), [spec.md](../spec.md),
  [plan.md](../plan.md), [data-model.md](../data-model.md), research dispositions in [research.md](../research.md).
- Test first: write the failing acceptance and unit tests named above before the code. Zero new suppressions; methods
  at complexity 15 or below; Javadoc on every type and member (doclint).

## Definition of Done

- The named tests exist, failed first, and pass.
- `./gradlew :missions:test :deployable:test --tests "*HexagonalArchitectureTest*"` green (and `check` stays green: no pinned path is affected before WP06).
- Owned files only; shared-file edits in one block.
