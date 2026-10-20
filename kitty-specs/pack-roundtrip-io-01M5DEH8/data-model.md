# Data Model: Pack parse/write-back round-trip

Domain entities (in `charter-domain`) and the port. All are plain Java; no infra type appears. Names are indicative; equality rules are binding (NFR-002).

## Value objects & entities

### TacticReference (new, value object, carried opaquely)
| Field | Type | Req | Notes |
|-------|------|-----|-------|
| name | String | yes | verbatim |
| type | String | yes | artifact-kind token, stored as a string — NOT resolved into a domain relationship |
| id | String | yes | verbatim |
| when | String | yes | verbatim |

Equality: structural. Never deduplicated or reordered.

### TacticStep (new, value object)
| Field | Type | Req | Notes |
|-------|------|-----|-------|
| title | String | yes | |
| description | Optional<String> | no | explicit-null and absent collapse to empty |
| examples | List<String> | no | order-sensitive; empty when absent |
| references | List<TacticReference> | no | order- and duplicate-sensitive; empty when absent |

### Tactic (new, entity)
| Field | Type | Req | Notes |
|-------|------|-----|-------|
| id | String | yes | identity for replace-by-id |
| schemaVersion | String | yes | |
| name | String | yes | |
| steps | List<TacticStep> | yes | non-empty (schema `minItems: 1`); order-sensitive |
| purpose | Optional<String> | no | null==absent |
| notes | Optional<String> | no | null==absent |
| failureModes | List<String> | no | order-sensitive; empty when absent |
| appliesToLanguages | List<String> | no | order-sensitive; empty when absent |
| references | List<TacticReference> | no | order- and duplicate-sensitive |

No `sourcePath` / storage coordinate on the model — the id→path mapping is resolved in the adapter (locate-by-id), so equality stays purely semantic and two identical tactics read from different files compare equal (D3, revised).

### PackDescriptor (existing, extended)
Extended with two optional path-string fields the schema declares but the current record drops: `activeCharter: Optional<String>` (`active_charter`) and `packDrg: Optional<String>` (`pack_drg`). Both are pack-relative path strings (not graph structures), so modelling them is in scope and does not touch the deferred graph work. The extension ripples to `PackDescriptorReader` and existing constructor callers. `undeclaredKeys` (names without values) remains **excluded** from the round-trip equality contract (D4); the proving fixture includes a control descriptor carrying `active_charter`/`pack_drg`.

### PackSnapshot (new aggregate — working name)
| Field | Type | Notes |
|-------|------|-------|
| descriptor | PackDescriptor | the pack's `pack.yaml`, as a domain model |
| tactics | List<Tactic> | order-sensitive; empty (not null) for a descriptor-only pack |
| violations | List<Violation> | files that failed to parse/validate (partial-tolerant fetch, FR-008) |

Distinct from the existing `CharterPack` (which requires a non-null `DoctrineGraph`). No graph field.

### Violation (new or mirror of existing PackContents.SchemaViolation/Unreadable)
Reported, not thrown: `{ file, kind, reason }` (mirror the existing `FileRead.Unreadable`/`SchemaViolation` shape where practical).

## Port (new, domain-owned)

```
interface PackElementRepository {
    // location is a DOMAIN value (PackRoot or a new domain PackLocator) — NOT the infra PackLocation.
    // Partial-tolerant: returns valid descriptor + tactics and reports violations; never refuses the whole pack on one bad file.
    PackSnapshot fetch(PackRoot location);

    // Replace-existing by id; the adapter resolves id->path (locate-by-id), atomic + pack-root-contained (NFR-004).
    // Element is a domain model (Tactic or descriptor); it carries no storage coordinate.
    void write(PackRoot location, <DomainElement> element);
}
```

- The location parameter MUST be a **domain-resident** value. ⚠️ Do **not** use the existing `PackLocation` — it lives in `adapter-out-yaml` (infra package `ai.speckitty.charter.yaml`), so using it in a domain port signature would itself violate NFR-001. Use the existing domain `PackRoot` (`record PackRoot(String name, Optional<PackLevel>, String locator)`) or mint a thin domain locator (e.g. `PackLocator(String locator)`) if `PackRoot`'s offering-config semantics (`name`, `configuredLevel`) are unwanted. `locator` stays opaque to the domain; the adapter resolves it.
- Every parameter and return type is a domain model — no `Path`, stream, or infra DTO (NFR-001, US3 scenario 2).
- Implemented by `FilesystemPackElementRepository` in `adapter-out-yaml`.

## Invariants

- **Round-trip (NFR-002)**: `fetch(write(fetch(x))) == fetch(x)` over modelled fields, equality per D4.
- **Collections** are order- and duplicate-sensitive value lists, never sets.
- **null==absent** for optional scalar fields, **and absent-list==empty-list** — one single-sourced collapse rule (see research D4), load-bearing for FR-006/FR-009/NFR-002.
- **Scalar typing** is pinned: fields that may parse as non-string (e.g. `schema_version: 1.0` → float, `x: null`) are stringified on read and re-emitted stably; fixtures quote such scalars.
- **Layering**: no infra type/annotation in `charter-domain`; port signatures domain-only (ArchUnit + known-bad control).
- **Write safety**: atomic (temp→rename); refuses paths escaping the pack root.

## State transitions

None — the vertical is read/write of static pack content; no lifecycle or status machine.
