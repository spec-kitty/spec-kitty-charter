# Data model — Pack repositories and validation module

## Slice carve (ADR-0006) — proposed, operator/squad reviewable

With only the pack concern materialized, `kernel` holds shared **data vocabulary** (read by future slices); `packs` holds the pack-management **behaviour** + the new aggregate/ports. Borderline classes are flagged `[review]`.

### `kernel.domain` (shared Pack/DRG value vocabulary) — 13 firm-kernel classes (architect-verified clean of packs references)
`DoctrineGraph`, `DrgNode`, `DrgEdge`, `NodeKind`, `Relation` (DRG graph value types); `PackDescriptor`, `PackLevel` (descriptor vocab); `Urn`, `Fingerprint`, `Pep440` (generic value primitives); `RefusalCode`, `CharterRefusal` (shared refusal vocab); `ArtifactKind`.

### `kernel.validation` (pure guard library — nested in the kernel module per DM 01M5F6RC) — see its own section below.

### `packs.domain` (pack-management behaviour + new aggregate)
Existing: `CharterPack`, `CharterPackRepository`, `PackContents`, `PackSnapshot`, `PackSources`, `PackRoot`, `PackValidation`, `PackInspector`, `PackPolicy`, `PackVerdict`, `PackFinding`, `PackFindingCode`, **`CharterOffering`** (references `CharterPack` → mandatorily packs, not kernel), **`AssetMimeTable`** (referenced only by `PackPolicy`), **`Diagnostic`** (referenced only by `PackFinding`/`CharterPack`/`PackFindingCode`), **`PackElementViolation`** (KEPT — it is the `PackInspectionQuery` return type).
Superseded (retired as a deliberate IC-04/05 step, NOT moved in IC-02): `PackElementRepository`, `PackLocator` (replaced by the new ports + identifier VOs). Transitional note: the old graph-coupled `CharterPack` and the new lightweight `Pack` aggregate both live in `packs.domain` for now (behaviour-preserving); intended convergence recorded for a later mission.
**New:**
- **`Pack`** «AggregateRoot» — identity `PackIdentifier`; composes `PackDescriptor` (kernel VO) + `Tactic`s + read violations. Final class; fluent builder; package-private all-args ctor.
- **`Tactic`** «Entity» (child) — identity `TacticIdentifier`; identity-based `equals`/`hashCode`; holds its `PackIdentifier`; owns `List<TacticStep>` + `List<TacticReference>` (order + duplicates significant); fields: schemaVersion, name, purpose?, notes?, failureModes[], appliesToLanguages[]. Final class; fluent builder; package-private all-args ctor.
- **`TacticStep`**, **`TacticReference`**, **`PackPaths`** «ValueObject» — records (value equality). `PackPaths(activeCharter?, packDrg?)` groups the two descriptor path strings.
- **`PackIdentifier`**, **`TacticIdentifier`** «ValueObject» — records wrapping the raw id (future-proof).
- **Ports** (framework-free interfaces, `packs.domain`):
  - `TacticRepository { void save(Tactic); Optional<Tactic> load(PackIdentifier, TacticIdentifier); }`
  - `PackDescriptorRepository { PackDescriptor get(PackIdentifier); void save(PackIdentifier, PackDescriptor); }`
  - aggregating `PackRepository { Pack load(PackIdentifier); }`
  - `PackInspectionQuery { List<PackElementViolation> partialReads(PackIdentifier); }` (violations off the `load` path; reuses the kept `PackElementViolation`)

### `kernel.validation` (pure guard library, `ai.speckitty.charter.kernel.validation`; java + jspecify only; gated by a validation-purity ArchUnit rule)
`Constraint` (record: predicate + description, value-correct `equals`), `Constraints`/`Checks` (factories/guards), `ModularRuleset { void guard(T); Evaluation check(T); }`, `Evaluation` (PASS/FAIL + reasons), `Reason`, `Rationale`, `Constrained<T>` (monadic result). Omitted: `Evidence`, dynamic descriptions, `dsl`.

### `packs.infra.storage` (edge, persistence)
- **`DomainRepositoryService`** «Adapter» `@Service` — realizes the domain ports; ctor-injects a `SerializationStrategy` + filesystem storage; maps DSO↔entity (hand-written).
- **`SerializationStrategy`** «Strategy» (interface) ← **`YamlSerializationStrategy`** — entity/DSO↔YAML text; reuses `YamlDumpEmitter`, `PackSchemas`.
- **`TacticDso`**, **`PackDescriptorDso`** «DSO» — records; **value equality carries the round-trip fidelity assertion**.
- Storage mechanics: temp→`ATOMIC_MOVE`, `PackIdentifier`→path (via file-kinds classification), pack-root containment (normalize + realpath symlink check).

## Invariants & equality
- **Entity identity:** `Pack`/`Tactic` equal iff same identifier (NOT field-wise). Identity is immutable.
- **Value equality:** value objects, DSOs, identifiers equal by all components; lists order- and duplicate-sensitive.
- **Round-trip fidelity (NFR-004):** `load → DSO`, write, `reload → DSO`, `dsoAfter.equals(dsoBefore)`, scoped to declared content (undeclared top-level `pack.yaml` keys are captured on read, dropped on write-back — pinned exception).
- **Atomicity (NFR-003):** a failed write leaves the target untouched and no stray temp; a target resolving outside the pack root is refused before any bytes.
- **Partial-tolerance:** a broken element file is excluded from `load` and reported via the inspection query; a missing/unreadable `pack.yaml` refuses the descriptor fetch.
- **Writes:** replace-existing by id only (C-008); new-element creation deferred.

## State transitions
None beyond create/replace of a pack element by identity (no lifecycle state machine in scope).
