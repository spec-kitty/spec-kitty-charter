# Quickstart: Pack parse/write-back round-trip

How to exercise and verify the vertical once implemented.

## The round-trip, in one sentence

Fetch a pack's descriptor + tactics into domain models through `PackElementRepository`, write them back through the same port, re-fetch, and the re-fetched model equals the original.

## Proving fixture

`service/adapter-out-yaml/src/test/resources/roundtrip-fixtures/<pack>/`:
- `pack.yaml` — a descriptor with **no undeclared keys**, but **carrying `active_charter` and `pack_drg`** (the control that proves D8's modelling survives write-back).
- one tactic with **some optionals present and some absent** and a `references` list containing a **repeated id in a fixed order** (the FR-006 shared fixture), including at least one `step` with nested `references` (for the fetch-fidelity oracle).
- one tactic that omits every optional field.
- (for the partial-tolerant check) one deliberately schema-invalid `*.tactic.yaml` — kept out of the pure round-trip assertion set (the round-trip test filters to valid elements).
- (a separate descriptor-only fixture dir) `pack.yaml` with no tactic files.

Quote scalars that would otherwise coerce (`schema_version: '1.0'`). Model a tactic on the existing shape, e.g. `.kittify/charter-packs/tactic/how-we-apply-directive-001.tactic.yaml` (required `id`, `schema_version`, `name`, `steps[].title`).

## Verifications

```bash
# Targeted: the new round-trip + layering battery
./gradlew :adapter-out-yaml:test --tests '*PackElementRoundTripTest'
./gradlew :charter-service:test --tests '*HexagonalArchitectureTest'

# Full gate (what CI runs)
./gradlew verifyAll
```

## What each test proves (maps to spec)

- **Fetch fidelity** (FR-002, US1): fetched step/reference fields (incl. `when`, nested step references, duplicate order) equal the fixture's **literal** values — guards against the reader silently dropping a field (which a `fetch==refetch` compare is blind to).
- **Semantic round-trip** (FR-005, SC-001): `fetch→write→fetch` equal; a same-fixture control mutates one field, **writes, and refetches**, asserting the refetch differs from the unmutated baseline (routed through the port, never an in-memory `equals()`).
- **Write is load-bearing** (FR-007): mutate→write→refetch returns the mutation (a no-op write + mis-wired re-read cannot pass).
- **Descriptor completeness** (D8): the control descriptor's `active_charter`/`pack_drg` survive write-back (they would be deleted by a from-scratch writer if unmodelled).
- **Presence/absence + order + duplicates** (FR-006): one shared fixture; present stays present, absent stays absent, references keep order and the duplicate.
- **Collection + descriptor-only** (FR-009): tactics come back with same members/order; descriptor-only pack yields an empty (not null) collection.
- **Partial-tolerant fetch** (FR-008): the invalid file is reported as a violation while valid descriptor/tactics still return; a valid-file positive control on the same fixture proves the probe can see a good file.
- **Layering** (NFR-001, SC-002): ArchUnit finds zero infra references in `charter-domain` and domain-only port signatures; a **committed known-bad sample** class, pointed at by the same production rule, asserts a violation is reported (not a manual toggle). Note: SC-002's "100% through the port" is near-vacuous for this vertical (no domain caller reaches storage yet) — this control is the real guard.
- **Write safety** (NFR-004): a **failure-injection seam** (fail between temp-write and rename) proves no partial file and no stray temp is left; a path escaping the pack root is refused, paired with an in-root path accepted on the same fixture.

## Delineation

This is a **domain-model (semantic)** round-trip. It is distinct from the existing byte/data-level `OpenPacksRoundTripTest` (graph-shard scalar identity); FR-005 is satisfied only by the new semantic test.
