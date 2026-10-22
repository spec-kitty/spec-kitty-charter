# Research — pack doctrine artefacts

Phase 0 research. Only genuine unknowns are recorded; the entity shapes are
settled against the canonical schemas and captured in [data-model.md](./data-model.md).

## R1 — Representation for `styleguide.structural_lint_config` and `tooling` (FR-018, M2)

**Decision.** Represent `structural_lint_config` as an immutable, order-preserving
**nested value tree** via a small sealed value type in `packs.infra.storage`:

```
sealed interface ConfigValue permits ConfigValue.Scalar, ConfigValue.Seq, ConfigValue.Mapping
  Scalar(Object raw)                         // the raw parsed scalar from the shared loader, preserved verbatim
  Seq(List<ConfigValue>)                     // order + duplicates significant
  Mapping(LinkedHashMap<String, ConfigValue>) // insertion order preserved for deterministic emit
```

- Built directly from the SnakeYAML parse result (which already yields nested
  `Map`/`List`/scalar objects) and emitted back through the same loader
  `YamlDumpEmitter`/`Yaml.schema()` uses, so types and nesting survive.
- `Scalar` **retains the raw parsed scalar `Object`** from the shared `Yaml.schema()`
  loader (SnakeYAML `JsonSchema`: small ints → `Integer`, large → `BigInteger`,
  floats → `Double`, plus booleans/strings/null) and defines equality on that object
  — *not* a hard-coded `Long`/`Double` set (m-4). The shared loader does **not**
  resolve YAML timestamps (that is the Core schema), so dates stay strings — no
  timestamp trap. Because one loader produces *both* compared `ConfigValue` trees,
  the round-trip is symmetric by construction.
- Equality is structural (value equality over the tree); `Mapping` compares by
  key→value (**order-insensitive**, as `Map.equals` — YAML mapping key order is not
  semantically significant), `Seq` by order. Emission is still insertion-order-stable
  by construction (the backing `LinkedHashMap` + order-preserving emit), so a
  round-trip is deterministic even though equality does not pin key order. This feeds
  the `StyleguideDso` record-equality fidelity assertion (NFR-001) the same way the
  flat fields do; "order-stable" in NFR-001 refers to that deterministic emission, not
  to key-order being part of equality.
- `tooling` is the simpler case — a `Map<String,String>` held as a `LinkedHashMap`
  (order-preserving for deterministic emit); it needs no `ConfigValue` tree because
  the schema types every value as a string.
- `structural_lint_config` and `tooling` are **nullable scalars** at the DSO level
  (`Optional<ConfigValue>` / `Optional<Map<String,String>>`): absent ≠ present-empty,
  per the spec's absence-vs-empty edge case.
- **The raw-tree primitive must bypass `YamlDumpEmitter`'s omit-empty/null rule** (m-5).
  The emitter deliberately omits empty lists and absent/null keys; for a *present*
  `structural_lint_config` a present-but-empty mapping (`{}`) and any nested explicit
  `null` must be written, not dropped. The new primitive therefore emits present
  Optionals (empty containers included) and nested nulls verbatim, rather than routing
  them through the empty-omitting `scalar`/`strings`/`mappings` helpers.

