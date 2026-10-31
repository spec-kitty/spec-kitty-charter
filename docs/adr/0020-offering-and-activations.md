---
title: "ADR-0020: The offering and activations are separate resources, and the service stores activations"
description: Proposes /offering for what can be used and /activations for active charters the service stores, activation writes reserved before 1.0.0, and a hosted-first contract.
doc_status: active
updated: 2026-10-31T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - adr/0010-cutover-scope-and-seam-boundary
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
  - adr/0012-pack-mutability-policy
  - adr/0013-persistent-deactivation
  - adr/0015-charter-repository-is-canonical-for-charter-structure
  - adr/0017-pack-schemas-imported-as-canonical-json-schema
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0019-packs-resource-model-and-hypermedia
  - adr/0021-contract-first-seam-generated-client-and-routing-proxy
  - adr/0022-contract-versions-start-at-0-x
  - architecture/offering-and-activation
  - architecture/packs-resource-model
  - api/service-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the offering tree, the activation document, the two build modes and what must land before 1.0.0.
  - profile: core-maintainer
    guidance_text: Read this for how core registers or builds an activation, how its seam maps its own files, and the requests to core.
  - profile: contract-consumer
    guidance_text: Read this for the activation resources, their status codes and how concurrent writes are refused.
---

# ADR-0020: The offering and activations are separate resources, and the service stores activations

## Status

