---
title: Service contract
description: The OpenAPI 3.1 contract of the charter service — the built operations, the planned reads and writes, the error model and the transports.
doc_status: draft
updated: 2026-11-12T00:00:00Z
type: API
kind: Reference
related:
  - architecture/contract-and-dtos
  - guides/compose-packs
  - changelog/2026-11-07-a-pack-registration
  - adr/0002-contract-source-of-record
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0074-the-remaining-cutover-reads-and-their-paths
  - conformance/divergence-register
  - api/compatibility
audience:
  - profile: contract-consumer
    guidance_text: Read this for the planned operations, the error model and the transports of the service.
---

# Service contract

The built operations are also readable as [API reference pages](/reference/api/main/), published from the contract bundle (unreleased). The raw contract is at [`openapi.yaml`](/reference/api/main/openapi.yaml), with its [SHA-256 checksum](/reference/api/main/openapi.yaml.sha256).

The planned operations, error model and transports of the charter service's OpenAPI contract. The surface derives from the reads core's Python charter answers today (`charter.activation` and `charter.offering` in core), from core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) (the staged Java service strangler), [ADR-0001](/adr/0001-java-service-with-openapi-contract/) and [ADR-0002](/adr/0002-contract-source-of-record/). Every operation is a proposal until it is in `contracts/charter/openapi.yaml`.

