---
work_package_id: WP09
title: Move the packs tests off the synced packs/qa and packs/internal trees
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
- T032
- T033
- T034
history:
- at: '2026-11-11T05:30:00Z'
  actor: claude
  action: created by /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/
create_intent: []
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ArtifactRemovalServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RelationshipRemovalServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ArtifactControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PackRemovalControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/HypermediaTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PolicyRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StorageRootLocationsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GlossaryPackYamlTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackSourcesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/RetiredFilesTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP09 — Move the packs tests off the synced packs/qa and packs/internal trees

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with `/spk-charter-profile-load java-jenny` before reading further, and load the action context with `spec-kitty charter context --action implement --json`.

## Objective

Move the packs tests that read the synced `packs/qa` and `packs/internal` trees onto the example packs.

## Context

- ADR-0070 (`docs/adr/0070-charter-tests-own-example-packs-not-open-packs-content.md`), the plan doc (`docs/plans/charter-example-packs.md`), and this mission's `plan.md`, `research.md`, `data-model.md`.
- Canonical shapes only (charter companion, 2026-11-10): `role`/`alt-roles`, named principles, `fallback_profile`. Invalid shapes belong to the `planted/` error-handling packs.
- Red first: each moved assertion must fail against an incomplete example before it passes.

## Branch Strategy

Planning branch and final merge target: `claude/adr-open-packs-456-458-adf2fo` (PR #474). Topology `single_branch`: no lane worktree; the work happens in the repository root checkout, sequentially, as `lanes.json` records.

Implement with:

```bash
spec-kitty agent action implement WP09 --agent claude --mission charter-example-packs-01M74GCF
```

## Subtask T032 — Inventory the synced-tree reads

 For each class, list what it reads from `packs/qa` / `packs/internal` (files, ids, counts) and the rule it proves.

## Subtask T033 — Application and API tests

 `ArtifactRemovalServiceTest`, `RelationshipRemovalServiceTest`, `ArtifactControllerTest`, `PackRemovalControllerTest`, `HypermediaTest`: configure the store over temp copies of the example packs (org-base for org-level writes); same assertions.

## Subtask T034 — Storage tests

 `PackFileSetTest`, `PolicyRoundTripTest`, `StorageRootLocationsTest`, `GlossaryPackYamlTest`, `FileSystemPackSourcesTest`, `RetiredFilesTest`: read the example packs; an org-specific assertion uses org-base.

## Definition of Done

- Each listed class reads `ExamplePacks` (or a temp copy) and no synced tree; per-class old/new assertion table.
- The touched modules' tests pass; `spotlessCheck` and Checkstyle clean on touched sources.

## Risks

- A rewritten assertion that no longer proves its rule (a vacuous green). Reviewers check each moved test has a failing control.
- Example packs drifting towards what is easy to test; WP03's coverage check is the guard.

## Reviewer Guidance

- Compare each moved test's assertions with the original: same rule, now over owned examples.
- Reject any expectation-list entry or exclusion added to make a test pass.