**Accepted** — 2026-10-27 (provisional since 2026-10-24), proposed 2026-10-20 and merged with #123. Decider: Stijn Dejongh (operator). It is ratified as of 2026-10-27, and
it is still revisited on the triggers under [Revisit when](#revisit-when). The design is in
[Offering and activation](/architecture/offering-and-activation/).

Operator rulings of 2026-10-19 and 2026-10-20 are marked **(ruled)** below. Decisions marked
**(default)** follow the design squad's recommendation and stand as decided until revisited; each
names the alternative it rejected.

**Implementation note, 2026-10-24.** PR #139 removed the action-context read, so D1's "move under
`/offering`" is now its return there, on the canonical `Pack` model
([#126](https://github.com/spec-kitty/spec-kitty-charter/issues/126)).

This record supersedes or amends, point by point:

- [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/), Risks: "reserve
  only names and types, never structures". The activation document's structure is reserved in
  1.0 (D8).
- [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/) decision 2: "activation's own state
  logic stays in core". The state moves to the service (D5).
- [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/) decision 2 (one daemon per
  worktree) becomes one deployment profile, and decision 4 (pack locations and fingerprints from
  `config.yaml`) is withdrawn (D12). Its revisit trigger "a hosted or shared service (#24, #44)"
  has fired.
- [ADR-0013](/adr/0013-persistent-deactivation/) decisions 1, 6 and 7: the ledger is not a file
  in the project, core does not own it, and activation does not wait for a later contract set
  (D4, D5, D8).
- [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/) decision 8 (fingerprint
  over `config.yaml` and `charter.yaml`), decision 9 (the worktree token and browser refusal as
  contract behavior) and decision 10's "200 is the only success status" (D9, D12).
- [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/) decision 1's `/charter` resource,
  which this record splits into `/offering` and `/activations`.

Superseded in part by [ADR-0033](/adr/0033-local-active-charter-is-source-of-truth/) (2026-10-26): local `activation.yaml` wins visibly on a cold-start divergence (D5).

Amended 2026-10-31 (operator rulings on #125): the activation set is declared in the contract and reserved; its open
questions 1 and 2 are ruled. See [Amendment 2026-10-31](#amendment-2026-10-31-the-activation-set-is-declared-125).

## Context

ADR-0019 gave `/packs` one meaning and left the composition of the charter, "which packs are in
force and in which merge order", to a resource it named `/charter`. The cutover read inventory
(epic [#94](https://github.com/spec-kitty/spec-kitty-charter/issues/94)) needs that resource for
the merged graph (R3, [#97](https://github.com/spec-kitty/spec-kitty-charter/issues/97)), mission
types (R5, [#99](https://github.com/spec-kitty/spec-kitty-charter/issues/99)) and, above all, the
effective sets (R9, [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103)), which
gate cutover stage 3 (ADR-0018 D5).

Today's records assume one service per worktree that reads the project's `config.yaml` and
`charter.yaml` (ADR-0011 D4, ADR-0018 D8), and an activation ledger that is a file in the project,
owned by core (ADR-0013 D1, D6). The operator overturned that premise on 2026-10-20:

> We will never read a project's config directly. This service is intended to be stand-alone,
> and hosted-first. Any activation of charter elements must therefore happen against an
> identifier, and stored on the service's repository.
>
> Core owns the interview, and the config is written there. It either registers the existing
> activation config in the web service, or it uses the web service to create an empty one, which it
> then configures through API calls. When in the second mode, core finalizes by fetching the full
> activation config file, so it can store it locally.

A design squad (architecture, core-fidelity and contract-review lenses) ran on 2026-10-20 before
this record. Its findings that shape the decisions:

- **No release exists.** No tag is cut and [#45](https://github.com/spec-kitty/spec-kitty-charter/issues/45)
  is open. The contract is published as 0.x until it is stable
  ([ADR-0022](/adr/0022-contract-versions-start-at-0-x/)), so every reshape below can land in a
  0.x minor. After 1.0.0, each one is breaking.
- **Core computes activation from many keys.** `activated_<plural>` for eleven kinds (three-state:
  absent, empty, a list), `activated_kinds`, `mission_type_activations` (absent means none, unlike
  every other key), presets with replace semantics, org `required_*` merged in at write time, a
  language filter, context-scoped activations and, on the unmerged core branch
  `core-align-3-persistent-deactivation`, `deactivated_<plural>` deny lists. `locked` is not in
  core.
- **Core's behavior has gaps a service must not copy.** On a scratch project with core 4.0.0rc8,
  `activate directive EVD_003` failed with "Unknown directive ID" although a preset had written that
  id, the allow list then mixed spellings, an org-required directive was not served under an
  explicit allow list, and the cascade report was text only.
- **The core branch rejects unknown top-level keys** in its new `.kittify/charter/activation.yaml`.
  A file that must round-trip with a service document cannot do that.

## Decision

### D1. Three top-level resources, one per domain concept (ruled)

The resources follow the glossary, so developers and users find the concept they already know:

| Resource | Concept | What it answers |
|---|---|---|
| `/packs` | Charter Pack | The packs this service knows (ADR-0019) |
| `/offering` | Charter offering, the offer side | What can be used: the merged offering of the packs in force, with its artifacts and provenance, graph, presets, kinds, mission types and actions |
| `/activations` | Active charter, the activation side | Which of it is in force for one activation, and the effective charter that follows (D2, D7) |

The offering deals with what can be used and never with a client's choices; an activation deals
with the effective charter and never restates the offering. There is no `/charter` resource:
"charter" names both sides together. Nesting activations under `/offering` was rejected because
it blurs the two concepts.

`/offering` is the **service's configured offering**: the built-in pack plus the org and project
packs the service is configured with or has registered (ADR-0005). It names no project, so it is
never project-effective. An activation document may name its own packs (D4); until it does, it
uses the service's offering.

The action-context read moves under it before 1.0.0, with no alias:
`GET /offering/mission-types/{missionType}/actions/{action}/context`. Its operation id,
parameters, schema, refusals and headers are unchanged, and it stays the unfiltered walk of
ADR-0018 D1.

### D2. An activation is a stored document keyed by an opaque `activationId` (ruled; placement default)

The service stores activation documents in its own repository, behind an `ActivationRepository`
port in the storage ring (ADR-0005 D4, ADR-0006). It never reads a project's files.

- **The key is `activationId`, not a project id (ruled).** The charter service is agnostic of
  projects; project management can live in a separate domain. A client decides what one
  activation stands for: one project, one branch of a project, or one worktree. Per-branch
  charters are therefore two activations, not a conflict inside one.
- **The id is opaque.** Pattern `^[A-Za-z0-9][A-Za-z0-9-]{0,63}$`, which a UUID and a ULID both
  satisfy. The service never parses it.
- **Placement (ruled):** `/activations/{activationId}`, a top-level resource (D1). Rejected:
  `/offering/activations/…`, which mixes the offer side and the activation side, and
  `/projects/{projectId}/…`, which brings back the project concept the ruling removed.

### D3. A client can choose the id, or the service can mint it (ruled)

| Operation | Who picks the id | Answer |
|---|---|---|
| `PUT /activations/{activationId}` | The client | `201 Created` with `Location` and `ETag` when it created the document; `200` when it replaced one |
| `POST /activations` | The service, a ULID | `201 Created` with `Location` and `ETag` |

A `PUT` with `If-None-Match: *` creates only: if the document exists the answer is `412`. Core
can use its existing `project.uuid`, or any id of its own, with `PUT`; a client without a stable
id of its own uses `POST`.

### D4. The activation document is minted here, in camelCase, and core maps to it (ruled)

Two schemas, both closed, both minted in this repository (ADR-0015):

- **`ActivationConfig`** is the document: only the state a client authors. It is the `PUT` body,
  the `config` member of every answer, and what core's seam maps its own files to and from. Its JSON Schema lives at
  `contracts/offering/schemas/ActivationConfig.schema.json` (JSON Schema 2020-12, the
  [ADR-0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/) pattern), and the OpenAPI
  contract references it. *(Amended 2026-10-31: the contract mirrors it, and three checks keep the two equal;
  `contextActivations` and `appliedPresets` are left out, `packs` holds `{packId, version?}`, and `Activation`
  carries `governanceFingerprint`. See the amendment.)*
- **`Activation`** is the resource: `activationId`, `revision`, `config` (an `ActivationConfig`),
  a derived `summary` (counts of active, inactive, excluded, required, locked and skipped
  artifacts) and `_links`. A config file carries no links and no server fields, so it is wrapped,
  not extended.

`ActivationConfig`, in outline (the schema issue fixes every field before 1.0.0; *fixed 2026-10-31 by #125: `packs`
holds `{packId, version?}` objects, and the last two rows are left out of the document; see the amendment*):

| Field | Meaning | Core's key today |
|---|---|---|
| `schemaVersion` | `1` | — |
| `packs` | Pack ids in merge order; a later pack wins over an earlier one. Absent means the service's offering (D1) | `charter_packs` in `config.yaml` |
| `activatedKinds` | Kind list; absent means every kind | `activated_kinds` |
| `missionTypes` | Mission types in force; absent means **none** | `mission_type_activations` |
| `selected` | Map from kind to an id list; a kind that is absent takes its default (all, except `skill`: org-required only) | `activated_<plural>` |
| `deactivated` | Map from a known kind to a non-empty id list: the user's deny list | `deactivated_<plural>` (core branch) |
| `languages` | Language list, as authored; core's derived languages are not sent | `catalog.languages` |
| `contextActivations` | *Left out (ruling 2, 2026-10-31).* Artifacts active only in one mission type or action | `governance.activations` |
| `appliedPresets` | *Left out (ruling 3, 2026-10-31).* Provenance only: which presets were applied, when | not persisted today |

**Three states, exactly as core has them** (core response, 2026-10-20): for `activatedKinds`,
`missionTypes`, `languages` and each `selected.<kind>`, an absent key is the default, `[]` is
empty, and a list is that set. `null` is not a value.

**Content alignment, not file shape (ruled, 2026-10-20, on the core side).** Core keeps its own
file layout: `.kittify/charter/activation.yaml` holds the activation keys, while `packs`,
`languages` and `contextActivations` stay in `config.yaml` and `charter.yaml` (`contextActivations` is no longer
mapped: the document leaves it out, amendment of 2026-10-31). Core's seam maps
those files to and from `ActivationConfig` in one module. The contract fixes the content, not
where core keeps it.

The wire names are camelCase like the rest of the contract; core maps its snake_case keys at its
seam. The document stores **canonical form**:

- ids are spelled as the declared id (the tail of the artifact's URN). A write may use another
  known spelling, and is normalized. An unknown id is `422 activation_invalid`, and so is a
  `deactivated` key for an unknown kind;
- lists are
deduplicated and sorted, empty deny lists are omitted, and keys are in schema order.
`revision` and the `ETag` are the `sha256:` digest of the RFC 8785 (JCS) serialization of the
canonical config, so two equivalent writes give the same strong `ETag`, and `GET` is byte-stable
for one revision.

Not stored, because it is derived from the packs: the org `required_*` floor, `locked` and other
mutability levels (ADR-0012), the set of available artifacts, and the skipped report.

### D5. The service is the authority once activation routes to it (ruled)

Once an activation operation is routed to the service (ADR-0021 D5, after 1.0.0), the service's
document is the one in force. Core's files then hold what its seam last mapped from a fetched
document. When they differ, the service wins and core fetches again; core sends its content only
to register it (mode 1, D6) or after the operator chooses to overwrite. Until then, core's files
are the authority, behind the in-process backend.

*Superseded in part by ADR-0033: the local file is the source of truth; on a cold-start divergence local wins, visibly.*

### D6. Two ways to build a document

**Mode 1, register.** Core sends its existing content, mapped by its seam:
`PUT /activations/{activationId}` with an `ActivationConfig` body. `If-Match` is optional:
without it the write is last-writer-wins, because core's content is what it means to register.

**Mode 2, build and fetch.**

1. **Create an empty document.** `PUT` with `If-None-Match: *` and `{"schemaVersion": 1}`, or
   `POST /activations`. An empty document means every kind takes its default, so every
   offering artifact except skills is active; core then narrows it.
2. **Configure it** with sub-resource writes. Each one needs `If-Match: <revision>` and answers
   `200` with an `ActivationChange` (the new `revision`, the ids applied, the skipped report and
   `_links`):

   | Change | Request |
   |---|---|
   | Select, with cascade | `PUT …/selections/{kind}/{artifactId}`, body `{"cascade": "<scope>", "includeDeactivated": false}` |
   | Unselect | `DELETE …/selections/{kind}/{artifactId}` |
   | Deactivate, with optional cascade | `PUT …/exclusions/{kind}/{artifactId}`, body `{"cascade": "<scope>"}` |
   | Re-activate | `DELETE …/exclusions/{kind}/{artifactId}` |
   | Apply a preset | `PUT …/presets/{presetId}`, body `{"force": false}` |
   | Change a scalar field (`languages`, `missionTypes`, `activatedKinds`) | A read-modify-write `PUT` of the whole document with `If-Match` |

   **The operations carry core's verbs (ruled, 2026-10-20).** Each one is a single write with
   one new revision:
   - **select** adds the id to `selected` and clears the id's own exclusion, as `charter activate`
     does (ADR-0013 D3);
   - **deactivate** adds the id to `deactivated` and removes it from `selected`, as
     `charter deactivate` does;
   - **unselect** and **re-activate** are the narrow operations: each changes one list only.

   **Cascades:**
   - `cascade` names one of core's cascade scopes; the schema issue fixes the list. *(Fixed 2026-10-31: a non-empty
     array of activatable kinds, or exactly `["all"]`; see the amendment.)*
   - A mission type can be a cascade source: `{kind}` is `mission-type`. *(2026-10-31: spelled `mission_type`, and a
     `{kind}` of selections only, since mission types have no deny list.)*
   - A cascading select stops at a deactivated artifact and reports it (ADR-0013 D4), unless
     `includeDeactivated` is `true`, which clears the exclusions the walk reaches.
   - A cascading deactivate removes the reached artifacts from `selected` but records a deny
     entry only for the named id (ADR-0013 D3), and keeps an artifact another active one still
     references.

   **Presets:** a preset expands into `selected` when it is applied, so writing the document back
   never applies it twice.
   - Without `force`, it never removes a deny entry.
   - With `"force": true` (ruled, 2026-10-20, matching core's `--preset … --force`), it clears the
     exclusions for the ids the preset lists and reports them in the change.
3. **Finalize.** `GET /activations/{activationId}`; core's seam maps `config` back onto its
   files.

**The skipped report** (`SkippedReport`) takes core's structure, which is already structured,
in camelCase:

- `skippedByScope`;
- `skippedDeactivated`, each entry with `viaRequires` and `requiredBy`;
- `unreachedBehindDeactivated`;
- for a cascading deactivate, `skippedShared`, each entry with the active artifact that still
  references it.

**Round trip.** For every stored document, a `PUT` of its `config` answers `200` with the same
`ETag`. A contract scenario and a property test pin this.

`DELETE /activations/{activationId}` removes a document: `204`, honoring `If-Match`. A
second `DELETE` is a `404` `activation_not_found` **(default**; rejected: `204` again, which hides
a client's mistaken id).

### D7. Effective reads are sub-resources of an activation

*(Declared 2026-10-31, #125: `EffectiveArtifactList` (paged), `ActivationSkippedReport`, `EffectiveGraph` and
`EffectiveActionContext`, closed and minimal; #103 may reshape them. See the amendment.)*

The project-effective reads of R9 are resources under the activation, with their own closed
schemas, never a filter on the offering's reads (ADR-0018 D1 and its rejected `activationFiltered`
flag):

| Resource | Answer |
|---|---|
| `GET …/{activationId}/artifacts{?kind,state}` | Each artifact with its `state` (`active`, `inactive`, `excluded`), its policy facts (`required`, `policy`) and a closed `reason` |
| `GET …/{activationId}/skipped` | The skipped report (ADR-0013 D4) |
| `GET …/{activationId}/graph` | The activation-filtered graph |
| `GET …/{activationId}/mission-types/{missionType}/actions/{action}/context{?depth}` | The effective action context, `EffectiveActionContext` |

Each effective answer carries the activation's `revision` beside `governanceFingerprint`, so a
cached answer is tied to both the document and the offering.

**Required and locked artifacts are a floor at read time (default).** An artifact a pack requires
or locks (ADR-0012 D7) is active whatever the document says, and writing a document that
deactivates one is refused. Core today promotes `required_*` only at write time, and the squad's
run showed a required directive not served under an existing allow list; the service's answer is
recorded in the [divergence register](/conformance/divergence-register/) as an intended
divergence. Rejected: matching core, which would serve a charter that breaks its own pack policy.

### D8. The activation operations are declared early and answer `501` until built (ruled: reserved)

Every operation of D3, D6 and D7 is declared in full in an early 0.x minor *(2026-10-31: inside the unreleased
0.1.0-SNAPSHOT, by the operator's ruling; 13 operations, #125)*, ahead of the other
reshapes, because core's deny-list work codes against it (ADR-0022 D5): request body, success
responses, refusals and headers. After 1.0.0 a request body or a success status added to an
existing operation is breaking, so declaring only the path would reserve nothing. Each reserved operation also:

- declares a `501 Not Implemented` `Problem` with no code;
- carries `x-reserved: {contractSet: activation, issue: "#…"}`;
- is placed under its own tag, `Activation`, so its generated interface is implemented by one
  controller that answers `501`;
- is pinned by a `@contract @reserved` scenario that it answers `501`, as reserved refusal codes
  are pinned today.

New lint rules check the shape: `x-reserved-shape`, a reserved operation declares `501`, and a
built operation does not. No link advertises a reserved operation (ADR-0019 D3). When an operation
is built, its `501` and `x-reserved` are removed, which is not breaking.

These are the service's own writes behind its storage port, not core's Python until stage 5
(ADR-0010 D3). They gate R9 and therefore cutover stage 3 (ADR-0018 D5).

### D9. Status codes and refusals for writes

| Status | When | Body |
|---|---|---|
| `200` | A replace, a sub-resource write, every read | The declared schema, with `ETag` |
| `201` | A `PUT` or `POST` that created a document | `Activation`, with `Location` and `ETag` |
| `204` | `DELETE` of a document | None |
| `404` | `activation_not_found`, `artifact_not_found`, `preset_not_found` | `CharterRefusal` |
| `409` | `artifact_required`, `artifact_locked`: a deactivation the pack policy forbids, whether by a deactivate call or a registered document that holds a deny entry on a required or locked id; nothing is stored | `CharterRefusal` naming every requiring pack |
| `412` | `If-Match` or `If-None-Match` failed | `Problem`, no code |
| `415` | A body that is not `application/json` | `Problem`, no code |
| `422` | `activation_invalid`: a well-formed document naming an unknown pack, kind or artifact (a `deactivated` key for an unknown kind included), or failing the schema | `CharterRefusal` with `violations` |
| `428` | A sub-resource write without `If-Match` | `Problem`, no code |
| `501` | A reserved operation (D8) | `Problem`, no code |

**Stale and unknown deny entries are refused (ruled, 2026-10-20).** A document is never stored
with a deny entry the service would ignore. Core treats such entries as inert, so its seam drops
them, or asks the user, before it registers.

`412` and `428` are HTTP preconditions, not charter facts, so they carry no code **(default**;
rejected: `activation_precondition_failed` codes, which would duplicate the status). `412` clears
after the client fetches the document again; the status-code rules gain that "retry after re-read"
class.

The lint rule `error-responses-are-problems` is amended so `201` and `204` are success statuses,
and ADR-0018 D10's "200 only" is superseded.

### D10. Refusal codes and `CharterRefusal` fields are reserved before 1.0.0 (default)

The codes of D9 enter `CharterRefusalCode` with `x-refusal` entries and `raised: false`.
`CharterRefusal` gains two optional members, because it is closed and they cannot be added after
the tag:

- `requiredBy`: a list of `{packId, via}`, one entry per pack that requires or locks the artifact,
  where `via` names the `extends:` chain it is inherited through. ADR-0012 D7 asks that the refusal
  name the pack, and core can name several (core response, 2026-10-20);
- `violations`: a list of `{pointer, code, detail}` for a `422`, where `pointer` is an RFC 6901
  JSON Pointer into the body.

The vendored `Problem` was to stay unchanged. *(Corrected 2026-10-31: `CharterRefusal` extends the closed vendored
`Problem`, so the two members are optional members of `Problem` itself; see the amendment.)* Rejected: a `422` with
prose only, which leaves core to parse `detail`.

### D11. The project's own content is a pack (default)

Project-tier content (synthesized artifacts and project-authored directives) is registered as a
pack of level `project` (ADR-0005) before a document names it in `packs`. An activation document
holds choices, never content. Rejected: uploading project content inside the document, which mixes
packs and activations.

### D12. The contract becomes hosted-first before 1.0.0 (default)

Every place the contract assumes a per-worktree daemon that reads project files is reshaped
before 1.0.0, in a 0.x minor:

- **Security scheme.** An issuer-backed bearer (`oauth2`, client credentials for core, with
  `openIdConnect` discovery) with scopes `offering:read`, `activation:read` and
  `activation:write`, declared on every operation, the reserved ones included. The per-worktree
  token of ADR-0018 D9 becomes a **local deployment profile** that issues a bearer holding every
  scope. Rejected: keeping `charterWorktreeToken` as the only scheme, whose name and description
  bind the contract to one client on loopback.
- **Per-activation authorization.** A token is entitled to a set of activation ids. A request for
  an activation the token may not see is answered as `404 activation_not_found`, so ids do not
  leak. Rejected: a coded `403`, which confirms the id exists.
- **Browser and loopback guard.** The `Host`, `Origin` and `Sec-Fetch-*` refusal becomes part of
  the local deployment profile, out of the `@contract` scenarios. A hosted deployment uses CORS
  policy, which is not contract.
- **Headers and service info.** `Charter-Worktree` becomes `Charter-Instance`, the opaque id of the
  serving instance, and `ServiceInfo.worktree` becomes `instance`. Rejected: dropping the header,
  which loses the stale-daemon check ADR-0011 relies on.
- **Fingerprint.** `Charter-Source-Fingerprint` and `governanceFingerprint` cover pack files only.
  The scenario that refuses `pack_unreadable` on `project-config/config.yaml` and the
  `charter.project-config-dir` property are removed. An activation's freshness is its `revision`.

## Consequences

### Positive

- One service can serve many clients and many activations, so hosting needs no 2.0.
- Core's interview stays in core, and the result is stored where every reader of the charter can
  reach it. The cutover's R9 gate reads one stored document instead of re-deriving a project's
  files.
- The activation document is one schema with a byte-stable round trip, and core computes the same
  revision, so core's mapped content can never drift silently: a changed copy has a different
  `ETag`.
- Per-branch or per-worktree charters need no special case: they are different ids.
- Core's id-spelling and required-floor gaps are fixed at the source instead of copied.

### Negative

- The contract grows: four schemas, about twelve reserved operations, three conditional headers,
  six refusal codes, two `CharterRefusal` members, a security scheme with scopes and three lint
  rules, all before 1.0.0. *(As declared on 2026-10-31: 13 reserved operations; the four document schemas plus the
  effective-read, request-body and `Links`/`Link` schemas; the two members on the shared `Problem`; four new lint
  rules and one amended; the scopes deferred to the hosted-first minor.)*
- The activation set must be built before cutover stage 3, not after stage 5, which moves work
  forward.
- Core must change its plan: the unmerged deny-list branch, its seam and its ADR
  `4.x/2026-10-16-1` (activation stays in core until after GA) are overtaken.
- `PUT` without `If-Match` is last-writer-wins. Mode 1 accepts that on purpose; two clients
  registering the same id unconditionally can overwrite each other.

### Risks

- **The reserved surface is wrong once built.** Mitigation: the operations stay `501` until each
  lands with its scenarios, and the schema issue settles every field before 1.0.0 *(settled 2026-10-31, #125)*. In 0.x, a reserved
  shape that proves wrong in core's integration changes in the next minor.
- **Normalized ids surprise core.** Content core registered comes back with canonical spellings.
  Mitigation: core emits declared ids already (its response of 2026-10-20), and the `ETag` changes
  only with meaning, not spelling.
- **Authorization is under-specified.** Which identity may write which activation is decided by the
  deployment's issuer. Mitigation: the contract fixes the scopes and the `404` rule; tenancy is a
  later record (ADR-0005).

## Alternatives considered

### B: the service reads the project's files

Keeps today's per-worktree design. Rejected by the operator's hosted-first ruling.

### C: the activation stays in core, and the service only answers reads it is sent

The seam would push the activation with every effective read. Rejected: every reader would need
core's files, and two readers could send two different activations for one project.

### D: one schema with `readOnly` server fields instead of an envelope

Rejected: a config file must not carry links or a revision, and a closed request schema that
accepts and ignores `readOnly` members is fragile in generated clients.

### E: `PATCH` with `application/merge-patch+json` for incremental changes

Rejected for now: it adds a second media type and validation path, and merge-patch replaces whole
lists, which is the wrong semantics for id sets. It can be added later as a new operation.

### F: add the activation set late, after the other reshapes

New paths are additive, so this is allowed. Rejected by the operator's ruling to reserve the
writes, and because core's deny-list work needs the document and operations first.

## Revisit when

- Core's interview needs a change the sub-resource writes cannot express.
- A second domain (project management) needs to look up activations by something other than their
  id.
- A hosted deployment needs tenancy beyond per-token activation entitlements.
- The schema issue shows a field of the outline above cannot be canonical.

## Open questions before 1.0.0

These need a ruling before 1.0.0, because each is a closed shape. Core's integration on 0.x
(ADR-0022) should inform them:

*(Questions 1 and 2 ruled 2026-10-31: both left out; see the amendment.)*

1. **Provenance per entry.** Should the document keep, per artifact, the last decision (how, when,
   by whom), as core's deny-list branch proposes? If so, is `by` an opaque actor id?
2. **Context activations.** Does `contextActivations` stay in the document, or move to the effective
   read as a derived fact?
3. **The `Links` shape** (ADR-0019 D3) applies here too: the activation resources carry many
   relations. *(Settled 2026-10-31: the shared `Links`/`Link` components, agreed with #126; the activation answers
   carry `self` until the writes are built.)*

## Requests to core

- **Map the document in one place.** Core keeps its file layout (D4). One seam module maps
  `.kittify/charter/activation.yaml`, `config.yaml` and `charter.yaml` to and from
  `ActivationConfig`, computes the revision, and translates refusals. Accepted in core's response
  of 2026-10-20 (`charter.activation.activation_contract`).
- **Register instead of reading locally, once routed.** After the switch (ADR-0021 D5), the seam
  registers the document (mode 1) or builds it (mode 2), and maps back what it fetches.
- **Pick the id.** Use `project.uuid` with `PUT`, or another stable id core already keeps.
- **Fix the id spelling gap.** `charter activate` must accept every spelling a preset writes; the
  service normalizes, but core's own commands should not fail on its own output.
- **Amend core ADR `4.x/2026-10-16-1`.** Activation state moves to the service for the cutover.
  Accepted in core's response of 2026-10-20: the service is authoritative after the 1.0.0
  cutover, and switching to the charter surface is a core 4.0 GA deliverable
  (spec-kitty/spec-kitty#6518).

## Amendment 2026-10-31: the activation set is declared (#125)

The schema issue this record names ([#125](https://github.com/spec-kitty/spec-kitty-charter/issues/125)) declared
the activation set in the contract. The operator ruled on its open points on 2026-10-31 (all as the mission's
research recommended, one later changed); they are recorded here, and the decisions above carry notes where they
change.

**Rulings (2026-10-31).**

1. **Per-entry provenance** (open question 1) is **left out** of `ActivationConfig`. Core's ledger keeps none, and a
   timestamp would make the revision depend on time, not meaning. A later 0.x minor may add it.
2. **Context activations** (open question 2) are **left out** of the document. "For this Mission, also include X"
   is the client's, in the activation context it passes (ADR-0033 D4); the effective context read is where it
   surfaces (#103).
3. **`appliedPresets`** is **left out** too: core never persists the preset name, and `ActivationChange` reports a
   preset's effect.
4. **Skills and mission types.** `selected.skill` absent means only the required skills, which the must-have items
   of the governing presets derive (ADR-0031, ADR-0042). `missionTypes` absent and `[]` both mean none in force; the
   two stay distinct on the wire, so a fetched document registers unchanged.
5. **Kinds.** `selected` and `deactivated` hold one member per activatable artifact kind: every artifact kind except
   `template` and `asset`, and except `mission_type`, which `missionTypes` holds (11; the snake_case tokens of
   `NodeKind`). They align with the charter schema's `activated_<plural>` keys, which a test checks.
6. **Cascade.** `cascade` is a non-empty array of kinds, or exactly `["all"]`; absent means no cascade, as core's
   `CascadeScope` is either "all" or a set of kinds. `all` beside kinds is a semantic `422`.
7. **Packs** are `{packId, version?}`, in merge order (ADR-0033 D6); `version` absent means the version the service
   holds.
8. **`requiredBy` and `violations`** are optional members of the vendored shared `Problem`, not of `CharterRefusal`
   alone: `CharterRefusal` extends the closed `Problem`. **This corrects D10's "The vendored `Problem` is
   unchanged."**
9. **Effective reads** are all four declared now, with closed and minimal schemas (`EffectiveArtifactList`, paged;
   `ActivationSkippedReport`; `EffectiveGraph`; `EffectiveActionContext`). #103 may reshape them in a later minor.
10. **The JSON Schema file is canonical; the contract mirrors it.** `contracts/offering/schemas/ActivationConfig.schema.json`
    is the ADR-0017 artifact; `schemas/ActivationConfig.yaml` and its parts mirror it, and three checks keep them
    equal (a structural comparison, and a shared corpus through networknt and through the scenario validator). **D4's
    "the OpenAPI contract references it" reads "mirrors it".**
11. **The 501 pin.** A `@reserved` scenario may call a reserved operation and expect `501` (D8); the scenario README
    has two forms of `@reserved`.
12. **Version (changed by the operator, 2026-10-31).** The set lands inside the unreleased `0.1.0-SNAPSHOT`, beside
    #126, under its `### Breaking` subsection; `info.version` stays `0.1.0-SNAPSHOT`. It was first ruled
    `0.2.0-SNAPSHOT`. ADR-0022 D5's order (0.1.0 first, then the activation minor) therefore collapses into 0.1.0.
13. **`x-stability-level`** (#46) does not touch reserved operations; `x-reserved` is their marker.
14. **No new record; no security scopes.** This amendment carries the rulings. D12's scopes come with the
    hosted-first minor.

**Design decisions made with them** (mission `activation-set`, research D-1 to D-15):

- **Validators (amends D4).** `Activation` carries `governanceFingerprint`, and its `summary` is derived for that
  fingerprint: an answer is byte-stable for one revision and one fingerprint. Its `ETag` is the quoted revision,
  which `If-Match` carries (or `*`). An effective read's `ETag` is opaque: a digest that changes with the revision or
  the fingerprint. JCS sorts keys itself, so the revision does not depend on member order; the service writes
  members in schema order.
- **Conditional headers are optional parameters**: a sub-resource write without `If-Match` is the declared `428`,
  not a framework `400`.
- **`mission_type` is a `{kind}` under `selections/` only**: mission types have no deny list, so an exclusion of one
  would not be the inverse of its `DELETE`. An unknown mission type is `mission_type_not_found`.
- **The 501 comes before binding.** A `@ReservedOperation` controller and an interceptor answer before the request is
  read, so a reserved operation answers `501` whatever its request holds. The controller lives in
  `packs.infra.api`, the one adapter allowed to name the generated types; the built activation set becomes a slice
  of its own, and the `PACKS_*` ArchUnit constants are generalized then (#140).
- **Growth.** A new activatable kind, effective state, policy level or reason is a contract change: a 0.x minor
  before 1.0.0, a major after. The activation links carry `self` only; `Link.method` admits `GET`, `POST` and `PUT`,
  and widens to `DELETE` when the writes are built.
- **Build notes.** When an operation is built: a String-to-enum converter for snake_case path kinds; unknown JSON
  members on activation bodies refused (`422`, never dropped); a body that fails bean validation answered
  `422 activation_invalid` with `violations`; `containerDefaultToNull` and the `NON_NULL` mix-in keep absent apart
  from `[]` (already in place).

## References

- [Offering and activation](/architecture/offering-and-activation/), the design.
- [ADR-0022](/adr/0022-contract-versions-start-at-0-x/), [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/), [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/),
  [ADR-0013](/adr/0013-persistent-deactivation/), [ADR-0012](/adr/0012-pack-mutability-policy/),
  [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/),
  [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/),
  [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/).
- [Development conventions](/development/): [HTTP status codes](/development/http-status-codes/),
  [Hypermedia and HATEOAS](/development/hypermedia-hateoas/).
- Issues: [#44](https://github.com/spec-kitty/spec-kitty-charter/issues/44) (register an active
  charter), [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103) (R9),
  [#24](https://github.com/spec-kitty/spec-kitty-charter/issues/24) (hosted service).
