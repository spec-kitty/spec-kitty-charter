---
work_package_id: WP02
title: 'Filesystem adapter: reader, Dump writers, repository'
dependencies:
- WP01
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-007
- FR-008
- NFR-004
- C-003
- C-004
planning_base_branch: feat/pack-roundtrip-io
merge_target_branch: feat/pack-roundtrip-io
branch_strategy: Planning artifacts for this mission were generated on feat/pack-roundtrip-io. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-roundtrip-io unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-roundtrip-io-01M5DEH8
base_commit: 2fbd926fb0fb93e79d7a56806677beed6e3209e7
created_at: '2026-10-20T20:57:41.688076+00:00'
subtasks:
- T007
- T008
- T009
- T010
- T011
- T012
phase: Phase 2 - Adapter
history:
- at: '2026-10-20T20:38:22Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/
create_intent:
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/TacticReader.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/YamlDumpEmitter.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/TacticWriter.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/PackDescriptorWriter.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/FilesystemPackElementRepository.java
execution_mode: code_change
model: ''
owned_files:
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/TacticReader.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/YamlDumpEmitter.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/TacticWriter.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/PackDescriptorWriter.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/FilesystemPackElementRepository.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/PackDescriptorReader.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/PackDescriptorReaderTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02 – Filesystem adapter: reader, Dump writers, repository

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the profile in the frontmatter before parsing the rest.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

---

## Objectives & Success Criteria

Implement `PackElementRepository` against the filesystem. Done when:

- A tactic-content reader parses `*.tactic.yaml` into `Tactic` (structured steps + references).
- A **new SnakeYAML `Dump` emitter** feeds a `TacticWriter` and a `PackDescriptorWriter` that serialize domain models from scratch.
- `PackDescriptorReader` now reads `active_charter`/`pack_drg` and passes them via the 9-arg `PackDescriptor` constructor.
- `FilesystemPackElementRepository` implements partial-tolerant `fetch` and atomic, pack-root-contained `write`-by-id with a failure-injection seam.
- `./gradlew :adapter-out-yaml:test :adapter-out-yaml:compileJava` green.

## Context & Constraints

- Depends on **WP01** (the domain types + port). Read [data-model.md](../data-model.md), [research.md](../research.md) D3 (adapter-owned id→path), D6 (Dump emitter + atomic write), D8 (descriptor fields).
- ⚠️ **Do NOT use `YamlDocument`/`ScalarWriter` to WRITE** (plan-review MAJOR-2): they only edit an already-parsed document and cannot construct one for a fresh directory or add/remove keys/list entries. The model→text capability pattern lives in `GraphShardWriter.java` (SnakeYAML `Dump`/`DumpSettings` over a `LinkedHashMap`). Reuse `PackSchemas`/`ParsedYaml`/`Yaml`/`YamlDocument` on the **read** side only.
- Fidelity is **semantic** (not byte): scalar style, quoting, and key order do not matter for equality. Omit absent optionals (do not emit `key: null`). Stringify scalars that coerce (`schema_version` float, nulls) consistently on read so string equality holds (D4 scalar-typing).
- Reuse the existing file-kinds classification for discovery — see `FileSystemPackInspector.java` (how it maps a `*.yaml` to a `kind` via `file-kinds.yaml` and validates via `PackSchemas.bundled().validate(kind, file, doc)`). Do **not** modify `FileSystemPackInspector`.

## Branch Strategy
- **Strategy**: (populated by finalize-tasks) · **Planning base**: feat/pack-roundtrip-io · **Merge target**: feat/pack-roundtrip-io

## Subtasks & Detailed Guidance

### Subtask T007 – `TacticReader`
- **Purpose**: Parse one `*.tactic.yaml` into a `Tactic` (mirror `PackDescriptorReader` structure).
- **Steps**: Parse via `ParsedYaml.parse(text, file)`; on parse failure or `PackSchemas.bundled().validate("tactic", file, doc)` violations return an `Unreadable`/violation result (so the repository can report it). On success map the mapping → `Tactic`: `id`, `schema_version`→`schemaVersion`, `name`, `steps[]`→`TacticStep` (title, description, examples, nested `references[]`→`TacticReference`), top-level `references[]`→`TacticReference`, `purpose`/`notes`→`Optional`, `failure_modes`/`applies_to_languages`→`List`. Stringify scalar values; treat explicit null and absent identically (D4). Preserve list order and duplicates.
- **Files**: `TacticReader.java`.
- **Notes**: Return a result type the repository can fold into `PackSnapshot.tactics` or `violations` — follow the `FileRead<T>` pattern already in the package.

### Subtask T008 – `YamlDumpEmitter` helper
- **Purpose**: The one new serialization primitive: domain value → YAML text.
- **Steps**: A small helper building a `LinkedHashMap<String,Object>` and dumping via `new Dump(DumpSettings.builder()...build()).dumpToString(root)`, modeled on `GraphShardWriter` (lines 26-55). Centralize `DumpSettings` (block style, no fancy flow) here so both writers share it. Provide helpers to add a scalar, a string-list, and a list-of-maps (for steps/references), **omitting** keys whose optional value is absent.
- **Files**: `YamlDumpEmitter.java`.
- **Notes**: This is the "new serialization surface" the plan calls out — keep its decisions (key omission for absent optionals, list rendering) in one place.

