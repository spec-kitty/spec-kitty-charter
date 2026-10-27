---
work_package_id: WP03
title: Storage read and write path for assets and templates
dependencies:
- WP01
- WP02
requirement_refs:
- FR-002
- FR-003
- FR-004
- FR-005
- FR-006
- FR-007
- FR-011
- NFR-002
- NFR-004
- C-001
- C-003
- C-005
planning_base_branch: feat/asset-template-round-trip
merge_target_branch: feat/asset-template-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/asset-template-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/asset-template-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T011
- T012
- T013
- T014
- T015
- T016
- T017
phase: Phase 2 - Storage
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
role: implementer
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AssetDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AssetYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AssetBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/TemplateBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBindings.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlFormats.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackLayout.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackCommitStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CommitStorePackWriteStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRevision.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRevisionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/KindBindingsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/AssetStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/TemplateStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServiceTest.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AssetDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AssetYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AssetBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/TemplateBinding.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/AssetStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/TemplateStoreTest.java
tags: []
task_type: implement
---

# Work Package Prompt: WP03 — Storage read and write path for assets and templates

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Widen the ADR-0037 storage seam to the `bytes` content form (ADR-0037 decision 4 hands this to #193): bind asset
sidecars as a YAML kind; read asset files and templates as bytes into the `Pack` (WP02 types); refuse a sidecar whose
payload is missing (R3); write every byte back through the per-pack store and the whole-pack store; and make the
source fingerprint and the pack revision cover the new files as bytes, with packs that hold none keeping their exact
digest. Everything here lands together: if sidecars loaded without their asset files, the WP02 invariant would breach
on every pack holding assets (`packs/qa`, `packs/internal`, served by the deployable).

Read first: `plan.md` (D2–D6 and "Acceptance checks" 10, 11), `data-model.md` (Storage, State and refusals),
`research.md` D3, D5, D6, D9 and post-plan P3, P4, P7, P16, P17, P19; ADR-0037 (`docs/adr/0037-*.md`).

## Branch Strategy

Planning branch and final merge target: `feat/asset-template-round-trip`. Lanes from `lanes.json`. Implement with:

```bash
spec-kitty agent action implement WP03 --agent claude --mission asset-template-round-trip-01M5YY7G
```

## Context (code you will change)

- `KindBindings.ALL` (append-only list, load and digest order) and `YamlFormats` (one `YamlFormat` per kind).
  Siblings #190/#191/#192 append here too: append one line each, at the end.
- `PackFileSet`: `files(packDir, kind)`, `digested(...)`, a `YamlCollector` walk of `*.yaml` regular files sorted by
  `String`, classification through `PackFileKind.bundled().classify(<packName>/<file>)`.
- `FilesystemPackStore`: `read(id)` → `PackRead(descriptor, elements by kind, violations)`; `readFile` decodes UTF-8;
  `write(id, binding, dso)` upserts `<id>.<token>.yaml`; `writeAtomically` (temp file + rename, containment checks).
- `DomainRepositoryService`: `assemble`, static `collect(pack, binding, read, builder, places)`, static
  `writeArtifact(store, key, artifact)` used by `PackCommitStore`.
- `WholePackStore`: `load` (its own assembly loop + `unmodelled`), `write` (`put` uses `Files.writeString`; loops
  `pack.artifacts()` → `layout.pathOf` + `text()`, both through `KindBindings` only).
- `PackLayout.pathOf` throws for a kind outside `KindBindings`.
- `PackCommitStore.revisionOf` and `CommitStorePackWriteStore.currentRevision` read every digested file with
  `Files.readString(UTF_8)` → `PackRevision.compute(List<String>)` (application ring).

## Subtasks

### T011 — Asset sidecar binding

- `AssetDso` (record: `id`, `mime`, `path`, `@Nullable title`), `AssetYaml` (the kind's `YamlFormat`, following
  `ParadigmYaml`/`TacticYaml`: schema-validated read with unknown keys refused, from-scratch write; an absent title is
  not written, `title: null` reads as absent), `AssetBinding` (`KindBinding<AssetDso, Asset>`, token `asset`, file
  `<id>.asset.yaml`, `toEntity`/`toDso`).
- Append `AssetBinding.INSTANCE` to `KindBindings.ALL` and `AssetYaml` to `YamlFormats`.
- `KindBindingsTest`: the existing assertions (format per binding, schema per token, default file name classifies back,
  entity types) must hold for asset; the "first five keep their order" test stays, asset is sixth.

### T012 — `PackFileSet` walk and digest

- Replace the `*.yaml`-only walk with a walk of every regular file (same visitor rules: hidden dirs entered, links not
  followed for dirs, vanished entries skipped, `String` order), each classified once. YAML kinds are grouped as today
  (only `*.yaml` names can match `files` rules, so their lists are unchanged).
- Add `assetFiles(packDir)` (paths classified `asset-payload`) and `templates(packDir)` (paths classified `template`),
  both `String`-ordered, pack-relative.
- `digested`: existing order, then asset files, then templates **after** `drg/fragment.yaml`. A pack with neither
  keeps its exact list (NFR-002). Update the class javadoc ("a file the service loads cannot escape the fingerprint").
- `PackFileSetTest`: a pack without payloads lists exactly what it listed before (compare with a literal list); a pack
  with a sidecar, a nested payload, a sidecar-less payload, a README under `assets/`, a top-level template and a
  mission template lists sidecar in the YAML block, payloads and the template at the end, README and mission template
  absent.

### T013 — Revision over bytes

- `PackRevision.compute` over the files' bytes: add `compute(List<byte[]>)` (or change the parameter) such that for
  UTF-8 text the value is byte-identical to today's (read `compute` and prove it with a test over existing text inputs:
  same hex). Remove the String overload only if nothing else needs it.
