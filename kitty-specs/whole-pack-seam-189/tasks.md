# Tasks: Whole-pack load and write through the Pack aggregate

Hand-authored (no CLI in the session). One lane, in order.

| WP | Title | Requirements | Depends on |
|----|-------|--------------|------------|
| WP01 | Kind-keyed `Pack` aggregate, `Pack.with`, registration services on it | FR-001, FR-002, FR-007 | — |
| WP02 | Storage kind registry: bindings, YAML formats, strategy, store, file set, repository service | FR-003, FR-004, FR-007, NFR-001 | WP01 |
| WP03 | DRG documents in the aggregate: both shapes, every field, reader and writer | FR-005 | WP01 |
| WP04 | `WholePackStore` load and write with layout, staging and refusals; round-trip tests | FR-005, FR-006, NFR-002 | WP02, WP03 |
| WP05 | Docs: ADR-0036 index row, architecture pages, changelog | — | WP04 |

## WP01

- [x] Replace the five lists and ten invariants with a kind map and two generic invariants; keep the messages.
- [x] Add `artifacts()`, `artifacts(kind)`, `artifacts(kind, type)`, `with(artifact)`, `toBuilder()`; typed views delegate.
- [x] Replace the copy-every-kind blocks in the four registration services and the URN collection in `RelationshipRegistrationService`.
- [x] Tests: `with` adds and replaces; a registration keeps every other kind.

## WP02

- [x] `KindBinding`, five bindings (mappers moved from `DomainRepositoryService`), `KindBindings`.
- [x] `YamlFormat` and five formats (moved from `YamlSerializationStrategy`); generic strategy methods.
- [x] `PackFileSet` takes kinds from the registry; `LoadedKind` removed.
- [x] `FilesystemPackStore` and `DomainRepositoryService` generic; tactic on the shared path; one `toDso` for the descriptor.
- [x] Tests updated; `KindBindingsTest`.

## WP03

- [x] `GraphDocument` sealed value; YAML read and write for both shapes; canonical edge view.
- [x] Round-trip test over every shard and fragment in the packs.

## WP04

- [x] `PackLayout`, `LoadedPack`, `WholePackStore.load` and `write`.
- [x] Refusals: existing target, violations; failure leaves nothing behind.
- [x] `WholePackRoundTripTest` over fixtures; open-packs variant in `openPacksRoundTrip`.

## WP05

- [x] ADR index row; `docs/architecture` pages that describe the aggregate and storage; changelog entry.
