---
title: Service contract
description: The OpenAPI 3.1 contract of the charter service — the built operations, the planned reads and writes, the error model and the transports.
doc_status: draft
updated: 2026-11-01T00:00:00Z
type: API
kind: Reference
related:
  - architecture/contract-and-dtos
  - adr/0002-contract-source-of-record
  - adr/0018-action-context-read-and-seam-access-surface
  - conformance/divergence-register
  - api/compatibility
audience:
  - profile: contract-consumer
    guidance_text: Read this for the planned operations, the error model and the transports of the service.
---

# Service contract

The planned operations, error model and transports of the charter service's OpenAPI contract. The surface derives from the reads core's Python charter answers today (`charter.activation` and `charter.offering` in core), from core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) (the staged Java service strangler), [ADR-0001](/adr/0001-java-service-with-openapi-contract/) and [ADR-0002](/adr/0002-contract-source-of-record/). Every operation is a proposal until it is in `contracts/charter/openapi.yaml`.

> **Partly built.** The operations under [Built surface](#built-surface) are in the contract and served by the Java service: the API root, the service info, the contract document, the offering's pack list, the generic artifact reads and the registrations. The other operations on this page are designed only. See the [API overview](/api/).

## The contract files

The contract is in [`contracts/charter/`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/openapi.yaml), at `0.1.0-SNAPSHOT`, unreleased (published as 0.x until it is stable, [ADR-0022](/adr/0022-contract-versions-start-at-0-x/)):

- [`openapi.yaml`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/openapi.yaml): the header and the map from each path to its file;
- `paths/`, `schemas/`, `parameters/`, `responses/` and `examples/`: one definition per file;
- [`scenarios/`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/scenarios/README.md): the contract scenarios, the behavior half of the contract;
- [`CHANGELOG.md`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/CHANGELOG.md): every change, scenario changes included, and what is provisional.

The single-file bundle that core vendors is built from them by `./gradlew :contracts:bundleContract`
and uploaded by CI as the `contract-bundle` artifact
([Building the service](/guides/service-build/)). It keeps `components`: each file a definition references is one
component named after the file, and every `$ref` in the bundle points at one (#294).

The contract is OpenAPI 3.1, served under `/api/v1`, with closed response schemas and
`Problem` bodies for refusals. How it is laid out, generated and versioned is in
[Contract and DTOs](/architecture/contract-and-dtos/).

## Built surface

A client starts at the API root and follows links ([ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/),
[Hypermedia (HATEOAS)](/development/hypermedia-hateoas/)). Every JSON success answer carries a required `_links`;
every `href` starts with `/api/v1` and points to a built operation.

| Operation | Answers | Path |
|---|---|---|
| `getApiRoot` | The API root: links to `info`, `offering`, `artifacts` (the template `/api/v1/packs/{packId}/artifacts{?kind}`) and `service-desc`, plus `self`. The root is `/api/v1/`, with the trailing slash; `/api/v1` is a 404 | `GET /` |
| `getServiceInfo` | The contract version, the service's build version (`serviceVersion`), the worktree it serves and a fingerprint of the sources it answers from, with `self` and `service-desc` links and a `Link` header to the contract document | `GET /info` |
| `getOpenApiDocument` | The bundled contract the service was built from, as `application/yaml`, with a strong `ETag`; `304` on a matching `If-None-Match` | `GET /openapi.yaml` |
| `getOffering` | The configured packs in force, in the merge order (configured level, then pack id), each with its id, configured level, name, version and an `artifacts` link | `GET /offering` |
| `listPackArtifacts` | One configured pack's artifacts of the served kinds as summaries, filtered by an optional `kind` and paged (`pageSize` 1 to 200, 50 by default; `pageCursor`), with `self`, `first` and `next` links | `GET /packs/{packId}/artifacts` |
| `getPackArtifact` | One artifact in the common `Artifact` envelope (`kind`, `id`, `packId`, `_links`, and one kind-named member with the kind's representation); an `ETag` for a writable kind | `GET /packs/{packId}/artifacts/{kind}/{artifactId}` |
| `registerPackDirective`, `registerPackStyleguide`, `registerPackParadigm`, `registerPackToolguide` | Register an artifact of that kind; `201` with `Location` and the `Artifact` envelope | `POST /packs/{packId}/artifacts/<kind>` |
| `putPackDirective`, `putPackStyleguide`, `putPackParadigm`, `putPackToolguide` | Register or replace an artifact of that kind, with `If-Match`; `200` (`201` when it creates) with the `Artifact` envelope | `PUT /packs/{packId}/artifacts/<kind>/{artifactId}` |
| `putPackRelationship` | Register a relationship between a pack's components; answers `RelationshipRegistered`, linked to the pack's artifacts | `PUT /packs/{packId}/relationships` |

The served kinds are `tactic`, `directive`, `styleguide`, `paradigm` and `toolguide`. A tactic is read only; the
other four are written through the operations of their kind
([ADR-0026](/adr/0026-write-surface-is-functional-registration/) D1.2, as amended 2026-10-31). No answer carries an
`edit` link yet, so registration is not advertised by a link. A path kind the service does not serve (for
example `procedure`) is a 404 `kind_not_found`; an unknown `kind` filter is a 400 `Problem` without a code.
The `kind` of a summary and of an `Artifact` is an open string, not an enum: a client skips an item whose kind it
does not know, so a kind a later version serves (a glossary, a procedure) is an additive change.

**Literal write paths beside the templated read.** The write paths name their kind literally
(`/packs/{packId}/artifacts/directive/{artifactId}`) beside the templated
`/packs/{packId}/artifacts/{kind}/{artifactId}` (operator ruling 2 on
[#126](https://github.com/spec-kitty/spec-kitty-charter/issues/126)). The service and this repository's
conformance harness match the method first, so `GET /packs/p/artifacts/directive/X` is `getPackArtifact`. Generic
OpenAPI tooling matches the path item before the method: a third-party validator or mock resolves that `GET` to
the literal path item, which defines only `PUT`, and refuses it. The ruling stands; this is a known limit of such
tools.

Every operation requires the per-worktree bearer token (`charterWorktreeToken`). Every answer
carries `Charter-Contract-Version` and `Charter-Worktree`. `Charter-Source-Fingerprint` is declared,
and required, only on the 200 of a verified read: `getServiceInfo`, `getOffering`, `listPackArtifacts` and
`getPackArtifact` ([#175](https://github.com/spec-kitty/spec-kitty-charter/issues/175)). It carries the digest
that read verified. The 200s of the five reserved activation reads (`getActivation`, `listEffectiveArtifacts`,
`getActivationSkippedReport`, `getEffectiveGraph`, `getEffectiveActionContext`) declare it too, ahead of being
built; while they answer `501` they send none. No refusal, write, root or contract-document answer carries it.

**The offering in this version.** `GET /offering` is the pack list only, the smallest Gate-A set (operator ruling
3 on [#126](https://github.com/spec-kitty/spec-kitty-charter/issues/126), 2026-10-31). The level is the one
storage serves the pack at: the `pack_level` its `pack.yaml` declares, `org` when it declares no valid one
([ADR-0038](/adr/0038-one-configuration-root-a-directory-per-domain/)); the offering never recomputes it from
`pack.yaml`. Merging is not applied yet
([#10](https://github.com/spec-kitty/spec-kitty-charter/issues/10),
[#11](https://github.com/spec-kitty/spec-kitty-charter/issues/11)): the offering lists the packs, not a merged
result. An operator ruling of 2026-10-31 (option A of the question the WP03 review raised) settles a gap the
specification left open: a configured pack whose `pack.yaml` is missing, unreadable or invalid is not a readable
canonical pack. `GET /offering`, the artifact list and the artifact item refuse it with a 500 `pack_unreadable`,
so every listed pack has a `name` and a `version`; 404 `pack_not_found` is answered only for an identifier that
names no configured pack.

PR #139 (2026-10-21) rebuilt the service on one canonical `Pack` aggregate. It removed
`listPacks` (`GET /packs`), `refreshPacks` (`POST /packs/refresh`) and `getActionContext`
(`GET /mission-types/{missionType}/actions/{action}/context`) together with their first-pass
read model. [#126](https://github.com/spec-kitty/spec-kitty-charter/issues/126) replaced its one-route-per-kind
reads and writes with the artifact tree above, without an alias; the changes are in the contract
[CHANGELOG](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/CHANGELOG.md).

### The action-context decision (#95)

> **Not served on `main`.** The operation was removed by PR #139. ADR-0018's decisions below stand
> for when it returns as `GET /offering/mission-types/{missionType}/actions/{action}/context`
> (ADR-0020 D1, #126).

[ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/) decides what the read
answers. The contract files are the authority; this section only summarizes them.

- **What it is.** The offering's walk, for good. It never filters by activation, exclusion or
  selection. What applies in one project is a separate operation of the activation contract set
  (R9, [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103)), and the seam does not
  switch its context read to the service before that operation exists.
- **The walk.** Scope from the action node, `requires` (transitive), `suggests` (up to `depth`),
  `requires` of the suggested artifacts, and `vocabulary` (one hop). Each artifact is listed once,
  by the first step that reaches it, sorted by URN. The mission-type node is not walked.
- **`depth`** is the number of `suggests` hops: 0 to 2, default 2. It is not `hops`, which is the
  path length from the action. The seam maps a first load to 2, a recorded load to 1 and the
  public bundle read to 0.
- **Why.** A 1.0 answer must never change meaning without the client opting in, and core's code is
  evidence of intent, not a specification. The differences from core are in the
  [divergence register](/conformance/divergence-register/).

### Generated models

Each schema is generated as one class. The generator's parser reads some `additionalProperties:
false` as a schema object standing for `false`, which the generator took for an open map, so it
emitted copies such as `Problem1` and `Tactic1`. The build reads such a boolean schema back as its
boolean (issue #119), and a test fails if a copy comes back. The wire shape never depended on it.

### Security and freshness

- **Token.** Required bearer on every operation. The service refuses to start without a token of
  at least 32 characters unless `charter.insecure-no-token=true`, and refuses a token that comes
  from argv or a system property. Residual risks for the launcher review (#70): a port squatter
  receives the token, a derived worktree id is guessable, and a `${x}` placeholder resolved from
  argv is not refused yet (#121). A browser request (an `Origin` or `Sec-Fetch-*` header, or a
  `Host` whose name is not loopback) is a 403.
- **Fingerprint.** It covers, per configured pack in order, `pack.yaml`, every file of a loaded kind
  (today tactic, directive, styleguide, toolguide and paradigm files, at any depth) and the declared graph inputs (root
  `*.graph.yaml` shards, then `drg/fragment.yaml`), each as its path under the pack's configured
  name and its bytes; and, when configured, `config.yaml` and `charter.yaml` under the project
  configuration directory, with presence.
  The graph inputs are digested although no served read loads them yet. A generated-tree test
  pins that it digests exactly the declared files the load reads (#120).
- **Stale reads.** Every verified read digests its inputs before and after it loads, answers 200 with
  the digest it verified, and refuses with `stale_input` on a difference, a failed load included.
  A configured pack whose directory is missing or whose `pack.yaml` is missing, unreadable or invalid, or a pack
  holding a file the fingerprint covers that cannot be read or a directory that cannot be listed, is a 500
  `pack_unreadable` on every verified read that reads the packs. A file that vanishes while a pack is walked, as in an atomic save, is not an error.

Some of the 1.0 contract is reserved for the cutover work before that work exists, because a
response field added after 1.0 is a breaking change: the `impacts` relation, the reserved
refusal code and these values (ADR-0009 and ADR-0018, provisional).

## Planned read surface

The cutover puts every read core makes of the offering through this API
([ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)). Those reads are being listed and
decided one by one in the cutover read inventory, epic
[#94](https://github.com/spec-kitty/spec-kitty-charter/issues/94): each becomes an operation
in an additive contract set ([ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/)),
is exempt, or is out of the cutover. Until a read is decided it has no shape here.

| Read | Inventory issue | Today in core | In this version |
|---|---|---|---|
| Effective context (R9): what applies in one project, with activation, exclusions, selection roots, the language filter and the directive allowlist, built on the walk. **It gates cutover stage 3** | [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103) | `spec-kitty charter context --action <name> --mission-type <type>` | Not built |
| Tension annotation over authored tension relationships ([ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/), [ADR-0045](/adr/0045-tension-beside-the-relationships-and-nodes-keep-every-declared-field/)), in the creed contract set | [#16](https://github.com/spec-kitty/spec-kitty-charter/issues/16), [#77](https://github.com/spec-kitty/spec-kitty-charter/issues/77) | the `in_tension_with` annotation in `charter context` | Not built |
| Artifact bodies (R2) | [#96](https://github.com/spec-kitty/spec-kitty-charter/issues/96) | `charter.offering` repositories | Answered per pack, for the five served kinds, by `listPackArtifacts` and `getPackArtifact`; the merged `/offering/artifacts` waits for merge |
| Merged graph and graph queries (R3) | [#97](https://github.com/spec-kitty/spec-kitty-charter/issues/97) | `charter.offering.drg` callers | Deferred to the minor that brings merge |
| Pack listing with presets (R4) | [#98](https://github.com/spec-kitty/spec-kitty-charter/issues/98) | `spec-kitty charter pack list` | Partly answered by `GET /offering` (the configured packs in force); presets deferred |
| Mission types and the action index (R5) | [#99](https://github.com/spec-kitty/spec-kitty-charter/issues/99) | `specify_cli` mission repositories | Deferred to the minor that brings merge |
| Profile resolution and lineage | [#102](https://github.com/spec-kitty/spec-kitty-charter/issues/102) | `spec-kitty agent profile show`, profile-load contract | Not built |
| Active charter and effective activation sets | [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103) | `spec-kitty charter` status commands | Not built |
| The other reads (step contracts, expected-artifact manifests, templates, assets, glossary packs, pack skills, model routing, override policy, pack pins, structured-prompt blocks, the kind taxonomy) | [#100](https://github.com/spec-kitty/spec-kitty-charter/issues/100), [#101](https://github.com/spec-kitty/spec-kitty-charter/issues/101), [#104](https://github.com/spec-kitty/spec-kitty-charter/issues/104) to [#112](https://github.com/spec-kitty/spec-kitty-charter/issues/112) | various | Not built; the kind taxonomy, `/offering/kinds` (R18), is deferred to the minor that brings merge |

R1, R3, R5 and R18 are deferred to the minor that brings merge, activation and cascade
([#10](https://github.com/spec-kitty/spec-kitty-charter/issues/10),
[#11](https://github.com/spec-kitty/spec-kitty-charter/issues/11)): they answer merged content, which the service
does not compute yet (operator ruling 3 on [#126](https://github.com/spec-kitty/spec-kitty-charter/issues/126),
2026-10-31). No link points to them.

The action-context walk (R1, #95) and its `depth` parameter are decided
([ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/)) but not served: PR #139
removed the operation. The reserved 1.0 surface that is built is `ServiceInfo`, the source
fingerprint inputs and the per-worktree token
([#113](https://github.com/spec-kitty/spec-kitty-charter/issues/113)).

## Decided, not built: packs resources (ADR-0019)

[ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/) gives `/packs` one
meaning, the packs this service knows, and makes every pack a resource whose contents are
sub-resources. The design is in [Packs resource model](/architecture/packs-resource-model/), and
the conventions in [Development conventions](/development/).

| Method | Path | Proposed operationId | Purpose | Inventory issue |
|---|---|---|---|---|
| `GET` | `/packs/{packId}` | `getPack` | One pack: today's `PackSummary` fields | — |
| `GET` | `/packs/{packId}/graph` | `getPackGraph` | The nodes and edges the pack declares | [#97](https://github.com/spec-kitty/spec-kitty-charter/issues/97) |
| `GET` | `/packs/{packId}/presets` | `listPackPresets` | The pack's activation presets | [#98](https://github.com/spec-kitty/spec-kitty-charter/issues/98) |
| `GET` | `/packs/{packId}/assets` | `listPackAssets` | The pack's asset paths | [#105](https://github.com/spec-kitty/spec-kitty-charter/issues/105) |
| `GET` | `/packs/{packId}/assets/{path}` | `getPackAsset` | One asset file | [#105](https://github.com/spec-kitty/spec-kitty-charter/issues/105) |
| `POST` | `/packs` | `registerPack` | Register a pack (ADR-0005); `201` with `Location` (needs the lint amendment of ADR-0020 D9) | — |
| `GET` | `/packs/{packId}/resolution` | `getPackResolution` | Read-only resolution state (ADR-0007 decision 7) | — |
| `POST` | `/packs/{packId}/distributions` | `distributePack` | Distribute a pack (ADR-0005) | — |

The API root and the artifact reads of ADR-0019 D2 are built (see [Built surface](#built-surface)); other
artifact kinds, such as the glossary terms of a glossary pack
([#106](https://github.com/spec-kitty/spec-kitty-charter/issues/106)), join the served kinds of
`listPackArtifacts` and `getPackArtifact` when their reads are decided. ADR-0019 names `{packId}` as the pack's
`pack_id` ULID; the built reads accept the identifier a pack is configured under or its declared `pack_id`. The
merged offering of the packs in force is the `/offering` resource below.

**Landed before 1.0.0** (in contract `0.1.0`, [ADR-0022](/adr/0022-contract-versions-start-at-0-x/)): the
`Links` component, `GET /`, and a required `_links` on every JSON success schema. These changed closed response
schemas, which is breaking after 1.0.0. With open relation keys (ADR-0019 D3), the per-pack operations above and
their relations can follow additively.

## Offering and activations (ADR-0020): the offering partly built, the activation set declared

[ADR-0020](/adr/0020-offering-and-activations/) adds two top-level resources:
`/offering`, the offer side (what can be used, merged from the packs in force), and
`/activations`, the activation side (active charters). An activation is a document the service
stores under an opaque `activationId`; the service never reads a project's files. The design, with the request
sequences, is in [Offering and activation](/architecture/offering-and-activation/).

| Method | Path | Purpose | Inventory issue |
|---|---|---|---|
| `GET` | `/offering` | The packs in force, in merge order, and the offering's links. **Built** as the pack list only (`getOffering`); the merged reads below are not | [#98](https://github.com/spec-kitty/spec-kitty-charter/issues/98) |
| `GET` | `/offering/artifacts`, `/offering/artifacts/{kind}/{artifactId}` | Merged artifacts with policy facts and provenance | [#96](https://github.com/spec-kitty/spec-kitty-charter/issues/96) |
| `GET` | `/offering/graph`, `/offering/presets`, `/offering/kinds` | The merged graph, offered presets, the kind taxonomy | [#97](https://github.com/spec-kitty/spec-kitty-charter/issues/97), [#98](https://github.com/spec-kitty/spec-kitty-charter/issues/98), [#112](https://github.com/spec-kitty/spec-kitty-charter/issues/112) |
| `GET` | `/offering/mission-types…` | Mission types and the action index | [#99](https://github.com/spec-kitty/spec-kitty-charter/issues/99) |
| `GET` | `/offering/mission-types/{missionType}/actions/{action}/context` | Today's action-context read, **moved here before 1.0.0** | [#95](https://github.com/spec-kitty/spec-kitty-charter/issues/95) |
| `POST` | `/activations` | `createActivation`: create an activation; the service mints the id. **Declared, reserved (501)** | [#125](https://github.com/spec-kitty/spec-kitty-charter/issues/125) |
| `PUT`, `GET`, `DELETE` | `/activations/{activationId}` | `replaceActivation`, `getActivation`, `deleteActivation`: register or replace, read, remove an activation. **Declared, reserved (501)** | [#125](https://github.com/spec-kitty/spec-kitty-charter/issues/125) |
| `PUT`, `DELETE` | `…/{activationId}/selections/{kind}/{artifactId}`, `…/exclusions/{kind}/{artifactId}`; `PUT …/presets/{presetId}` | `selectActivationArtifact`, `unselectActivationArtifact`, `deactivateActivationArtifact`, `reactivateActivationArtifact`, `applyActivationPreset`: change one choice, with `If-Match`. **Declared, reserved (501)** | [#125](https://github.com/spec-kitty/spec-kitty-charter/issues/125) |
| `GET` | `…/{activationId}/artifacts`, `skipped`, `graph`, `mission-types/…/context` | `listEffectiveArtifacts`, `getActivationSkippedReport`, `getEffectiveGraph`, `getEffectiveActionContext`: the effective reads (R9). **Declared, reserved (501)**; R9 gates cutover stage 3 | [#125](https://github.com/spec-kitty/spec-kitty-charter/issues/125) declares them; [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103) builds them |

**The activation set is in the contract** (#125, inside `0.1.0-SNAPSHOT` by the operator's ruling of 2026-10-31):
every activation operation is declared in full under the tag `Activation`, with `x-reserved` and a `501`, and
answers `501` until it is built; the `ActivationConfig` document (canonical as JSON Schema 2020-12 in
`contracts/offering/schemas/`), `Activation`, `ActivationChange`, the skipped report and the effective-read schemas;
`ETag`, `If-Match` and `If-None-Match`; the six activation refusal codes, declared and not raised; and the shared
`Problem`'s `requiredBy` and `violations`. The rulings are in ADR-0020's amendment of 2026-10-31.

**Before 1.0.0, still to come:** the move of the action-context read and the hosted-first reshape of the security
scheme, `Charter-Worktree`, `ServiceInfo.worktree` and the fingerprint inputs (ADR-0020 D12).

## Planned write surface (later)

The activation writes are declared and reserved (#125) and are built before cutover stage 3 (ADR-0020). The other
writes come after the read stages, one at a time: charter document save and compile. See
[Strangler transition](/architecture/strangler-transition/).

## Error model

- Every refusal is a `Problem` body. The shared `Problem` schema and response in
  `contracts/_shared/` are canonical in this repository (ADR-0015), and every answer, the
  `default` `Problem` response included, carries `Charter-Contract-Version` and
  `Charter-Worktree`; only the 200 of a verified read carries `Charter-Source-Fingerprint`.
- Refusal codes are part of the contract, not implementation detail. They are snake_case on
  the wire; the integration seam in core maps them to core's own spellings. The codes and
  when each applies are defined in [`CharterRefusalCode`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/schemas/CharterRefusalCode.yaml),
  and the load findings that do not stop a read in [`DiagnosticCode`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/schemas/DiagnosticCode.yaml).
  `CharterRefusalCode` carries a machine-readable `x-refusal` map (status, retryable, raised)
  for every code. A refusal's examples and description name only raised codes, which `:contracts:check` checks
  ([#176](https://github.com/spec-kitty/spec-kitty-charter/issues/176)). The refusals of a reserved operation
  (`x-reserved`) describe it once built and are judged when it is built; until then it answers `501`.
- `kind_not_found` (404) is raised when the path of an artifact read names a kind the service does not serve.
  The pack is judged first, so an unknown pack is `pack_not_found` whatever the kind; an id the pack does not hold
  is the not-found code of its kind (`tactic_not_found`, …).
- `stale_input` is raised by every verified read (`getServiceInfo`, `getOffering`, `listPackArtifacts` and
  `getPackArtifact`): inputs changed while the answer was computed, a failed
  load whose inputs changed included. It may be retried.
- `governance_policy_violation` (409) is raised by the registration writes when the mutability level of the
  pack that owns the target does not permit the write (ADR-0012, epic #55). No policy file is read yet, so every
  target is open and no write is refused with it until one is.
- `pack_level_mismatch` and `drg_schema_error` are declared and raised by no operation: no operation reaches the
  whole-pack load that still refuses a graph as `drg_schema_error`. `ReservedRefusalCodesTest` reads every code the
  `x-refusal` map marks not raised and fails when a main class on the wire names one (#293).
- **401 and 403 are transport failures, not refusals.** They are `Problem` bodies without a
  charter code: 401 is a missing, malformed or wrong token, 403 is a browser request. The seam
  treats both as transport failures. They carry no fingerprint.
- Fail loud: a dropped artifact, an unresolved edge, an unresolved profile or a stale input is
  a named refusal or a diagnostic, never an empty success.

## Transports

- **REST** over loopback, for the core's seam. See [Service lifecycle](/architecture/service-lifecycle/).
- **MCP**, for agent harnesses, after its authorization decision
  ([open questions](/plans/open-questions/), question 7).

Both call the same read application. Neither implements resolution on its own.
