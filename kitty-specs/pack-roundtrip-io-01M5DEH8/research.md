# Research: Pack parse/write-back round-trip

Phase 0 research. No spec `[NEEDS CLARIFICATION]` markers remained after discovery; the items below record design rationale and the supply-chain result. Verified against current `main` code.

## D1 — New aggregate + new port vs. reusing `CharterPack`/`CharterPackRepository`

- **Decision**: Introduce a new descriptor+tactics aggregate and a new `PackElementRepository` port. Do not reuse `CharterPack` or overload `CharterPackRepository`.
- **Rationale**: `CharterPack` requires a non-null `DoctrineGraph graph` (`Objects.requireNonNull(graph, "graph")`), so reusing it would drag the deferred graph scope back in or force a synthetic empty graph. `CharterPackRepository` exposes only `CharterOffering load()` with an explicit all-or-nothing contract ("a partial offering is never returned"); the mission needs single-pack, partial-tolerant fetch and per-element write — a different granularity and failure posture. A fresh aggregate/port keeps the vertical separable and honours C-002/C-006.
- **Alternatives considered**: (a) Add write methods to `CharterPackRepository` — rejected: forces every `load()` caller through a port that now speaks writes and conflates all-or-nothing load with per-element write. (b) Reuse `CharterPack` with an empty graph — rejected: reintroduces the deferred graph concept and a non-faithful synthetic field.

## D2 — Fetch granularity & partial tolerance (FR-008)

- **Decision**: `fetch(packLocation)` returns the aggregate (descriptor + valid tactics) **plus** a list of reported violations for files that failed to parse/validate; it does not refuse the whole pack on one bad file.
- **Rationale**: Reverses the offering-level all-or-nothing posture deliberately, at a finer granularity, so a single bad artifact neither hides the valid content nor crashes the fetch. This is new behaviour (hence FR-008 is `[build]`, corrected from an earlier `[ratchet]` mislabel). The existing `PackContents` already models `FileRead.Unreadable`/`SchemaViolation`, so violations have a precedent shape to mirror.
- **Alternatives considered**: All-or-nothing (mirror `load()`) — rejected: contradicts the spec's partial-tolerant scenario and the mission's robustness intent.

## D3 — Deterministic id→path mapping for replace-by-id, resolved in the adapter (NFR-004, FR-007) — REVISED after plan review

- **Decision**: The id→path mapping lives entirely in the **adapter**, not on the domain model. The domain `Tactic` carries **no** storage coordinate. On `write`, the adapter resolves an existing element's id to its file by locating the `*.tactic.yaml` whose id matches (the `fetch` already walks the pack directory and classifies via `file-kinds.yaml`), then replaces that file.
- **Rationale (revised)**: An earlier draft carried a pack-relative `sourcePath` on the `Tactic` record. The plan review flagged two defects, both correct: (1) a source path is the filesystem-layout concept even when typed as `String`, so it is an architectural leak (and pre-empts the paused sources/distribution mission, C-005); (2) as a required field in structural equality it pollutes the equality contract — two semantically identical tactics from different files would compare unequal, roping a storage coordinate into "no modelled information lost" (FR-005). Keeping the mapping behind the port leaves `Tactic` path-free and equality purely semantic. Replace-existing is unambiguous (the file exists and is locatable by id), so glob-derivation ambiguity does not arise.
- **Alternatives considered**: `sourcePath` on the model (rejected — leak + equality pollution); minting a new element's path from the kind glob (deferred — write-of-new-element is out of scope; the round-trip fixtures use existing files).

## D4 — Equality semantics (NFR-002, MINOR follow-through)

- **Decision (single source of the collapse rule)**: Equality is structural record `.equals()` over modelled fields. All collections (`steps`, top-level and nested `references`, the tactic collection, `examples`, `failure_modes`, `applies_to_languages`) are **order- and duplicate-sensitive value lists**, never sets. Two collapses, defined here once and referenced elsewhere: (i) explicit YAML `null` and an absent key collapse to the same empty scalar state (empty `Optional`); (ii) an absent list and an empty list collapse to the same empty list. `PackDescriptor.undeclaredKeys` is **excluded** from the equality contract (names without values) and the proving fixture descriptors carry none.
- **Scalar typing (added)**: All modelled fields are strings or string lists, but unquoted YAML coerces types — `schema_version: 1.0` parses as a float, `x: null` as null. The reader stringifies on read and the writer re-emits stably so string equality holds; fixtures quote such scalars. (Pinned in the reader contract; raised by the plan review n3.)
- **Rationale**: The round-trip invariant and its positive controls are only checkable if equality is pinned. The schema types `purpose`/`notes`/step `description` as `anyOf [string, null]` with `default: null`, so explicit-null is possible on disk; collapsing avoids an ambiguous double-representation. The list collapse was previously stated only in data-model.md — single-sourced here per the plan review (n4).
- **Alternatives considered**: Distinguish explicit-null from absent — rejected: adds a tri-state for no behavioural benefit and complicates equality.

