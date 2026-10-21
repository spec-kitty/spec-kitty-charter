# Data Model — Application-edge read path on the Pack model

The canonical model already exists from mission `01M5EET9`; this mission makes it the **only** model and adds the read use case + the upsert write contract. No new persisted schema.

## Domain (pure — `packs.domain`, `kernel.domain`)

### Pack (aggregate root)
- Identity: `PackIdentifier` (value object; non-blank text).
- Owns: a set of `Tactic` child entities; its own `violations()` (the **single** owner of read-time violations — the duplicate `PackInspectionQuery.partialReads` projection is removed).
- Invariants (enforced through the root constructor and re-checked on the write path): every tactic belongs to the pack; no duplicate tactic identities.
- Construction: fluent chainable builder (public); package-private full constructor for mapping/serialization only (C-005).

### Tactic (child entity)
- Identity: `TacticIdentifier` (value object; non-blank text). Identity equality (not value equality).
- Fields (domain): schema version, name, ordered steps, optional purpose/notes, failure modes, applies-to languages, references. Guarded via `kernel.validation` (`guard()` fail-fast).

### Value objects
- `PackIdentifier`, `TacticIdentifier` — single-field records, value equality, blank rejected.
- `TacticStep`, `TacticReference`, `PackPaths` — value-equal records.

### Repository ports (domain-owned; signatures name only domain/kernel/JDK/jspecify types)
- `PackRepository` — load a pack by identity (aggregate), and **`tacticsOf(PackIdentifier)` — all tactics of a pack** (as realized: the collection-scoped read lives on the aggregate-root port, where a sensible default can delegate to the aggregate load; the storage edge overrides it point-wise).
- `TacticRepository`:
  - **read**: one tactic by `(PackIdentifier, TacticIdentifier)`.
  - **save** (upsert): `save(tactic)` — **creates** when the identity is unknown, **replaces** when known. Write surface stays HTTP-unexposed (C-006) but the contract is pinned by a port-level test covering both arms.
- Single owner for violations: `Pack.violations()` is canonical; no second read-violations query port.

## Application edge (`packs.application` — now framework-permitted)

### TacticReadService (`@Service`)
- Orchestrates `PackRepository` / `TacticRepository` to answer:
  - "all tactics of pack X" → list of domain `Tactic` (or a client-facing refusal when the pack is absent/unreadable).
  - "tactic Y of pack X" → one `Tactic` or a not-found refusal.
- Returns domain/result types; never transport types. Invoked by the thin API controller (no business logic in the controller).
- Point-read efficiency (NFR-001): resolve the pack by a descriptor-only read; do not parse every tactic of every pack to resolve identity.

## Persistence edge (`packs.infra.storage`)

- `DomainRepositoryService` (`@Service`, **scan-driven** via `StorageConfiguration` `@ComponentScan`; no explicit `@Bean`) realizes the ports with hand-written DSO↔entity mapping.
- `FilesystemPackStore`: atomic, pack-root-contained writes (temp → `ATOMIC_MOVE`). **New create arm** for upsert: a never-seen tactic id writes a new file atomically in the pack dir, with the same lexical + real-path containment checks as the replace arm (preserve the fail-closed behaviour the correctness lens verified).
- `SerializationStrategy` — **injected as a bean** (not hard-newed in the service ctor); one `YamlSerializationStrategy`. Keeps the pluggability real at the wiring seam.
- DSOs (`TacticDso`, `PackDescriptorDso`): value-equal records, contained to `infra.storage`. Round-trip fidelity asserted on the DSO's value equality (preserved).

## Refusal model (read path)
- Absent/unreadable pack or absent tactic → a **client-facing not-found (4xx-class)** outcome, not `INTERNAL_ERROR`. `InfoController`'s refusal handling is reused; only the inbound adapter names `INTERNAL_ERROR` (gate-enforced).

## Removed (one model, no coexistence)
`CharterPack`, `CharterOffering`, `CharterPackRepository`, `FileSystemCharterPackRepository`, `ConsistentLoad`, `PackListingQuery`, `ActionContextQuery` — and their tests + ArchUnit control fixtures.
