---
work_package_id: WP06
title: Storage proofs
dependencies:
- WP05
requirement_refs:
- FR-011
- FR-012
- NFR-002
planning_base_branch: feat/asset-template-round-trip
merge_target_branch: feat/asset-template-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/asset-template-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/asset-template-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T025
- T026
- T027
- T028
phase: Phase 3 - Proofs, gate and records
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
role: implementer
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackSourcesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/AssetPackRegistrationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RelationshipRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/BinaryPayloadRevisionTest.java
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/application/AssetPackRegistrationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/BinaryPayloadRevisionTest.java
tags: []
task_type: implement
---
# Work Package Prompt: WP06 — Storage proofs

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Prove through production entry points that, with assets present, the fingerprint stays exact for packs without them,
registrations keep asset files, `asset:`/`template:` URNs are valid relationship endpoints, and revisions and
If-Match work over a pack with binary payloads. Tests only; if a test finds a defect in WP05 code, record it in the
handoff and fix it on WP05's files with a one-line out-of-map rationale — never weaken the test. Plan "Acceptance
checks" 6 and 11; post-plan P3, P4, P12.

## Branch Strategy

Planning branch and final merge target: `feat/asset-template-round-trip`. Execution worktrees are allocated per
computed lane from `lanes.json`; do not pick a base branch by hand. Implement with:

```bash
spec-kitty agent action implement WP06 --agent claude --mission asset-template-round-trip-01M5YY7G
```

## Validation discipline

Run the targeted tests for the files you touch and the owning module's fast tier (`./gradlew :packs:test
:packs:check`; `:deployable:test` when named). Never run a whole-repo heavy sweep (`verifyAll` is CI's,
directive NO_FULL_HEAVY_SUITES_IN_MISSION); record the exact commands and pass/fail counts in the review handoff.
Java 25 via `JAVA_HOME=/usr/lib/jvm/temurin-25`; Maven Central HTTP 429 → wait and retry.

## Subtasks

### T025 — Literal fingerprint pin

- For fixture packs without assets or templates (find them via `PackFixtures` and test resources), pin the
  `PackFileSet.digested(dir)` list and the fingerprint value from `FileSystemPackSources.fingerprint()` as literals
  captured on `main` at 90de8f9 (capture with the code before this mission, e.g. a scratch worktree of `main`;
  record how in a comment). This branch must reproduce both exactly (NFR-002).
- Positive controls on the same fixture, through `FileSystemPackSources.fingerprint()`: adding a payload under
  `assets/` changes the fingerprint; flipping one of its bytes changes it again; adding a top-level template changes
  it; flipping one template byte changes it again (FR-011).

### T026 — Registration keeps asset files

- `AssetPackRegistrationTest`: through a real registration service (e.g. `DirectiveRegistrationService`, wired as its
  test wires it, over the real `DomainRepositoryService` and commit store on a temp pack) register a directive into a
  pack holding a sidecar, its payload, a sidecar-less payload and a template. Assert success (no 422); the reloaded
  pack holds both asset files, the asset and the template; the payload bytes on disk are unchanged (SHA-256); the
  revision advanced. Negative control on the same fixture: a hand-built `Pack` lacking the asset file breaches.

### T027 — Relationship endpoints through the real store

- `RelationshipRegistrationServiceTest`: add cases wired over the real `DomainRepositoryService` and a temp pack
  holding a sidecar, its payload and a template (not the in-memory `FakeStore`): a relationship to `asset:<id>` and one
  to `template:<path>` register; an `asset:` URN the pack does not hold is refused as today (same fixture). Check the
  mutability policy accepts the new kinds (R6).

### T028 — Revision and If-Match over binary payloads

- `BinaryPayloadRevisionTest`: a copy of the repository's `packs/internal` (fonts and PNG; or the open-packs
  `internal` if the repository pack has no binaries — check) in a temp config root: the current revision computes
  without `PACK_UNREADABLE`; a commit (registering a directive) succeeds; If-Match with the reported revision passes
  and with the pre-edit revision after a one-byte payload edit fails.

## Definition of Done

- `./gradlew :packs:test :packs:check` and `:deployable:test` green.
- Every refusal/absence assertion has a same-fixture positive control.

## Risks / reviewer guidance

- The literal pin must come from `main`; the fingerprint must go through `FileSystemPackSources.fingerprint()`.