- `PackCommitStore.revisionOf` and `CommitStorePackWriteStore.currentRevision`: `Files.readAllBytes`.
- `PackRevisionTest`: old and new paths agree on text; a PNG-like byte array (0x89 lead, NUL) computes without error
  and differs from its one-byte variant.

### T014 — `TemplateBinding` and `PackLayout`

- `TemplateBinding` (concrete, package-private): `toEntity(PackIdentifier, String pathUnderTemplates, Payload)`,
  `pathOf(Template)` → `templates/<id>`, `payloadOf(Template)`. No interface, no registry (P16).
- `PackLayout.pathOf`: a `Template` goes to `templates/<id>` when no place is recorded; every other kind unchanged.

### T015 — `FilesystemPackStore` bytes and refusals

- `read(id)`: besides the YAML kinds, read each `PackFileSet.assetFiles` file with `Files.readAllBytes` into
  `AssetFile(path relative to assets/, Payload)` and each template into `(path under templates/, Payload)`; extend
  `PackRead` with both (immutable lists).
- A kind whose rule declares a content form other than `YAML` or `BYTES` is refused to read (violation naming the form
  and "not built yet"; P19). Nothing in the table declares one today — test with a changed table copy if the seam
  allows, else document why it is unreachable.
- Missing payload (R3): after reading, each asset sidecar whose `path` is not a read asset file is removed from the
  elements and recorded as `PackElementViolation(file = the sidecar's pack-relative path, kind = "asset", reason =
  "payload assets/<path> does not exist")` — keep the exact reason format; WP05 pins it
  (`payload assets/assets/audiences/line_manager.md does not exist` for the built-in case). A second sidecar naming an
  already-claimed file: violation `"payload assets/<path> is already named by asset <id>"`.
- Write side: `writeAssetFile(id, AssetFile)` writes `assets/<path>` with the same atomic temp+rename and
  containment checks as `writeAtomically`, but bytes (`Files.write`); a template write likewise to `templates/<id>`.
  Refactor `writeAtomically` to take `byte[]` (text callers encode UTF-8) rather than duplicating it.
- `AssetStoreTest`/`TemplateStoreTest`: a binary payload (PNG header + NUL + `\r\n` pairs + no final newline) and a
  0-byte file read and write back byte-identical (SHA-256 equal); a sidecar with `surprise: 1` is refused naming the
  key while a clean sidecar on the same fixture loads (FR-006); a sidecar with a missing payload is a violation with
  the exact reason while one with its payload loads (FR-005); the six-built-in shape (`path: assets/...`) gives the
  exact reason above; a non-UTF-8 payload is no violation.

### T016 — Shared assembly; `writeAssetFile`

- One package-private helper that turns a `PackRead` into a `Pack.Builder` (descriptor, violations, every bound YAML
  kind via `collect`, asset files, templates) and fills `places`; `DomainRepositoryService.assemble` and
  `WholePackStore.load` both call it — no second assembly loop.
- `DomainRepositoryService.writeArtifact` handles a `Template` (bytes write to `templates/<id>`); add
  package-private `writeAssetFile(PackIdentifier, AssetFile)` used by the gate adapter (WP05). No new repository port
  (P17); `DomainRepositoryService`'s `implements` list is unchanged.
- `DomainRepositoryServiceTest`: `load` over a fixture with assets and templates builds a valid `Pack` holding them;
  `writeArtifact(template)` and `writeAssetFile` write byte-identical files.

### T017 — `WholePackStore` bytes write

- `put` takes `byte[]`; descriptor, YAML artifacts and graphs encode UTF-8 as today; asset files are written to
  `assets/<path>`, templates to their layout path. Keep the case-folding duplicate check (`two parts of the pack are
  written to …`) across every part, payloads included.
- `load`: the modelled set includes every loaded asset file (`assets/<path>`) and template path, so `unmodelled()`
  lists neither (the excluded READMEs remain unmodelled).
- A pack with violations is still refused (R3 + existing rule).
- `WholePackStoreTest`: a fixture with nested binary payloads, a sidecar-less payload and a template writes and
  reloads with identical bytes and an identical aggregate; two asset files `A.png` and `a.png` refuse the write and
  leave no target; a 0-byte template round-trips; `unmodelled()` holds the README under `assets/` only.

## Definition of Done

- `./gradlew :packs:test` green (all packs unit tests, not only new ones), `:deployable:test` green (it serves
  `packs/qa` and `packs/internal`, which now load assets), `:packs:check` green.
- Run `./gradlew :packs:openPacksRoundTrip` and report: the built-in pack is expected to report six violations here
  (WP05 pins them); every other pack must load with zero violations.
- No change to `contracts/charter/openapi.yaml`, the kernel, or the gate test sources (WP05 owns them).

## Risks / reviewer guidance

- Digest order: packs without assets/templates must keep their exact digest list (reviewer: check the test compares a
  literal list).
- Two assembly sites → one helper. Missing-payload refusal lives in the store only; the domain invariant is the guard.
- Append-only edits to `KindBindings`/`YamlFormats`: one line each.
