---
work_package_id: WP05
title: Re-point the composition, file and digest tests
dependencies:
- WP01
requirement_refs:
- FR-004
- C-001
- C-003
planning_base_branch: claude/adr-open-packs-456-458-adf2fo
merge_target_branch: claude/adr-open-packs-456-458-adf2fo
branch_strategy: Planning artifacts for this mission were generated on claude/adr-open-packs-456-458-adf2fo. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into claude/adr-open-packs-456-458-adf2fo unless the human explicitly redirects the landing branch.
subtasks:
- T017
- T018
- T019
- T020
history:
- at: '2026-11-11T05:30:00Z'
  actor: claude
  action: created by /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/
create_intent: []
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
role: implementer
tags: []
tracker_refs: []
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

`PackDigestBaselineTest` proves a pack with no `enhancements/` keeps the fingerprint it had before the step-enhancement binding: pin the digests of the example packs that hold no `enhancements/` (computed with `digestOfPack`), with a comment giving the command to recompute after an intended example change. Known-failure and count assertions in `OpenPacksComposeTest` become rules over the examples (no known failures; the served packs equal the three examples). `PackManifestParityTest`: the example built-in manifest's hashes reproduce.

## Subtask T020 — File-kind, inspector, level-schema and governance tests

`PackFileKindTest`, `FileSystemPackInspectorTest` (validate mode over the built-in example: zero findings), `PackLevelSchemasTest`, `ProfileGovernanceCorpusTest` (example profiles' governance walk).

## Definition of Done

- Each listed class reads `ExamplePacks` only; per-class old/new assertion table with one failing control each.
- `PackDigestBaselineTest` pins the example packs' digests with the regeneration command beside the pins.
- The touched modules' tests pass; `spotlessCheck` and Checkstyle clean on touched sources.

## Risks

- A rewritten assertion that no longer proves its rule (a vacuous green). Reviewers check each moved test has a failing control.
- Example packs drifting towards what is easy to test; WP03's coverage check is the guard.

## Reviewer Guidance

- Compare each moved test's assertions with the original: same rule, now over owned examples.
- Reject any expectation-list entry or exclusion added to make a test pass.
