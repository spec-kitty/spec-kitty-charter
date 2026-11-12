---
title: "ADR-0074: The remaining cutover reads, and the paths a client reads them by"
description: How the seven open cutover reads (R4, R11 to R16) are decided, and the paths assets, skills, glossary contexts and presets are read by.
doc_status: active
updated: 2026-11-12T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-12'
type: ADR
kind: ADR
related:
  - adr/0010-cutover-scope-and-seam-boundary
  - adr/0019-packs-resource-model-and-hypermedia
  - adr/0040-content-form-and-payload-kinds
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0049-glossary-contexts-and-terms
  - adr/0054-guards-are-enforced-checks-implemented-by-executable-assets
  - adr/0063-pack-mutability-policy-file
  - api/service-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you add a read for assets, skills, glossary contexts, presets or the model routing catalog, or a path under an artifact.
  - profile: core-maintainer
    guidance_text: Read this for which offering reads the service answers at the cutover, and which stay in core.
---

# ADR-0074: The remaining cutover reads, and the paths a client reads them by

## Status

**Accepted**, 2026-11-12. Decider: Stijn Dejongh (operator), in the Op that decides the open reads of epic
[#94](https://github.com/spec-kitty/spec-kitty-charter/issues/94).

- Decisions 1 to 7, one per read, were chosen by the operator from options that named the scope recorded here. Where
  a decision follows an existing convention (camel case, a refusal code), it says so.
- Decisions 8 to 14 were proposed after the operator asked for the paths to be re-checked for user experience and
  accessor consistency, reviewed by a two-lens squad, and then ruled one by one by the operator in an interview on
  2026-11-12. The operator kept ADR-0049's per-kind glossary list and extended it to assets and skills, gave the
  three new kinds literal item paths, and made an asset's `mime` the payload's `Content-Type`.

It **amends** two Accepted records, and each is annotated where it stands:

- [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/) decision 2: the asset rows `GET /packs/{packId}/assets`
  and `GET /packs/{packId}/assets/{path}` are replaced by id-addressed reads (decisions 2, 9 to 11 and 13).
- [ADR-0040](/adr/0040-content-form-and-payload-kinds/) decision 1: an asset's `mime` stays metadata for the store,
  but it becomes the `Content-Type` of the payload read when it is a well-formed `type/subtype` (decision 11).

It **amends and extends** [ADR-0049](/adr/0049-glossary-contexts-and-terms/) decision 7: `?kind=glossary` is not
replaced by the per-kind list read; it stays beside it, and the per-kind list is given to assets and skills too
(decision 9).

## Context

Epic #94 lists every read core makes of the offering, and asks each to be decided: a contract operation, exempt, or
out of the cutover. Seven reads were still open on 2026-11-12. Here is what main holds for them:

- **R4, presets (#98).** Packs are listed by `getOffering` (PR #303) and read by `getPack`. A preset can be applied
  (`applyActivationPreset`, PR #399), but no operation reads one. A preset is a pack-level file, not an artifact
  ([ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/) decisions 2 and 7), so the kind
  read cannot serve it.
- **R11 to R13: assets, glossaries and skills (#105 to #107).** `asset`, `skill` and `glossary_pack` are artifact
  kinds in the kernel and in `contracts/packs/file-kinds.yaml`. `ArtifactReadService.SERVED` leaves all three out. A
  guard names its implementation as `asset:<id>`
  ([ADR-0054](/adr/0054-guards-are-enforced-checks-implemented-by-executable-assets/)), so a client that runs a guard
  must read the asset. The glossary schema is still core's `glossary-pack.schema.json`, not ADR-0049's glossary
  contexts and terms. Skill bodies are held as a `text` companion since
  [#432](https://github.com/spec-kitty/spec-kitty-charter/issues/432) (PR #501), but no operation serves them.
- **R14, model-task routing (#108).** The catalog and its matcher are in core. Neither an ADR nor the contract
  mentions them.
- **R15, override and pack policy (#109).** These reads are already answered by the effective reads:
  - Every `EffectiveArtifact` carries its `policy`, `open`, `extendable` or `locked`.
  - `getActivationSkippedReport` lists `overridden` and `overrideNotApplied` (PRs #427 and #448).
  - The policy file and the rule that the highest level along the closure wins are
    [ADR-0063](/adr/0063-pack-mutability-policy-file/), built in PR #398.
- **R16, pin, freshness and fingerprint (#110).** The built-in pin and the snapshot fingerprint belong to core's
  vendoring code. ADR-0042 decision 5 leaves one item to #110: which files the manifest lists, and restamping it after
  a write. Today a per-artifact write leaves the manifest stale.

The new reads must also fit the read tree that is already built. On main, that tree answers each question one way:

- A list is the generic `…/artifacts?kind=` read, in a pack and under an activation alike.
- The per-kind collection path, such as `/artifacts/directive`, is only a write target (`POST`).
- One item read serves every kind, `…/artifacts/{kind}/{artifactId}`. A kind gets a literal path only where its body
  needs one: `agent_profile` and `mission_type` in both trees, and `mission_step_contract` under an activation only
  (`getEffectiveMissionStepContract`; in a pack it takes the generic read).
- The parts of an artifact are sub-resources under its item: `steps/{stepId}`, `skeletons/{skeletonName}` and
  `references`.
- Most pack reads of an artifact have an activation twin at the same suffix. One does not: `listArtifactReferences`
  has no `/activations/{activationId}/artifacts/{kind}/{artifactId}/references`.

Two planned reads break that pattern. ADR-0019's `/assets/{path}` names a file by its path, not by its identity.
ADR-0049 decision 7 would make the glossary the only kind whose collection path answers a `GET`. The re-check put
both to the operator, who ruled on each (decisions 9 to 14).

## Decision

<!-- markdownlint-disable MD029 -->
<!-- Decisions are numbered across the sections so they can be cited by number. -->
### One decision per read (accepted)

1. **R4, presets (#98): a contract operation, in slice A, at P1.** The pack listing is served by `getOffering` and
   `getPack`. #98 is narrowed to the preset read:
   - `listPackPresets`, `GET /packs/{packId}/presets`;
   - `getPackPreset`, `GET /packs/{packId}/presets/{presetId}`.

   The shape is ADR-0042 decision 2's preset: `id`, `description` and the `activated_<plural>` lists, with each item's
   `must_have` mark (the operator's choice). The rest follows existing conventions: the fields are camel case, as in
   every schema of the contract, and a preset the pack does not hold is `404 preset_not_found`, the code
   `applyActivationPreset` already raises. Its description, scoped today to the activation's closure, is widened to a
   pack, and `getPackPreset` joins the operations that raise it. The merged `/offering/presets` stays deferred until
   merged content is read (ruling 3 on #126).
2. **R11, assets (#105): a contract operation, in slice A.** `asset` joins the served kinds. The item read answers the
   asset's descriptor (its sidecar, with `mime` and any `executable` block), and a `payload` sub-resource answers its
   bytes (decisions 10 and 11).
3. **R12, glossary contexts and terms (#106): a contract operation, in slice A.** #106 is retitled to glossary
   contexts and terms. The kind becomes `glossary`, in ADR-0049's shape. That shape replaces `glossary-pack` outright,
   with no reader of the old shape beside it. A term is an item inside its context, read as a part of it
   (decisions 11 and 12).
4. **R13, pack skills (#107): a contract operation, in slice A; command projection is core-side.** `skill` joins the
   served kinds, and its item read carries the skill body, the `text` companion #432 made readable. Projecting a skill into
   a harness's command files is installation work. It runs in core and is not a read of the offering.
5. **R14, model-task routing (#108): stays in the cutover as a contract operation.** The routing catalog moves into a
   pack as pack content, and the service serves it through the contract. ADR-0010 is not amended: the read is
   in scope, as its decision 2 says. How it is built is decision 8.
   [#151](https://github.com/spec-kitty/spec-kitty-charter/issues/151), the
   pipeline that keeps the catalog current, stays open. Once the catalog is pack content, that pipeline edits pack
   content.
6. **R15, override and pack policy (#109): already served.** The reads listed in the Context answer it, and no
   operation is added.
7. **R16 (#110): re-scoped to manifest membership and restamping, in slice B.** The built-in pin and the snapshot
   fingerprint are core's vendoring code. They are not tracked here (`docs/development/issue-tracker.md`, Rule 5).
   #110 becomes ADR-0042 decision 5's deferred item, with the scope the operator's chosen option named:
   - which files the manifest lists;
   - restamping the manifest on every write: a whole-pack registration, a per-artifact `PUT` and a `DELETE`.

   It records its rule as an ADR-0042 amendment when it is built. It is a write concern, so it leaves the read
   inventory for slice B, where registration lives.

### The routing catalog's build

8. **The build mission of decision 5 records two choices in an ADR of its own** before any pack changes:
   - the catalog's shape: an artifact kind, or a pack-level file as a preset is;
   - whether the service only serves the catalog, or also evaluates a task against it (core's `load` and `evaluate`
     do both today).

   Its paths follow decisions 9 to 14.

### The paths of the new reads

9. **Lists.** `GET …/artifacts?kind=<token>` lists every kind, in a pack and under an activation, the new kinds
   included. The three new kinds also answer a `GET` on their own collection path, with summary fields of their kind:
   - `GET /packs/{packId}/artifacts/asset`: id, `mime` and whether it carries an `executable` block;
   - `GET /packs/{packId}/artifacts/skill`: id, title, description and form;
   - `GET /packs/{packId}/artifacts/glossary`: id, name, description, tags and term count (ADR-0049 decision 7, which
     stands).

   The kinds served before this record keep `?kind=` only; their collection paths stay write targets.
10. **Items.** `asset`, `skill` and `glossary` each get a literal item path and an operation of their own, in both
    trees, as `agent_profile` and `mission_type` have: `…/artifacts/asset/{assetId}`, `…/artifacts/skill/{skillId}`
    and `…/artifacts/glossary/{contextId}`. Each answers its kind's own schema. As for an agent profile, the templated
    item read `…/artifacts/{kind}/{artifactId}` does not render them.
11. **Parts are sub-resources under the item.** This follows `steps/{stepId}`, `skeletons/{skeletonName}` and
    `references`. A part that is a collection takes a plural noun, and a single part takes a singular one:
    - `GET …/artifacts/asset/{assetId}/payload`: the asset's bytes. The `Content-Type` is the asset's `mime` when it is
      a well-formed `type/subtype`, and `application/octet-stream` when it is not; the item keeps `mime` as written,
      and validate mode still reports a malformed or mismatched one as a policy finding. This amends ADR-0040 decision
      1 for the payload read only: the store still never chooses a content form by `mime`.
    - `GET …/artifacts/glossary/{contextId}/terms/{termId}`: one term. The path mirrors the term's URN,
      `term:<context-id>/<term-id>`.
12. **Every artifact read has an activation twin at the same suffix.** The twin is under `/activations/{activationId}`
    instead of `/packs/{packId}`, and it answers the version in force: the per-kind lists, the items, the payload and
    the term read included. Two reads on main do not meet the rule yet and are gaps to fill in a later slice, filed as
    follow-ups: `listArtifactReferences` has no activation twin
    ([#532](https://github.com/spec-kitty/spec-kitty-charter/issues/532)), and `mission_step_contract` is read by a
    literal path under an activation but by the generic read in a pack
    ([#533](https://github.com/spec-kitty/spec-kitty-charter/issues/533)). A pack-level file that is not an artifact, a preset, is
    read only in its pack; at `/activations/{activationId}/presets/{presetId}` the noun stays the apply write.
13. **An artifact is addressed by its identity, never by its file path.** An asset is read by its id, which is the
    reference a guard writes (`asset:<id>`). ADR-0019's `/packs/{packId}/assets` and `/packs/{packId}/assets/{path}`
    are dropped. An id that is one path segment is a precondition: the asset and glossary schemas pin no `id`
    pattern today, so an id such as `a/payload` would make `…/asset/a/payload` ambiguous. The build pins the
    kebab-case id pattern the skill and preset schemas carry, and a pack that breaks it is refused as invalid content.
    The read route takes no client path, so a read cannot ask for a file outside the pack; the sidecar check at load
    (SHARING-004) stays.
14. **The path names the kind by its token**, in the singular: `asset`, `skill`, `glossary`. A collection is
    plural: under an artifact, `terms`, `steps` and `skeletons`; under a pack, `presets`.
<!-- markdownlint-enable MD029 -->

## Consequences

### Positive

- Every read of epic #94 is decided. Five are built in slice A: presets, assets, glossary contexts, skills and, with
  its own ADR, the routing catalog. #110 moves to slice B, and #109 is served already.
- `?kind=` still lists every kind, and every artifact read has its activation twin, so a generic client keeps
  working. A client of the new kinds gets a list and an item of their own shape, as for agent profiles and mission
  types. Presets, which are not artifacts, are read in their pack only.
- A payload is served with its own media type, so a client can hand it on without a lookup.
- Assets are read by identity, so the read the guard client makes is the reference the guard holds.

### Negative

- Slice A grows by four reads (presets, assets, glossary, skills), and the routing catalog adds a mission with a
  design step.
- The glossary read waits for the ADR-0049 shape to be built, which is a model change of its own.
- Two ways to list the new kinds: `?kind=` and their own collection path. The kinds served before keep one.
- Six new item operations (three kinds, two trees) and their controllers, plus the payload and term reads and their
  twins, widen slice A.

### Risks

- Serving the routing catalog moves content core owns today into a pack. Mitigation: the build ADR decides the shape
  before any pack is changed, and `spec-kitty-open-packs` follows the model.

## Alternatives considered

- **Keep ADR-0019's path-addressed asset reads.** Not chosen: the path is a file layout, not an identity, and a guard
  names the asset by id.
- **One accessor per question:** list every kind by `?kind=` only, read the new kinds through the templated item read,
  and serve every payload as `application/octet-stream`. Proposed by the re-check; not chosen by the operator, who
  preferred kind-shaped lists and items for the new kinds and a typed payload (2026-11-12).
- **A `GET` on every kind's collection path, the older kinds included.** Not chosen: it widens slice A to kinds
  that are already served.
- **Refuse a malformed `mime` at load.** Not chosen: real packs with a bad `mime` would stop registering; the
  payload falls back to `application/octet-stream` instead.
- **Take model routing out of the cutover with an ADR-0010 amendment.** Proposed in the Op, not chosen by the
  operator (2026-11-12).

## References

- Epic [#94](https://github.com/spec-kitty/spec-kitty-charter/issues/94); issues #98, #105 to #110, #151, #432, #532, #533; #109 is served and closed, and #110 is re-scoped to a write concern (decision 7)
- [Service contract](/api/service-contract/), "The other reads"
- [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/), [ADR-0040](/adr/0040-content-form-and-payload-kinds/), [ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/),
  [ADR-0049](/adr/0049-glossary-contexts-and-terms/), [ADR-0063](/adr/0063-pack-mutability-policy-file/)