## D5 — References & steps carried as opaque verbatim structs (C-004)

- **Decision**: `TacticReference` carries all four schema fields (`name`, `type`, `id`, `when`) verbatim; `TacticStep` carries `title` + optional `description`/`examples`/nested `references`. None is interpreted as a typed domain relationship.
- **Rationale**: `tactic.schema.json` defines `references` as a list of 4-field `TacticReference` objects (not ids) and `steps` as structured `TacticStep` objects; an "id list" model would silently drop `name`/`type`/`when` and break round-trip equality. `TacticReference.type` *names* an artifact-kind relationship, so carrying the struct opaquely (store, never resolve) is exactly how "relationships deferred" (C-002) is honoured.
- **Alternatives considered**: Model references as `List<String>` of ids — rejected: factually wrong against the schema and breaks fidelity.

## D6 — Write mechanism is a NEW SnakeYAML Dump-based serializer; atomic + contained write (NFR-004, C-003) — REVISED after plan review

- **Decision**: The descriptor and tactic writers **serialize from scratch** using a SnakeYAML `Dump`/`DumpSettings` emitter over a `LinkedHashMap`, mirroring the existing `GraphShardWriter` pattern. They do **not** reuse `YamlDocument`/`ScalarWriter` for emission. Writes go to a temp file then atomic-rename into place; the writer refuses any resolved path that escapes the pack root; atomicity is proven via a failure-injection seam (fail between temp-write and rename).
- **Rationale (corrected)**: The plan review correctly flagged that `YamlDocument`/`ScalarWriter` is a parse-then-edit-one-scalar model (`render()` returns the parsed bytes; `withScalar`/`mapScalars` only replace existing scalars) — it **cannot** build a document for a fresh directory or add/remove keys and list entries, which US2 (write to a fresh dir) and FR-007 (replace, possibly adding structure) require. The genuine model→text capability already exists in `GraphShardWriter` via SnakeYAML `Dump`, and `OpenPacksRoundTripTest` proves that path round-trips semantically. So C-003's "reuse plumbing / no new serialization" framing was wrong for the writers: a new Dump-based serializer is required and is real (if modest) new surface. Because fidelity is **semantic** (not byte), scalar style, quoting and key order are non-issues — plain vs quoted re-parse to the same String, key order does not change record fields, and absent optionals may be freely omitted (D4). `PackSchemas`/`ParsedYaml`/`YamlDocument` are still reused on the **read** side.
- **Alternatives considered**: In-place `YamlDocument.mapScalars` (rejected — cannot construct or restructure a document); direct in-place overwrite without temp+rename (rejected — non-atomic, corrupts on crash).

## D8 — Descriptor completeness: model `active_charter` and `pack_drg` (FR-005, C-002) — added after plan review

- **Decision**: Extend `PackDescriptor` with `activeCharter: Optional<String>` (`active_charter`) and `packDrg: Optional<String>` (`pack_drg`), and read/write both. Add a control descriptor to the proving fixture that carries them.
- **Rationale**: `pack-descriptor.schema.json` declares 8 properties; `PackDescriptor` models 6 (+`undeclaredKeys`). Because `active_charter`/`pack_drg` are schema-*declared*, they are excluded from `undeclaredKeys` too — so a from-scratch writer (D6) would **delete** them from disk while the round-trip equality passed vacuously (both sides drop them). Both are plain optional **path strings** (verified in schema: `anyOf[string,null]`, "pack-relative path to …") — not graph structures — so modelling them is cheap and does not touch the deferred graph/DRG work (C-002: `pack_drg` is only the *path to* a DRG source, not the DRG itself). Extending the shared record ripples to `PackDescriptorReader` and existing constructor callers; that is necessary, in-scope work for an honest round-trip.
- **Alternatives considered**: Scope-exclude both + make the writer preserve unmodelled bytes — rejected: conflicts with the from-scratch Dump writer (D6) and leaves the vertical silently lossy for real descriptors.

## D7 — Delineation from the existing `OpenPacksRoundTripTest`

