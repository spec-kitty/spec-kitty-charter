---
work_package_id: WP05
title: Re-point the composition, file and digest tests
dependencies:
- WP01
subtasks:
- T017
- T018
- T019
- T020
requirement_refs:
- FR-004
planning_base_branch: claude/adr-open-packs-456-458-adf2fo
merge_target_branch: claude/adr-open-packs-456-458-adf2fo
branch_strategy: single_branch
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/OpenPacksComposeTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompanionFilesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompanionDigestTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/BinaryPayloadRevisionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDigestBaselineTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackManifestParityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileKindTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspectorTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackLevelSchemasTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ProfileGovernanceCorpusTest.java
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/
create_intent: []
agent_profile: java-jenny
role: implementer
agent: claude
history:
- at: '2026-11-11T05:30:00Z'
  actor: claude
  action: created by /spec-kitty.tasks
---

# WP05 — Re-point the composition, file and digest tests

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with `/spk-charter-profile-load java-jenny` before reading further, and load the action context with `spec-kitty charter context --action implement --json`.

## Objective

Move the composition, companion, digest, manifest, file-kind, inspector and governance corpus tests onto the example packs.

## Context

- ADR-0070 (`docs/adr/0070-charter-tests-own-example-packs-not-open-packs-content.md`), the plan doc (`docs/plans/charter-example-packs.md`), and this mission's `plan.md`, `research.md`, `data-model.md`.
- Canonical shapes only (charter companion, 2026-11-10): `role`/`alt-roles`, named principles, `fallback_profile`. Invalid shapes belong to the `planted/` error-handling packs.
- Red first: each moved assertion must fail against an incomplete example before it passes.

## Branch Strategy

Planning branch and final merge target: `claude/adr-open-packs-456-458-adf2fo` (PR #474). Topology `single_branch`: no lane worktree; the work happens in the repository root checkout, sequentially, as `lanes.json` records.

Implement with:

```bash
spec-kitty agent action implement WP05 --agent claude --mission charter-example-packs-01M74GCF
```

## Subtask T017 — Composition test

`OpenPacksComposeTest` → `ExamplePacksComposeTest`: built-in, org-base and project compose through the offering read; the mutated-copy refusals (bare reference → `PACK_REFERENCE_UNQUALIFIED`, unknown dependency → `PACK_DEPENDENCY_UNKNOWN`, removed `depends_on` → `PACK_REFERENCE_PACK_MISSING`) run on copies of the example packs.

## Subtask T018 — Companion and binary-payload tests

`CompanionFilesTest`, `CompanionDigestTest`, `BinaryPayloadRevisionTest`: use the built-in example's guide companion, skill body and binary asset.

## Subtask T019 — Digest and manifest tests

`PackDigestBaselineTest`: record the digests of the three example packs (computed once, pinned with a comment saying how to recompute). `PackManifestParityTest`: the example built-in manifest's hashes reproduce.

## Subtask T020 — File-kind, inspector, level-schema and governance tests

`PackFileKindTest`, `FileSystemPackInspectorTest` (validate mode over the built-in example: zero findings), `PackLevelSchemasTest`, `ProfileGovernanceCorpusTest` (example profiles' governance walk).

## Definition of Done

- Every subtask's validation holds; the touched modules' tests pass (`./gradlew :packs:test :deployable:test` as relevant).
- `spotlessCheck` and Checkstyle clean on touched sources.
- No test reads open-packs content (except the WP07 drift job).

## Risks

- A rewritten assertion that no longer proves its rule (a vacuous green). Reviewers check each moved test has a failing control.
- Example packs drifting towards what is easy to test; WP03's coverage check is the guard.

## Reviewer Guidance

- Compare each moved test's assertions with the original: same rule, now over owned examples.
- Reject any expectation-list entry or exclusion added to make a test pass.
