---
title: "ADR-0024: Charter kinds share a thin CharterArtifact interface over per-kind entities and ports"
description: Records why directive, styleguide, paradigm and tactic share one thin CharterArtifact interface over per-kind entities and ports, and why the kind registry is deferred.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0006-onion-vertical-slices-archunit
  - adr/0017-pack-schemas-imported-as-canonical-json-schema
audience:
  - profile: core-maintainer
    guidance_text: Read this for the contract every new charter kind follows and why adding one is a wide but bounded change until the registry lands.
  - profile: charter-service-contributor
    guidance_text: Read this before adding a charter kind — where the family interface lives, what each kind owns, and what is deferred.
---

# ADR-0024: Charter kinds share a thin CharterArtifact interface over per-kind entities and ports

## Status

**Accepted** — 2026-10-24. Decider: Stijn Dejongh (operator). Amended 2026-10-26.

Superseded in part by [ADR-0035](/adr/0035-charter-artifact-interface-lives-in-the-kernel/) (2026-10-27): the interface lives in `kernel.domain`, and its schema version is optional.

Superseded in part by [ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/) (2026-10-27): the amendment's step 3 no longer holds as written; the storage and aggregate plumbing of #169 is keyed by kind before the new kinds are built; the read and registration services keep the amendment's order.

### Amendment 2026-10-26: when the registry is built

Decision 4 stands, and this amendment says what ends the deferral. The next goal is the whole-pack
round trip ([#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186)): read every
Charter Pack file into the domain and write it back. That adds eight kinds (procedure,
agent_profile, mission_step_contract, glossary_pack, skill, anti_pattern, asset, template) and the
pack-level files.

An architecture review on 2026-10-26 (architect-alphonso) measured the per-kind cost. Each of the
five modelled kinds is a slice of 7 to 9 files and 800 to 1,230 lines. Most of that is real
domain content, which no registry removes. The duplication is in the plumbing: the read and
registration services come to 1,416 lines across 9 files, two of the read services are about 70%
identical, and `DomainRepositoryService` implements every repository port. The kinds are already
drifting apart: tactic has its own read path, and `when()` is a `String` on one kind but
`Optional` on another.

The registry is still not built first, for two reasons:

- **It would take the wrong shape.** The eight new kinds are not like the five modelled ones.
  Assets and templates are not activatable, assets have no schema version, skills are in force
  only when required, anti-patterns are nodes inside another kind's graph rather than files, and
  pack-level files are not artifacts at all.
- **It has no home yet.** ADR-0006 forbids a slice from depending on another slice's domain, so
  where a registry lives is the same open question as
  [#171](https://github.com/spec-kitty/spec-kitty-charter/issues/171).

The order is:

1. Settle #171 before any new kind: decide where `CharterArtifact` lives, and keep only what every
   kind has (`schemaVersion` optional). Every new kind implements this interface, so it is the one
   real prerequisite.
2. Hand-build two or three new kinds, at least one of them not YAML-authored (asset or template).
3. Then extract the shared read, write and registration code (#169) from those real examples,
   with the whole-pack round-trip gate
   ([#187](https://github.com/spec-kitty/spec-kitty-charter/issues/187)) as the safety net.

The cheap #169 tidy-ups can land at any time: tactic onto the shared read path, and one owner for
`toDso`.

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
no abstraction at all would leave the aggregate, the charter-reference-graph view and reports
switching on kind everywhere.

The mission's decision record first placed the shared interface in `kernel.domain`. That is
wrong: the spine returns a `PackIdentifier`, which lives in `packs.domain`, so a kernel
interface would invert the dependency and break the "no kernel change" guarantee (ADR-0006).

## Decision

1. **A thin `CharterArtifact` family interface in `packs.domain`**, exposing only the genuine
   spine (`kind`, `packId`, `localId`, `schemaVersion`, `label`, `description`, `edges`, and a
   default `urn()` of `<kind>:<localId>`). It is placed in `packs.domain`, not the kernel,
   because it returns a `PackIdentifier`; the kernel stays untouched (ADR-0006).

   *Superseded in part by ADR-0035: `CharterArtifact` and `PackIdentifier` live in `kernel.domain`, and `schemaVersion` is optional.*
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

- The aggregate, the CRG view and reports treat the four kinds uniformly through one spine,
  while each kind keeps its own schema-distinct body.
- The kernel is unchanged; the domain ring depends on nothing outward (the ArchUnit gate holds).
- Round-trip fidelity is a per-kind DSO-record equality, provable field by field.

### Negative

- The add-a-kind surface is wide and re-paid per kind until the registry (#169) lands;
  `SerializationStrategy` and the Pack per-kind invariant pairs are the two unbounded surfaces.

### Risks

- The interim CRG edge relation mapping (typed reference → `SUGGESTS`, bare → `REQUIRES`) is
  provisional pending confirmation against core's extractor
  ([#167](https://github.com/spec-kitty/spec-kitty-charter/issues/167)). Nothing in production
  assembles these edges yet, so an interim-wrong mapping misleads no consumer today; #167 must
  be resolved before any code consumes `CharterArtifact.edges()`.

## Alternatives considered

### A fat shared base entity

Rejected: the kinds share almost no body, so a common base forces false commonality and leaks
one kind's concerns onto the others.

### No shared abstraction

Rejected: every cross-kind reader (aggregate, CRG view, reports) would switch on kind, and a
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
