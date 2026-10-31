---
work_package_id: WP05
title: Migration harness and open-packs bundle
dependencies: [WP03]
requirement_refs:
- FR-010
- SC-004
planning_base_branch: issue-194-mission-type-consolidation
merge_target_branch: issue-194-mission-type-consolidation
branch_strategy: Planning artifacts for this mission were generated on issue-194-mission-type-consolidation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-194-mission-type-consolidation unless the human explicitly redirects the landing branch.
subtasks:
- T017
- T018
- T019
history: []
agent_profile: java-jenny
authoritative_surface: service/packs/src/migration/
create_intent: []
execution_mode: code_change
owned_files:
- service/packs/src/migration/**
- service/packs/build.gradle.kts
role: implementer
tags: []
task_type: implement
tracker_refs: []
---
# Work Package Prompt: WP05 — Migration harness and open-packs bundle

## Do This First: Load Agent Profile

Load the agent profile `java-jenny` (`spec-kitty agent profile show java-jenny`) and follow its guidance.

## Objective

Build the throwaway harness in a `migration` source set of `:packs`, off the test runtime classpath, with task `:packs:packMigration -PmigrationSource=<pinned checkout> -PmigrationTarget=<copy>` refusing to write into the pinned checkout. `LegacyMissionTierReader` reads every retired family as plain maps and fails on any unmapped key; `MissionTierMapper` maps by ADR-0044 §3 in precedence order (discovery/research merge; runtime order to `steps`; other step directories to `on_demand_steps`; profiles to primary roles; directive stems to ids; Q1 descriptions; Q2 templates; command templates to prompts; fragment `body_path`); output written with `MissionTypeYaml` and the Markdown codec; only retired-glob files deleted, every other file asserted unchanged. `FieldMappingVerifier` is independent (raw YAML, own expectation table, every drop listed, equality for repeats, pinned counts, reference ids resolve, load-then-write byte-identical) with a self-test of planted mapper defects. Run it on built-in, cave-kitty and qa; commit the report and diffs under `research/migration/`, the `git format-patch` bundle against `5df92a5d`, and issue drafts under `research/open-packs-issues/` (keyed `open-packs#TBD-<slug>`); send them to the orchestrator.

## Context

- ADR-0044 (`docs/adr/0044-mission-types-consolidated-into-one-sanitized-shape.md`), [spec.md](../spec.md),
  [plan.md](../plan.md), [data-model.md](../data-model.md), research dispositions in [research.md](../research.md).
- Test first: write the failing acceptance and unit tests named above before the code. Zero new suppressions; methods
  at complexity 15 or below; Javadoc on every type and member (doclint).

## Definition of Done

- The named tests exist, failed first, and pass.
- `./gradlew :packs:packMigration ...` green (and `check` stays green: no pinned path is affected before WP06).
- Owned files only; shared-file edits in one block.