**Rationale.** The canonical `styleguide.schema.json` makes `structural_lint_config`
`additionalProperties:true` — an arbitrary tree. The existing serialization
primitives (`scalar`, `strings`, `mappings`) are flat, and the reader's `text()`
helper coerces every scalar to `String` and drops `Map`/`List` values — so reusing
them would lose both type information and nesting, violating NFR-001 ("0 field
drift") and C-001 ("no field invented/dropped beyond the canonical schema"). A
dedicated value tree is the minimal structure that preserves both and yields a
clean equality for the round-trip assertion.

**Alternatives considered.**
- *Reuse `mappings(List<Map>)` / `text()` coercion* — rejected: cannot represent
  nesting, and silently stringifies numbers/booleans → fidelity failure.
- *Store the raw SnakeYAML `Object` tree untyped* — rejected: leaks the parser's
  mutable types into the DSO, and `null`/order handling is implicit; a sealed
  `ConfigValue` makes type, immutability and equality explicit and testable.
- *Model the known lint keys as typed fields* — rejected: the schema is explicitly
  open (`additionalProperties:true`); enumerating keys would drop unknown-but-valid
  config, violating C-001.

**Scope note (S1).** This is the riskiest surface and is isolated as concern IC-04
so it can be its own work package. US2 (styleguide) is therefore materially larger
than US1/US3; the planner should weight it accordingly.

## R2 — DRG edge derivation: target kind + Relation (FR-019, m7, M-1)

**Target kind (settled, in-repo).** Bare-string refs map to a fixed target kind by
field: `directive.procedures[]` → `procedure`; `paradigm.directive_refs[]` →
`directive`. Typed references (`{type,id,...}`) resolve their target kind from the
`type` token via `ArtifactKind.fromToken(...)` → `NodeKind.of(...)`. This resolution
is **total for schema-valid input** — each schema's reference `type` enum equals the
`ArtifactKind` set, and every `ArtifactKind` has a matching `NodeKind` (confirmed in
kernel). Empty bare-ref items are rejected by a domain invariant (R4), so `Urn`
(which rejects an empty id) never throws here. Styleguide mints no edges.

**Relation (interim mission choice + required IC-01 confirmation).** There is **no
in-repo Relation-minting precedent** for these fields — `grep` across `service/` main
shows only `PackPolicy` uses `Relation`, unrelated. The authoritative per-field
relation mapping lives in **core / spec-kitty-open-packs' DRG extractor**, so it is a
**cross-repo input, not an in-repo lookup** — obtaining it is an explicit **IC-01
prerequisite / risk** (recorded in plan.md).

The mission ships with this **justified interim mapping**, to be confirmed against
core during IC-01 (and corrected if core diverges — low-risk, since no test or
success criterion asserts the relation *value*; US3-3/SC-003 assert edge presence and
target kind only). This table is the single source; data-model.md mirrors it:

| Source field | Relation | Justification |
|--------------|----------|---------------|
| `paradigm.directive_refs[]` | `REQUIRES` | the paradigm is *realized by* those directives (spec US3) — a hard dependency whose cascade activation follows the edge |
| `directive.procedures[]` | `REQUIRES` | the directive is *carried out by* those procedures — activating the directive cascades to them |
| typed `references[]` (directive/paradigm/tactic) | `SUGGESTS` | a soft cross-reference; the schema encodes `type`/`id` (and `when`/`reason`) but **no relation token**, and the Tactic precedent never resolved `type` into a relationship — a soft default is the honest interim until core's mapping is obtained |

`APPLIES` is **rejected** as the carries-out default: its documented meaning is "an
*action* applies a directive or tactic", and neither a paradigm nor a directive is an
action, so it would misstate the edge. Every minted edge sets `impacts = empty` (no
`IMPACTS` edges), satisfying the `DrgEdge` invariant.

**Alternatives considered.** Inferring kind from id-shape — rejected as fragile; the
field name is the authoritative signal. Leaving the Relation unstated in data-model —
rejected: that was the research↔data-model contradiction M-1 flagged.

## R4 — Empty bare-reference items (m-3)

**Decision.** Add a **domain invariant** (not a schema-parity rule) to the directive
and paradigm `ModularRuleset`s: every `procedures[]` / `directive_refs[]` item must be
non-blank. The canonical schema types these as `string` with no `minLength`, so
`procedures: [""]` is schema-valid, yet `new Urn(kind, "")` throws. Rejecting it in
the domain (rather than silently skipping it in `edges()`) keeps the entity honest and
makes `edges()` total. This is an *additional* invariant beyond NFR-002's schema
parity, recorded as such so the validation-parity audit does not expect it from the
schema.

## R3 — Supply-chain / dependency posture (DIRECTIVE_051)

**Decision.** No dependency is added, upgraded, or removed. The mission reuses the
existing kernel, packs-domain, and the storage adapter's already-present SnakeYAML
stack. `supply_chain_security_check`: **not applicable — no dependency delta**.
Recorded explicitly because silence is not compliance.

**Adversarial evidence.** No security-impacting dependency decision is made, so the
`adversarial-squad-deployment` challenge pass has nothing to contest here; the
design-level adversarial review happens at the pre-PR squad step of closeout over
the integrated diff.
