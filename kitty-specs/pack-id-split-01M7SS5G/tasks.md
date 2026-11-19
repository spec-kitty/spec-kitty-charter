# Tasks: Pack id split

Mission: `pack-id-split-01M7SS5G` · Planning base and merge target: `feat/583-pack-id-split`

One sequential lane: each WP leaves its module's checks green, and WP06 leaves `verifyAll` green. The split is one
type change, so the WPs are ordered by the build graph: kernel, storage edge, locator, application, consumers, guard.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | IdentifierGrammar.ulid() constraint and its test | WP01 | |
| T002 | PackUlid record with guard, GRAMMAR_DETAIL and isUlid | WP01 | |
| T003 | PackUlidTest: valid, 25/27 chars, I/L/O/U, lower case, blank | WP01 | |
| T004 | PackDirectory record (one segment) and PackDirectory.of(PackIdentifier) | WP02 | |
| T005 | FilesystemPackStore addresses by PackDirectory | WP02 | |
| T006 | PackCommitStore staging and aside names as PackDirectory | WP02 | |
| T007 | WholePackStore, CommitStorePackWriteStore, DomainRepositoryService, DomainRoundTrip directory sites | WP02 | |
| T008 | StoreRead and ArtifactFiles take the registered id from the caller (no ULID assembly) | WP02 | |
| T009 | Storage tests and test-resource pack.yaml fixtures: PackDirectory, real ULIDs | WP02 | |
| T010 | PackLocator.locate matches the configured name only | WP03 | |
| T011 | StorageCodes.PACK_ID_INVALID | WP03 | |
| T012 | StorageRootLocations refuses a non-pack-id entry with pack_id_invalid | WP03 | |
| T013 | Tests: seeded a--b beside a valid pack; locate by ULID is not found; positive controls | WP03 | |
| T014 | RefusalPack(PackUlid, PackIdentifier, String) and its callers; ClosureView.refusalPack fallback finds the real ULID | WP04 | |
| T015 | MergedClosure.Winner.descriptorPackId and LayerMerge as PackUlid | WP04 | |
| T016 | EdgeIndex Place/byPack/idOf by PackUlid; EdgeNode carries the registered id; holdingPack without the orElse tolerance | WP04 | |
| T017 | ClosureView, ClosureQueries, ClosureRoleFacts, InForceProfiles, ProfileEnhancers, PackMutabilityPolicy, RegistrationImpact, GraphWalk | WP04 | |
| T018 | ProfileEnhancementGateway Dropped/Enhancing pack as PackIdentifier; ClosureView:886, ProfileEnhancementRule:95 | WP04 | |
| T019 | InboundReferences: StagedTree carries the upload id; new-pack member named by it | WP04 | |
| T020 | Domain and application tests on the canonical shape (real ULIDs, PackUlid) | WP04 | |
| T021 | ClosureViewSession, ClosureSnapshot, PacksAvailability, ClosureRoots on PackUlid/PackIdentifier | WP05 | |
| T022 | RefusalHandler, ReferenceReads, EffectiveBodies on the typed RefusalPack/HoldingPack | WP05 | |
| T023 | PackCheck.validate takes the registered id; directory name default (D2b) | WP05 | |
| T024 | PackRef wraps PackIdentifier; PackPin.packId contract grammar in both schemas; Breaking fragment | WP05 | |
| T025 | Tests in these modules on the canonical shape | WP05 | |
| T026 | PackIdentifier constructor guards slug(63); Javadoc; PackIdentifierTest | WP06 | |
| T027 | Remaining test trees use the type that fits (profiles, missions, routing, edges, deployable) | WP06 | |
| T028 | PackId.yaml description names the registry (D8) | WP06 | |
| T029 | ADR-0075 decision 2 status, glossary and the work breakdown | WP06 | |
| T030 | NFR-003 grep, verifyAll and the whole-pack round trip | WP06 | |

## WP01 - Kernel: PackUlid and the ULID grammar

Prompt: `tasks/WP01-kernel-pack-ulid.md` · Priority P1 · Dependencies: none.

