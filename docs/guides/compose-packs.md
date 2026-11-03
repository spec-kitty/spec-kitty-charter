---
title: Compose packs
description: "Declare depends_on, qualify cross-pack references, settle a sibling conflict, and fix each of the eleven composition refusals. The single refusal table."
doc_status: draft
updated: 2026-11-03T00:00:00Z
type: Guide
kind: How-To
related:
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0055-cutover-sequencing-the-java-service-first
  - architecture/descriptors
  - architecture/logical-model
  - architecture/resolution-model
  - development/http-status-codes
  - changelog/2026-11-03-pack-composition-depends-on-and-resolver
audience:
  - profile: pack-author
    guidance_text: Follow this when your pack builds on another pack, references its artifacts, or meets a composition refusal.
  - profile: governance-lead
    guidance_text: Use the refusal table to read a composition refusal and to review a pack pull request that declares a dependency.
---

# Compose packs

This page is for a pack author whose pack builds on another pack, and for whoever reviews that pack. It covers
declaring the dependency, writing references across packs, settling a conflict between sibling packs, and reading
a refusal. The concepts are in the [logical model](/architecture/logical-model/#charter-packs-and-pack-levels);
the decisions are [ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/) and
[ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/).

The offering resolves the [compose](/glossary/charter/#compose): the
[closure](/glossary/charter/#closure) of `depends_on` rooted at one level's configured packs, dependencies first,
each level contiguous (built-in, then org, then project). It serves the compose whole or refuses it by name.

## Migrating an existing pack

`pack.yaml` identities are now checked, and this is a breaking change. A pack that breaks any of these is
unreadable (`pack_unreadable`):

- `pack_id`, `parent_pack`, `depends_on[].pack_id` and `overrides[].member` are ULIDs;
- `name` matches `^[a-z0-9][a-z0-9_-]*$`, because the name is the qualifier of a cross-pack reference;
- `pack_level` is present whenever `depends_on` is non-empty.

An **org or project pack that references a built-in artifact bare is now refused**
(`pack_reference_unqualified`). To migrate:

1. Add `depends_on` on built-in, with a range:
   `{pack_id: 01ARWG13C000000000000000FG, range: ">=1.0"}`. Declare `pack_level` too.
2. Write each such reference as `built-in::<urn>`, for example `built-in::directive:DIRECTIVE_001`.

The `internal` pack is migrated through open-packs#113 and
[#363](https://github.com/spec-kitty/spec-kitty-charter/issues/363); until then a compose that holds it with
`built-in` is refused.

## Declare a dependency

In `pack.yaml`:

```yaml
pack_level: org
depends_on:
  - pack_id: 01ARWG13C000000000000000FG   # the descriptor pack_id of the pack you build on
    range: ">=1.2,<2"
```

- `pack_id` is the dependency's descriptor ULID, not its configured id and not its name.
- `range` is a [PEP 440 specifier set](/glossary/charter/#pep-440-range) against the dependency's `pack_version`,
  the grammar `requires_core` uses. Examples: `>=1.2,<2` (from 1.2 up to, not including, 2),
  `~=1.4` (1.4 or later in 1.x), `==1.3.*` (any 1.3 release), `>=2.0`.
- Two dependents may declare ranges on one pack. A shared dependency loads once, so the ranges must share at least
  one version. Ranges that only touch, such as `<2` and `>=2`, share none.
- `parent_pack` is lineage only. It does not load anything; `depends_on` does.

## Respect the level rule

A pack may take a `parent_pack` only at its own level or one nearer built-in. A pack may `depends_on` only packs
at its own level or one nearer built-in. A project pack may build on project, org or built-in packs; an org pack
on org or built-in packs; a built-in pack on built-in packs only.

## Reference another pack's artifact

Write `<pack-name>::<urn>` on a graph edge (`source` or `target`) and in the `id` of a typed reference. The
`<pack-name>` is the `name` in the other pack's `pack.yaml`.

```yaml
references:
  - type: directive
    id: built-in::directive:DIRECTIVE_001
```

- A reference to an artifact in your own pack stays bare. So does a bare id in a directive's `procedures` or a
  paradigm's `directive_refs`: those are not typed references and are not checked.
- The qualified pack must be in **your pack's own** `depends_on` closure, not merely in the compose, and it must
  hold that artifact.
- The pack is provenance, not identity. The artifact's own URN stays bare.
- Following a qualified reference across packs is part of a served read, the walk.

## Settle a sibling conflict

Two packs at the same level that both change, or both define, the same artifact id are a
[sibling conflict](/glossary/charter/#sibling-conflict). It is refused unless an
[`overrides` edge](/glossary/charter/#overrides-edge) settles it. Across levels the same id is a legitimate layer
override, so no edge is needed.

```yaml
overrides:
  - artifact: directive:DIRECTIVE_001
    member: 01ARZ3NDEKTSV4RRFFQ69G5FAV   # descriptor pack_id of a pack that contests it
```

A second edge on one artifact refuses the descriptor. Two caveats hold until the deferred work lands:

- until [#358](https://github.com/spec-kitty/spec-kitty-charter/issues/358), any pack of the compose may declare
  the edge, not only the distribution pack;
- until [#357](https://github.com/spec-kitty/spec-kitty-charter/issues/357), settling only clears the refusal. The
  named member's version is not yet put in force.

## Read a refusal

Every composition refusal answers **500** and is not retryable, like every offering refusal
([HTTP status codes](/development/http-status-codes/)). Each carries a detail in words and a structured member
naming the violating pack or packs by descriptor `packId` and `name`.

A broken compose refuses every composed read: the offering, and the walk that follows references. A pack-scoped
read of one artifact by id still answers, so you can inspect a broken pack.

| Code | Cause | Fix |
|---|---|---|
| `pack_dependency_cycle` | `depends_on` edges lead from a pack back to itself. | Remove an edge to break the cycle; a shared base belongs in a pack both depend on. |
| `pack_dependency_unknown` | A `depends_on[].pack_id` names no pack the service holds. | Correct the ULID, or register the dependency's pack. |
| `pack_dependency_out_of_range` | The held version misses the range; or two ranges on one dependency share no version; or a range is malformed. | Widen the range, update the held pack, align the dependents' ranges, or fix the PEP 440 syntax. |
| `pack_parent_unresolved` | `parent_pack` names no pack the service holds. | Correct the ULID, register the parent, or set `parent_pack: null`. |
| `pack_level_violation` | A `parent_pack` or `depends_on` target is not at the pack's own level or one nearer built-in. | Depend on a pack at your level or nearer built-in, or move your pack's level. |
| `pack_duplicate_id` | Two packs declare the same descriptor `pack_id`. | Give each pack its own ULID; a copied `pack.yaml` is the usual cause. |
| `pack_name_conflict` | Two packs declare the same `name`. | Rename one. Names are the qualifier of references and are unique without exception. |
| `pack_sibling_conflict` | Two packs at one level change or define the same artifact id, and no `overrides` edge settles it. | Remove one change, or declare `overrides` naming a contesting member (see the caveats above). |
| `pack_reference_unqualified` | A bare reference resolves only to an artifact another pack holds. | Write it `<pack-name>::<urn>` and depend on that pack. |
| `pack_reference_pack_missing` | A qualified reference names a pack outside the referring pack's own `depends_on` closure. | Add a `depends_on` on that pack, or correct the name. |
| `pack_reference_artifact_missing` | A qualified reference names a pack in the closure, but that pack holds no such artifact. | Correct the URN, or reference the pack that holds it. |

## See also

- [Descriptors](/architecture/descriptors/#packyaml--pack-descriptor): the `pack.yaml` fields.
- [Resolution model](/architecture/resolution-model/): where the compose sits in the pipeline.
- [Changelog: pack composition](/changelog/2026-11-03-pack-composition-depends-on-and-resolver/).