### Subtask T009 – `TacticWriter`
- **Purpose**: `Tactic` → YAML via `YamlDumpEmitter`.
- **Steps**: Emit `id`, `schema_version`, `name`, `steps` (each a map: `title`, and `description`/`examples`/`references` only when present), top-level `references`, and the optional scalars/lists only when present. Order and duplicates preserved exactly as the model holds them.
- **Files**: `TacticWriter.java`.

### Subtask T010 – `PackDescriptorWriter`
- **Purpose**: `PackDescriptor` → `pack.yaml` via `YamlDumpEmitter`, **including `active_charter`/`pack_drg`** (D8 — the whole point: a from-scratch writer must emit them or it deletes them from disk).
- **Steps**: Emit `pack_id`, `pack_version`, `name`, and `parent_pack`/`pack_level`/`requires_core`/`active_charter`/`pack_drg` only when present. `undeclaredKeys` holds only names (no values) → it is NOT emitted and is out of the equality contract (document this in a comment).
- **Files**: `PackDescriptorWriter.java`.

### Subtask T011 – Update `PackDescriptorReader`
- **Purpose**: Populate the two new descriptor fields.
- **Steps**: Read `active_charter`/`pack_drg` from the parsed mapping (stringify; absent→`Optional.empty()`) and pass them through the **9-arg** `PackDescriptor` constructor. Update `PackDescriptorReaderTest` to assert the two fields are read (present and absent cases).
- **Files**: `PackDescriptorReader.java`, `PackDescriptorReaderTest.java`.
- **Notes**: Because `active_charter`/`pack_drg` are now modelled, they must no longer appear in `undeclaredKeys` — verify the `declaredProperties` filter already excludes them (it keys off the schema, which declares them).

### Subtask T012 – `FilesystemPackElementRepository`
- **Purpose**: Implement the port.
- **Steps**:
  - `fetch(PackLocator)`: resolve the locator to the pack directory, read `pack.yaml` via `PackDescriptorReader`, discover tactic files via the file-kinds classification, read each via `TacticReader`; assemble `PackSnapshot(descriptor, validTactics, violations)`. **Partial-tolerant**: a parse/schema failure on one file becomes a `PackElementViolation`, never an exception that aborts the fetch.
  - `write(PackLocator, Tactic)`: resolve id→path by locating the existing tactic file whose id matches (reuse the fetch-side classification); serialize via `TacticWriter`; **atomic** write (write to a temp file in the pack dir, then `Files.move(tmp, target, ATOMIC_MOVE)`); refuse a resolved target that escapes the pack root (normalize + `startsWith` check — see `AssetFacts.containedInAssetsRoot` for the existing pattern). `write(PackLocator, PackDescriptor)` writes `pack.yaml` the same way.
  - **Failure-injection seam** (for T018): factor the "temp-write then rename" so a test can inject a failure between the two steps (e.g. a protected package-private hook or an injectable `Rename` strategy). Keep it minimal and documented.
- **Files**: `FilesystemPackElementRepository.java`.
- **Notes**: Keep `Path`/IO entirely inside this class and the writers/reader — never on the domain types.

## Test Strategy

Update `PackDescriptorReaderTest` (T011) for the two new fields. The full round-trip, write-safety, and layering battery is **WP03** (authored against the port). Your gate:

```bash
./gradlew :adapter-out-yaml:compileJava :adapter-out-yaml:compileTestJava :adapter-out-yaml:test
```

Do not duplicate WP03's round-trip assertions here (test economy). Compiler diagnostics must pass alongside the runner.

## Risks & Mitigations

- **Reaching for `YamlDocument` to write** → it cannot construct documents; use `YamlDumpEmitter` (SnakeYAML Dump). Re-read D6 if tempted.
- **Non-atomic write** → always temp-then-`ATOMIC_MOVE`; expose the injection seam or T018 cannot prove atomicity.
- **Scalar coercion drift** → stringify on read; a `schema_version: 1.0` read as a float then written would break string equality.
- **Silent descriptor loss** → `PackDescriptorWriter` MUST emit `active_charter`/`pack_drg`.

## Review Guidance

- Confirm writers use SnakeYAML `Dump`, not `YamlDocument`/`ScalarWriter`.
- Confirm `fetch` is partial-tolerant (one bad file → violation, valid elements still returned).
- Confirm write is atomic (`ATOMIC_MOVE`) and refuses pack-root escape, with a usable failure-injection seam.
- Confirm no `Path`/IO type leaks onto a domain model.

## Progress & Status
- `spec-kitty agent tasks add-history WP02 --note "..."` · `spec-kitty agent tasks move-task WP02 --to <status>`
