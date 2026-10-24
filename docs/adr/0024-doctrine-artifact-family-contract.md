---
title: "ADR-0024: Doctrine kinds share a thin CharterArtifact interface over per-kind entities and ports"
description: Records why directive, styleguide, paradigm and tactic share one thin CharterArtifact interface over per-kind entities and ports, and why the kind registry is deferred.
doc_status: active
updated: 2026-10-24T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0006-onion-vertical-slices-archunit
  - adr/0017-pack-schemas-imported-as-canonical-json-schema
audience:
  - profile: core-maintainer
    guidance_text: Read this for the contract every new doctrine kind follows and why adding one is a wide but bounded change until the registry lands.
  - profile: charter-service-contributor
    guidance_text: Read this before adding a doctrine kind — where the family interface lives, what each kind owns, and what is deferred.
---

# ADR-0024: Doctrine kinds share a thin CharterArtifact interface over per-kind entities and ports

## Status

**Accepted** — 2026-10-24. Decider: Stijn Dejongh (operator).

## Context

The charter service first modelled only the **tactic** as a rich pack entity. Mission
`pack-doctrine-artefacts-01M5JHD0` added **directive**, **styleguide** and **paradigm** as
first-class pack entities so the service can load, validate and round-trip them against the
canonical open-packs schemas (ADR-0017). That raised a design question the mission had to
settle and that the next two kinds (toolguide and procedure) will re-ask: how far should the
four kinds be unified?

The kinds share almost nothing beyond identity and references. A directive carries intent,
enforcement and integrity rules; a styleguide carries scope, principles, patterns and a
machine-checkable config tree; a paradigm carries a summary and the directives it is realized
by. Only `kind`, `packId`, `localId`, `schemaVersion`, `label`, `description`, `edges` and a
derived `urn` are genuinely common. A fat shared base entity would force false commonality;
no abstraction at all would leave the aggregate, the doctrine-reference-graph view and reports
switching on kind everywhere.

The mission's decision record first placed the shared interface in `kernel.domain`. That is
wrong: the spine returns a `PackIdentifier`, which lives in `packs.domain`, so a kernel
interface would invert the dependency and break the "no kernel change" guarantee (ADR-0006).

## Decision

1. **A thin `CharterArtifact` family interface in `packs.domain`**, exposing only the genuine
   spine (`kind`, `packId`, `localId`, `schemaVersion`, `label`, `description`, `edges`, and a
   default `urn()` of `<kind>:<localId>`). It is placed in `packs.domain`, not the kernel,
   because it returns a `PackIdentifier`; the kernel stays untouched (ADR-0006).
2. **One rich entity per kind**, each with its own typed identifier value object, its
   concept-bearing body, `ModularRuleset` invariants, a package-private all-args constructor
   behind a public fluent builder, a flat persistence DSO whose record equality is the
   round-trip fidelity assertion, and a per-kind repository port implemented by the storage
   adapter. `Tactic` is retrofitted onto the interface additively — its contract is extended,
   not changed.
3. **`ConfigValue` is a domain value object** (a sealed scalar/sequence/mapping tree in
   `packs.domain`) so a styleguide's nested `structural_lint_config` is modelled in the domain
   rather than held as a raw untyped map, while the domain still treats it as opaque
   passthrough.
4. **The kind registry is deferred to [#169](https://github.com/spec-kitty/spec-kitty-charter/issues/169).**
   Adding a kind today is a wide change (entity, identifier, reference, repository port, DSO,
   two `SerializationStrategy` methods, store read/write, the repository service, a Pack child
   collection and its two invariants, a builder method). The right abstraction needs at least
   two more kinds to pull against, so the registry is not built now.

## Consequences

### Positive

- The aggregate, the DRG view and reports treat the four kinds uniformly through one spine,
  while each kind keeps its own schema-distinct body.
- The kernel is unchanged; the domain ring depends on nothing outward (the ArchUnit gate holds).
- Round-trip fidelity is a per-kind DSO-record equality, provable field by field.

### Negative

- The add-a-kind surface is wide and re-paid per kind until the registry (#169) lands;
  `SerializationStrategy` and the Pack per-kind invariant pairs are the two unbounded surfaces.

### Risks

- The interim DRG edge relation mapping (typed reference → `SUGGESTS`, bare → `REQUIRES`) is
  provisional pending confirmation against core's extractor
  ([#167](https://github.com/spec-kitty/spec-kitty-charter/issues/167)). Nothing in production
  assembles these edges yet, so an interim-wrong mapping misleads no consumer today; #167 must
  be resolved before any code consumes `CharterArtifact.edges()`.

## Alternatives considered

### A fat shared base entity

Rejected: the kinds share almost no body, so a common base forces false commonality and leaks
one kind's concerns onto the others.

### No shared abstraction

Rejected: every cross-kind reader (aggregate, DRG view, reports) would switch on kind, and a
new kind would touch each reader.

### Build the kind registry now

Rejected as premature: the shaping surface that a registry should hide needs at least two more
kinds (toolguide, procedure — #168) to pull against before the abstraction is trustworthy
(#169).

## References

- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/` (CharterArtifact, Directive,
  Styleguide, Paradigm, Tactic, ConfigValue, Pack, repository ports)
- [ADR-0006: Onion vertical slices with ArchUnit](/adr/0006-onion-vertical-slices-archunit/)
- [ADR-0017: Pack schemas imported as canonical JSON Schema](/adr/0017-pack-schemas-imported-as-canonical-json-schema/)
