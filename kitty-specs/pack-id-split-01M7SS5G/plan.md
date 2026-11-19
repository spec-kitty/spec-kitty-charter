# Implementation Plan: Pack id split: the registered pack id and the descriptor ULID

**Branch**: `kitty/mission-pack-id-split-01M7SS5G` → `feat/583-pack-id-split` → `main` | **Date**: 2026-11-19 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/pack-id-split-01M7SS5G/spec.md`. The rulings are in `research/decisions.md`
(D1 to D9, all as recommended), and the site census is in `research/census.md`.

## Summary

`PackIdentifier` holds the registered pack id only, guarded as `slug(63)`. The descriptor's `pack_id` gets a kernel
`PackUlid`. The edge index, the closure view, the closure session, the layer merge's winners and `RefusalPack` key by it.
The store addresses directories through a storage-local `PackDirectory`, so `.pack-commit-*` staging names and
`.pack-old-*` aside names never pose as pack ids. The locator takes the registered id only. A stored directory whose name
is not a pack id is refused by `StorageRootLocations` with the storage code `pack_id_invalid`. Artifacts carry the
registered id on every read path. The activation pin follows the pack id grammar (`### Breaking`).

## Technical Context

**Language/Version**: Java 25 (Gradle, `./gradlew verifyAll`; strict SHA-256 dependency verification)
**Primary Dependencies**: Spring Boot web transport, the kernel rule vocabulary (`kernel.validation`), OpenAPI generator (contract-first)
**Storage**: Files: the pack domain on disk (`FilesystemPackStore`, `PackCommitStore`, `StorageRootLocations`)
**Testing**: JUnit 5 + AssertJ per module, ArchUnit gate, contract scenarios, whole-pack round trip (`:pack-validation:wholePackRoundTrip`)
**Target Platform**: Linux server (JVM)
**Project Type**: single (Gradle multi-module under `service/`)
**Performance Goals**: none new. The `FilesystemPackStore` whole-read counter tests must not move.
**Constraints**: no wire change except D7 (PackPin) and FR-008 (new-pack pointer); no ArchUnit rule widened; no new dependency
**Scale/Scope**: 67 production constructions, ~420 files referencing `PackIdentifier`, ~550 test constructions

## Charter Check

- **Canonical model (2026-11-10)**: one identity per type. No dual-lookup locator and no legacy constructor. PASS by
  design (D3).
- **Tests carry only the canonical shape**: fake ULIDs (`01PACK`, `02OTHER`, ...) become real ULIDs. Invalid literals
  stay only in error-handling tests (`a--b`, `my pack`, `../x`). PASS (FR-010).
- **Contract versioning (ADR-0022)**: the narrowing of PackPin.packId is on 0.x, with a `### Breaking` fragment. PASS.
- **Adversarial review squad**: this is a domain change. An independent adversarial review is requested from the
  orchestrator at for_review and recorded as a PR comment.
- **Supply chain**: no dependency is added, upgraded or removed. DIRECTIVE_051 is not engaged.
- **Issue rules**: `Closes #583`. Tick #549's checklist row. Findings below MEDIUM are folded, not filed.

## Project Structure

### Documentation (this mission)

```
kitty-specs/pack-id-split-01M7SS5G/
├── spec.md  plan.md  research.md  data-model.md  quickstart.md  occurrence_map.yaml
├── contracts/pack-pin.md        # the contract delta (D7, D8)
├── research/census.md  research/decisions.md
└── tasks/
```

### Source Code (repository root)

```
service/kernel/            PackIdentifier (guard), PackUlid (new), RefusalPack (typed), CharterArtifact (doc)
service/packs/             domain (MergedClosure, LayerMerge), application (EdgeIndex, ClosureView, InboundReferences,
                           ProfileEnhancement*), infra/storage (PackDirectory new, FilesystemPackStore, PackCommitStore,
                           WholePackStore, StoreRead, ArtifactFiles, PackLocator, StorageRootLocations, rules/StorageCodes)
service/activations/       ClosureViewSession, PacksAvailability, ClosureRoots, PackRef
service/pack-validation/   PackCheck caller-passed id (D2b)
service/*-edge/            controllers and activation adapters (unchanged calls; fixtures)
contracts/charter/         parameters/PackId.yaml (wording), schemas/PackPin.yaml; contracts/offering ActivationConfig
```

**Structure Decision**: the existing multi-module layout. `PackUlid` sits beside `PackIdentifier` in `kernel.domain`, and
`PackDirectory` is package-private in `packs.infra.storage`.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|------|------------|--------------------------------------|
| `PackDirectory` (storage-local) | staging and aside names must travel through the same store reads as live packs | a plain `Path` loses the one-segment-under-root check `directoryOf` makes today |
| Typed `RefusalPack` | `ClosureView.refusalPack`'s fallback writes the registered id into `descriptorPackId`, which mixes the identities | keeping Strings leaves the mix invisible to the compiler |

