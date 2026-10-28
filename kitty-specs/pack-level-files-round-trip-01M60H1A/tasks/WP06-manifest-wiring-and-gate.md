---
work_package_id: WP06
title: "Manifest owned by WholePackStore, regenerated on write, and gated"
dependencies:
- WP03
- WP04
- WP05
requirement_refs:
- FR-009
- FR-010
- FR-013
- FR-014
- FR-015
- FR-018
- NFR-004
- SC-001
- SC-003
planning_base_branch: feat/195-pack-level-round-trip
merge_target_branch: feat/195-pack-level-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/195-pack-level-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/195-pack-level-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T021
- T022
- T023
- T024
- T025
agent: claude
history:
- at: '2026-10-28T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackManifestRoundTripTest
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackManifestRoundTripTest.java
- service/packs/src/test/resources/pack-level/manifest-pack/pack.yaml
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackManifestRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MeaningComparator.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MeaningComparatorTest.java
- service/packs/src/test/resources/pack-level/manifest-pack/**
role: implementer
tags: []
task_type: implement
---
# Work Package Prompt: WP06 — Manifest owned by WholePackStore, regenerated on write, and gated

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Wire the manifest into the whole-pack seam and the gate (spec FR-009, FR-010, FR-013–FR-015, FR-018). `WholePackStore`
is the manifest's only owner (research P3). This WP edits files WP04 owns: `file-kinds.yaml`, `WholePackStore`,
`PackFileSet`, `FileSystemPackInspector`, `DomainRoundTrip`, `WholePackRoundTripTest`, `WholePackOpenPacksRoundTripTest`,
`expected-red.yaml`, `known-failures.yaml` and `FileSystemPackInspectorTest`. These are recorded out-of-map edits,
made after WP04 has merged into the lane (the dependency is declared).

## Subtask T021 — Rule and seam

- `file-kinds.yaml`: done in WP01 (the rule and the retired exclusion).
- `SerializationStrategy` gains `readManifest` and `writeManifest`, built on `readValidated`.
- `WholePackStore`:
  - `LoadedPack` gains `Optional<PackManifestDso> manifest`; a 4-argument constructor defaults it to empty.
  - Load reads `pack-manifest.yaml` when present. An unreadable manifest is a violation on the aggregate (add it to
    the builder before `build`), with the store's exact text, and `unmodelled` excludes the file.
  - Write stages everything else, then calls `PackManifests.recompute(dso, path -> bytes in staging)`.
    - Any missing path → `CharterRefusal(PACK_UNREADABLE, "pack-manifest.yaml of <pack> lists <path>, which the
      write does not hold")`, and no target is left behind.
    - Otherwise the stamped manifest is written last.
- `FilesystemPackStore` and `DomainRepositoryService` do not parse the manifest.

## Subtask T022 — Fingerprint and validate

- `PackFileSet.digested` appends `pack-manifest.yaml` after the presets, in the same ordered tail.
- The fingerprint tests: a one-byte manifest edit changes the fingerprint.
- `FileSystemPackInspectorTest`: the built-in validate test pins exactly 3 findings.

## Subtask T023 — Gate

- `DomainRoundTrip`:
  - `MODELLED` gains `pack-manifest`.
  - Read the source manifest through the strategy; if it is unreadable, `Refused` with the exact violation text.
  - Otherwise recompute over the pairing `(kind, id)` → the written artefact file, and preset path → the written
    preset. Missing → `Refused("pack-manifest.yaml lists <path>, which the round trip did not write")`. Write the
    stamped manifest.
- `MeaningComparator`: a declared `pack-manifest` rule that skips `manifest_hash` and every `content_hash`, documented
  as ADR-0043's derived fields.
- `WholePackRoundTripTest`:
  - for `pack-manifest`, the red detail also includes `PackManifests.stale` on the source side (source bytes) and on
    the written side (paired bytes);
  - `REQUIRED_KINDS` and `bound` gain `pack-manifest`.
- `expected-red.yaml`: delete `other:pack-manifest.yaml`.
- `known-failures.yaml`: add `pack-manifest.yaml`, keyed `…#TBD-manifest-shape`, with the exact `at`. The reason
  notes the follow-on: until #194 binds procedures, the post-OP-2 outcome is "lists procedures/…, which the round trip
  did not write".

## Subtask T024 — Proof on fixtures and the corpus

- `src/test/resources/pack-level/manifest-pack/`: a nested layout with commented artifacts, two presets and a
  sanitized manifest whose hashes are fresh for the source bytes.
- `PackManifestRoundTripTest`, through `WholePackStore`:
  - every written `content_hash` differs from its source and verifies, and `manifest_hash` verifies;
  - constituents and presets are equal by meaning;
  - a listed missing path refuses the write and no target exists afterwards;
  - a stale source `content_hash` is reported by `stale`, beside a fresh paired control;
  - a writer that keeps stale hashes goes red in the gate driver (a control through `beforeSave` or a decorator).
- `WholePackOpenPacksRoundTripTest`:
  - pinned refusals go from 8 to 9;
  - the corrected built-in copy also writes an OP-2 manifest (a test helper over the modelled constituents only,
    until #194 binds procedures), and loads, writes and reloads with 0 violations;
  - `stale` is empty on both sides.

## Subtask T025 — Comparator test and timing

- `MeaningComparatorTest`: the manifest rule skips hashes but catches a changed `path` or `id`, a dropped constituent
  and a changed `schema_version`.
- NFR-004: time loading the built-in pack before and after; note it in the WP review (no assertion on timing).

## Definition of Done

- The gate is green with exactly 3 known failures at pack level.
- `./gradlew :packs:test :packs:wholePackRoundTrip :packs:openPacksRoundTrip` is green.
- Acceptance checks 1–3, 6, 7, 9 and 11 of plan.md hold.

## Branch Strategy

Planning base and merge target: `feat/195-pack-level-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP06 --agent claude --mission pack-level-files-round-trip-01M60H1A`.

Tests come first (charter: test first, no fake green). Each refusal is paired with a clean control on the same fixture.
`contracts/charter/**` is never edited (C-001). Shared files are edited append-only, except the edits plan.md lists.
