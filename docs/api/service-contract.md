---
title: Service contract
description: The OpenAPI 3.1 contract of the charter service — the built operations, the planned reads and writes, the error model and the transports.
doc_status: draft
updated: 2026-10-20T00:00:00Z
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

> **Partly built.** Three reads and one refresh are in the contract and served by the Java service; the other operations on this page are designed only. See the [API overview](/api/).

## The contract files

The contract is in [`contracts/charter/`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/openapi.yaml), at `1.0.0-SNAPSHOT`, unreleased:

- [`openapi.yaml`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/openapi.yaml): the header and the map from each path to its file;
- `paths/`, `schemas/`, `parameters/`, `responses/` and `examples/`: one definition per file;
- [`scenarios/`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/scenarios/README.md): the contract scenarios, the behavior half of the contract;
- [`CHANGELOG.md`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/CHANGELOG.md): every change, scenario changes included, and what is provisional.

The single-file bundle that core vendors is built from them by `./gradlew :contracts:bundleContract`
and uploaded by CI as the `contract-bundle` artifact
([Building the service](/guides/service-build/)).

The contract is OpenAPI 3.1, served under `/api/v1`, with closed response schemas and
`Problem` bodies for refusals. How it is laid out, generated and versioned is in
[Contract and DTOs](/architecture/contract-and-dtos/).

## Built surface

| Operation | Answers | Path |
|---|---|---|
| `listPacks` | The configured Charter Packs, their levels and versions, with the diagnostics of each | `GET /packs` |
| `refreshPacks` | Re-reads every configured pack and the project configuration, discards any built read model, and answers the refreshed pack listing with the fingerprint it verified; no request body | `POST /packs/refresh` |
| `getActionContext` | The offering's walk for one action: the five steps below, each entry with its `relation`, `via` and `hops`, the `depth` it was walked at and the offering's load `diagnostics`; provisional (`x-provisional`) until the conformance corpus decides the registered divergences | `GET /mission-types/{missionType}/actions/{action}/context` |
| `getServiceInfo` | The contract version, the service's build version (`serviceVersion`), the worktree it serves and a fingerprint of the sources it answers from | `GET /info` |

Every operation requires the per-worktree bearer token (`charterWorktreeToken`). Every answer
carries `Charter-Contract-Version` and `Charter-Worktree`. Only a 200 answer carries
`Charter-Source-Fingerprint`, once, with the digest that read verified; no refusal carries it.

### The action-context decision (#95)

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

### Known tooling gap

The generator duplicates `Problem` as `Problem1` and `PackListing` as `PackListing1` (a
swagger-parser bug with closed schemas). The wire shape is unaffected; the dedupe is issue #119.

### Security and freshness

