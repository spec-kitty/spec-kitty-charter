---
work_package_id: WP04
title: Storage proofs
dependencies:
- WP03
requirement_refs:
- FR-011
- FR-012
- NFR-002
- C-001
planning_base_branch: feat/asset-template-round-trip
merge_target_branch: feat/asset-template-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/asset-template-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/asset-template-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T018
- T019
- T020
- T021
- T022
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
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GeneratedPackTree.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetExactnessTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackSourcesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackCommitStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/AssetPackRegistrationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RelationshipRegistrationServiceTest.java
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/application/AssetPackRegistrationTest.java
tags: []
task_type: implement
---

# Work Package Prompt: WP04 — Storage proofs

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Prove, through production entry points, that WP03's storage changes keep the fingerprint exact, keep registrations
and relationship registrations working on packs that hold assets, and compute revisions over binary payloads. Tests
only; if a test finds a defect in WP03 code, record it in the review handoff and fix it in a follow-up on WP03's files
only with a one-line rationale (out-of-map edit) — do not paper over it in the test. Plan "Acceptance checks" 5, 6,
11 and post-plan P3, P4, P12 bind this WP.

## Branch Strategy

Planning branch and final merge target: `feat/asset-template-round-trip`. Lanes from `lanes.json`. Implement with:

```bash
spec-kitty agent action implement WP04 --agent claude --mission asset-template-round-trip-01M5YY7G
```

## Subtasks

### T018 — Generated-tree oracle rows

- Read `GeneratedPackTree` (verdicts, kinds, `Shape` enum, `DIRECTORIES`, `DECOY_ASSET`) and
  `PackFileSetExactnessTest` (the iff-oracle: a file is digested iff its shape says so).
- `DECOY_ASSET` (`x.asset.yaml` under random dirs) is no longer a decoy: a sidecar now loads and is digested. Rework
  it so the oracle stays truthful: a sidecar is digested whether or not its payload exists (it is a bound kind's
  file); asset files are digested only under `<pack>/assets/`.
- Add shapes: sidecar + payload under `assets/` (both digested), sidecar-less payload under `assets/` (digested),
  nested payload under `assets/a/b/` (digested), `README.md` under `assets/` (not digested), top-level template
  `templates/x/y.md` (digested), mission template `missions/m/templates/t.md` (decoy, not digested), a file named
  like a payload outside `assets/` (decoy).
- Drive the oracle through `FileSystemPackSources.fingerprint()` (the production path), not only `PackFileSet`.

### T019 — Literal fingerprint pin

- For the existing fixture packs that hold no assets or templates (find them via `PackFixtures` and the test
  resources), pin the `PackFileSet.digested(dir)` list **and** the fingerprint value as literals captured on `main`
  at 90de8f9 (run the current code on `main` in a scratch checkout or from the parent commit of this lane to capture
  them; record how in a comment). Assert the code on this branch reproduces both exactly (NFR-002).
- A positive control on the same fixture: adding one payload file under `assets/` changes the fingerprint; flipping
  one of its bytes changes it again (FR-011).

### T020 — Registration keeps asset files

- `AssetPackRegistrationTest`: through a real registration service (e.g. `DirectiveRegistrationService`, wired as its
  existing test wires it, with the real `DomainRepositoryService`/commit store over a temp pack), register a directive
  into a pack that holds a sidecar, its payload and a sidecar-less payload. Assert: the registration succeeds (no
  422), the reloaded pack still holds both asset files and the asset, the payload bytes on disk are unchanged
  (SHA-256), and the revision advanced. Negative control on the same fixture: a hand-built `Pack` lacking the asset
  file breaches (shows the invariant would have fired).

### T021 — Relationship endpoints

- In `RelationshipRegistrationServiceTest` append: registering a relationship whose endpoint is `asset:<id>` (and one
  with `template:<path>`) of a held artifact succeeds; an `asset:` URN that the pack does not hold is refused as
  today. Same fixture for both (non-vacuity). This is operator ruling R6; ADR-0040 records it (WP06).

### T022 — ETag and commit over binaries

- In `PackCommitStoreTest` (and wherever `CommitStorePackWriteStore.currentRevision` is tested): a pack holding a PNG
  and an OTF-like payload (NUL bytes, 0x89 lead) commits and reports a revision without `PACK_UNREADABLE`; If-Match
  with the reported revision passes; with a revision computed before a one-byte payload edit it fails.

## Definition of Done

- `./gradlew :packs:test` and `:deployable:test` green; `:packs:check` green.
- Every refusal/absence assertion has a same-fixture positive control (tactic acceptance-criteria-non-vacuity).

## Risks / reviewer guidance

- The literal pin must be captured from `main`, not from this branch (otherwise it proves nothing).
- The oracle must go through `FileSystemPackSources.fingerprint()`.
