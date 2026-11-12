---
title: Compose packs
description: "Declare depends_on, qualify cross-pack references, settle a sibling conflict, register and remove packs, artifacts and edges, and fix each refusal."
doc_status: draft
updated: 2026-11-12T00:00:00Z
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
  - changelog/2026-11-05-open-packs-real-compose
  - adr/0064-graph-documents-are-the-only-source-of-relationships
  - changelog/2026-11-05-edges-only-in-graph-documents
  - changelog/2026-11-07-a-pack-registration
  - conformance/divergence-register
  - guides/service-build
  - guides/enforce-org-presets
audience:
  - profile: pack-author
    guidance_text: Follow this when your pack builds on another pack, references its artifacts, or meets a composition refusal.
  - profile: governance-lead
    guidance_text: Use the refusal table to read a composition refusal and to review a pack pull request that declares a dependency.
---

# Compose packs

This page is for a pack author whose pack builds on another pack, and for whoever reviews that pack. It covers
declaring the dependency, writing references across packs, seeing how they resolve, settling a conflict between
sibling packs, reading a refusal, and changing a registered pack: registering it whole, removing an artifact or an
edge, and what the strict check refuses. The concepts are in the [logical model](/architecture/logical-model/#charter-packs-and-pack-levels);
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
2. Qualify each such edge end: `built-in::directive:DIRECTIVE_001`. See
   [Reference another pack's artifact](#reference-another-packs-artifact).

The packs in spec-kitty-open-packs are migrated, and `OpenPacksComposeTest` composes the real ones at the pin.

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
- `parent_pack` is an ordering and a cycle edge, as `depends_on` is: the pack a child extends merges before it, and a
  `parent_pack` cycle, a pack that names itself included, is `pack_dependency_cycle`. It adds no `depends_on` entry and no
  version range.

## Respect the level rule

A pack may take a `parent_pack` only at its own level or one nearer built-in. A pack may `depends_on` only packs
at its own level or one nearer built-in. A project pack may build on project, org or built-in packs; an org pack
on org or built-in packs; a built-in pack on built-in packs only.

## Reference another pack's artifact

A reference across packs is an end of a graph edge. Edges are written only in a pack's graph documents: the root
`*.graph.yaml` shards in built-in, `drg/fragment.yaml` in an org or project pack. No artifact field carries a reference
([ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/)). Either end of an edge (`source` or
`target`) is written `<pack-name>::<kind>:<id>`. The `<pack-name>` is the `name` in the other pack's `pack.yaml`,
whatever its directory is called.

```yaml
edges:
  - source: directive:ORG_001
    target: built-in::directive:DIRECTIVE_001
    relation: requires
```

- A bare reference resolves in this order. If your own pack holds the artifact, it is yours, even when another pack
  holds the same id (your pack shadows it). Else, if another pack holds it, the reference is refused as unqualified
  (`pack_reference_unqualified`). Else no pack holds it and nothing is refused, as for a kind the service does not serve.
- The qualified pack must be in **your pack's own** `depends_on` closure, not merely in the compose, and it must
  hold that artifact.
- The pack is provenance, not identity. The artifact's own URN stays bare, and the edge records the qualifier you
  wrote.
- A bare reference to the artifact's own id, or one qualified with the pack that writes it, is a self-loop, and the
  pack is refused as unreadable (`pack_unreadable`). A reference to the same id qualified with another pack, such as a
  project pack's `SHARE` writing `base-pack::SHARE`, is legal: it names a different artifact.
- When you register a relationship, its `to` may be qualified and its `from` may not: `from` is an artifact of the
  pack you register into. The registration resolves the whole compose, so it is refused with the composition code
  (500) when another configured pack is broken.
- Resolving a qualified edge end across packs is part of a served read, such as an artifact's references.

## See how references resolve

`GET /packs/{packId}/artifacts/{kind}/{artifactId}/references` (`listArtifactReferences`) lists the edges in this
pack's graph documents whose source is the artifact, each with its relation, then the artifact's derived relations
(today only a guard's `implemented_by`). Each item gives:

- the reference as written, bare or qualified, and its relation;
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

Presets contest the same way, with one difference: no `overrides` edge settles them. Two packs at one level that both
hold a preset with the same id are refused as `pack_sibling_conflict` unless one is on the other's `parent_pack`
line, where the child, which merges after its parent, takes the place of the parent's copy. Across levels the preset of
the level nearer the project wins, as a layer override (ADR-0059 decision 3).

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
([ADR-0056](/adr/0056-a-refusal-code-may-declare-a-status-per-operation/)). Each carries a detail in words and a `packs`
member, a list of `{descriptorPackId, packId, name}` that names the packs involved, the violating pack first.
`descriptorPackId` is the `pack_id` its `pack.yaml` declares and `packId` the identifier it is configured under, the one
the pack-scoped paths take.

A broken compose refuses every composed read: the offering, and the walk that follows references. A pack-scoped
read of one artifact by id still answers, so you can inspect a broken pack.

A pack the service cannot read is a different refusal, `pack_unreadable` (also 500). `GET /offering` refuses with it
when any file of a configured pack is invalid or cannot be read, an artifact file or a graph file included, and when
`depends_on` ranges, `requires_core` or `pack_version` are malformed, the same verdict validate mode gives. A
pack-scoped read of an artifact that is readable still answers.

The offering names and composes every configured pack whole, so one unreadable file refuses it, while that pack's
pack-scoped reads still answer.

| Code | Cause | Fix |
|---|---|---|
| `pack_dependency_cycle` | `depends_on` and `parent_pack` edges lead from a pack back to itself, or a pack names itself as its parent. The detail names a pack on the cycle and the cycle path. | Remove an edge to break the cycle; a shared base belongs in a pack both depend on. |
| `pack_dependency_unknown` | A `depends_on[].pack_id` names no configured pack. | Correct the ULID, or configure the dependency's pack. |
| `pack_dependency_out_of_range` | The held version misses the range, or two ranges on one dependency share no version. A malformed range is not this code: it makes `pack.yaml` unreadable (`pack_unreadable`). | Widen the range, update the held pack, or align the dependents' ranges. |
| `pack_parent_unresolved` | `parent_pack` names no configured pack. | Correct the ULID, configure the parent pack, or set `parent_pack: null`. |
| `pack_level_violation` | A `parent_pack` or `depends_on` target is not at the pack's own level or one nearer built-in. | Depend on a pack at your level or nearer built-in, or move your pack's level. |
| `pack_duplicate_id` | Two packs declare the same descriptor `pack_id`. | Give each pack its own ULID; a copied `pack.yaml` is the usual cause. |
| `pack_name_conflict` | Two packs declare the same `name`. | Rename one. Names are the qualifier of references and are unique without exception. |
| `pack_sibling_conflict` | Two packs at one level both change, or both define, the same artifact id, and no `overrides` edge settles it. | Remove one change, or declare `overrides` naming a contesting member (see the caveats above). |
| `pack_reference_unqualified` | A bare edge end names an artifact only another pack holds. | Write it `<pack-name>::<urn>` and depend on that pack. |
| `pack_reference_pack_missing` | A qualified reference names a pack outside the referring pack's own `depends_on` closure. | Add a `depends_on` on that pack, or correct the name. |
| `pack_reference_artifact_missing` | A qualified reference names a pack in the closure, but that pack holds no such artifact. Also: a preset item or a `policy.yaml` lock names an artifact the closure holds only under another case (`directive_001` for `DIRECTIVE_001`); ids are matched exactly, so it would govern nothing (operator ruling of 2026-11-06). | Correct the URN or the id to the declared spelling, or reference the pack that holds it. |

## Register a pack

A whole pack is registered in one request, and the service then holds exactly that pack, less the files it reports as
not held. An artifact, an edge, a preset item or a file the upload does not carry is gone. This differs from core, which merges per artifact
([DIV-601](/conformance/divergence-register/#div-601-a-registration-replaces-the-stored-pack)). The per-artifact and
per-relationship writes stay for a small change.

Registration is off by default (`charter.registration.enabled`). While it is off, every registration and both removals
(`DELETE` of an artifact and of a relationship) answer `503 pack_source_unavailable`. Only the org and the project level are writable: a pack that declares the built-in level
is refused `409`. The operator settings are in [Building the service](/guides/service-build/#registering-packs).

1. **Send the pack.** Either `PUT /packs/{packId}` with the pack directory as one archive (gzip tar or zip, at most
   32 MiB), or `POST /packs` with a source: an allowlisted https archive, a git repository and ref, or a path under a
   configured root. An oversized body is `413` and a wrong media type or magic bytes is `415`, both before a
   registration exists. The service answers `202` with a status and a `Location`.
2. **Poll the status.** `GET /pack-registrations/{registrationId}` shows `pending`, `running`, `succeeded` or `refused`.
   A refused registration answers `200` here, with its problem in the body. A succeeded one carries the pack, its
   `outcome` (`created`, `replaced` or `unchanged`) and its `notices`. The status is kept for one hour, at most 1,000
   entries, and a restart forgets it (`404 registration_not_found`). Send the pack again: the version rule makes that
   safe.
3. **Read the pack.** `GET /packs/{packId}` answers the stored pack, with its revision as the `ETag`.

Rules of the request:

- `If-Match` with a pack revision is optional. Without it the last writer wins on the whole pack. A stale one is `412`
  at once, before any registration exists. `If-None-Match: *` makes the request create-only.
- A source registration records its source in the status. `source.url` keeps no query, no fragment and no user
  information. For a git source, `source.commit` is the commit the ref resolved to.
- For `POST`, the `name` in the pack's `pack.yaml` must equal the target `packId`. For `PUT` it need not.
- A git ref that is a branch needs a commit, or `If-Match`, or `If-None-Match: *`, else `428`. A tag, a commit id and a
  branch with a commit need none of them.
- A file that is not pack content is not held and not refused: a file an `exclusions` rule of `file-kinds.yaml` names
  (a `README.md` beside a mission type, `plan-field-declaration.yaml`, a hidden file under `assets/`), and a Markdown file
  that no rule models (an author's `docs/notes.md`). The service removes it before it commits, and reports it in the
  answer's `diagnostics` as a `file_not_held` warning whose `detail` names the file and the reason. Any other file
  nothing models (`notes.txt`) is refused `unmodelled`.
- The pack document is `README.md` at the pack root. The retired `pack.md`, `PROVENANCE.md` and `org-charter.yaml`, and a
  root file that differs from a rule's file only in letter case (`Readme.md`), are refused by name with violation code
  `retired`. A `pack.yaml` that declares `active_charter` is refused as unreadable: one `pack_unreadable` violation at
  `pack`, whose detail names the undeclared key; the pack's other faults show only once the key is removed. To require artifacts of an
  organization's projects, see [Enforce presets on an organization's projects](/guides/enforce-org-presets/).
- The notices list the must-have items the write added to or took off the required floor, and the artifacts it put under
  or released from a lock. They name activatable kinds only. An entry of another kind (a template, an asset, a guard, a
  lifecycle) is left out and logged at WARN with its URN. Issue
  [#417](https://github.com/spec-kitty/spec-kitty-charter/issues/417) tracks widening the kinds.

Limits of the queue. Two registrations run and eight wait. A ninth waiter is refused `503` with `Retry-After`. The
90 second deadline of a registration counts the time it waits in the queue, not only its run. A registration takes its
slot only after the service has read the whole body, so a slow upload holds no slot while it arrives and cannot make other
callers get `503`; the queue and the `If-Match` are judged again then. At most ten uploads may be open at once: the
eleventh is `503` with `Retry-After`, and a finished upload frees its place. The service refuses before it reads the body
when the declared length is over 32 MiB (`413`), ten uploads are open or the queue is full (`503` with `Retry-After`),
the `If-Match` is stale (`412`) or the volumes lack room for it and for what the uploads already open will write
(`503 pack_storage_full`). A registration needs about three times its archive in free space: the
archive, the tree it expands to and the copy made beside the pack, each at most 32 MiB.

## The version rule

`pack_version` is a label, not an order. A registration may go back to an older version. The service keeps every
`(pack_version, revision)` a pack name was registered with, in a history beside the packs. A `DELETE` keeps the history.

- The same version with the same revision ends `succeeded` as `unchanged`.
- The same version with another revision is refused `409 pack_version_conflict`, whether or not that version is the
  stored one.
- The version rule runs before the `If-Match` precondition. A re-send of an archive with the same `pack_version`, after
  a per-artifact `PUT` moved the revision, answers `409 pack_version_conflict`, not `412`. The `412` race needs a new
  version.
- When the service restores a lost history entry, the entry carries the time of the repair, not of the lost
  registration. It restores only a `pack_version` the history does not hold at all: a per-artifact `PUT` that moved the
  revision of a version registered earlier does not make that version impossible to restore. Send the archive that was
  registered and it is accepted.

## What a registration is refused for

A registration, and every per-artifact write and removal, is judged over every closure the pack joins, as it will be
after the write. PR A is strict: the write is refused `422`, and nothing is written, when a closure no longer composes
(one of the [eleven composition codes](#read-a-refusal)) or breaks a relation rule (`override_not_permitted`,
`enhances_cycle`, `conflicting_relations`, `self_override`, `override_cycle`). The detail names the packs. Core warns
here ([DIV-604](/conformance/divergence-register/#div-604-the-impact-check-is-strict-and-an-unreadable-pack-is-repaired-by-a-whole-pack-replace)).
The refusal is not repaired for you: the repair writes of [#404](https://github.com/spec-kitty/spec-kitty-charter/issues/404)
are not applied.

- **A stored dependent that cannot be read** makes every registration of its ancestor fail `pack_unreadable`. Fix the
  dependent, or remove it.
- **A stored pack that cannot be read** is repaired by a whole-pack registration. A per-artifact write into it, or into a
  pack whose dependency is missing, is refused even when the write would repair it. The service salvages the
  stored pack for the diff. It reads the pack file by file: an artifact file counts by the id it declares and by the id
  its path names, and each readable entry of a graph file is an edge. An artifact the upload leaves out is a `REMOVE`
  through the gate and the check of inbound references. A dropped edge is a `RELATIONSHIP` write on its source. A stored
  artifact that does not bind counts as a `REPLACE`. When the set of stored artifacts cannot be determined at all, for
  example when the directory cannot be listed, the registration is refused `pack_unreadable`: the pack is never taken as
  empty.
- **The mutability policy** applies as for any write. Removing an artifact is a `REMOVE`, and each edge the write adds or
  drops is a `RELATIONSHIP` write on its source. An edge from a locked artifact of an ancestor pack is refused `409
  governance_policy_violation`.
- At start-up the service logs a WARN for each stored pack that its strict check would now refuse.

## Remove from a pack

Two `DELETE`s remove one thing from a pack without sending the pack again. Both are synchronous and need `If-Match`: a
quoted pack revision or `*`. Without it the answer is `428`, with a stale revision `412`. Both answer `204` and the new
revision as the `ETag`, and both are strict as above (`422`).

`DELETE /packs/{packId}/artifacts/{kind}/{artifactId}` removes the artifact, its companion files, its registered edges
and the preset items of the pack that name it. A preset key left with no item is deleted, because every preset list
needs an item. It refuses with:

- `404` by code, when the pack or the artifact is not there;
- `409 artifact_referenced`, while another artifact or pack refers to it. Another pack's preset item and a descriptor
  `overrides` entry count;
- `409 governance_policy_violation`, when an ancestor of the pack holds and locks the id;
- `409 registration_conflict`, when a graph document the author wrote names the artifact's node or an edge from it.

The artifact `DELETE` never rewrites an author's graph file. It does re-serialize a preset file when it removes a preset
item, so comments and formatting in that file are lost.

`DELETE /packs/{packId}/relationships?from=<ref>&to=<ref>` removes one registered edge, in either orientation. It
removes only from `registered.graph.yaml`, the file `PUT /packs/{packId}/relationships` writes.

- `404 relationship_not_found` when no graph document and no derived relation joins the pair.
- `409 relationship_not_registered` when an author wrote the edge into a source shard or `drg/fragment.yaml`, when a
  component derives it, or when the pair is held both there and in the registered file. Register the whole pack to
  change it.
- `409 governance_policy_violation` when the edge's source is a locked artifact of an ancestor. A whole-pack replace is
  refused for dropping that edge, and so is the `DELETE`.
- A reference that names a pack matches only an edge end that names the same pack. With the edges `A` to `base::X` and
  `A` to `other::X`, a `DELETE` naming `base::X` removes that one. A reference that names no pack matches by its URN.
  A reference that names a pack does not match an edge end written without one.

Known gaps:

- `pack-manifest.yaml` is not updated by a per-artifact write or a `DELETE`. Its hashes go stale, and after a `DELETE` it
  still lists the removed file. Reads do not verify the manifest. A round trip and validate mode report it.
- A `DELETE` is judged on the facts overlay. The edited tree is not read again.
- A per-artifact `PUT` with `If-Match: *` satisfies the `428` requirement.

## A cascading deactivate (A13)

A cascading deactivate of `t` removes `t` and what its walk reached, unless something else that is active keeps it.
The service does not remember the selections before the select. So a select of `t` followed by a deactivate of `t`
does not always restore the earlier set: an artifact that was selected before, and that the walk reached, is removed.
Decision `DM-01M6RA8XSM9CZZX1ADX6G80T3X`; see also `depth` in the [activation changelog](/changelog/2026-11-07-a0-kind-model/).

## See also

- [HTTP status codes](/development/http-status-codes/): the 422 a relationship registration answers, and the statuses of registration and removal.
- [Building the service](/guides/service-build/#registering-packs): the operator settings for registration and its sources.
- [Divergence register](/conformance/divergence-register/#registration-div-601699-adr-0065): where registration differs from core.
- [Changelog: pack registration](/changelog/2026-11-07-a-pack-registration/).
- [Descriptors](/architecture/descriptors/#packyaml--pack-descriptor): the `pack.yaml` fields.
- [Enforce presets on an organization's projects](/guides/enforce-org-presets/): a governing preset and `policy.yaml`, in
  place of the retired `org-charter.yaml`.
- [Resolution model](/architecture/resolution-model/): where the compose sits in the pipeline.
- [Changelog: pack composition](/changelog/2026-11-03-pack-composition-depends-on-and-resolver/).