- **Decision**: The new `PackElementRoundTripTest` asserts **domain-model** (semantic) equality through the new port; it is distinct from the existing byte/data-level `OpenPacksRoundTripTest` (graph-shard, `YamlDocument` scalar identity). FR-005 is satisfied only by the new semantic test, not the pre-existing byte test.
- **Rationale**: Avoids a reviewer/implementer assuming FR-005 is already covered. Mirror the existing test's fixture-driven structure and reuse `YamlDocument`/scalar plumbing, but assert at the model level.

## Supply-chain security (DIRECTIVE_051 / supply-chain-install-safety)

- **Result (not assumed): NIL.** This mission adds, upgrades, and removes **zero** dependencies — it reuses `PackSchemas`/`ParsedYaml`/`Yaml`/`YamlDocument`/`ScalarWriter` and the already-present networknt/SnakeYAML/jspecify/JUnit/AssertJ/ArchUnit libraries. No install, lockfile, or version change occurs, so the directive's per-dependency controls have nothing to evaluate. Recorded explicitly because silence is not compliance.
- **Adversarial evidence**: No security-impacting dependency decision was made, so no supply-chain adversarial challenge is owed. The architectural adversarial squad still runs per the operator run contract (plan stage and pre-PR), and its findings are dispositioned below.

## Plan review squad — findings disposition

Two opus lenses (design + correctness) reviewed the plan over the integrated artifacts; every finding was independently verified against the code before disposition. No contested finding is silently dropped.

| Finding | Lens | Verified | Disposition |
|---------|------|----------|-------------|
| Descriptor drops schema-declared `active_charter`/`pack_drg` → silent on-disk loss, vacuous equality | design MAJOR-1 | ✓ (schema has 8 props; model has 6; both excluded from `undeclaredKeys`) | FOLDED — D8: model both as `Optional<String>`; fixture control descriptor |
| `YamlDocument`/`ScalarWriter` cannot build a document; need a Dump-based emitter; C-003 reuse framing wrong | design MAJOR-2 / corr. m5 | ✓ (`YamlDocument` is parse-then-edit; `GraphShardWriter` uses `Dump`) | FOLDED — D6 rewritten; plan Technical Context + IC-03 corrected |
| `sourcePath` on `Tactic` leaks layout + pollutes equality | design MAJOR-3 / corr. n2 | ✓ (would be a required equality field; `PackRoot.locator` is the infra-free precedent) | FOLDED — D3 revised: mapping moved to adapter; `sourcePath` removed |
| Round-trip blind to reader-dropped fields; need a direct fetch-fidelity literal oracle | corr. M1 | ✓ (fetch==refetch cannot see a both-sides drop) | FOLDED — FR-002/US1-sc1 now assert against fixture literals; IC-04 enumerates the oracle |
| FR-005 control risks an in-memory `equals()` tautology | corr. m1 | ✓ | FOLDED — FR-005 control routes through write→refetch |
| Known-bad ArchUnit control must be a committed rule-against-sample, not a manual toggle | corr. m2 | ✓ | FOLDED — NFR-001 reworded; IC-04 |
| Atomicity needs a failure-injection seam; path-escape needs an in-root positive control | corr. m3 | ✓ | FOLDED — NFR-004 reworded; IC-03/IC-04 |
| Infra `PackLocation` in a domain port signature self-violates NFR-001 | corr. m4 | ✓ (`PackLocation` is in `adapter-out-yaml`) | FOLDED — port uses domain `PackRoot`/new `PackLocator` |
| Scalar typing (`schema_version: 1.0`→float, `null`) must be pinned | corr. n3 | ✓ | FOLDED — D4 scalar-typing clause; fixtures quote |
| D4 vs data-model list-collapse inconsistency | corr. n4 | ✓ | FOLDED — single-sourced in D4 |
| Tactic discovery/classification (`file-kinds`) dependency unnamed | design MINOR-3 | ✓ | FOLDED — IC-03 names the classification dependency |
| IC-04 reads test-last vs ATDD posture | design MINOR-1 | n/a (process) | FOLDED — each IC carries its own tests; acceptance authored against the port first |
| SC-002 "100% through the port" is near-vacuous (no domain caller in this vertical) | corr. n1 | ✓ | ACKNOWLEDGED — stated in plan; the known-bad ArchUnit control is the real guard |
| Port/aggregate/location design sound; opaque-struct modelling correct | both NOTE | ✓ | NO CHANGE — confirmations, not defects |
| Fixture economy minimal; keep invalid file out of the pure round-trip assertion set | corr. n5 | ✓ | FOLDED — IC-04 filters the round-trip set to valid elements |
