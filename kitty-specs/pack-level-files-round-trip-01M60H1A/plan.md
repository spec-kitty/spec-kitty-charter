# Implementation Plan: Pack-level files round trip

**Branch**: `feat/195-pack-level-round-trip` | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/pack-level-files-round-trip-01M60H1A/spec.md`

## Summary

This plan reads the four pack-level files of a Charter Pack into the domain and writes them back, each through a
closed, sanitized storage shape:

- **Activation presets** (`presets/<id>.yaml`) become a `Preset` value held by the `Pack`. Its items carry the
  per-item must-have mark.
- **`pack.md`** becomes a pack-level `bytes` payload (`pack-document`) held by the `Pack`.
- **`pack.yaml`'s schema closes.** `undeclaredKeys` and the `PACK_UNKNOWN_KEY` finding are removed, so an unknown key
  makes the pack unreadable.
- **`pack-manifest.yaml`** is held beside the aggregate in `WholePackStore.LoadedPack`. Its hashes are derived: the
  writer recomputes them from the bytes it writes, and the gate verifies them.

The whole-pack gate models the three kinds. `pack.yaml` and `pack.md` turn green. The built-in presets and manifest go
on the known-failure list, keyed by drafted upstream issues. ADR-0042 records the decisions.

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain; local fallback `-Pcharter.javaVersion=21`, CI always runs 25).
Temurin 25 is installed in this session.

**Primary Dependencies**: Spring Boot, SnakeYAML Engine through the service's `Yaml` wrapper, and the networknt JSON
Schema validator through `PackSchemas`. All exist already. SHA-256 comes from the JDK `MessageDigest`. RFC 8785
canonical JSON is hand-written over strings, lists and maps only. No dependency is added.

**Storage**: Files, as pack directories under the configuration root (ADR-0038).

**Testing**:
- JUnit 5 + AssertJ.
- The whole-pack gate (`WholePackRoundTripTest`, tag `whole-pack`).
- The open-packs seam test (`WholePackOpenPacksRoundTripTest`).
- ArchUnit (`HexagonalArchitectureTest`).
- `./gradlew verifyAll` and the docs checks.

**Target Platform**: JVM service.

**Project Type**: single. Gradle multi-module: `service/kernel`, `service/packs`, `service/deployable`.

**Performance Goals**: reading the 1388-line built-in manifest adds under 0.5 s to the built-in load (NFR-004).

**Constraints**: C-001 to C-006 of the spec. The HTTP contract is frozen, shared files are edited append-only, no
tolerance logic is added, merged schemas stay untouched, the comparator gets no exception, and built-in fixes go to
core first.

**Scale/Scope**: open-packs holds 3 presets (2 of them in the gate), 1 `pack.md`, 2 `pack.yaml` and 1 manifest (272
constituents and 2 presets).

### Supply chain (DIRECTIVE_051)

No dependency is added, upgraded or removed, and strict SHA-256 dependency verification is unchanged. Hashing uses
the JDK.

## Charter Check

| Charter rule | How this plan meets it |
|---|---|
| Divergence from the oracle must not break core | No service operation serves presets or the manifest. Core keeps reading the open-packs files in its own shapes until it adopts the sanitized ones (C-006, draft C-1). The descriptor is closed, as core's `PackDescriptor` already is (`extra="forbid"`). |
| No incompatible OpenAPI change | No file under `contracts/charter/**` changes (C-001). The served behaviour changes in one way: a pack whose `pack.yaml` has an unknown key is `pack_unreadable` instead of loading with a warning. ADR-0036 ruling 1 decided this, and the `DiagnosticCode` description question is on #195. `Charter-Source-Fingerprint` values change once for packs that hold presets, `pack.md` or a manifest. |
| Open-packs YAML round-trips without loss of meaning | Presets and the manifest compare by meaning. The manifest's derived fields are verified rather than compared, and are declared in ADR-0042. `pack.md` compares by bytes. A pinned file in another shape is refused, pinned and filed upstream (ADR-0036 decision 5). |
| No unverified dependency | None is added. |
| Adversarial squad at the plan point for contract, architecture and domain changes | A post-plan squad (architect, reviewer and reducer lenses) runs before tasks. Its findings are dispositioned in `research.md`. |
| BDD / specification by example; test first | Each concern starts from the spec's acceptance scenarios as failing tests. Every refusal is paired with a same-fixture clean control. |
| DDD in core's vocabulary | `Preset`, `PresetItem` and the pack document live in `packs.domain`, named in the glossary terms of the spec's Domain Language. |
| Decision documentation | ADR-0042, amendment notes on ADR-0031, 0036, 0037 and 0040, and an ADR index line. |
| Living documentation | Updated in the same PR: the `file-kinds.yaml` header, `contracts/packs/README.md`, the logical-model and descriptor-schema pages, a `docs/changelog/` entry, the glossary, and conformance DIV-005 and DIV-006 (closed). |
| Boy scout vs smallest diff | Only what the pack-level files need. Merged-DSO violations are listed for epic #186 and not fixed (C-004). |

Gate result: pass, with no violations to justify.

## Project Structure

### Documentation (this mission)

```
kitty-specs/pack-level-files-round-trip-01M60H1A/
├── spec.md
├── plan.md                  # this file
├── research.md              # decisions, squad findings and dispositions
├── research/grounding-squad.md, research/draft-upstream-issues.md
├── data-model.md            # Preset, PresetItem, pack document, PackManifestDso, descriptor
├── contracts/pack-level-shapes.md   # the three JSON Schemas' shapes and the hash recipes
├── quickstart.md
├── decisions/               # DM-*.md (specify)
└── tasks.md, tasks/         # after /spec-kitty.tasks
```

### Source Code (repository root)

```
contracts/packs/
├── file-kinds.yaml                    # rules: presets, pack-manifest (files); pack.md (payloads); two exclusions retired
├── schemas/preset.schema.json         # new, closed
├── schemas/pack-manifest.schema.json  # new, closed
├── schemas/pack-descriptor.schema.json# closed; description text consistent
└── README.md
service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackDescriptor.java   # undeclaredKeys removed
service/packs/src/main/java/ai/speckitty/charter/packs/
├── domain/Preset.java, PresetItem.java, Pack.java (presets, document), PackFindingCode.java, PackPolicy.java, Diagnostic.java
└── infra/storage/
    ├── PresetDso.java, PresetYaml.java           # read/write the preset shape; id == stem check
    ├── PackManifestDso.java, PackManifestYaml.java, PackManifests.java   # shape, hashes, restamp
    ├── PackFileKind.java (BYTES_KINDS += pack-document), PackFileSet.java (listing + digest)
    ├── FilesystemPackStore.java (PackRead: presets, document), DomainRepositoryService.java (assembly, writes)
    ├── WholePackStore.java (LoadedPack.manifest, write presets/document/manifest), PackDescriptorDso/Reader/Writer
    └── YamlSerializationStrategy.java / SerializationStrategy.java (preset and manifest read/write)
service/packs/src/test/...                        # unit, storage, gate, open-packs, fixtures
service/packs/src/test/resources/whole-pack/{expected-red,known-failures}.yaml
docs/adr/0042-*.md, docs/adr/{0031,0036,0037,0040}-*.md (amendment notes), docs/adr/README.md
docs/changelog/2026-10-28-pack-level-files-round-trip.md, docs/glossary/…, docs/architecture/*.md, docs/conformance/*
```

**Structure Decision**: The existing `packs` module is used. No new module is added.

## Complexity Tracking

| Item | Why it is needed | Simpler alternative rejected because |
|---|---|---|
| `Pack` gains two pack-level slots (presets, document) beyond its one recorded exception (asset files) | Neither is a `CharterArtifact`. Presets have no URN, and `pack.md` has no id. | A `KindBinding` is typed to `CharterArtifact` and `<id>.<token>.yaml` files. Forcing a URN on them invents identity that core does not have. Rule of three: a fourth slot extracts a `PackLevelContent` value (ADR-0042). |
| Manifest held beside the aggregate | It holds file paths and hashes, and `Pack` holds no paths by design. | Putting it in `Pack` breaks the aggregate's path-free rule. ADR-0037 decision 5 already keeps graph documents beside it. |

## Key design decisions (detail in research.md)

- **D1 Presets are pack-level domain values.** `Preset(id, description, activatedKinds, lists)`, where `lists` maps
  each activation list to its ordered `PresetItem(id, mustHave)` items. The required set and "governing" are derived.
  The `Pack` holds the presets sorted by id, and two presets with one id breach an invariant. The storage
  `PresetYaml` checks `id == file stem` and turns a mismatch into a violation on the file.
- **D2 The preset shape is sanitized** (rules S1–S8). The mark is per item (`must_have`). Empty lists are refused, so
  the comparator needs no exception (C-005).
- **D3 `pack.md` is a `bytes` payload** of kind `pack-document`, matched by the payloads rule `*/pack.md`.
  `Pack.document()` returns an `Optional<Payload>`.
- **D4 The descriptor is closed.** Unknown and retired keys are refused by the schema on the descriptor's existing
  path (`PACK_UNREADABLE`). `undeclaredKeys`, `PACK_UNKNOWN_KEY` and the write-side refusal go.
  `contracts/charter/**` stays as it is.
- **D5 The manifest is a storage object beside the aggregate.** `PackManifestDso(schemaVersion, manifestHash,
  constituents, presets)`. `PackManifests` owns the two recipes and `restamp(dso, packDir)`. `WholePackStore.write`
  stages every other file, then restamps the manifest from the staged bytes, then writes it. A listed path that was
  not staged refuses the write.
- **D6 The gate models `preset`, `pack-document` and `pack-manifest`.**
  - `DomainRoundTrip` writes presets and `pack.md` in place, paired by path.
  - It writes the manifest restamped over the target.
  - The gate compares a manifest's non-derived fields by meaning, with a declared per-kind rule in
    `MeaningComparator`, and verifies the derived fields on each side against that side's bytes.
- **D7 The fingerprint appends** presets, then `pack.md`, then the manifest, after the templates. A pack without them
  keeps its digest list (NFR-002).
- **D8 Known-failure keys** admit `spec-kitty/spec-kitty-open-packs#TBD-<slug>`.

## Implementation Concern Map

### IC-01 — Pack vocabulary and schemas

- **Purpose**: Declare the three pack-level kinds and their closed schemas, and close the descriptor schema.
- **Relevant requirements**: FR-001, FR-006, FR-011 (schema half), FR-005 (rule)
- **Affected surfaces**:
  - `contracts/packs/file-kinds.yaml`: `files` rules `*/presets/*.yaml → preset` and
    `*/pack-manifest.yaml → pack-manifest`; the two exclusions retired; the `payloads` rule
    `*/pack.md → pack-document, bytes`; the header.
  - `schemas/preset.schema.json`, `schemas/pack-manifest.schema.json`, `schemas/pack-descriptor.schema.json`.
  - `PackFileKind.BYTES_KINDS`.
  - Tests: `PackFileKindTest`, `PackSchemasTest`, `SchemaKeywordCoverageTest`, `VocabularyEqualityTest`.
  - `contracts/packs/README.md`.
- **Sequencing/depends-on**: none
- **Risks**: sibling #194 edits the same `exclusions` block, so keep each edit to its own lines. A schema that the
  keyword-coverage test does not know may need its keyword list appended.

### IC-02 — Preset and pack document in the domain

- **Purpose**: `Preset` and `PresetItem` with their invariants and derived required set; `Pack` slots for presets
  and the document.
- **Relevant requirements**: FR-002, FR-003 (domain half), FR-005 (domain half)
- **Affected surfaces**: `packs.domain` (`Preset`, `PresetItem`, `Pack` and its builder, `toBuilder`, `with`);
  `HexagonalArchitectureTest`
- **Sequencing/depends-on**: none
- **Risks**: `Pack.with` must keep the presets and the document, or a registration would drop them. A test goes
  through a real registration service.

### IC-03 — Descriptor closure

- **Purpose**: Remove the undeclared-keys path end to end.
- **Relevant requirements**: FR-011, FR-012
- **Affected surfaces**:
  - Code: kernel `PackDescriptor`, `PackDescriptorDso`, `PackDescriptorReader`, `PackPolicy`, `PackFindingCode`,
    `Diagnostic.Code`, `WholePackStore.write`.
  - Tests: `PackDescriptorReaderTest`, `PackRoundTripTest`, `WholePackStoreTest`, `DomainRepositoryServiceTest`,
    `PackFindingTest`, `PackPolicyRulesTest`, `PackPolicyOrderTest`, `VocabularyEqualityTest`,
    `CharterWiringTest`, and planted fixtures.
  - Docs: conformance DIV-005 and DIV-006.
- **Sequencing/depends-on**: IC-01 (the closed schema)
- **Risks**: `VocabularyEqualityTest` pins the contract enum, which keeps `pack_unknown_key`. Only the Java side
  loses it, and the contract test must still pass (C-001).

### IC-04 — Storage: presets and the pack document

- **Purpose**: Read, write and fingerprint presets and `pack.md`.
- **Relevant requirements**: FR-002, FR-003, FR-004, FR-005, FR-013
- **Affected surfaces**:
  - New: `PresetDso`, `PresetYaml`.
  - `SerializationStrategy` and `YamlSerializationStrategy` (`readPreset`, `writePreset`).
  - `PackFileSet` (`Listing.presets`, `Listing.documents`, `digested`).
  - `FilesystemPackStore` (`PackRead` presets and document).
  - `DomainRepositoryService` (`assembly`, `writePreset`, `writeDocument`).
  - `WholePackStore` (write; `unmodelled` excludes them).
- **Sequencing/depends-on**: IC-01, IC-02
- **Risks**: Each preset violation is per file (partial-tolerant), like an artifact's. A pack without presets must
  write no `presets/` directory.

### IC-05 — The manifest

- **Purpose**: The sanitized manifest is read, its hashes recomputed on write, and its recipes proven.
- **Relevant requirements**: FR-006, FR-007, FR-008, FR-009, FR-010, NFR-001, NFR-004
- **Affected surfaces**:
  - New: `PackManifestDso`, `PackManifestYaml`, `PackManifests` (content hash, JCS manifest hash, restamp, stale
    check).
  - `WholePackStore` (`LoadedPack.manifest`, read, write last).
  - `FilesystemPackStore` (read the manifest; an unreadable one is a violation on the file).
  - `PackFileSet.digested`.
- **Sequencing/depends-on**: IC-01; IC-04 for the preset paths
- **Risks**: JCS correctness. Tests cover fixed vectors computed independently (Python `json.dumps` with sorted keys
  and compact separators, which equals JCS for string-only data), escapes and non-ASCII. Membership is never
  regenerated (#110).

### IC-06 — Gate, known failures and controls

- **Purpose**: The gate models the three kinds and verifies the manifest's derived fields. Expected-red loses three
  kinds, and known-failures gains three files.
- **Relevant requirements**: FR-014, FR-015, SC-001–SC-004
- **Affected surfaces**:
  - `DomainRoundTrip` (`MODELLED`, outcomes for the three kinds).
  - `MeaningComparator` (a `pack-manifest` rule that skips derived fields).
  - `WholePackRoundTripTest` (derived-field verification, controls).
  - `WholePackExpectation` (the TBD key).
  - `WholePackOpenPacksRoundTripTest`.
  - `expected-red.yaml`, `known-failures.yaml`.
  - The fixture pack under `src/test/resources`.
- **Sequencing/depends-on**: IC-03, IC-04, IC-05
- **Risks**: Census counts (`REQUIRED_KINDS`) are shared with #194, so the edits are append-only. The pinned `at` of
  each known failure must be the exact refusal text.

### IC-07 — Decision records and living docs

- **Purpose**: ADR-0042 and the amendment notes, the changelog, the glossary, the index, the architecture pages,
  the conformance register, and the reconcile with main after #194.
- **Relevant requirements**: FR-016, FR-017, NFR-003
- **Affected surfaces**: `docs/adr/0042-*.md`, `docs/adr/{0031,0036,0037,0040}-*.md` (notes), `docs/adr/README.md`,
  `docs/changelog/`, `docs/glossary/`, `docs/architecture/{logical-model,descriptor-schemas,packs-resource-model}.md`,
  `docs/conformance/divergence-register.md`, `cspell.json`
- **Sequencing/depends-on**: IC-06
- **Risks**: an ADR number clash with #194. Whichever merges first keeps 0042.