- **Token.** Required bearer on every operation. The service refuses to start without a token of
  at least 32 characters unless `charter.insecure-no-token=true`, and refuses a token that comes
  from argv or a system property. Residual risks for the launcher review (#70): a port squatter
  receives the token, a derived worktree id is guessable, and a `${x}` placeholder resolved from
  argv is not refused yet (#121). A browser request (an `Origin` or `Sec-Fetch-*` header, or a
  `Host` whose name is not loopback) is a 403.
- **Fingerprint.** It covers every loaded pack file plus `config.yaml` and `charter.yaml` under
  the configured project directory. No test yet pins that it digests exactly the files the load
  reads (#120).
- **Stale reads.** Every read digests its inputs before and after it answers and refuses with
  `stale_input` on a difference, a failed load included.

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

| Read | Inventory issue | Today in core |
|---|---|---|
| Effective context (R9): what applies in one project, with activation, exclusions, selection roots, the language filter and the directive allowlist, built on the walk. **It gates cutover stage 3** | [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103) | `spec-kitty charter context --action <name> --mission-type <type>` |
| Tension annotation over negative `impacts`, in the creed contract set | [#16](https://github.com/spec-kitty/spec-kitty-charter/issues/16), [#77](https://github.com/spec-kitty/spec-kitty-charter/issues/77) | the `in_tension_with` annotation in `charter context` |
| Artifact bodies | [#96](https://github.com/spec-kitty/spec-kitty-charter/issues/96) | `charter.offering` repositories |
| Merged graph and graph queries | [#97](https://github.com/spec-kitty/spec-kitty-charter/issues/97) | `charter.offering.drg` callers |
| Pack listing with presets | [#98](https://github.com/spec-kitty/spec-kitty-charter/issues/98) | `spec-kitty charter pack list` |
| Mission types and the action index | [#99](https://github.com/spec-kitty/spec-kitty-charter/issues/99) | `specify_cli` mission repositories |
| Profile resolution and lineage | [#102](https://github.com/spec-kitty/spec-kitty-charter/issues/102) | `spec-kitty agent profile show`, profile-load contract |
| Active charter and effective activation sets | [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103) | `spec-kitty charter` status commands |
| The other reads (step contracts, expected-artifact manifests, templates, assets, glossary packs, pack skills, model routing, override policy, pack pins, structured-prompt blocks, the kind taxonomy) | [#100](https://github.com/spec-kitty/spec-kitty-charter/issues/100), [#101](https://github.com/spec-kitty/spec-kitty-charter/issues/101), [#104](https://github.com/spec-kitty/spec-kitty-charter/issues/104) to [#112](https://github.com/spec-kitty/spec-kitty-charter/issues/112) | various |

The action-context walk (#95) and the reserved 1.0 surface (`depth`, `ServiceInfo`, the source
fingerprint inputs, the per-worktree token and refresh) are decided and built
([#113](https://github.com/spec-kitty/spec-kitty-charter/issues/113),
[ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/)).

## Proposed: packs resources (ADR-0019)

[ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/) (Proposed) gives `/packs` one
meaning, the packs this service knows, and makes every pack a resource whose contents are
sub-resources. The design is in [Packs resource model](/architecture/packs-resource-model/), and
the conventions in [Development conventions](/development/).

| Method | Path | Proposed operationId | Purpose | Inventory issue |
|---|---|---|---|---|
| `GET` | `/` | `getApiRoot` | The API root: links to every top-level resource | — |
| `GET` | `/packs/{packId}` | `getPack` | One pack: descriptor, source, counts, diagnostics | [#98](https://github.com/spec-kitty/spec-kitty-charter/issues/98) |
| `GET` | `/packs/{packId}/artifacts` | `listPackArtifacts` | The pack's artifacts, `?kind=`, paged with `PageInfo` | [#96](https://github.com/spec-kitty/spec-kitty-charter/issues/96), [#106](https://github.com/spec-kitty/spec-kitty-charter/issues/106) (glossary) |
| `GET` | `/packs/{packId}/artifacts/{kind}/{artifactId}` | `getPackArtifact` | One artifact with its body | [#96](https://github.com/spec-kitty/spec-kitty-charter/issues/96) |
| `GET` | `/packs/{packId}/graph` | `getPackGraph` | The nodes and edges the pack declares | [#97](https://github.com/spec-kitty/spec-kitty-charter/issues/97) |
| `GET` | `/packs/{packId}/presets` | `listPackPresets` | The pack's activation presets | [#98](https://github.com/spec-kitty/spec-kitty-charter/issues/98) |
| `GET` | `/packs/{packId}/assets/{path}` | `getPackAsset` | One asset file | [#105](https://github.com/spec-kitty/spec-kitty-charter/issues/105) |
| `POST` | `/packs` | `registerPack` | Register a pack (ADR-0005); `201` with `Location` | — |
| `GET` | `/packs/{packId}/resolution` | `getPackResolution` | Read-only resolution state (ADR-0007 decision 7) | — |
| `POST` | `/packs/{packId}/distributions` | `distributePack` | Distribute a pack (ADR-0005) | — |

`{packId}` is the pack's `pack_id` ULID. The composition of the charter (which packs are in force
for this worktree, in which merge order, and the merged graph) is a separate resource, `/charter`,
still to be designed ([#97](https://github.com/spec-kitty/spec-kitty-charter/issues/97),
[#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103)).

**Before the 1.0 tag** ([#45](https://github.com/spec-kitty/spec-kitty-charter/issues/45)): the
closed `Links` component, `GET /`, and a required `_links` on every existing response
(`PackListing`, `PackSummary`, the refresh answer, `ActionContext`, `ServiceInfo`). These change
closed response schemas, so they are breaking after the tag. The per-pack operations are new
paths and can follow additively.

## Planned write surface (later)

Write operations come only after the read stages, one at a time: activation plan and commit,
charter document save, compile. See [Strangler transition](/architecture/strangler-transition/).

## Error model

- Every refusal is a `Problem` body. The shared `Problem` schema and response in
  `contracts/_shared/` are canonical in this repository (ADR-0015), and every answer, the
  `default` `Problem` response included, carries `Charter-Contract-Version` and
  `Charter-Worktree`; only a 200 carries `Charter-Source-Fingerprint`.
- Refusal codes are part of the contract, not implementation detail. They are snake_case on
  the wire; the integration seam in core maps them to core's own spellings. The codes and
  when each applies are defined in [`CharterRefusalCode`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/schemas/CharterRefusalCode.yaml),
  and the load findings that do not stop a read in [`DiagnosticCode`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/schemas/DiagnosticCode.yaml).
  `CharterRefusalCode` carries a machine-readable `x-refusal` map (status, retryable, raised)
  for all nine codes.
- `stale_input` is raised by every read: inputs changed while the answer was computed, a failed
  load whose inputs changed included. It may be retried.
- `governance_policy_violation` is reserved: it is declared and pinned by a scenario and a unit
  test, and raised by nothing until the pack mutability policy lands (#48).
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
