---
work_package_id: WP02
title: Kernel kind, schema and vocabulary
dependencies: []
requirement_refs:
- FR-001
- FR-004
planning_base_branch: issue-194-mission-type-consolidation
merge_target_branch: issue-194-mission-type-consolidation
branch_strategy: Planning artifacts for this mission were generated on issue-194-mission-type-consolidation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-194-mission-type-consolidation unless the human explicitly redirects the landing branch.
subtasks:
- T005
- T006
- T007
history: []
agent_profile: java-jenny
authoritative_surface: service/kernel/
create_intent:
- contracts/packs/schemas/mission-type.schema.json
execution_mode: code_change
owned_files:
- service/kernel/**
- contracts/packs/schemas/mission-type.schema.json
- contracts/packs/schemas/pack-manifest.schema.json
- contracts/packs/file-kinds.yaml
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/VocabularyEqualityTest.java
- service/packs/src/test/resources/schema-fixtures/mission-type/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---
# Work Package Prompt: WP02 — Kernel kind, schema and vocabulary

## Do This First: Load Agent Profile

Load the agent profile `java-jenny` (`spec-kitty agent profile show java-jenny`) and follow its guidance.

## Objective

Add `ArtifactKind.MISSION_TYPE` (facts in the enum's real shape: not activatable through `activated_kinds`, not org-requirable, not selection-overlayable) and make `NodeKind.MISSION_TYPE` artifact-backed; `fromPlural("mission_types")` keeps resolving. Write `contracts/packs/schemas/mission-type.schema.json` exactly per ADR-0044 §3 (closed at every level, S16 `minItems: 1`, the id, role and artifact-key grammars, `blocking: const true`, derived `prompt_path`/`guidelines_path` patterns, `template_path`), with accepted/refused schema fixtures. Add `mission_type` to `file-kinds.yaml` `artifact_kinds` and to the pack-manifest constituent `kind` enum; update `VocabularyEqualityTest` (including the inspector's hand list of artifact schema kinds), `ArtifactKindTest`, `NodeKindTest`.

## Context

- ADR-0044 (`docs/adr/0044-mission-types-consolidated-into-one-sanitized-shape.md`), [spec.md](../spec.md),
  [plan.md](../plan.md), [data-model.md](../data-model.md), research dispositions in [research.md](../research.md).
- Test first: write the failing acceptance and unit tests named above before the code. Zero new suppressions; methods
  at complexity 15 or below; Javadoc on every type and member (doclint).

## Definition of Done

- The named tests exist, failed first, and pass.
- `./gradlew :kernel:test :packs:test` green (and `check` stays green: no pinned path is affected before WP06).
- Owned files only; shared-file edits in one block.
