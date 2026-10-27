---
work_package_id: WP02
title: Asset and template domain model
dependencies: []
requirement_refs:
- FR-002
- FR-003
- FR-004
- FR-007
- C-003
planning_base_branch: feat/asset-template-round-trip
merge_target_branch: feat/asset-template-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/asset-template-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/asset-template-round-trip unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-asset-template-round-trip-01M5YY7G
base_commit: 5716da33739d2f3892597a2fb96bc3d5ad4469d8
created_at: '2026-10-27T17:22:40.298387+00:00'
subtasks:
- T006
- T007
- T008
- T009
- T010
phase: Phase 1 - Foundations
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Payload.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AssetIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Asset.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AssetFile.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/TemplateIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Template.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PayloadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/AssetEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/TemplateEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackAssetFilesTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Payload.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AssetIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Asset.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AssetFile.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/TemplateIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Template.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Pack.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PayloadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/AssetEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/TemplateEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackAssetFilesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/CharterArtifactFamilyTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP02 — Asset and template domain model

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Add the domain types for the two payload-bearing kinds in `packs.domain` (ADR-0006, ADR-0035): `Payload`,
`AssetFile`, `Asset` (+ `AssetIdentifier`), `Template` (+ `TemplateIdentifier`), and extend the `Pack` aggregate to
hold asset files. Read first: `data-model.md` (authoritative field and invariant table), `plan.md` D2–D4, `research.md`
D2–D4 and post-plan P3, P18. No storage code here.

## Branch Strategy

Planning branch and final merge target: `feat/asset-template-round-trip`. Lanes come from `lanes.json`. Implement with:

```bash
spec-kitty agent action implement WP02 --agent claude --mission asset-template-round-trip-01M5YY7G
```

## Context

- Read an existing kind end to end first: `Tactic.java`/`TacticIdentifier.java` (constructor + `RULES.guard`, builder,
  `CharterArtifact` members), `Paradigm.java`, and `service/kernel/.../kernel/domain/CharterArtifact.java` (kind,
  packId, localId, label, optional description, relationships, `urn()`, optional `schemaVersion()` — ADR-0035).
- `ArtifactKind.ASSET` and `ArtifactKind.TEMPLATE` already exist in the kernel; do not change the kernel.
- `Pack.java`: artifacts keyed by kind (ADR-0037), `builder()`, `toBuilder()` (around line 99), `with(artifact)`
  (line ~114, builds from a fresh `builder()` — that is the bug P3 names), typed views (`tactics()`, …), violations.
  Its javadoc says kinds join "with no field, accessor or builder slot of its own" and it "holds no … file paths":
  asset files are the recorded exception (R1), and the javadoc must say so.

## Subtasks

### T006 — `Payload`

- `public final class Payload` with a private defensive copy of the bytes; `static Payload of(byte[])` copies;
  `byte[] bytes()` returns a copy; `int size()`; `String sha256()` (lower-case hex) for diagnostics; `equals`/`hashCode`
  by content (`Arrays.equals`/`Arrays.hashCode`); `toString()` prints size and digest, never the bytes.
- Tests (`PayloadTest`): equal by content, not identity; mutation of the input array or of `bytes()` result does not
  change the payload; empty payload allowed; a payload with NUL bytes and CR/LF pairs round-trips through `bytes()`.

### T007 — `AssetIdentifier`, `Asset`

- `AssetIdentifier`: value object over a non-blank string (follow `TacticIdentifier`).
- `Asset implements CharterArtifact`: fields per `data-model.md` (`id`, `packId`, `mime`, `path`, `Optional<String>
  title`). Invariants: `mime` and `path` non-blank. Store `mime` and `path` exactly as authored: no MIME check
  (`PackPolicy` owns it), no path normalization. `kind()` → `ASSET`; `localId()` → id; `label()` → title, else id;
  `description()` empty; relationships none (follow how the kernel models "none"); `schemaVersion()` empty.
  Builder in the house style.
- Tests (`AssetEntityTest`): blank mime or path breach (`ConstraintBreachException`); label falls back to id; urn is
  `asset:<id>`; schema version empty; `path` `assets/audiences/x.md` is kept verbatim (the domain does not "fix" the
  built-in inconsistency — C-003).

### T008 — `AssetFile`

- `public record AssetFile(String path, Payload payload)`: `path` relative to the pack's `assets/` directory,
  `/`-separated, non-blank, not absolute, no `..` segment, no empty segment; breach otherwise.
- Tests: valid nested path; `../x`, `/x`, `a//b`, blank refused; equality by path and payload.

### T009 — `TemplateIdentifier`, `Template`

- `TemplateIdentifier`: the path under the pack's top-level `templates/`, same path rules as `AssetFile.path`.
- `Template implements CharterArtifact`: `id`, `packId`, `Payload content`. `kind()` → `TEMPLATE`; `localId()` → the
  path; `label()` → last segment; description empty; relationships none; schema version empty; `urn()` →
  `template:writing/meeting-minutes.md` for that id (check the kernel `Urn` accepts `/` and `.` in a local id; the
  pack schemas already allow `template:[A-Za-z0-9_/.-]+`).
- Tests (`TemplateEntityTest`): urn, label, path rules, content equality.

### T010 — `Pack` additions

- Field `SortedMap<String, AssetFile>`-like holding (sorted by path, one per path); accessor `assetFiles()` returns an
  immutable list sorted by path; `Builder.assetFile(AssetFile)`; a repeated path breaches.
- Typed views `assets()` and `templates()` (one-line delegates like `tactics()`).
- Invariants, guarded in the constructor: every `Asset` names a held asset file by `path`; no two assets name the same
  file. Messages name the asset id and the path.
- `toBuilder()` carries asset files; `with(artifact)` starts from `toBuilder()` minus the replaced artifact (same kind
  and local id) so asset files and violations survive and the replaced artifact still moves to the end of its kind
  (ADR-0037 consequence — keep that behaviour and its existing tests green).
- Javadoc: record that asset files are the one pack-level collection beside the kind map (operator ruling R1, an
  asset payload may have no sidecar and no id) and that they hold payload paths by design.
- Tests (`PackAssetFilesTest`): an asset naming a missing file breaches; two assets naming one file breach; a
  sidecar-less asset file is allowed; `with(directive)` on a pack holding an asset and its file keeps both and stays
  valid (P3, positive control: the same pack without the asset file breaches); `toBuilder().build()` equals the source.
- `CharterArtifactFamilyTest`: if it enumerates implementing kinds, add `Asset` and `Template` (read it first).

## Definition of Done

- `./gradlew :packs:test --tests '*Payload*' --tests '*AssetEntity*' --tests '*TemplateEntity*' --tests '*PackAssetFiles*' --tests '*Pack*Test'` green, plus `:deployable:test --tests '*HexagonalArchitecture*'`.
- `./gradlew :packs:check` green (Error Prone, NullAway, Checkstyle, Spotless).
- No storage, kernel or contract change.

## Risks / reviewer guidance

- Verify `with()` keeps asset files (the P3 failure: registrations would 422 on packs holding assets).
- Verify no MIME/containment logic duplicates `PackPolicy`/`AssetMimeTable`.
