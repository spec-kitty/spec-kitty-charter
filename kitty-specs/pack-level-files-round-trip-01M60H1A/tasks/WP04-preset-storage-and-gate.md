---
work_package_id: WP04
title: "Presets read, written, fingerprinted, validated and gated"
dependencies:
- WP01
- WP02
requirement_refs:
- FR-002
- FR-003
- FR-004
- FR-013
- FR-014
- FR-018
- NFR-002
- C-002
- C-005
- SC-002
planning_base_branch: feat/195-pack-level-round-trip
merge_target_branch: feat/195-pack-level-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/195-pack-level-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/195-pack-level-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T011
- T012
- T013
- T014
- T015
- T016
- T017
agent: claude
history:
- at: '2026-10-28T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/Preset
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PresetDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PresetYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PresetFiles.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PresetRoundTripTest.java
- service/packs/src/test/resources/pack-level/presets-pack/pack.yaml
execution_mode: code_change
owned_files:
- contracts/packs/file-kinds.yaml
- contracts/packs/README.md
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PresetDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PresetYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PresetFiles.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/SerializationStrategy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlSerializationStrategy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PresetRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileKindTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspectorTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRoundTrip.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectation.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackExpectationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackOpenPacksRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetExactnessTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GeneratedPackTree.java
- service/packs/src/test/resources/whole-pack/expected-red.yaml
- service/packs/src/test/resources/whole-pack/known-failures.yaml
- service/packs/src/test/resources/pack-level/presets-pack/**
role: implementer
tags: []
task_type: implement
---
# Work Package Prompt: WP04 — Presets read, written, fingerprinted, validated and gated

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Presets are read into the `Pack`, written back, fingerprinted and checked in validate mode by the same rules, and the
gate models the `preset` kind (spec FR-002–FR-004, FR-013, FR-014, FR-018).

## Subtask T011 — The rule (done in WP01, see its scope note; here only the README wording if needed)

`contracts/packs/file-kinds.yaml`:

- Add the `files` rule `*/presets/*.yaml → preset`.
- Retire the exclusion `**/presets/*.yaml`. This is a non-append edit, justified in plan.md.
- Update the header and `contracts/packs/README.md`.
- In `PackFileKindTest`, sample `built-in/presets/minimal.yaml` as mapped to `preset`, and
  `built-in/missions/x/presets/a.yaml` as unclassified.

## Subtask T012 — `PresetDso`, `PresetYaml`, `PresetFiles`

- `PresetYaml` implements `YamlFormat`-like `read(Map)` / `write(dso)` with `YamlFields` and `YamlDumpEmitter`.
  - Key order: `schema_version`, `id`, `description`, `activated_kinds`, then the lists in schema order.
  - Write `must_have: true` only when it is set.
- Extract a private `readValidated(schema, text, file, reader)` in `YamlSerializationStrategy` (reducer D3r).
  `read(kind, …)` and the new `readPreset` both use it.
- `SerializationStrategy` gains `readPreset` and `writePreset`.
- `PresetFiles.stemMismatch(file, id)` returns `Optional<String>` with the reason
  `preset id '<id>' is not its file stem '<stem>'`.

## Subtask T013 — Store and assembly

- `PackFileSet.Listing.presets`.
- `FilesystemPackStore.PackRead.presets` (stored DSO with file):
  - an unreadable preset is a violation on its file;
  - a stem mismatch is a violation on its file.
- `DomainRepositoryService.assembly` builds `Preset`; a domain breach becomes a violation.
- `writePreset(pack, preset)` writes in place at `presets/<id>.yaml`.
- `WholePackStore.write` writes the presets, and `unmodelled` excludes them.
- No `presets/` directory for a pack without presets.

## Subtask T014 — Fingerprint

- `PackFileSet.digested` appends the presets in path order after the templates, in one ordered "pack-level tail" step
  that the manifest joins later.
- `PackFileSetExactnessTest` and `GeneratedPackTree`: a preset decoy becomes loaded, and a one-byte edit of a preset
  changes the fingerprint.
- The literal digest lists of packs without presets are unchanged (NFR-002).

## Subtask T015 — Validate mode (D9, research P1/P2)

- `FileSystemPackInspector`:
  - `ArtifactKinds` leaves out `preset` explicitly, with a comment;
  - a preset's stem mismatch, through `PresetFiles`, becomes an unreadable-file violation, so `PackPolicy` gives
    `ARTIFACT_SCHEMA_ERROR`.
- `FileSystemPackInspectorTest`:
  - the built-in validate test now pins exactly the 2 preset findings (the manifest follows in WP06);
  - a sanitized preset with id ≠ stem is refused in both validate and load (one fixture).

## Subtask T016 — Gate

- `DomainRoundTrip`:
  - `MODELLED` gains `preset`;
  - presets are written in place and paired by path;
  - a refused preset is `Refused` with the store's exact violation reason: no kind prefix and no unknown-field
    suffix (research R5).
- `WholePackRoundTripTest`: `REQUIRED_KINDS` and `bound` gain `preset`; `withPayloads` keeps the presets.
- `expected-red.yaml`: delete `other:presets`.
- `known-failures.yaml`: add `presets/default.yaml` and `presets/minimal.yaml`, keyed
  `spec-kitty/spec-kitty-open-packs#TBD-preset-shape`. Each `at` is the exact refusal text, which is also the seam
  test's text.
- `WholePackExpectation`: admit exactly that placeholder (and the manifest one, used by WP06), with a test that any
  other `#TBD-` key is refused.

## Subtask T017 — Fixture pack and seam test

- `src/test/resources/pack-level/presets-pack/` holds a `pack.yaml` and three presets:
  - a governing one with every list;
  - a convenience one;
  - a commented one.
- `PresetRoundTripTest` (through `WholePackStore`):
  - presets equal by meaning, `must_have` included;
  - a writer that drops `must_have` goes red at the item;
  - each refusal paired with a clean control: unknown key, `must_have: false`, empty list, id ≠ stem, duplicate
    id, `null` document;
  - after a registration through a real registration service, the presets are byte-identical.
- `WholePackOpenPacksRoundTripTest`:
  - pinned refusals go from 6 to 8;
  - the corrected built-in copy also rewrites the two presets in OP-1 shape (a test helper) and loads with 0
    violations.

## Definition of Done

- The gate is green, with exactly 2 new known failures at `preset`.
- `./gradlew :packs:test :packs:wholePackRoundTrip :packs:openPacksRoundTrip` is green.
- Acceptance checks 1 (presets part), 2, 4, 9 (presets), 10, 11 and 12 of plan.md hold.

## Branch Strategy

Planning base and merge target: `feat/195-pack-level-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP04 --agent claude --mission pack-level-files-round-trip-01M60H1A`.

Tests come first (charter: test first, no fake green). Each refusal is paired with a clean control on the same fixture.
`contracts/charter/**` is never edited (C-001). Shared files are edited append-only, except the edits plan.md lists.
