---
title: "ADR-0048: Pack composition: a dependency graph of named packs, with built-in as a distribution pack"
description: Packs compose through versioned depends_on edges into a diamond-shaped graph; built-in becomes a content-free distribution pack over a slimmed foundation pack.
doc_status: draft
updated: 2026-11-02T00:00:00Z
decision_status: 'Proposed'
decision_date: '2026-11-02'
type: ADR
kind: ADR
related:
  - adr/0012-pack-mutability-policy
  - adr/0015-charter-repository-is-canonical-for-charter-structure
  - adr/0017-pack-schemas-imported-as-canonical-json-schema
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - architecture/logical-model
audience:
  - profile: pack-author
    guidance_text: Read this for how a pack declares what it builds on, how versions are checked, and what a specialist pack may and may not change in the packs below it.
  - profile: governance-lead
    guidance_text: Read this for what ships by default after the split, and which packs become opt-in.
  - profile: charter-service-contributor
    guidance_text: Read this before building the depends_on resolver, the compose-time refusals or the parent_pack checks.
  - profile: core-maintainer
    guidance_text: Read this for the consequences on core's pin, sync-packs, packaging and upgrade migrations.
---

# ADR-0048: Pack composition: a dependency graph of named packs, with built-in as a distribution pack

## Status

**Proposed**, 2026-11-02. Decider: Stijn Dejongh (operator). Records the operator rulings of 2026-11-02
on [#145](https://github.com/spec-kitty/spec-kitty-charter/issues/145),
[#146](https://github.com/spec-kitty/spec-kitty-charter/issues/146) and
[#147](https://github.com/spec-kitty/spec-kitty-charter/issues/147) (epic
[#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142)). The rulings are recorded here
before an architectural, planning and debrief pass. That pass may refine the consequences and the
open questions, but not the decisions, without a new operator ruling.

## Context

- **Identity is per pack.** A pack descriptor carries `pack_id` (a ULID), `pack_version` (a semantic
  version, required at every level), `pack_level` (built-in, org or project) and `parent_pack`.
- **Artifacts carry no content version.** Artifact files carry `schema_version`, which versions the
  file format. A Charter Reference Graph (CRG) link between artifacts is an unversioned URN (`directive:DIRECTIVE_001`). The one
  exception is the skill, which carries its own `version`.
- **There is no dependency relation between packs.** Org packs are a flat list. `parent_pack` is
  lineage only and sets no load order.
- **`built-in` is one pack** (`pack_id 01ARWG13C000000000000000FG`) holding about 420 files, split by
  kind into subdirectories: four Mission types, 27 agent profiles, 121 tactics, 39 directives, 37
  toolguides, 26 styleguides, 22 procedures, 14 paradigms and two presets (spec-kitty-open-packs
  `a45afcf`).
- **`built-in` is too exhaustive.** It ships stack-specific content to every consumer, for example
  `java-conventions`, `maven-review-checks`, `drupal-conventions` and `python-review-checks`.
- **Standalone packs already exist.** spec-kitty-open-packs already publishes `qa`, `event-driven` and
  `cave-kitty` as top-level packs beside `built-in`.
- **A retired field.** The retired `accompanies_doctrine_pack` field (core ADR `2026-10-06-2`,
  ADR-0042) no longer applies. There is no separate companion pack.

## Decision

### Versioning and dependencies (A1)

1. **Versions live at the pack level.** A pack declares what it builds on as
   `depends_on: [{pack_id, range}]`, where `range` is a semantic-version range against the dependency's
   `pack_version`.
2. **Artifact links stay unversioned.** Consistency between artifacts is checked by the existing CRG
   closure check: a link to an artifact that no loaded pack provides fails at load.
3. **Skills keep their own content `version`.** A skill is a separate entry point and deliverable, so
   it is versioned on its own.
4. **Resolution fails closed.** It refuses with a structured error on a dependency cycle, an unknown
   dependency, or a dependency whose version is outside the declared range.

### Lineage (A2)

5. **`parent_pack` is lineage only.** It records which pack this one derives from. It sets no load
   order; `depends_on` does. #147 is folded into #146.
6. **A `parent_pack` that is set must resolve** to a registered or available pack. Otherwise the pack
   is refused.
7. **Level rule.** A pack's `pack_level` may not be higher than its parent's, in the order
   project < org < built-in.

### Composition shape (S1, S2)

8. **Packs compose into a graph that may form diamonds.** A foundation pack A has specialist packs
   that depend on it (B, C and D each `depends_on` A), and a distribution pack Z depends on the
   specialists. A shared dependency is loaded once, deduplicated by `pack_id`, and the version ranges
   that every dependent declares on it must overlap. Otherwise the compose is refused.
9. **Specialist packs only add.** A specialist pack adds artifacts and CRG edges.
   - When two sibling packs both refine the same artifact (`enhances` or `overrides`) inside one
     compound, the compose is refused.
   - The distribution pack may settle it explicitly with its own edge.
   - There is no "last loaded wins", so the result never depends on the order packs are listed in.
10. **Artifact identity does not change.** URNs stay global and unqualified. Which pack holds an
    artifact is provenance, not identity, and an id must be unique across every pack in a compose.

### The named-packs split (S3, S4)

11. **Each topical pack is a full top-level pack** in spec-kitty-open-packs (for example `foundational`,
    `writing`, `java`, `drupal`). None is nested under `built-in/`.
12. **`built-in` becomes a distribution pack.** It keeps its `pack_id` and `pack_level: built-in`,
    holds no artifacts of its own, and aggregates its members through `depends_on`. The presets
    (`default`, `minimal`) stay on `built-in`, because they reach across members.
13. **The foundation pack keeps the Mission types and base agent profiles we ship, and is slimmed.**
    It holds the cross-cutting directives, paradigms, glossary, base templates and assets, the
    shipped Mission types and their step contracts, and the base agent profiles. Content outside that
    core moves out to a topical pack or is cut.
14. **Stack packs are opt-in.** `java`, `drupal`, `python` and their like are not members of
    `built-in`. A project or org registers them the way it registers `qa` today.
15. **Member packs are roots.** A member of `built-in` carries `parent_pack: null`; the distribution
    pack depends on it and does not derive it. An org pack that refines the built-in distribution names
    `built-in` as its parent.
16. **Members version separately but release together.** Each member has its own `pack_version`.
    Until a need arises they are released in lockstep, and `built-in` pins its members' exact
    versions.

### Activation (S6, scope limit)

17. **No whole-pack deactivation in this decision.** Every member of the distribution is loaded.
    Activation stays per artifact, through presets and the cascade by Mission type. Switching off a
    whole pack would be a new activation concept and needs its own ADR.

## Consequences

- **Schemas.** `depends_on` and the compose refusals go into the canonical pack schemas (#114). The
  reads they change reach the API only through an additive contract set (ADR-0009).
- **Charter service.** It needs a resolver for the dependency graph (ordering, cycle check, range
  check, deduplication by `pack_id`), the `parent_pack` existence and level checks, and the
  sibling-override refusal. ADR-0012's mutability policy still applies per pack.
- **Core: pin and fetch.** Core keeps one pin per spec-kitty-open-packs commit (`built-in.source.yaml`)
  and one bump command. `make sync-packs` fetches `built-in` and every pack it pulls in, from that
  commit, and checks each tree's digest.
- **Core: packaging.** The wheel include and `test_packaging_safety.py` widen to exactly the packs
  `built-in` pulls in. That changes a packaging invariant, so it needs an amendment to core ADR
  `2026-08-16-3` (core's decision to make).
- **Core: upgrade.** A consumer that relies on an artifact that leaves `built-in` (for example
  `java-conventions`) gets an upgrade step that detects the activated artifact which is no longer
  shipped and suggests the opt-in pack that now carries it, with a doctor warning in the meantime.
  Nothing is dropped silently.
- **Curation.** Slimming the foundation pack is a content decision per artifact. It is done from a
  census and ruled by the operator, not inferred.
- **Ordering of work.** The dependency graph lands first, behind a compatibility shim that keeps
  today's single `built-in` tree loading unchanged (#145's risk note). The split follows.

## Open questions

These are for the architectural, planning and debrief pass. Each needs an operator ruling.

1. **Tier of our own opt-in packs.** Should the packs we publish but don't ship (`java`, `qa`, …) be
   `org`, or should the order gain a tier for them?
2. **Foundation membership.** Which directives, tactics, paradigms and agent profiles are "base",
   which move to a topical pack, and which are cut? The census proposes; the operator rules.
3. **Topical packs inside `built-in`.** Besides foundation, does `built-in` aggregate any topical pack
   (for example `writing`, if the documentation Mission type's guidance moves there), or does it hold
   foundation alone?
4. **Range syntax.** Which semantic-version range grammar (npm-style, Maven-style, or a minimal
   subset)?
5. **How settling a sibling conflict is written.** What exact edge form does the distribution pack use?

## Alternatives considered

- **Split by kind** (a directives pack, a tactics pack and so on). Rejected. It only renames today's
  subdirectories, every Mission type would still need every pack, and it does nothing for curation.
- **Members nested under `built-in/<name>`.** Rejected by the operator. Members are first-class packs
  that can be reused and registered on their own.
- **Versions per artifact.** Rejected. Every edit would mean a version bump for little gain, because
  the closure check already catches a link to an artifact that is missing.
- **`parent_pack` as the composition edge.** Rejected. It conflates lineage with load order, and a
  pack can have only one parent, which cannot express a diamond.
- **Last loaded wins for sibling overrides.** Rejected. It would make the effective charter depend on
  the order packs are listed in.

## References

- Epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142); keystone
  [#145](https://github.com/spec-kitty/spec-kitty-charter/issues/145) (operator rulings comment of
  2026-11-02); [#146](https://github.com/spec-kitty/spec-kitty-charter/issues/146);
  [#147](https://github.com/spec-kitty/spec-kitty-charter/issues/147) (closed into #146).
- Core: spec-kitty/spec-kitty#2466, #2467, #5228, #3511, #3518; ADR `2026-08-16-1` (manifest
  unification), ADR `2026-08-16-3` (internal pack not force-shipped), ADR `2026-10-06-2`
  (charter-pack cutover).
