---
work_package_id: WP05
title: "Manifest storage object, YAML format and hash recipes"
dependencies:
- WP01
requirement_refs:
- FR-007
- FR-008
- NFR-001
- SC-004
planning_base_branch: feat/195-pack-level-round-trip
merge_target_branch: feat/195-pack-level-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/195-pack-level-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/195-pack-level-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T018
- T019
- T020
agent: claude
history:
- at: '2026-10-28T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackManifest
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackManifestDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackManifestYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackManifests.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackManifestsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackManifestParityTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackManifestDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackManifestYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackManifests.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackManifestsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackManifestParityTest.java
role: implementer
tags: []
task_type: implement
---
# Work Package Prompt: WP05 — Manifest storage object, YAML format and hash recipes

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

This WP builds the sanitized manifest's storage object, its YAML format, and the two hash recipes with a single
`recompute` (spec FR-007, FR-008, NFR-001). It touches no file-kind rule or store.

## Subtask T018 — DSO and YAML

- `PackManifestDso(schemaVersion, manifestHash, List<Constituent(kind, id, path, contentHash)>,
  List<PresetEntry(id, path, contentHash)>)`.
- `PackManifestYaml` reads and writes the DSO with `YamlFields` and `YamlDumpEmitter`.
  - Key order: `schema_version`, `manifest_hash`, `constituents`, `presets`.
  - Leave `presets` out when the list is empty.

## Subtask T019 — `PackManifests`

- `contentHash(bytes)`: `Payload.of(lf(bytes)).sha256()`, where `lf` turns CRLF, then lone CR, into LF.
- `manifestHash(dso)`: SHA-256 of the UTF-8 bytes of the RFC 8785 JCS of
  `{"schema_version", "constituents":[{kind,id,path,content_hash}], "presets":[...]}`.
  - Leave `presets` out when the list is empty.
  - The JCS is hand-written for strings, lists and maps only, with keys sorted by UTF-16 code units.
  - String escaping per RFC 8785: `\"`, `\\`, `\b`, `\f`, `\n`, `\r`, `\t`, and other C0 controls as
    `\u00xx` lowercase. Everything else is literal.
  - The name and javadoc say it is narrow.
- `recompute(dso, Function<String, Optional<byte[]>> bytesAt)` returns `Recomputed(PackManifestDso stamped,
  List<String> missing)`.
- `stale(read, recomputed)` lists the field paths whose values differ:
  - `constituents{<path>}.content_hash`
  - `presets{<path>}.content_hash`
  - `manifest_hash`
  - `missing: <path>`

## Subtask T020 — Tests

- `PackManifestsTest`:
  - LF, CRLF, lone-CR and mixed vectors (an empty file included);
  - JCS vectors from Python `json.dumps(obj, sort_keys=True, separators=(',', ':'), ensure_ascii=False)`, including
    `"`, `\`, a tab, U+001F, `é`, `😀`, and an empty `constituents`;
  - `recompute` stamps every field and reports a missing path;
  - `stale` is empty for a fresh manifest and names one stale constituent.
- `PackManifestParityTest`: read `../spec-kitty-open-packs/packs/built-in/pack-manifest.yaml` with the raw `Yaml.load`.
  For all 272 constituents and 2 presets, the pinned `content_hash` equals `PackManifests.contentHash` of the pinned
  file (skipped when the checkout is absent, as the other open-packs tests are).

## Definition of Done

- `./gradlew :packs:test` is green.
- NFR-001 holds: 274/274 pinned hashes and every vector pass.

## Branch Strategy

Planning base and merge target: `feat/195-pack-level-round-trip`. Lane from `lanes.json`. Implement with
`spec-kitty agent action implement WP05 --agent claude --mission pack-level-files-round-trip-01M60H1A`.

Tests come first (charter: test first, no fake green). Each refusal is paired with a clean control on the same fixture.
`contracts/charter/**` is never edited (C-001). Shared files are edited append-only, except the edits plan.md lists.
