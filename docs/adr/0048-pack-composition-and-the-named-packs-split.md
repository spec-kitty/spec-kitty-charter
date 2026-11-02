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
  - adr/0049-glossary-contexts-and-terms
  - adr/0050-mission-types-and-lifecycles-as-declared-state-machines
  - adr/0051-event-registry-a-thin-provider-neutral-consistency-check
  - adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts
  - architecture/logical-model
  - plans/pack-ecosystem-design-notes
  - plans/pack-ecosystem-work-breakdown
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

**Refined 2026-11-02** by that pass. No ruled decision changes. Decision 16 adds the ruled `missions/` to
`mission_types/` rename (the notes' section 4, M1), which rides on the split
([ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/)). The pass also adds the census of
spec-kitty-open-packs at `1060bfc`, the contract consequences, and a recommended default for
each open question. The other rulings of the same session are formalized in
[ADR-0049](/adr/0049-glossary-contexts-and-terms/) (glossaries),
[ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/) (mission types and lifecycles),
[ADR-0051](/adr/0051-event-registry-a-thin-provider-neutral-consistency-check/) (the event registry) and
[ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/) (procedures and
anti-patterns).

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
  `a45afcf`, the count at the time of the ruling; the formalization census below is current).
- **`built-in` is too exhaustive.** It ships stack-specific content to every consumer, for example
  `java-conventions`, `maven-review-checks`, `drupal-conventions` and `python-review-checks`.
- **Standalone packs already exist.** spec-kitty-open-packs already publishes `qa`, `event-driven` and
  `cave-kitty` as top-level packs beside `built-in`.
- **A retired field.** The retired companion-pack field (core ADR `2026-10-06-2`, ADR-0042) no longer
  applies. There is no separate companion pack.
- **The census at `1060bfc`** (the formalization pass) counts 26 agent profiles, 39 directives, 14 paradigms,
  22 procedures, 25 styleguides, 121 tactics, 18 toolguides, 4 mission types with 21 step contracts, one glossary of
  129 terms, 6 assets, 2 templates and 2 presets in `built-in`. `internal` is a fifth top-level pack beside `qa`,
  `event-driven` and `cave-kitty`.

## Decision

<!-- markdownlint-disable MD029 -->
<!-- Decisions are numbered across the sections so they can be cited by number. -->
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
16. **The split renames `missions/` to `mission_types/`** in every member that holds a mission type, and moves
    the step contracts into the type's bundle (ADR-0050 decisions 4 and 5). Both moves land in the split's re-pin.
17. **Members version separately but release together.** Each member has its own `pack_version`.
    Until a need arises they are released in lockstep, and `built-in` pins its members' exact
    versions.

### Activation (S6, scope limit)

18. **No whole-pack deactivation in this decision.** Every member of the distribution is loaded.
    Activation stays per artifact, through presets and the cascade by Mission type. Switching off a
    whole pack would be a new activation concept and needs its own ADR.

<!-- markdownlint-enable MD029 -->

## Consequences

- **Schemas.** `depends_on` and the compose refusals go into the canonical pack schemas (#114). The
  reads they change reach the API only through an additive contract set (ADR-0009).
- **Contract** (found by the formalization pass; none applied here). `pack-descriptor.schema.json` gains
  `depends_on` with a closed item `{pack_id, range}`, and `pack_id` and `parent_pack` gain a ULID pattern. Three
  contract shapes meet the decisions and change on a 0.x minor, with a `### Breaking` entry (ADR-0022):
  `ActivationConfig.packs` says "a later pack wins over an earlier one", which decision 9 rules out for siblings;
  `PackPin` pins one exact `version` under a lowercase `Slug` `packId`, which holds neither a range nor a ULID;
  and the closed `OfferingPack` list has no place for members pulled in through `depends_on`. The new compose
  refusal codes (cycle, unknown dependency, range, sibling conflict) are breaking as well (ADR-0009).
- **Undeclared level.** `pack_level` is optional in the descriptor: an absent level is the `pack_level_missing`
  diagnostic and the level the pack is configured at is used
  ([ADR-0038](/adr/0038-one-configuration-root-a-directory-per-domain/)). The level rule (decision 7) has to say
  which level it reads (open question 7).
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
- **Event registries, anti-patterns and step enhancements across packs** all resolve through the same
  `depends_on` closure ([ADR-0051](/adr/0051-event-registry-a-thin-provider-neutral-consistency-check/),
  [ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/),
  [ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/)).
- **Ordering of work.** The dependency graph lands first, behind a compatibility shim that keeps
  today's single `built-in` tree loading unchanged (#145's risk note). The split follows.

## Open questions

Each needs an operator ruling. The formalization pass recommends a default for each; until ruled, the
[work breakdown](/plans/pack-ecosystem-work-breakdown/) plans with the default.

1. **Tier of our own opt-in packs.** Should the packs we publish but don't ship (`java`, `qa`, …) be
   `org`, or should the order gain a tier for them? *Recommended:* `org`. The pass found no behavior a new tier
   would need: an opt-in pack is registered the way an org pack is, and the level rule (decision 7) already lets
   it name `built-in` as its parent.
2. **Foundation membership.** Which directives, tactics, paradigms and agent profiles are "base",
   which move to a topical pack, and which are cut? *Recommended:* the census proposal in the
   [work breakdown](/plans/pack-ecosystem-work-breakdown/#foundation-membership-census-proposal). In short:
   foundation keeps about 30 directives, 8 paradigms, 13 agent profiles, 5 styleguides, 3 toolguides, about 60
   tactics, the four mission types, the step contracts, the glossary and both presets. Topical packs `writing`,
   `architecture` (with DDD), `refactoring` and `spdd`, with `testing` folded into `qa`. Stack packs `java`,
   `python`, `drupal` and `javascript` (node and frontend). Cuts and moves to `internal` are listed there.
3. **Topical packs inside `built-in`.** Besides foundation, does `built-in` aggregate any topical pack
   (for example `writing`, if the documentation Mission type's guidance moves there), or does it hold
   foundation alone? *Recommended:* foundation and `writing`. The shipped documentation Mission type relies on the
   writing styleguides and profiles, so leaving `writing` out would ship a Mission type whose guidance is opt-in.
4. **Range syntax.** Which semantic-version range grammar (npm-style, Maven-style, or a minimal
   subset)? *Recommended:* a minimal subset: an exact version, `^`, `~`, and the comparison operators `>=`, `>`,
   `<=`, `<` joined by a space (AND). No `||`, no wildcards, no pre-release matching beyond exact. Note that the
   descriptor's `requires_core` already takes a PEP 440 specifier set (`>=4.0,<5.0`); reusing that grammar for
   `range` is the alternative that keeps one range grammar per descriptor.
5. **How settling a sibling conflict is written.** What exact edge form does the distribution pack use?
   *Recommended:* the distribution pack declares its own `overrides` edge on the contested artifact, naming the
   member whose version wins. No new edge type. Whether add-only step enhancements from siblings compose instead of
   conflicting is ADR-0050 open question 7.
6. **What a "compose" is** (decision 10). *Recommended:* one `depends_on` closure at one level. An id is unique
   within it, except where an explicit `overrides` replaces it; across levels the layer merge applies (project over
   org over built-in), as for glossary terms in [ADR-0049](/adr/0049-glossary-contexts-and-terms/).
7. **An undeclared `pack_level`**. *Recommended:* the level rule reads the configured level, and the
   pack-descriptor schema makes `pack_level` required in the version that adds `depends_on`.
8. **The foundation pack's id.** The rulings write both `foundational` and "foundation pack". *Recommended:*
   `foundation`.

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