## Implementation Concern Map

### IC-01 — The kernel types

- **Purpose**: `PackUlid` (the ULID grammar, Crockford base32, 26 characters) beside `PackIdentifier`. `RefusalPack` takes
  `(PackUlid, PackIdentifier, String name)`.
- **Relevant requirements**: FR-002, FR-007
- **Affected surfaces**: `kernel/domain/PackUlid.java` (new), `RefusalPack.java`, `IdentifierGrammar` (a `ulid()` constraint), kernel tests
- **Sequencing/depends-on**: none
- **Risks**: `RefusalPack` callers (15) must each find the real ULID. Where none exists (a pack not yet read), the
  refusal must carry what the caller holds. The plan is to look it up, never to substitute.

### IC-02 — The storage edge names directories

- **Purpose**: `FilesystemPackStore`, `PackCommitStore`, `WholePackStore`, `StoreRead`, `ArtifactFiles`,
  `CommitStorePackWriteStore`, `DomainRoundTrip` and `DomainRepositoryService` address a directory by `PackDirectory`.
  Staging and aside names are built as one.
- **Relevant requirements**: FR-006
- **Affected surfaces**: `infra/storage/*`, and its tests in `packs`
- **Sequencing/depends-on**: none (it compiles alone with the blank-only guard)
- **Risks**: #517 (PR #609) edits `PackCommitStore` and `PackRegistrationService`. Rebase, and tell its session about the
  signatures.

### IC-03 — One locator per identity; refuse on read

- **Purpose**: `PackLocator.locate` matches the configured name only. `StorageRootLocations` refuses an entry whose name
  is not a pack id with `StorageCodes.PACK_ID_INVALID` (`pack_id_invalid`), through its existing `refuse(entry, reason)`.
  `EdgeIndex.holdingPack` drops its `orElse(referringPack)` tolerance.
- **Relevant requirements**: FR-005, FR-009
- **Affected surfaces**: `PackLocator`, `StorageRootLocations`, `rules/StorageCodes`, `StorageCodesTest`
- **Sequencing/depends-on**: IC-02
- **Risks**: a caller that passed a ULID to `locate` (census item 4) must move to the registered id. Every such caller is
  listed in the occurrence map.

### IC-04 — Indexes key by `PackUlid`; artifacts carry the registered id

- **Purpose**: `EdgeIndex` (`Place`, `EdgeNode`, `byPack`, `idOf`), `ClosureView`, `ClosureViewSession`,
  `MergedClosure.Winner.descriptorPackId` and `LayerMerge` key by `PackUlid`. `StoreRead.of`, `ArtifactFiles` and
  `PackAssembly` assemble under a registered id their caller passes (registration: `upload.packId()`; validate mode:
  caller-passed, falling back to the directory name, D2b). `InboundReferences` names a new staged pack by its upload id.
  `ProfileEnhancementGateway.Dropped/Enhancing.pack` become `PackIdentifier`.
- **Relevant requirements**: FR-003, FR-004, FR-007, FR-008
- **Affected surfaces**: `packs.domain`, `packs.application`, `activations.infra.packs`, `pack-validation` (`PackCheck`)
- **Sequencing/depends-on**: IC-01, IC-02
- **Risks**: `EdgeNode` implements `CharterArtifact`, whose `packId()` must be the registered id. The node gets the
  registered id from `byPack`. The golden snapshots and contract scenarios are the guard against answer drift.

### IC-05 — The guard and the fixtures

- **Purpose**: `PackIdentifier`'s constructor guards `slug(63)`. Every fixture uses the type that fits. Fake ULIDs become
  real ULIDs. The `ConstraintBreachException` from a non-pack-id is the error path tests assert.
- **Relevant requirements**: FR-001, FR-010, NFR-003
- **Affected surfaces**: `PackIdentifier.java`, `PackIdentifierTest`, test sources in every module (occurrence map)
- **Sequencing/depends-on**: IC-01 to IC-04
- **Risks**: test-pack YAML under `src/test/resources` may carry fake `pack_id`s. These are fixed to real ULIDs, as the
  schema requires.

### IC-06 — Contract and docs

- **Purpose**: PackPin.packId refs the pack id grammar (offering `ActivationConfig.schema.json` `$defs/PackPin`, charter
  `PackPin.yaml`). `PackRef` wraps `PackIdentifier`. `PackId.yaml`'s description names the registry. A `### Breaking`
  CHANGELOG fragment. The glossary and ADR-0075 decision 2 status are updated.
- **Relevant requirements**: FR-011, FR-012, NFR-002
- **Affected surfaces**: `contracts/charter/**`, `contracts/offering/**`, `service/activations/domain/PackRef.java`, CHANGELOG fragments, `docs/`
- **Sequencing/depends-on**: IC-05
- **Risks**: the generated clients and contract validation (`verifyAll`) must pass. The offering schema and the charter
  YAML must stay in parity.
