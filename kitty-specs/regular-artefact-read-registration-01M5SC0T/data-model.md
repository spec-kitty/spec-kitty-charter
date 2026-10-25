# Data model: Regular artifact read & registration

Grounded in ADR-0025 (relationships), ADR-0026 (registration), ADR-0027 (identity), ADR-0024 (the
CharterArtifact family) and the canonical pack schemas in `contracts/packs/schemas/`.

## Kernel graph core (`kernel.domain`, pure)

### Relationship «ValueObject»
- Fields: `from: Urn`, `to: Urn`, `type: RelationType`, `when: Optional<String>`, `rationale: Optional<String>`.
- **Identity**: the **unordered** `{from, to}` pair — `equals`/`hashCode` compare the pair only; direction is stored but not part of identity (ADR-0027). At most one relationship per pair.
- Invariants: `from`, `to`, `type` non-null; `from != to`.

### RelationType «enum» (behaviour-carrying)
- The closed catalogue of DRG relations (supersedes the kernel `Relation` enum; the glossary's "DRG Relation" denotes a constant here).
- Each constant declares `directionality(): Directionality` and `token(): String`.
- `propagation()` is **deferred** (shipped later with the tension/`impacts` model) — not minted or read in this mission.

### Directionality «enum»
- `DIRECTED` (from→to), `BIDIRECTIONAL` (holds both ways). Derived from `RelationType`.

### GraphWalker «Visitor» interface + Descent «enum»
- `onNode(DrgNode): Descent`, `onRelationship(Relationship): Descent`.
- `Descent`: `CONTINUE | SKIP | STOP`.

### DoctrineGraph
- Holds `Relationship`s for serialisation/validation; its legacy `(source,relation)` index no longer drives runtime traversal (the lazy walk does).

## CharterArtifact family (`packs.domain`)

### CharterArtifact «interface» (spine)
- `kind()`, `packId()`, `localId()`, `schemaVersion()`, `label()`, `description(): Optional<String>`, **`relationships(): List<Relationship>`** (supersedes `edges()`), default `urn()`.
- `relationships()` = the relationships this component originates: `from == self`, plus `BIDIRECTIONAL` relationships with `to == self`.

### Per-kind entities (each `final`, identity-equality, package-private ctor + fluent Builder, ModularRuleset invariants)
- **Directive** — id `DirectiveIdentifier`; title, intent, `Enforcement`, optional scope; lists procedures/integrityRules/validationCriteria/explicitAllowances; `DirectiveReference`s. (exists; add `relationships()`, registration)
- **Styleguide** — id `StyleguideIdentifier`; title, `StyleguideScope`, principles, optional `StyleguidePattern`/`StyleguideAntiPattern`/tooling/qualityTest/`ConfigValue` structuralLintConfig; bare references. (exists)
- **Paradigm** — id `ParadigmIdentifier`; name, summary, directiveRefs, `ParadigmReference`s. (exists)
- **Toolguide** — **NEW**; id `ToolguideIdentifier`; `tool`, `title`, `guidePath` (relative `*.md`), `summary`, `commands`, `appliesToLanguages`, optional `lastUpdated`, bare `references`. `kind()=TOOLGUIDE`, `label()=title`, `description()=summary`, `relationships()=List.of()` (bare-string citations mint no edges). From `contracts/packs/schemas/toolguide.schema.json`.
- (Tactic — unchanged; already wired for read; excluded from the "four in-scope kinds".)

### Identity & value objects
- Identifier VOs enforce the canonical lowercase id pattern per each kind's schema.
- Distinctive VOs stay per kind (no shared base): `DirectiveReference`, `StyleguidePattern`/`AntiPattern`/`StyleguideScope`/`Enforcement`/`ConfigValue`, `ParadigmReference`.

### Pack «AggregateRoot»
- Owns per-kind child collections incl. a **new `toolguides`** collection.
- Invariants per kind: "every child belongs to this pack", "no two children share an id".
- **Registration revalidation**: a write loads the Pack, mutates it, and re-runs `RULES.guard` over the whole aggregate before persisting (ADR-0026).

### ComponentResolver «Port» (`packs.domain`)
- `resolve(Urn): Optional<CharterArtifact>` — dispatches on `urn.kind()` to that kind's read path; returns empty for out-of-scope/unwired/external kinds (D6).

## Storage DSOs (`packs.infra.storage`)
- One flat DSO per kind (record-equality = round-trip assertion), incl. **`ToolguideDso`**.
- **`DrgEdge` «DSO»** (moved from kernel): `source, target, relation, impacts?, when?, reason?`; `RelationshipMapper` converts `Relationship` ↔ `DrgEdge` at the YAML boundary; relationships persist in the pack DRG shard via `GraphShardWriter`.
- Aggregate-commit store: stage the whole pack directory, atomic directory swap.

## Transitions / lifecycle
- **Register artifact** (POST collection / PUT item): validate DTO → assemble into Pack → revalidate aggregate → stage+swap → `201`/`200` + `ETag`; refusal → `Problem` (`422`/`409`), pack unchanged.
- **Register relationship** (`PUT /packs/{id}/relationships`): idempotent on the `{from,to}` pair (explicit upsert, last-write-wins); `from` must resolve in the pack, `to` may be external.
- **Walk**: from a component, follow `relationships()`, resolve each neighbour via `ComponentResolver` on demand, cycle-guarded, empty on unwired kinds.