> **Partly built.** The operations under [Built surface](#built-surface) are in the contract and served by the Java service: the API root, the service info, the contract document, the offering's pack list, the generic artifact reads and the registrations. The other operations on this page are designed only. See the [API overview](/api/).

## The contract files

The contract is in [`contracts/charter/`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/openapi.yaml), at `0.2.0-SNAPSHOT`, unreleased (published as 0.x until it is stable, [ADR-0022](/adr/0022-contract-versions-start-at-0-x/)):

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
| `getApiRoot` | The API root: links to `info`, `offering`, `activations` (`/api/v1/activations`, `method: POST`, the register a client creates an activation in), `artifacts` (the template `/api/v1/packs/{packId}/artifacts{?kind}`) and `service-desc`, plus `self`. The root is `/api/v1/`, with the trailing slash; `/api/v1` is a 404 | `GET /` |
| `getServiceInfo` | The contract version, the service's build version (`serviceVersion`), the worktree it serves and a fingerprint of the sources it answers from, with `self` and `service-desc` links and a `Link` header to the contract document | `GET /info` |
| `getOpenApiDocument` | The bundled contract the service was built from, as `application/yaml`, with a strong `ETag`; `304` on a matching `If-None-Match` | `GET /openapi.yaml` |
| `getOffering` | The configured packs in force, in the merge order (dependencies before the packs that need them, each level contiguous; packs no dependency relates stay in configured-level, then pack-id order), each with its id, `descriptorPackId`, configured level, name, version, resolved `dependsOn` and an `artifacts` link | `GET /offering` |
| `listPackArtifacts` | One configured pack's artifacts of the served kinds as summaries, filtered by an optional `kind` and paged (`pageSize` 1 to 200, 50 by default; `pageCursor`), with `self`, `first` and `next` links | `GET /packs/{packId}/artifacts` |
| `getPackArtifact` | One artifact in the common `Artifact` envelope (`kind`, `id`, `packId`, `_links`, and one kind-named member with the kind's representation); the pack revision as its `ETag` for every kind it serves, the `If-Match` of a write or a `DELETE`; no `If-None-Match` | `GET /packs/{packId}/artifacts/{kind}/{artifactId}` |
| `listArtifactReferences` | The references one artifact writes, in written order, each resolved through the compose: the reference as written, its relation, the `holdingPack` (`packId`, `descriptorPackId`, `name`), the bare `urn` and an `artifact` link to the pack-scoped read of the target. A composed read: it refuses a broken compose with the composition code of `GET /offering`, while `getPackArtifact` still answers. The pack, kind and artifact are judged as for `getPackArtifact` | `GET /packs/{packId}/artifacts/{kind}/{artifactId}/references` |
| `getPackAgentProfile` | One agent profile in the `Artifact` envelope with `kind: agent_profile`: the profile in `agentProfile` and, in `governance`, the artifacts its governance walk lists over the pack's composed `depends_on` closure (tag `AgentProfiles`). `depth` (0 or 1, default 0) is the number of `suggests` hops the walk follows. Every entry names its `step`, `relation`, `via`, `hops`, the words of the edge it was reached through (`edge`: `when` and `reason`, both optional) whether the place its edge leads to holds it (`held`) and, for a held entry, the pack whose copy wins the layer merge of the closure (`packId` and the `artifact` link name that copy, whose edges the walk followed); a profile the pack does not hold is a 404 `artifact_not_found`. The `ETag` is the pack revision, the `If-Match` of the profile's `DELETE`; it is not a validator of the governance, which a dependency pack can change without changing it, and the read takes no `If-None-Match` | `GET /packs/{packId}/artifacts/agent_profile/{artifactId}` |
| `getEffectiveArtifact` | The version of one artifact in force under an activation, of any kind the pack-scoped routes serve: the winner's `packId` and `level`, its `state`, `required`, `policy` and `reason`, and no body. Links `self`, `artifact` (the pack-scoped read of the winner's pack) and `up` (the activation). A strong `ETag`, `304` on a match, `400` for a `depth` outside 0 to 1 | `GET /activations/{activationId}/artifacts/{kind}/{artifactId}` |
| `getEffectiveAgentProfile` | One agent profile under an activation (tag `EffectiveAgentProfiles`): the facts of `getEffectiveArtifact`, the winner's profile in `agentProfile` and the governance walk judged by the activation. Links `self`, `artifact` (the pack-scoped read of the winner's pack) and `up` (the activation); an entry links `effective-artifact` and `artifact` for the kinds the pack-scoped routes serve. A strong `ETag`, `304` on a match | `GET /activations/{activationId}/artifacts/agent_profile/{artifactId}` |
| `registerPackDirective`, `registerPackStyleguide`, `registerPackParadigm`, `registerPackToolguide` | Register an artifact of that kind; `201` with `Location` and the `Artifact` envelope | `POST /packs/{packId}/artifacts/<kind>` |
| `putPackDirective`, `putPackStyleguide`, `putPackParadigm`, `putPackToolguide` | Register or replace an artifact of that kind, with `If-Match`; `200` (`201` when it creates) with the `Artifact` envelope | `PUT /packs/{packId}/artifacts/<kind>/{artifactId}` |
| `putPackRelationship` | Register a relationship between a pack's components; answers `RelationshipRegistered`, linked to the pack's artifacts | `PUT /packs/{packId}/relationships` |
| `getPackMissionType` | One mission type in the `Artifact` envelope, member `missionType`: its stored fields, its steps then its on-demand steps, each linked, and its skeletons, each linked. The `ETag` is the pack revision, a concurrency token naming the pack, not a cache validator (the answer can carry content from dependency packs); no `If-None-Match` | `GET /packs/{packId}/artifacts/mission_type/{missionTypeId}` |
| `getPackMissionStep` | One step of a mission type: its stored fields, its prompt and guidelines files byte for byte, its expected input and output artifacts, and links to its mission type, the step contract bound to it and the skeleton of each expected output artifact that names one. The `ETag` is the pack revision, as for `getPackMissionType` | `GET /packs/{packId}/artifacts/mission_type/{missionTypeId}/steps/{stepId}` |
| `getPackMissionSkeleton` | One skeleton of a mission type, a file under its `templates/`: the stored bytes as `text` or `base64`, with `sha256` and `size`. The `ETag` is the pack revision, as for `getPackMissionType` | `GET /packs/{packId}/artifacts/mission_type/{missionTypeId}/skeletons/{skeletonName}` |
| `getEffectiveMissionType` | The layer-merge winner of a mission type under an activation (tag `EffectiveMissionTypes`), whatever its state: the body of `getPackMissionType`, `packId` the pack that holds the winner, its skeletons and step files from that pack, and the step contracts the merged closure binds to it. Links stay under the activation, but `artifact` (the winner's pack read). No `ETag` | `GET /activations/{activationId}/artifacts/mission_type/{missionTypeId}` |
| `getEffectiveMissionStep` | One step of the winning mission type under an activation: the body of `getPackMissionStep`, its `step-contract` link to `getEffectiveMissionStepContract`. No `ETag` | `GET /activations/{activationId}/artifacts/mission_type/{missionTypeId}/steps/{stepId}` |
| `getEffectiveMissionSkeleton` | One skeleton of the winning mission type under an activation, from the pack that holds the winner. No `ETag` | `GET /activations/{activationId}/artifacts/mission_type/{missionTypeId}/skeletons/{skeletonName}` |
| `getEffectiveMissionStepContract` | The layer-merge winner of a step contract under an activation, in the `Artifact` envelope as the pack read answers it; `mission-type`, `step` and candidate links stay under the activation, `artifact` is the winner's pack read. No `ETag` | `GET /activations/{activationId}/artifacts/mission_step_contract/{contractId}` |

The served kinds are `tactic`, `directive`, `styleguide`, `paradigm`, `toolguide` and `agent_profile`. A tactic is read
only, and so is a profile (it is read and removed, not written); the other four are written through the operations of their kind
([ADR-0026](/adr/0026-write-surface-is-functional-registration/) D1.2, as amended 2026-10-31). No answer carries an
`edit` link yet, so registration is not advertised by a link. A path kind the service does not serve (for
example `procedure`) is a 404 `kind_not_found`; an unknown `kind` filter is a 400 `Problem` without a code.
The `kind` of a summary and of an `Artifact` is an open string, not an enum: a client skips an item whose kind it
does not know, so a kind a later version serves (a glossary, a procedure) is an additive change. `agent_profile` was the sixth: the
list, `?kind=`, the references and the removal serve it, a profile the pack does not hold is a 404 `artifact_not_found`
(the five older kinds keep their own codes), and the templated `getPackArtifact` never carries a profile body, which only
`getPackAgentProfile` answers.

**Mission types and step contracts**
([#99](https://github.com/spec-kitty/spec-kitty-charter/issues/99),
[#100](https://github.com/spec-kitty/spec-kitty-charter/issues/100)). `mission_type` and `mission_step_contract` are
listed, filtered (`?kind=`), referenced (`/references`) and removed like the served kinds. A step contract, its
steps, delegations and gate bindings included, is read by `getPackArtifact`; a mission type's item is read by
`getPackMissionType`, whose literal path the service prefers over the templated one. Neither kind is written through
the service; both reads answer the pack revision as their `ETag` (#487), the token a `DELETE` sends in `If-Match`. A skeleton is named by one path segment: its path under the type's
`templates/`, escaped as in RFC 6901 (`~` as `~0`, then `/` as `~1`), so `templates/divio/howto-template.md` is
`divio~1howto-template.md`; a name that does not decode is `skeleton_not_found` (D9). Refusals come in order:
`pack_not_found` or `pack_unreadable`, `mission_type_not_found`, then `action_not_found` (an unknown step) or
`skeleton_not_found`. A `DELETE` of a step contract follows the five kinds' rules; a `DELETE` of a mission type removes
the type, its step files and its skeletons as one removal under one impact check, and is refused `409` while a step
contract binds the type or a graph edge names one of its action nodes (`action:<type>/<step>`). A removal of any kind
prunes the directories it leaves empty, up to the pack's root (operator ruling). Under an activation the four
`getEffectiveMission*` reads answer the layer-merge winner, whatever its state; their literal paths are preferred over
the templated `getEffectiveArtifact`, which serves neither mission kind, so a step's `step-contract` link never leaves
the activation. Refusals there: `activation_not_found`, then the kind's not-found code (a type or step contract no pack
of the closure holds), then `action_not_found` or `skeleton_not_found`. Under an activation the type is the layer-merge
winner; its steps, skeletons and step files come from the pack that holds the winner; and its step contracts are each
the merged closure's winner bound to the type, which another pack of the closure may hold. A pack-scoped read answers
the requested pack's own copy, with that pack's own skeletons, step files and step contracts. A type, step or skeleton
read under an activation is two reads at one source fingerprint (the winner, then what its pack stores beside it), so
a holding pack that is configured no longer, or no longer holds the type, at the second read is `stale_input`, never
a refusal of a pack the request did not name.

**Literal write paths beside the templated read.** The write paths name their kind literally
(`/packs/{packId}/artifacts/directive/{artifactId}`) beside the templated
`/packs/{packId}/artifacts/{kind}/{artifactId}` (operator ruling 2 on
[#126](https://github.com/spec-kitty/spec-kitty-charter/issues/126)). The service and this repository's
conformance harness match the method first, so `GET /packs/p/artifacts/directive/X` is `getPackArtifact`. Generic
OpenAPI tooling matches the path item before the method: a third-party validator or mock resolves that `GET` to
the literal path item, which defines only `PUT`, and refuses it. The ruling stands; this is a known limit of such
tools. The profile read has the mirror image of it: `/packs/{packId}/artifacts/agent_profile/{artifactId}` is a literal
path item that defines only `GET` (`getPackAgentProfile`), while the profile's `DELETE` (`deletePackArtifact`) and its
references stay on the templated paths. The service matches the method first and routes that `DELETE` to
`deletePackArtifact`; a tool that matches the path item first resolves it to the literal item and refuses it as a
method the item does not allow. It is the same known limit.

**Agent profile reads and two divergences from core.** The profile reads walk governance with the engine of the action
context (research D4 of the mission `agent-profile-reads-01M70ZDM`). They differ from core's profile channel in two
places, both by operator ruling, and field-merge resolution is not one of them (it stays with
[#102](https://github.com/spec-kitty/spec-kitty-charter/issues/102)):

- **`suggests` entries are listed.** Core's profile channel walks `requires` and `specializes_from` only and treats
  `suggests` as composition-only and inert. This service lists a `suggests` entry, and with `depth=1` what the suggested
  artifact requires (`requires_of_suggested`). Under an activation a suggestion out of force is listed with `state:
  inactive` and `reason: not_selected`.
- **An ancestor not in force is dropped under an activation.** `getEffectiveAgentProfile` lists no `specializes_from`
  ancestor that is not in force, and seeds nothing from it (operator ruling `01M7115WE6WQ7Q41HSS9ZHD1F3`). The cascade
  of the activation writes passes an ancestor through instead. The profile the walk starts from is exempt: it is answered
  whatever its state, and the state is on the read. The pack-scoped read has no such predicate: every node is in force.

**The `effective-artifact` relation and the `up` link.** `effective-artifact` is the link from an item to its read
under an activation. The effective list, the action context and the entries of an effective profile's governance carry
it for a kind the pack-scoped routes serve (`getEffectiveAgentProfile` for an `agent_profile`, `getEffectiveArtifact`
for the rest), beside `artifact`, which keeps its meaning: the pack-scoped read of the pack that holds the version in
force. `getEffectiveMissionType` and `getEffectiveMissionStepContract` answer it for the two mission kinds, and a
step contract's delegation candidates link it under an activation. A graph node names no pack, so it links
`effective-artifact` only; a step node `action:<type>/<step>` links `getEffectiveMissionStep`, and so does the action
context's own `_links`, beside `artifact` (the step's pack read). Every effective read of one artifact, the
generic one and the profile's, links `up` to its activation (the mission reads, deliberately, do not), and the `Activation` answer links `artifacts`, the
template `/api/v1/activations/{activationId}/artifacts{?kind,state}` of its effective list. The relation table is in
[Hypermedia (HATEOAS)](/development/hypermedia-hateoas/).

Every operation requires the per-worktree bearer token (`charterWorktreeToken`). Every answer
carries `Charter-Contract-Version` and `Charter-Worktree`. `Charter-Source-Fingerprint` is declared,
and required, only on the 200 of a verified read: `getServiceInfo`, `getOffering`, `listPackArtifacts`,
`getPackArtifact`, `listArtifactReferences`, `getPackAgentProfile` and the three pack mission reads ([#175](https://github.com/spec-kitty/spec-kitty-charter/issues/175)). It carries the digest
that read verified. The 200s of the four effective reads (`listEffectiveArtifacts`,
`getActivationSkippedReport`, `getEffectiveGraph`, `getEffectiveActionContext`) declare it too and send it, as each is a
verified read; none is reserved any more (the first two since PR B, the last two since PR C). So do `getEffectiveArtifact`, `getEffectiveAgentProfile`, `getEffectiveMissionType`, `getEffectiveMissionStep`,
`getEffectiveMissionSkeleton` and `getEffectiveMissionStepContract`. `getActivation` and `listActivationPacks` send none: the document
read depends only on the stored document (ADR-0059 D5), and the closure read reports a change through its `ETag`.
No refusal, write, root or contract-document answer carries it.

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
names no configured pack. The offering also refuses `pack_unreadable` when an artifact file or a graph file of a
configured pack cannot be read, or when a `depends_on` range, `requires_core` or `pack_version` is malformed; a
pack-scoped read of a readable artifact still answers.

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
| Effective context (R9): what applies in one project, with activation, exclusions, selection roots, the language filter and the directive allowlist, built on the walk. **It gates cutover stage 3** | [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103) | `spec-kitty charter context --action <name> --mission-type <type>` | The effective context is built (PR C): `getEffectiveActionContext`, with the effective list, report and graph (below). The seam's switch to it (stage 3) is not part of the service |
| Tension annotation over authored tension relationships ([ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/), [ADR-0045](/adr/0045-tension-beside-the-relationships-and-nodes-keep-every-declared-field/)), in the creed contract set | [#16](https://github.com/spec-kitty/spec-kitty-charter/issues/16), [#77](https://github.com/spec-kitty/spec-kitty-charter/issues/77) | the `in_tension_with` annotation in `charter context` | Not built |
| Artifact bodies (R2) | [#96](https://github.com/spec-kitty/spec-kitty-charter/issues/96) | `charter.offering` repositories | **Decided: a contract operation, built per pack and under an activation.** Answered per pack, for the seven served kinds (`agent_profile` since [#407](https://github.com/spec-kitty/spec-kitty-charter/issues/407), `procedure` since [#484](https://github.com/spec-kitty/spec-kitty-charter/issues/484)), by `listPackArtifacts` and `getPackArtifact`, and for a profile by `getPackAgentProfile`, whose body carries the lineage-walked governance; of the other kinds, only the mission type (`getPackMissionType`), the step contract (`getPackArtifact`) and the skeletons (`getPackMissionSkeleton`) are answered. Under an activation, `getEffectiveArtifact` reads the winner of any served kind (no body) and `getEffectiveAgentProfile` a profile with its governance; an effective list item links them with `effective-artifact`. Glossary and skill bodies are their own reads, R12 ([#106](https://github.com/spec-kitty/spec-kitty-charter/issues/106)) and R13 ([#107](https://github.com/spec-kitty/spec-kitty-charter/issues/107)). The merged `/offering/artifacts` waits for merge |
| Merged graph and graph queries (R3) | [#97](https://github.com/spec-kitty/spec-kitty-charter/issues/97) | `charter.offering.drg` callers | The effective graph of an activation is built (PR C): `getEffectiveGraph`. The graph queries over a pack and the merged `/offering/graph` wait for merge |
| Pack listing with presets (R4) | [#98](https://github.com/spec-kitty/spec-kitty-charter/issues/98) | `spec-kitty charter pack list` | Partly answered by `GET /offering` (the configured packs in force); presets deferred |
| Mission types and the action index (R5) | [#99](https://github.com/spec-kitty/spec-kitty-charter/issues/99) | `specify_cli` mission repositories | Mission types and their steps are answered per pack: `getPackMissionType` and `getPackMissionStep`, a step's prompt and guidelines byte for byte. Under an activation they are answered by `getEffectiveMissionType`, `getEffectiveMissionStep` and `getEffectiveMissionSkeleton`, linked from the effective list, graph and action context with `effective-artifact`; the merged `/offering/mission-types` waits for merge |
| Template catalog (R10) | [#104](https://github.com/spec-kitty/spec-kitty-charter/issues/104) | `resolve_template_by_id`, over `<mission>/templates/` (`resolve_command` reads `<mission>/command-templates/`) | **Decided: a contract operation, built as the skeleton reads.** Core addresses a template as `<mission>/<name>`, a mission type and a file of its `templates/`. `getPackMissionSkeleton` answers that per pack, and `getEffectiveMissionSkeleton` under an activation, from the pack that holds the layer-merge winner of the mission type. That is where the charter's model decides between packs: per mission type, where core's org, global and package tiers decide per file. Core's project tiers (override and legacy) are files of the project, not of the offering. A command template is folded into its step's prompt ([ADR-0044](/adr/0044-mission-types-consolidated-into-one-sanitized-shape/)), which `getPackMissionStep` serves. A file under a pack's top-level `templates/` (kind `template`) is read by no core call and is not served: it stays `kind_not_found` |
| Step contracts and their gate bindings | [#100](https://github.com/spec-kitty/spec-kitty-charter/issues/100) | `specify_cli` step-contract repositories | Answered per pack by `getPackArtifact` (`mission_step_contract`): the steps, delegations and gates as stored, linked to the mission type and the step they bind. Under an activation by the literal `getEffectiveMissionStepContract` |
| Profile resolution and lineage | [#102](https://github.com/spec-kitty/spec-kitty-charter/issues/102) | `spec-kitty agent profile show`, profile-load contract | Partly built: the profile and its governance are read (`getPackAgentProfile`, `getEffectiveAgentProfile`). The governance is lineage-walked: `specializes_from` ancestors come first as entries a client follows, then what the profile and its ancestors `require`, `suggest` and cite in the vocabulary. The field-merge resolution (`resolve_profile`, root to leaf) is not done: the body answered is the stored leaf body, and the question stays with [#102](https://github.com/spec-kitty/spec-kitty-charter/issues/102) |
| Active charter and effective activation sets | [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103) | `spec-kitty charter` status commands | The effective activation sets are built (PR B and PR C): `listEffectiveArtifacts`, `getActivationSkippedReport`, `getEffectiveGraph` and `getEffectiveActionContext`. The status commands' own output is not part of the service |
| The other reads (expected-artifact manifests, the kind taxonomy) | [#101](https://github.com/spec-kitty/spec-kitty-charter/issues/101), [#112](https://github.com/spec-kitty/spec-kitty-charter/issues/112) | various | Not built; the kind taxonomy, `/offering/kinds` (R18), is deferred to the minor that brings merge |
| Assets (R11), glossary contexts and terms (R12), pack skills (R13) | [#105](https://github.com/spec-kitty/spec-kitty-charter/issues/105), [#106](https://github.com/spec-kitty/spec-kitty-charter/issues/106), [#107](https://github.com/spec-kitty/spec-kitty-charter/issues/107) | `?kind=` and a per-kind list (`…/artifacts/asset`, `…/skill`, `…/glossary`); a literal item read per kind; `…/artifacts/asset/{assetId}/payload`; `…/artifacts/glossary/{contextId}/terms/{termId}`; each with its activation twin | **Decided 2026-11-12, slice A** ([ADR-0074](/adr/0074-the-remaining-cutover-reads-and-their-paths/) decisions 2 to 4). The glossary takes ADR-0049's shape, replacing `glossary-pack`; skill bodies are held as a `text` companion since [#432](https://github.com/spec-kitty/spec-kitty-charter/issues/432). Projecting skills into a harness's commands is core-side, not a read. The paths are decisions 9 to 14 |
| Model-task routing (R14) | [#108](https://github.com/spec-kitty/spec-kitty-charter/issues/108) | the routing catalog, as pack content | **Decided 2026-11-12: a contract operation, in the cutover** (ADR-0074 decision 5). The catalog moves into a pack. How it is built (its shape, and whether the service also evaluates a task against it) is decision 8 |
| Override and pack policy (R15) | [#109](https://github.com/spec-kitty/spec-kitty-charter/issues/109) | `listEffectiveArtifacts` (`policy`), `getActivationSkippedReport` (`overridden`, `overrideNotApplied`) | **Served** (PRs #427, #448; the policy file of [ADR-0063](/adr/0063-pack-mutability-policy-file/) in PR #398; ADR-0074 decision 6) |
| Built-in pin, manifest freshness, snapshot fingerprint (R16) | [#110](https://github.com/spec-kitty/spec-kitty-charter/issues/110) | none: a write concern | **Re-scoped 2026-11-12** (ADR-0074 decision 7): the pin and the fingerprint are core's vendoring code. #110 is now manifest membership and restamping after every write, in slice B, recorded as an ADR-0042 amendment when built |
| SPDD/REASONS blocks (R17) | [#111](https://github.com/spec-kitty/spec-kitty-charter/issues/111) | `charter.spdd_reasons`, over the command templates | **Decided: a contract operation with no separate shape.** The marker blocks are text in the built-in pack's `software-dev` step prompts, so they come with the mission-type read that serves those prompts (R5, [#99](https://github.com/spec-kitty/spec-kitty-charter/issues/99)). Whether a project should see them depends on its effective artifacts, which R9 answers. Under [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 10 a project with no activation keys stops seeing them. *Amended 2026-11-08: the blocks move into the opt-in `spdd` pack as prompt fragments of step enhancements, filling slots the built-in prompts declare ([ADR-0066](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/)). The prompt read then returns the base text and the active fragments per slot; the contract change is made with R5 and R9, not here.* |

The merged `/offering/mission-types` read of R5, and R18, are deferred to the minor that brings merge, activation and cascade
([#10](https://github.com/spec-kitty/spec-kitty-charter/issues/10),
[#11](https://github.com/spec-kitty/spec-kitty-charter/issues/11)): they answer merged content, which the service
does not compute yet (operator ruling 3 on [#126](https://github.com/spec-kitty/spec-kitty-charter/issues/126),
2026-10-31). No link points to them; R5's pack and activation reads above are built.

The action-context walk (R1, #95) and its `depth` parameter
([ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/)) are served by `getEffectiveActionContext`
(PR C). The 1.0 surface that is built also holds `ServiceInfo`, the source
fingerprint inputs and the per-worktree token
([#113](https://github.com/spec-kitty/spec-kitty-charter/issues/113)).

## Decided, not built: packs resources (ADR-0019)

[ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/) gives `/packs` one
meaning, the packs this service knows, and makes every pack a resource whose contents are
sub-resources. The design is in [Packs resource model](/architecture/packs-resource-model/), and
the conventions in [Development conventions](/development/).

| Method | Path | Proposed operationId | Purpose | Inventory issue |
|---|---|---|---|---|
| `GET` | `/packs/{packId}` | `getPack` | One pack: its identity, version, level and revision, with the revision as the `ETag`. **Built** | — |
| `GET` | `/packs/{packId}/doc` | `getPackDocument` | The pack's document, its `README.md`: `Accept: text/markdown` answers the held file, `application/json` a `PackDocumentRead` (title, body, links); one `ETag`, the pack revision, and `Vary: Accept`; `304` on `If-None-Match`; `406` for an `Accept` that names neither; `404 pack_document_not_found` when the pack holds none. **Built** | [#451](https://github.com/spec-kitty/spec-kitty-charter/issues/451) |
| `GET` | `/packs/{packId}/graph` | `getPackGraph` | The nodes and edges the pack declares | [#97](https://github.com/spec-kitty/spec-kitty-charter/issues/97) |
| `GET` | `/packs/{packId}/presets`, `/packs/{packId}/presets/{presetId}` | `listPackPresets`, `getPackPreset` | The pack's activation presets, and one preset in the ADR-0042 shape with `mustHave` per item. **Decided 2026-11-12, slice A, P1** (ADR-0074 decision 1) | [#98](https://github.com/spec-kitty/spec-kitty-charter/issues/98) |
| `GET` | `/packs/{packId}/artifacts/asset`, `…/asset/{assetId}`, `…/asset/{assetId}/payload` | `listPackAssets`, `getPackAsset`, `getPackAssetPayload` | The pack's assets, one asset's descriptor, and its bytes with the asset's `mime` as `Content-Type` (`application/octet-stream` when it is malformed). **Decided 2026-11-12, slice A** (ADR-0074 decisions 2, 9 to 11 and 13); replaces ADR-0019's `/assets` and `/assets/{path}` | [#105](https://github.com/spec-kitty/spec-kitty-charter/issues/105) |
| `POST` | `/packs` | `registerPackFromSource` | Register a pack from an https archive, a git repository or a local path, replacing the pack held under the target `packId`. **Built**: `202` with a status and a `Location` | — |
| `PUT` | `/packs/{packId}` | `registerPackFromArchive` | Register a whole pack from an archive, replacing the pack held. **Built**: `202` with a status and a `Location` | — |
| `GET` | `/pack-registrations/{registrationId}` | `getPackRegistration` | The status of a registration: `pending`, `running`, `succeeded` or `refused`. **Built** | — |
| `DELETE` | `/packs/{packId}/artifacts/{kind}/{artifactId}`, `/packs/{packId}/relationships` | `deletePackArtifact`, `deletePackRelationship` | Remove one artifact, or one registered edge, from a pack; `204` with the new revision as the `ETag`. **Built** | — |
| `GET` | `/packs/{packId}/resolution` | `getPackResolution` | Read-only resolution state (ADR-0007 decision 7) | — |
| `POST` | `/packs/{packId}/distributions` | `distributePack` | Distribute a pack (ADR-0005) | — |

The API root and the artifact reads of ADR-0019 D2 are built (see [Built surface](#built-surface)); other
artifact kinds join the served kinds of `listPackArtifacts` and `getPackArtifact` as they are built: `asset`,
`skill` and the glossary contexts of
[#106](https://github.com/spec-kitty/spec-kitty-charter/issues/106) are decided for slice A
([ADR-0074](/adr/0074-the-remaining-cutover-reads-and-their-paths/)). ADR-0019 names `{packId}` as the pack's
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
| `POST` | `/activations` | `createActivation`: create an activation; the service mints the id. **Built** | [#125](https://github.com/spec-kitty/spec-kitty-charter/issues/125) |
| `PUT`, `GET`, `DELETE` | `/activations/{activationId}` | `replaceActivation`, `getActivation`, `deleteActivation`: register or replace, read, remove an activation. **Built** | [#125](https://github.com/spec-kitty/spec-kitty-charter/issues/125) |
| `GET` | `/activations/{activationId}/packs` | `listActivationPacks`: the `depends_on` and `parent_pack` closure rooted at the document's packs. **Built** | [#360](https://github.com/spec-kitty/spec-kitty-charter/issues/360) |
| `PUT`, `DELETE` | `…/{activationId}/selections/{kind}/{artifactId}`, `…/exclusions/{kind}/{artifactId}`; `PUT …/presets/{presetId}` | `selectActivationArtifact`, `unselectActivationArtifact`, `deactivateActivationArtifact`, `reactivateActivationArtifact`, `applyActivationPreset`: change one choice, with `If-Match`. **Built** | [#125](https://github.com/spec-kitty/spec-kitty-charter/issues/125) declares them; [#291](https://github.com/spec-kitty/spec-kitty-charter/issues/291) builds them |
| `GET` | `…/{activationId}/artifacts`, `skipped`, `graph`, `mission-types/…/context` | `listEffectiveArtifacts`, `getActivationSkippedReport`, `getEffectiveGraph`, `getEffectiveActionContext`: the effective reads (R9). **Built** (the list and the report in PR B, the graph and the context in PR C); R9 gates cutover stage 3 | [#125](https://github.com/spec-kitty/spec-kitty-charter/issues/125) declares them; [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103) builds them |
| `GET` | `…/{activationId}/artifacts/{kind}/{artifactId}`, `…/{activationId}/artifacts/agent_profile/{artifactId}` | `getEffectiveArtifact` (tag `Activation`) and `getEffectiveAgentProfile` (tag `EffectiveAgentProfiles`): one artifact in force under the activation, and one agent profile with its governance judged by the activation. **Built** | [#407](https://github.com/spec-kitty/spec-kitty-charter/issues/407) |

**The activation set is in the contract** (#125, inside `0.1.0-SNAPSHOT` by the operator's ruling of 2026-10-31):
every activation operation is declared in full under the tag `Activation`, but `getEffectiveAgentProfile`, which is
under the tag `EffectiveAgentProfiles` so that the profiles slice implements it in a controller of its own (#407). The
register (`createActivation`, `replaceActivation`, `getActivation`, `deleteActivation`), the closure read
(`listActivationPacks`, #360) and the five per-artifact and preset writes (#291) are built, and so are the four
effective reads and the two reads of one artifact (`getEffectiveArtifact` and `getEffectiveAgentProfile`, #407): no operation of the set keeps
`x-reserved` or answers `501` any more (ADR-0020 D8; the interceptor stays, with a planted-contract control, for a later
reservation). The set declares the `ActivationConfig` document (canonical as JSON Schema 2020-12 in
`contracts/offering/schemas/`), `Activation`, `ActivationChange`, the skipped report and the effective-read schemas;
`ETag`, `If-Match` and `If-None-Match`; the six activation refusal codes; and the shared `Problem`'s `requiredBy`
and `violations`. Every one of the six codes is raised: `activation_not_found` (404), `artifact_not_found` and
`preset_not_found` (404, a per-artifact write or a preset application whose path names what the closure does not
hold, and `artifact_not_found` also on `getEffectiveArtifact` and `getEffectiveAgentProfile` for an id no pack of the
closure holds), `activation_invalid` (422), `artifact_required` and `artifact_locked` (409). The rulings are in ADR-0020's amendment of 2026-10-31.

**Before 1.0.0, still to come:** the move of the action-context read and the hosted-first reshape of the security
scheme, `Charter-Worktree`, `ServiceInfo.worktree` and the fingerprint inputs (ADR-0020 D12).

## Planned write surface (later)

The activation writes are built: the register and its per-artifact and preset writes (#125, #291), ahead of cutover
stage 3 (ADR-0020). The other
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
  (`x-reserved`) describe it once built and are judged when it is built; until then it answers `501`. No operation is
  reserved today, so every declared refusal of the activation set is raised.
- `kind_not_found` (404) is raised when the path of an artifact read names a kind the service does not serve.
  The pack is judged first, so an unknown pack is `pack_not_found` whatever the kind; an id the pack does not hold
  is the not-found code of its kind (`tactic_not_found`, …).
- The eleven refusals of the pack composition (`pack_dependency_cycle`, `pack_dependency_unknown`,
  `pack_dependency_out_of_range`, `pack_parent_unresolved`, `pack_level_violation`, `pack_name_conflict`,
  `pack_duplicate_id`, `pack_sibling_conflict`, `pack_reference_unqualified`, `pack_reference_pack_missing` and
  `pack_reference_artifact_missing`) are 500 and not retryable. `getOffering` and `listArtifactReferences` raise
  them, and the `packs` member of the `Problem` lists the packs they name, the violating pack first, each as
  `{descriptorPackId, packId, name}` (`RefusalPack`). The causes and fixes are in [Compose packs](/guides/compose-packs/#read-a-refusal).
- `stale_input` is raised by every verified read (`getServiceInfo`, `getOffering`, `listPackArtifacts`,
  `getPackArtifact` and `listArtifactReferences`): inputs changed while the answer was computed, a failed
  load whose inputs changed included. It may be retried.
- `governance_policy_violation` (409) is raised by the registration writes when the mutability level of the
  pack that owns the target does not permit the write (ADR-0012, epic #55). Each pack's `policy.yaml` is read, per
  pack, along the closure of the pack written ([ADR-0063](/adr/0063-pack-mutability-policy-file/)); no open pack ships one yet, so every target is open and
  no write is refused with it until one does.
- `pack_level_mismatch`, `unresolved_edge_endpoint` and `prompt_slot_missing` are declared and raised by no operation. `ReservedRefusalCodesTest` reads every code the
  `x-refusal` map marks not raised and fails when a main class on the wire names one (#293).
- `drg_schema_error`, `prompt_fragment_invalid`, `prompt_slot_malformed` and `prompt_slot_not_empty` are raised as
  violation codes: a refused registration (`422 validation_failed`) lists every fault of the staged pack, each at its
  pack-relative file, and a composing read that meets a prompt fault answers `500 pack_unreadable` naming the file
  (#428).
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