T001 IdentifierGrammar.ulid() constraint and its test (WP01)
T002 PackUlid record with guard, GRAMMAR_DETAIL and isUlid (WP01)
T003 PackUlidTest: valid, 25/27 chars, I/L/O/U, lower case, blank (WP01)

## WP02 - Storage edge: PackDirectory names directories

Prompt: `tasks/WP02-storage-pack-directory.md` · Priority P1 · Dependencies: WP01.

T004 PackDirectory record (one segment) and PackDirectory.of(PackIdentifier) (WP02)
T005 FilesystemPackStore addresses by PackDirectory (WP02)
T006 PackCommitStore staging and aside names as PackDirectory (WP02)
T007 WholePackStore, CommitStorePackWriteStore, DomainRepositoryService, DomainRoundTrip directory sites (WP02)
T008 StoreRead and ArtifactFiles take the registered id from the caller (no ULID assembly) (WP02)
T009 Storage tests and test-resource pack.yaml fixtures: PackDirectory, real ULIDs (WP02)

## WP03 - One locator per identity; refuse a non-pack-id directory on read

Prompt: `tasks/WP03-locator-and-refuse-on-read.md` · Priority P1 · Dependencies: WP02.

T010 PackLocator.locate matches the configured name only (WP03)
T011 StorageCodes.PACK_ID_INVALID (WP03)
T012 StorageRootLocations refuses a non-pack-id entry with pack_id_invalid (WP03)
T013 Tests: seeded a--b beside a valid pack; locate by ULID is not found; positive controls (WP03)

## WP04 - Closure and edge indexes key by PackUlid; typed refusal pack

Prompt: `tasks/WP04-indexes-key-by-pack-ulid.md` · Priority P1 · Dependencies: WP03.

T014 RefusalPack(PackUlid, PackIdentifier, String) and its callers; ClosureView.refusalPack fallback finds the real ULID (WP04)
T015 MergedClosure.Winner.descriptorPackId and LayerMerge as PackUlid (WP04)
T016 EdgeIndex Place/byPack/idOf by PackUlid; EdgeNode carries the registered id; holdingPack without the orElse tolerance (WP04)
T017 ClosureView, ClosureQueries, ClosureRoleFacts, InForceProfiles, ProfileEnhancers, PackMutabilityPolicy, RegistrationImpact, GraphWalk (WP04)
T018 ProfileEnhancementGateway Dropped/Enhancing pack as PackIdentifier; ClosureView:886, ProfileEnhancementRule:95 (WP04)
T019 InboundReferences: StagedTree carries the upload id; new-pack member named by it (WP04)
T020 Domain and application tests on the canonical shape (real ULIDs, PackUlid) (WP04)

## WP05 - Consumers: activations, artifacts-edge, web transport, pack-check; PackPin grammar

Prompt: `tasks/WP05-consumers-and-pack-pin.md` · Priority P1 · Dependencies: WP04.

T021 ClosureViewSession, ClosureSnapshot, PacksAvailability, ClosureRoots on PackUlid/PackIdentifier (WP05)
T022 RefusalHandler, ReferenceReads, EffectiveBodies on the typed RefusalPack/HoldingPack (WP05)
T023 PackCheck.validate takes the registered id; directory name default (D2b) (WP05)
T024 PackRef wraps PackIdentifier; PackPin.packId contract grammar in both schemas; Breaking fragment (WP05)
T025 Tests in these modules on the canonical shape (WP05)

## WP06 - The slug(63) guard, the remaining fixtures, and the docs

Prompt: `tasks/WP06-guard-fixtures-and-docs.md` · Priority P1 · Dependencies: WP05.

T026 PackIdentifier constructor guards slug(63); Javadoc; PackIdentifierTest (WP06)
T027 Remaining test trees use the type that fits (profiles, missions, routing, edges, deployable) (WP06)
T028 PackId.yaml description names the registry (D8) (WP06)
T029 ADR-0075 decision 2 status, glossary and the work breakdown (WP06)
T030 NFR-003 grep, verifyAll and the whole-pack round trip (WP06)
