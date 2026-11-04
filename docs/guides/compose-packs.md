---
title: Compose packs
description: "Declare depends_on, qualify cross-pack references, see how they resolve, settle a sibling conflict, and fix each of the eleven composition refusals."
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
declaring the dependency, writing references across packs, seeing how they resolve, settling a conflict between
sibling packs, and reading a refusal. The concepts are in the [logical model](/architecture/logical-model/#charter-packs-and-pack-levels);
the decisions are [ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/) and
[ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/).

The offering resolves the [compose](/glossary/charter/#compose): the
[closure](/glossary/charter/#closure) of `depends_on` rooted at every configured pack, across levels, dependencies first,
each level contiguous (built-in, then org, then project). It serves the compose whole or refuses it by name. Packs that
no dependency relates keep the configured-level, then pack-id order.

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
- A range does not exclude prereleases unless it names one: `>=1.0` admits `1.1a1`. This deliberately departs from the
  default of `packaging`, so a pack at a prerelease version can satisfy a range written for its release line.
- `parent_pack` is lineage only. It does not load anything; `depends_on` does.

## Respect the level rule

A pack may take a `parent_pack` only at its own level or one nearer built-in. A pack may `depends_on` only packs
at its own level or one nearer built-in. A project pack may build on project, org or built-in packs; an org pack
on org or built-in packs; a built-in pack on built-in packs only.

## Reference another pack's artifact

Write `<pack-name>::<urn>` on either end of a graph edge (`source` or `target`), in the `id` of a typed reference,
and in an entry of a directive's `procedures` or a paradigm's `directive_refs`. The `<pack-name>` is the `name` in the
other pack's `pack.yaml`. Those two lists take an optional qualifier and no other `::`.

```yaml
references:
  - type: directive
    id: built-in::directive:DIRECTIVE_001
```

- A bare reference resolves in this order. If your own pack holds the artifact, it is yours, even when another pack
  holds the same id (your pack shadows it). Else, if another pack holds it, the reference is refused as unqualified
  (`pack_reference_unqualified`). Else no pack holds it and nothing is refused, as for a kind the service does not serve.
- The qualified pack must be in **your pack's own** `depends_on` closure, not merely in the compose, and it must
  hold that artifact.
- The pack is provenance, not identity. The artifact's own URN stays bare, and the relationship it mints records
  the qualifier you wrote.
- A bare reference to the artifact's own id is a self-loop, and the pack is refused as unreadable (`pack_unreadable`).
  A qualified reference to the same id in another pack, such as a project pack's `SHARE` writing `base-pack::SHARE`,
  is legal: it names a different artifact.
- When you register a relationship, its `to` may be qualified and its `from` may not: `from` is an artifact of the
  pack you register into.
- Following a qualified reference across packs is part of a served read, the walk.

## See how references resolve

`GET /packs/{packId}/artifacts/{kind}/{artifactId}/references` (`listArtifactReferences`) lists the references one
artifact writes, in the order it writes them and without repeats. Each item gives:

- the reference as written, bare or qualified, and the relation it is written under when the artifact mints one;
- the `holdingPack` that holds what it names (`packId`, `descriptorPackId`, `name`), and the bare `urn` it resolves to;
- an `artifact` link to the pack-scoped read of the target.

This is where you see shadowing: a bare reference to an id both your pack and another pack hold shows your pack as
holder, and a qualified one shows the pack it names. A reference no pack holds has no `holdingPack` and no link.

It is a composed read. It resolves the compose and refuses with the same composition code as `GET /offering` when
the compose is broken, while the pack-scoped read of the artifact itself still answers. The pack, the kind and the
artifact are judged as for the artifact read (404).

## Settle a sibling conflict

Two packs at the same level that both change, or both define, the same artifact id, of any artifact kind, are a
[sibling conflict](/glossary/charter/#sibling-conflict). A pack that defines an artifact and a sibling that
enhances it do not conflict. The conflict is refused unless an
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
([HTTP status codes](/development/http-status-codes/)), with one exception: registering a relationship
(`PUT /packs/{packId}/relationships`) answers its `to` that the compose cannot resolve with **422** and the same code
(`pack_reference_pack_missing`, `pack_reference_unqualified` or `pack_reference_artifact_missing`), before anything is
written, because there the request is what is wrong
([ADR-0057](/adr/0057-a-refusal-code-may-declare-a-status-per-operation/)). Each carries a detail in words and a `packs`
member, a list of `{descriptorPackId, packId, name}` that names the packs involved, the violating pack first.
`descriptorPackId` is the `pack_id` its `pack.yaml` declares and `packId` the identifier it is configured under, the one
the pack-scoped paths take.

A broken compose refuses every composed read: the offering, and the walk that follows references. A pack-scoped
read of one artifact by id still answers, so you can inspect a broken pack.

A pack the service cannot read is a different refusal, `pack_unreadable` (also 500). `GET /offering` refuses with it
when any file of a configured pack is invalid or cannot be read, an artifact file or a graph file included, and when
`depends_on` ranges, `requires_core` or `pack_version` are malformed, the same verdict validate mode gives. A
pack-scoped read of an artifact that is readable still answers.

**Known gap:** the offering names and composes every configured pack whole, so one unreadable file refuses it. The
pinned built-in pack of the open packs holds files that cannot load yet, so `GET /offering` cannot serve it until
[spec-kitty-open-packs#75](https://github.com/spec-kitty/spec-kitty-open-packs/issues/75) lands. Its pack-scoped reads
still answer.

| Code | Cause | Fix |
|---|---|---|
| `pack_dependency_cycle` | `depends_on` edges lead from a pack back to itself. The detail names a pack on the cycle and the cycle path. | Remove an edge to break the cycle; a shared base belongs in a pack both depend on. |
| `pack_dependency_unknown` | A `depends_on[].pack_id` names no configured pack. | Correct the ULID, or configure the dependency's pack. |
| `pack_dependency_out_of_range` | The held version misses the range, or two ranges on one dependency share no version. A malformed range is not this code: it makes `pack.yaml` unreadable (`pack_unreadable`). | Widen the range, update the held pack, or align the dependents' ranges. |
| `pack_parent_unresolved` | `parent_pack` names no configured pack. | Correct the ULID, configure the parent pack, or set `parent_pack: null`. |
| `pack_level_violation` | A `parent_pack` or `depends_on` target is not at the pack's own level or one nearer built-in. | Depend on a pack at your level or nearer built-in, or move your pack's level. |
| `pack_duplicate_id` | Two packs declare the same descriptor `pack_id`. | Give each pack its own ULID; a copied `pack.yaml` is the usual cause. |
| `pack_name_conflict` | Two packs declare the same `name`. | Rename one. Names are the qualifier of references and are unique without exception. |
| `pack_sibling_conflict` | Two packs at one level both change, or both define, the same artifact id, and no `overrides` edge settles it. | Remove one change, or declare `overrides` naming a contesting member (see the caveats above). |
| `pack_reference_unqualified` | A bare reference, on an edge end or in a typed reference, `procedures` or `directive_refs`, names an artifact only another pack holds. | Write it `<pack-name>::<urn>` and depend on that pack. |
| `pack_reference_pack_missing` | A qualified reference names a pack outside the referring pack's own `depends_on` closure. | Add a `depends_on` on that pack, or correct the name. |
| `pack_reference_artifact_missing` | A qualified reference names a pack in the closure, but that pack holds no such artifact. | Correct the URN, or reference the pack that holds it. |

## See also

- [HTTP status codes](/development/http-status-codes/): the 422 a relationship registration answers.
- [Descriptors](/architecture/descriptors/#packyaml--pack-descriptor): the `pack.yaml` fields.
- [Resolution model](/architecture/resolution-model/): where the compose sits in the pipeline.
- [Changelog: pack composition](/changelog/2026-11-03-pack-composition-depends-on-and-resolver/).
