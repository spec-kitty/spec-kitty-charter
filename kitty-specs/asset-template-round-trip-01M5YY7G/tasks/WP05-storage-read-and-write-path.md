---
work_package_id: WP05
title: Storage read and write path for assets and templates
dependencies:
- WP01
- WP02
- WP03
- WP04
requirement_refs:
- FR-002
- FR-003
- FR-004
- FR-005
- FR-006
- FR-007
- FR-010
- FR-011
- NFR-001
- NFR-002
- NFR-004
- C-005
planning_base_branch: feat/asset-template-round-trip
merge_target_branch: feat/asset-template-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/asset-template-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/asset-template-round-trip unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-asset-template-round-trip-01M5YY7G
base_commit: 54b37081b30b3da5ad72bfa006d7eccf331263f3
created_at: '2026-10-27T17:42:21.695125+00:00'
subtasks:
- T017
- T018
- T019
- T020
- T021
- T022
- T023
- T024
phase: Phase 2 - Storage
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AssetDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AssetYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/AssetBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/TemplateBinding.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/AssetStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/TemplateStoreTest.java
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
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/KindBindingsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/AssetStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/TemplateStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GeneratedPackTree.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetExactnessTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackOpenPacksRoundTripTest.java
role: implementer
tags: []
task_type: implement
---
# Work Package Prompt: WP05 — Storage read and write path for assets and templates

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Widen the ADR-0037 storage seam to the `bytes` content form (ADR-0037 decision 4 hands this to #193): bind asset
sidecars as a YAML kind; read asset files and templates as bytes into the `Pack` (WP02 types); refuse a sidecar whose
payload is missing (R3); write every byte back through the per-pack store and the whole-pack store; make the source
fingerprint cover the new files with packs that hold none keeping their exact digest; and keep `:packs:check` green
by updating the oracle and the open-packs round trip in the same change. Sidecars, asset files and templates land
together: sidecars without their asset files would breach the WP02 invariant on every pack holding assets.

Read first: `plan.md` (D2–D6, "Acceptance checks" 1, 2, 5, 10), `data-model.md` (Storage, Persistence entry points,
State and refusals), `research.md` D3, D5, D6, D9 and P3, P7, P8, P10, P16, P17; ADR-0037.

## Branch Strategy

Planning branch and final merge target: `feat/asset-template-round-trip`. Execution worktrees are allocated per
computed lane from `lanes.json`; do not pick a base branch by hand. Implement with:

```bash
spec-kitty agent action implement WP05 --agent claude --mission asset-template-round-trip-01M5YY7G
```

## Validation discipline

Run the targeted tests for the files you touch and the owning module's fast tier (`./gradlew :packs:test
:packs:check`; `:deployable:test` when named). Never run a whole-repo heavy sweep (`verifyAll` is CI's,
directive NO_FULL_HEAVY_SUITES_IN_MISSION); record the exact commands and pass/fail counts in the review handoff.
Java 25 via `JAVA_HOME=/usr/lib/jvm/temurin-25`; Maven Central HTTP 429 → wait and retry.

## Context (code you will change)

- `KindBindings.ALL` / `YamlFormats`: append-only lists shared with #190/#191/#192 — append one line each at the end.
- `PackFileSet`: `files(packDir, kind)`, `digested(...)`, a `YamlCollector` walk of `*.yaml` regular files sorted by
  `String`, classification via `PackFileKind.bundled().classify(<packName>/<file>)`.
- `FilesystemPackStore`: `read(id)` → `PackRead(descriptor, elements by kind, violations)` (a 3-argument constructor
  is called by `PackRoundTripTest:87` — you own that test); `readFile` decodes UTF-8; `write` upserts
  `<id>.<token>.yaml`; `writeAtomically` (temp + rename, containment).
- `DomainRepositoryService`: `assemble`, static `collect`, static `writeArtifact(store, key, artifact)` (used by
  `PackCommitStore`); per-kind typed saves only — there is no generic instance save today.
- `WholePackStore`: `load` (own assembly loop + `unmodelled`), `write` (`put` uses `Files.writeString`, loops
  `pack.artifacts()` → `layout.pathOf` + `text()`, both via `KindBindings` only). `PackLayout.pathOf` throws for a
  kind outside `KindBindings`.
- Oracle: `GeneratedPackTree` + `PackFileSetExactnessTest` (`theFingerprintChangesIfAndOnlyIf…`,
  `theStoreLoadsExactlyThePlantedFilesOfEveryKind`); `DECOY_ASSET` plants `x.asset.yaml` as a decoy — it stops being
  one when `AssetBinding` joins `KindBindings`.
- `WholePackOpenPacksRoundTripTest` runs in `:packs:check` (`build.gradle.kts:168`) and requires zero violations per
  pack; built-in will have six after this WP.

## Subtasks

### T017 — Asset sidecar binding

- `AssetDso` (record: `id`, `mime`, `path`, `@Nullable title`), `AssetYaml` (follow `ParadigmYaml`/`TacticYaml`:
  schema-validated read, unknown keys refused, from-scratch write; absent title not written; `title: null` reads as
  absent), `AssetBinding` (`KindBinding<AssetDso, Asset>`, token `asset`, file `<id>.asset.yaml`).
- Append to `KindBindings.ALL` and `YamlFormats`. `KindBindingsTest` holds for asset (format, schema per token, file
  name classifies back, entity type); asset is sixth after the first five.
- Store round-trip tests (`AssetStoreTest`): a sidecar without `title` is written without `title` and is equal by
  meaning (`MeaningComparator.adr0036()`) to its source; with a title, the title survives (US1 scenario 2, FR-002).

### T018 — `PackFileSet` walk and digest

- Walk every regular file (same visitor rules: hidden dirs entered, dir links not followed, vanished entries skipped,
  `String` order), classify each once. YAML kind lists unchanged. Add `assetFiles(packDir)` and `templates(packDir)`.
- `digested`: existing order, then asset files, then templates **after** `drg/fragment.yaml` (NFR-002). Update the
  class javadoc.
- `PackFileSetTest`: a pack without payloads lists exactly a literal list; a pack with a sidecar, a nested payload, a
  sidecar-less payload, a README under `assets/`, a top-level template and a mission template lists the sidecar in the
  YAML block, payloads then the template at the end; README and mission template absent.

### T019 — `TemplateBinding` and `PackLayout`

- `TemplateBinding` (concrete, package-private): `toEntity(PackIdentifier, String pathUnderTemplates, Payload)`,
  `pathOf(Template)` → `templates/<id>`, `payloadOf(Template)`. No interface, no registry (P16).
- `PackLayout.pathOf`: a `Template` with no recorded place goes to `templates/<id>`; other kinds unchanged.

### T020 — `FilesystemPackStore` bytes and refusals

- `read(id)`: read each `PackFileSet.assetFiles` file with `Files.readAllBytes` into `AssetFile(path relative to
  assets/, Payload)` and each template into `(path under templates/, Payload)`; extend `PackRead` with both lists and
  update `PackRoundTripTest:87` (owned here) — or keep a 3-argument constructor delegating with empty lists.
- Missing payload (R3): each sidecar whose `path` is not a read asset file is dropped from the elements and recorded as
  `PackElementViolation(file = sidecar's pack-relative path, kind = "asset", reason = "payload assets/<path> does not
  exist")` — exactly this text; WP04 pins `payload assets/assets/audiences/line_manager.md does not exist`. A second
  sidecar naming an already-claimed file: `"payload assets/<path> is already named by asset <id>"`.
- Write: `writeAtomically` takes `byte[]` (text callers encode UTF-8); asset files go to `assets/<path>`, templates to
  `templates/<id>`, same atomic temp + rename and containment checks.
- `AssetStoreTest` / `TemplateStoreTest`: a binary payload (PNG header, NUL, `\r\n` pairs, no final newline) and a
  0-byte file round-trip byte-identical (SHA-256); `surprise: 1` on a sidecar is refused naming the key while a clean
  sidecar on the same fixture loads (FR-006); missing payload gives the exact reason while a sidecar with its payload
  loads (FR-005); the built-in shape (`path: assets/...`) gives the exact pinned reason; a non-UTF-8 payload is no
  violation.

### T021 — Shared assembly and persistence entry points

- One package-private helper turns a `PackRead` into a `Pack.Builder` (descriptor, violations, every bound YAML kind
  via `collect`, asset files, templates) and fills `places`; `DomainRepositoryService.assemble` and
  `WholePackStore.load` both use it.
- Package-private **instance** methods on `DomainRepositoryService` for the gate adapter (WP07):
  `void saveArtifact(PackIdentifier pack, CharterArtifact artifact)` (any bound kind or a `Template`) and
  `void writeAssetFile(PackIdentifier pack, AssetFile file)`. The static `writeArtifact(store, key, artifact)` also
  handles a `Template`. No new repository port; the `implements` list is unchanged (P17).
- `DomainRepositoryServiceTest`: `load` over a fixture with assets and templates builds a valid `Pack` holding them;
  `saveArtifact(template)` and `writeAssetFile` write byte-identical files.

### T022 — `WholePackStore` bytes write

- `put(byte[])`; descriptor, YAML artifacts and graphs encode UTF-8 as today; asset files to `assets/<path>`,
  templates to their layout path; the case-folding duplicate check covers every part.
- `load`: the modelled set includes every loaded asset file and template path; `unmodelled()` keeps only the excluded
  README under `assets/`.
- `WholePackStoreTest`: nested binary payloads, a sidecar-less payload and a template write and reload with identical
  bytes and aggregate; `A.png`/`a.png` asset files and `x/A.md`/`x/a.md` templates each refuse the write and leave no
  target; a 0-byte template round-trips.

### T023 — Oracle rows

- Rework `DECOY_ASSET`: a sidecar is digested whether or not its payload exists; asset files only under
  `<pack>/assets/`.
- New shapes: sidecar + payload under `assets/`; sidecar-less payload under `assets/`; nested payload `assets/a/b/`;
  `README.md` under `assets/` (not digested); top-level template `templates/x/y.md`; mission template
  `missions/m/templates/t.md` (decoy); payload-like file outside `assets/` (decoy).
- Drive the oracle through `FileSystemPackSources.fingerprint()` (production path).

### T024 — Open-packs fidelity

- `WholePackOpenPacksRoundTripTest`:
  1. Each pack's violations equal its pinned refusals from `expected-red.yaml` (WP04's accessor), matched by file
     **and** reason; a new or a missing one fails (R10). Packs with no pinned entries: zero violations.
  2. For every pack that loads clean: write, reload, compare the aggregate (extend `meaning()` with `assetFiles()`
     and `templates()`) **and** compare files — sidecars with `MeaningComparator.adr0036()`, asset files and
     templates by SHA-256.
  3. Pinned counts: internal 10 sidecars, 10 asset files, 4 binary (3 `.otf`, 1 `.png`); qa 3/3; event-driven 1/1;
     repository `packs/qa` and `packs/internal` (read their own counts). `unmodelled()` holds no file under `assets/`
     or `templates/` except `README.md`.
  4. Corrected built-in copy: copy built-in to a temp dir, strip the `assets/` prefix from the six sidecar `path`
     values (test fixture only — the open-packs fix, never service logic): zero violations, 6 sidecars, 7 asset files,
     2 templates, full write + reload + file comparison green; the unmodified built-in in the same test gives exactly
     the six pinned violations.
  5. Log the whole-pack load time of the internal pack (NFR-004: under 1 s growth; recorded in the handoff, not
     asserted).

## Definition of Done

- `./gradlew :packs:test :packs:check` green (includes `openPacksRoundTrip`); `:deployable:test` green.
- Packs without assets/templates keep their exact digest list (literal in `PackFileSetTest`).
- No change to `contracts/charter/openapi.yaml`, the kernel, or WP07's gate sources.

## Risks / reviewer guidance

- Reviewer: revert the missing-payload refusal and confirm the open-packs test and `AssetStoreTest` go red; revert
  the bytes write (encode via UTF-8 text) and confirm the binary tests go red.
- Two assembly sites → one helper; missing-payload refusal in the store only.
