---
work_package_id: WP06
title: Re-pin and gates
dependencies: [WP04, WP05]
requirement_refs:
- FR-009
- FR-011
- SC-001
- SC-003
- SC-002
planning_base_branch: issue-194-mission-type-consolidation
merge_target_branch: issue-194-mission-type-consolidation
branch_strategy: Planning artifacts for this mission were generated on issue-194-mission-type-consolidation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-194-mission-type-consolidation unless the human explicitly redirects the landing branch.
subtasks:
- T020
- T021
- T022
history: []
agent_profile: java-jenny
authoritative_surface: gradle.properties
create_intent: []
execution_mode: code_change
owned_files:
- gradle.properties
- packs/qa/**
- contracts/packs/file-kinds.yaml
- service/packs/src/test/resources/whole-pack/expected-red.yaml
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/**
- service/packs/src/migration/**
- service/packs/build.gradle.kts
role: implementer
tags: []
task_type: implement
tracker_refs: []
---
# Work Package Prompt: WP06 — Re-pin and gates

## Do This First: Load Agent Profile

Load the agent profile `java-jenny` (`spec-kitty agent profile show java-jenny`) and follow its guidance.

## Objective

One commit on the orchestrator's open-packs sha: move `openPacksRef`; re-vendor `packs/qa` exactly (and `LOCAL_DELTA`, `PROVENANCE.md`); in `file-kinds.yaml` remove the seven #194 mission exclusions, exclude `*/missions/**/README.md`, add the `retired` section; narrow `other:missions` to what stays outside the model, with that reason; add `mission-type` to `REQUIRED_KINDS`; add `MissionTypeCorpusTest` (pinned counts per pack; no retired file in pinned or vendored packs; every `template_path` resolves); move `MarkdownCorpusTest`'s glob to the new layout and pin its count; delete the `migration` source set, task and harness and extend `theMigrationHarnessIsGone` to the build file. Prove: `./gradlew :packs:test :packs:openPacksRoundTrip :packs:wholePackRoundTrip :deployable:test` then `./gradlew verifyAll`.

## Context

- ADR-0044 (`docs/adr/0044-mission-types-consolidated-into-one-sanitized-shape.md`), [spec.md](../spec.md),
  [plan.md](../plan.md), [data-model.md](../data-model.md), research dispositions in [research.md](../research.md).
- Test first: write the failing acceptance and unit tests named above before the code. Zero new suppressions; methods
  at complexity 15 or below; Javadoc on every type and member (doclint).

## Definition of Done

- The named tests exist, failed first, and pass.
- `./gradlew verifyAll :packs:wholePackRoundTrip` green (and `check` stays green: no pinned path is affected before WP06).
- Owned files only; shared-file edits in one block.
