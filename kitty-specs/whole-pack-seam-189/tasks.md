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

- [ ] Replace the five lists and ten invariants with a kind map and two generic invariants; keep the messages.
- [ ] Add `artifacts()`, `artifacts(kind)`, `artifacts(kind, type)`, `with(artifact)`, `toBuilder()`; typed views delegate.
- [ ] Replace the copy-every-kind blocks in the four registration services and the URN collection in `RelationshipRegistrationService`.
- [ ] Tests: `with` adds and replaces; a registration keeps every other kind.

## WP02

- [ ] `KindBinding`, five bindings (mappers moved from `DomainRepositoryService`), `KindBindings`.
- [ ] `YamlFormat` and five formats (moved from `YamlSerializationStrategy`); generic strategy methods.
- [ ] `PackFileSet` takes kinds from the registry; `LoadedKind` removed.
- [ ] `FilesystemPackStore` and `DomainRepositoryService` generic; tactic on the shared path; one `toDso` for the descriptor.
- [ ] Tests updated; `KindBindingsTest`.

## WP03

- [ ] `GraphDocument` sealed value; YAML read and write for both shapes; canonical edge view.
- [ ] Round-trip test over every shard and fragment in the packs.

## WP04

- [ ] `PackLayout`, `LoadedPack`, `WholePackStore.load` and `write`.
- [ ] Refusals: existing target, violations; failure leaves nothing behind.
- [ ] `WholePackRoundTripTest` over fixtures; open-packs variant in `openPacksRoundTrip`.

## WP05

- [ ] ADR index row; `docs/architecture` pages that describe the aggregate and storage; changelog entry.
