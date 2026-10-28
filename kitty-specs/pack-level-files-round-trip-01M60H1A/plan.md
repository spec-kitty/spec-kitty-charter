# Implementation Plan: Pack-level files round trip

**Branch**: `feat/195-pack-level-round-trip` | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/pack-level-files-round-trip-01M60H1A/spec.md`

## Summary

This plan reads the four pack-level files of a Charter Pack into the domain and writes them back, each through a
closed, sanitized storage shape:

- **Activation presets** (`presets/<id>.yaml`) become a `Preset` value held by the `Pack`. Its items carry the
  per-item must-have mark.
- **`pack.md`** is held through the `markdown` content form that sibling #194 builds in its PR-A. That work package
  comes last and depends on PR-A merging.
- **`pack.yaml`'s schema closes.** `undeclaredKeys` and the `PACK_UNKNOWN_KEY` finding are removed, so an unknown key
  makes the pack unreadable.
- **`pack-manifest.yaml`** is held beside the aggregate in `WholePackStore.LoadedPack`. Its hashes are derived: the
  writer recomputes them from the bytes it writes, and the gate verifies them.

The whole-pack gate models the three kinds. `pack.yaml` and `pack.md` turn green. The built-in presets and manifest go
on the known-failure list, keyed by drafted upstream issues. ADR-0043 records the decisions.

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
| No incompatible OpenAPI change | No file under `contracts/charter/**` changes (C-001). Three behaviour changes, none served on the wire: (1) a pack whose `pack.yaml` has an unknown key becomes `pack_unreadable` instead of loading with a warning (ADR-0036 ruling 1; the `DiagnosticCode` description question is on #195); (2) validate mode reports the pinned built-in presets and manifest as `ARTIFACT_SCHEMA_ERROR`, so built-in validate goes from 0 to 3 findings and the load-path violations of the seam test from 6 to 9 (`PackValidation` has no controller); (3) `Charter-Source-Fingerprint` changes once for packs that hold presets or a manifest. |
| Open-packs YAML round-trips without loss of meaning | Presets and the manifest compare by meaning. The manifest's derived fields are verified rather than compared, and are declared in ADR-0043. `pack.md` compares by bytes. A pinned file in another shape is refused, pinned and filed upstream (ADR-0036 decision 5). |
| No unverified dependency | None is added. |
| Adversarial squad at the plan point for contract, architecture and domain changes | A post-plan squad (architect, reviewer and reducer lenses) runs before tasks. Its findings are dispositioned in `research.md`. |
| BDD / specification by example; test first | Each concern starts from the spec's acceptance scenarios as failing tests. Every refusal is paired with a same-fixture clean control. |
| DDD in core's vocabulary | `Preset`, `PresetItem` and the pack document live in `packs.domain`, named in the glossary terms of the spec's Domain Language. |
| Decision documentation | ADR-0043, amendment notes on ADR-0031, 0036, 0037 and 0040, and an ADR index line. |
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
| `Pack` gains pack-level slots (presets now, the document with #194's form) beyond its one recorded exception (asset files) | Neither is a `CharterArtifact`. Presets have no URN, and `pack.md` has no id. | A `KindBinding` is typed to `CharterArtifact` and `<id>.<token>.yaml` files. Forcing a URN on them invents identity that core does not have. The rule of three has fired. Extracting a `PackLevelContent` value is a follow-up on epic #186, because `Pack` is shared with #194 (research P8). |
| Manifest held beside the aggregate | It holds file paths and hashes, and `Pack` holds no paths by design. | Putting it in `Pack` breaks the aggregate's path-free rule. ADR-0037 decision 5 already keeps graph documents beside it. |


## Key design decisions (detail in research.md)

- **D1 Presets are pack-level domain values.**
  - `Preset(id, description, activatedKinds, artifacts, missionTypes)` holds `PresetItem(id, mustHave)` lists.
  - The required set and "governing" are derived.
  - `Pack` holds the presets sorted by id, through a keyed-sort helper generalised from `sortedByPath`; two presets
    with one id breach an invariant.
  - The id == file-stem rule is one storage helper, `PresetFiles`, called by both the store and validate mode.
- **D2 The preset shape is sanitized** (S1–S8): `must_have` is `const: true` and empty lists are refused, so there
  is no comparator exception (C-005).
- **D3 `pack.md` uses #194's `markdown` content form** (`DM-01M60HM8`). Its work package is last and depends on PR-A.
- **D4 The descriptor is closed.**
  - Unknown and retired keys are refused by the schema on the descriptor's path (`PACK_UNREADABLE`).
  - `undeclaredKeys`, `PACK_UNKNOWN_KEY`, the write-side refusal and the dead `PackDescriptorWriter` are removed.
  - `contracts/charter/**` is unchanged.
- **D5 `WholePackStore` is the manifest's only owner.**
  - `LoadedPack` gains `Optional<PackManifestDso> manifest`, and a 4-argument constructor keeps the old signature.
  - The load reads the manifest. An unreadable manifest is a violation on the aggregate, so the write refuses.
    `FilesystemPackStore` and `DomainRepositoryService` never parse it.
  - The write stages everything else, then calls `PackManifests.recompute(dso, bytesAt(staging))` and writes the
    result last. A listed path that was not staged refuses the write.
  - `PackManifests` uses `Payload.sha256()` for both hashes, plus LF normalisation and a narrow string-only JCS.
- **D6 The gate models `preset` and `pack-manifest`.**
  - `DomainRoundTrip` writes presets in place.
  - It reads the manifest through the strategy, recomputes it over a pairing, and writes it. The pairing maps each
    constituent `(kind, id)` to the file the adapter wrote and each preset path to itself. A constituent of an
    unbound kind refuses, naming the path.
  - `MeaningComparator` has a declared `pack-manifest` rule that skips the derived fields.
  - The driver verifies the derived fields on each side by diffing them against `recompute`.
  - The real-corpus proof is the seam test's corrected built-in copy, run through `WholePackStore`.
- **D7 Fingerprint order.** The fingerprint appends presets in path order, then the manifest (and later `pack.md`,
  between them, with #194's form), after the templates. One ordered tail step. A pack without them keeps its digest
  list (NFR-002).
- **D8 Placeholder keys.** The known-failure loader admits exactly two placeholders,
  `spec-kitty/spec-kitty-open-packs#TBD-preset-shape` and `…#TBD-manifest-shape`. Both are removed when the issues
  are filed.
- **D9 Validate mode agrees with load.** A schema-breaking preset or manifest, or a preset whose id is not its file
  stem, is `ARTIFACT_SCHEMA_ERROR` (ADR-0036 decision 3). `preset` is left out of the inspector's `ArtifactKinds`.

### Non-append edits to shared files (C-002), each justified

| File | Edit | Why |
|---|---|---|
| `contracts/packs/file-kinds.yaml` | Retire the exclusions `**/presets/*.yaml` and `*/pack-manifest.yaml`. The new rule is narrower: `*/presets/*.yaml`. | A file may match only one rule. No pinned preset is nested. |
| `whole-pack/expected-red.yaml` | Delete `other:presets` and `other:pack-manifest.yaml` (and `other:pack.md` later) | The gate's own rule: remove an entry when its kind turns modelled |
| `WholePackRoundTripTest` `REQUIRED_KINDS`, `bound` | Add `preset` and `pack-manifest` | The census must account for the new kinds |
| `FileSystemPackInspectorTest` built-in validate pin | 0 findings → exactly the 3 new findings | D9 |
| `WholePackOpenPacksRoundTripTest` pinned refusals | 6 → 9; the corrected copy gains OP-1 and OP-2 | Research R1 |

## Acceptance checks (post-plan squad, binding for tasks)

1. **Gate.** `byKind` gives `preset` = known failure ×2 and `pack-manifest` = known failure ×1, while
   `pack-descriptor` stays green. `REQUIRED_KINDS` gains exactly `preset` and `pack-manifest`, and expected-red loses
   exactly `other:presets` and `other:pack-manifest.yaml`.
2. **Refusal texts match.** Each new known failure's `at` equals the seam test's violation text, `refused: <reason>`,
   character for character. The seam test pins 9 refusals.
3. **Corrected built-in copy.** The six sidecar paths are fixed, and a test helper writes the two presets in OP-1
   shape and the manifest in OP-2 shape. The manifest lists the modelled constituents only until #194 binds
   procedures, then all of them. The copy then:
   - loads with 0 violations;
   - writes whole and reloads;
   - keeps both presets equal by meaning;
   - shows no stale derived field on either side.

   The unmodified original, in the same test, gives exactly 9 violations.
4. **Preset fixture.**
   - `must_have` round-trips, and a writer that drops it goes red at the item.
   - Each refusal is paired with a clean control: an unknown key (`name`, `required_directives`,
     `mission_type_activations`), `must_have: false`, an empty list, id ≠ stem, a duplicate id, a `null` document.
   - `required()` and `governing()` are covered.
5. **Hash parity.**
   - The 274 pinned hashes, read through the raw YAML loader.
   - CRLF, lone-CR and mixed line-ending vectors.
   - JCS vectors, computed with Python `json.dumps(sort_keys=True, separators=(',',':'), ensure_ascii=False)`, cover
     escapes, non-ASCII and an empty `constituents`. Add the RFC 8785 example.
6. **Restamp.** On a fixture with commented artifacts, every written `content_hash` differs from its source and
   verifies, and `manifest_hash` verifies. A listed path that is missing refuses the write, and no target remains.
7. **Stale hashes.** A stale source `content_hash` is red at `constituents{<path>}.content_hash` while a fresh paired
   manifest is green. A writer that keeps stale hashes goes red.
8. **Descriptor closure.**
   - `surprise` and `accompanies_doctrine_pack` give `PACK_UNREADABLE` with the exact text, beside a clean pack.
   - No `undeclaredKeys` or `PACK_UNKNOWN_KEY` is left in Java.
   - `git diff origin/main -- contracts/charter` holds only the `DiagnosticCode` description and the contract
     CHANGELOG line approved on #195.
   - `VocabularyEqualityTest` is green.
9. **Fingerprint.** A one-byte edit to a preset or to the manifest changes the fingerprint. The literal digest lists
   of packs without them are unchanged.
10. **No stray files and no dropped slots.**
    - A pack without presets or a manifest writes no `presets/` directory and no manifest.
    - `toBuilder` and `with` keep the presets (tested directly).
    - After a registration the presets are byte-identical and the manifest is stale (the known gap).
11. **Validate mode.** Built-in validate gives exactly the 3 new `ARTIFACT_SCHEMA_ERROR` findings. A sanitized preset
    with id ≠ stem is refused in both validate and load.
12. **Placeholders.** Only the two placeholder slugs load, and the hand-off says to remove them.
13. **Living docs.**
    - ADR-0043, the amendment notes, the changelog, the glossary, and DIV-005 and DIV-006 closed.
    - A new divergence entry for validate mode against core.
    - The docs checks and `verifyAll` are green on Java 25.

## Implementation Concern Map

### IC-01 — Pack vocabulary and schemas

- **Purpose**: Declare `preset` and `pack-manifest`, write their closed schemas, and close the descriptor schema.
- **Relevant requirements**: FR-001, FR-006, FR-011 (schema half)
- **Affected surfaces**:
  - `contracts/packs/file-kinds.yaml` and its header.
  - The three schemas.
  - `contracts/packs/README.md`.
  - `PackFileKindTest`, `PackSchemasTest`, `SchemaKeywordCoverageTest`, `VocabularyEqualityTest`.
- **Sequencing/depends-on**: none
- **Risks**: #194 edits the same `exclusions` block.

### IC-02 — Preset domain

- **Purpose**: `Preset` and `PresetItem` with their invariants and derived required set; the `Pack` presets slot and
  the keyed-sort helper.
- **Relevant requirements**: FR-002, FR-003 (domain half)
- **Affected surfaces**: `packs.domain`, `HexagonalArchitectureTest`
- **Sequencing/depends-on**: none

### IC-03 — Descriptor closure

- **Purpose**: Remove the undeclared-keys path end to end and delete `PackDescriptorWriter`.
- **Relevant requirements**: FR-011, FR-012
- **Affected surfaces**:
  - The kernel `PackDescriptor`.
  - `PackDescriptorDso`, `PackDescriptorReader`, `PackPolicy`, `PackFindingCode`, `Diagnostic`, `WholePackStore`.
  - The listed tests, plus DIV-005 and DIV-006.
- **Sequencing/depends-on**: IC-01

### IC-04 — Preset storage and validate mode

- **Purpose**: Read, write and fingerprint presets, and give validate mode the same rules.
- **Relevant requirements**: FR-002–FR-004, FR-013, FR-018
- **Affected surfaces**:
  - `PresetDso`, `PresetYaml`, `PresetFiles`.
  - `SerializationStrategy` and `YamlSerializationStrategy` (`readValidated`, preset read and write).
  - `PackFileSet` (`Listing.presets`, the ordered tail of `digested`).
  - `FilesystemPackStore.PackRead`, `DomainRepositoryService` (`assembly`, `writePreset`), `WholePackStore`.
  - `FileSystemPackInspector` and its test.
- **Sequencing/depends-on**: IC-01, IC-02

### IC-05 — The manifest

- **Purpose**: The manifest's DSO, YAML format, recipes and `recompute`, with `WholePackStore` as its only owner.
- **Relevant requirements**: FR-006–FR-010, NFR-001, NFR-004
- **Affected surfaces**:
  - `PackManifestDso`, `PackManifestYaml`, `PackManifests`.
  - `WholePackStore` (`LoadedPack`, load, write), `PackFileSet.digested`, `SerializationStrategy`.
- **Sequencing/depends-on**: IC-01, IC-04

### IC-06 — Gate, known failures and seam test

- **Purpose**: Model the two kinds in the gate, verify the derived fields, and add the known failures, the controls
  and the corrected-copy proof.
- **Relevant requirements**: FR-014, FR-015, SC-001–SC-004
- **Affected surfaces**:
  - `DomainRoundTrip`, `MeaningComparator`, `WholePackRoundTripTest`, `WholePackExpectation`.
  - `WholePackOpenPacksRoundTripTest`.
  - `expected-red.yaml`, `known-failures.yaml`.
  - The test fixture packs.
- **Sequencing/depends-on**: IC-03, IC-04, IC-05

### IC-07 — Decision records and living docs

- **Purpose**: ADR-0043 and the amendment notes (0031, 0036, 0037, 0040), the changelog, the glossary, the index,
  the architecture pages and the conformance register.
- **Relevant requirements**: FR-016, FR-017, NFR-003
- **Sequencing/depends-on**: IC-06

### IC-08 — `pack.md` through #194's `markdown` form

- **Purpose**: Hold and write `pack.md` through #194's form, model `pack-document` in the gate, and cover the lossy
  controls.
- **Relevant requirements**: FR-005, FR-013 (`pack.md` half), SC-001b
- **Sequencing/depends-on**: #194 PR-A merged; IC-06
- **Risks**: If PR-A has not merged by hand-off, this concern stays open. `other:pack.md` stays expected-red, and the
  hand-off says so.
