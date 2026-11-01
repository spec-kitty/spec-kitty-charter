# Changelog

All notable changes to the `charter` contract are recorded here, newest first. The module
version is the `info.version` of `openapi.yaml`; the canonical JSON Schemas of `../offering/schemas/`
(`ActivationConfig.schema.json`) are versioned with it. The contract scenarios under `scenarios/` are
half of the contract, and an OpenAPI diff cannot see them, so every scenario change gets a line
here too.

A contract change does not edit this file: it adds a fragment under `changes/`, which the release
pull request assembles into the top version section (`changes/README.md`, ADR-0047).

## 0.1.0-SNAPSHOT

The first contract version core will vendor, not yet released: an unreleased contract version
carries the `-SNAPSHOT` suffix. A first `0.1.0-SNAPSHOT` draft landed before the operator rulings
of 2026-10-18, which renumbered it `1.0.0-SNAPSHOT`. The operator ruling of 2026-10-20 (ADR-0022)
withdrew that label: the contract is published as 0.x while it iterates with core's generated
client, and 1.0.0 is minted once it is stable. The version is `0.1.0-SNAPSHOT` again. No release
was cut under either label. `Added` lists what this version holds that the first draft did not;
`Changed` lists what differs from that draft.

### Breaking

The activation set, declared and reserved (mission activation-set, issue #125; ADR-0020; operator rulings of
2026-10-31). It lands inside this unreleased version, beside #126, by the operator's ruling, so `info.version` stays
`0.1.0-SNAPSHOT`. Breaking on the 0.x line, which ADR-0022 allows; no release carried the old shape. What core's seam
has to change is said per item.

- Added: 13 operations under the new tag `Activation`, each declared in full (request body, success statuses,
  refusals, conditional headers) and reserved: `x-reserved: {contractSet: activation, issue: "#125"}` and a `501`
  `Problem` with no code, which the service answers before it reads the request (ADR-0020 D8). They are
  `createActivation` (`POST /activations`); `replaceActivation`, `getActivation` and `deleteActivation`
  (`PUT`, `GET`, `DELETE /activations/{activationId}`); `selectActivationArtifact` and `unselectActivationArtifact`
  (`…/selections/{kind}/{artifactId}`); `deactivateActivationArtifact` and `reactivateActivationArtifact`
  (`…/exclusions/{kind}/{artifactId}`); `applyActivationPreset` (`…/presets/{presetId}`); and the effective reads
  `listEffectiveArtifacts` (`…/artifacts`, paged with the shared `PageInfo`), `getActivationSkippedReport`
  (`…/skipped`), `getEffectiveGraph` (`…/graph`) and `getEffectiveActionContext`
  (`…/mission-types/{missionType}/actions/{action}/context`). Core's seam: generate the `ActivationApi` client;
  treat `501` as "not built yet" and keep its in-process backend.
- Added: the activation document `ActivationConfig` (closed; `schemaVersion`, `packs` as `{packId, version?}` in
  merge order, `activatedKinds`, `missionTypes`, `selected`, `deactivated`, `languages`), canonical as JSON Schema
  2020-12 at `contracts/offering/schemas/ActivationConfig.schema.json` (ADR-0017's pattern), which this CHANGELOG
  versions too, and mirrored in `schemas/ActivationConfig.yaml`. Per-entry provenance, `contextActivations` and
  `appliedPresets` are left out by ruling. `selected` and `deactivated` hold one member per activatable kind (11).
  Core's seam: map `.kittify/charter/activation.yaml`, `config.yaml` and `charter.yaml` to and from it (content
  alignment, not file shape).
- Added: `Activation`, `ActivationSummary`, `ActivationChange`, `SkippedReport` (`skippedByScope`,
  `skippedDeactivated`, `unreachedBehindDeactivated`, `skippedShared`), `ActivationSkippedReport`,
  `EffectiveArtifactList`, `EffectiveArtifact`, `EffectiveGraph`, `EffectiveActionContext`, the request bodies
  `SelectionRequest`, `ExclusionRequest` and `PresetApplication`, their parts (`PackPin`, `SelectedArtifacts`,
  `DeactivatedArtifacts`, `ArtifactIdList`, `DeniedArtifactIdList`, `ArtifactId`, `ActivationId`, `LanguageToken`,
  `ActivatableKind`, `SelectableKind`, `CascadeKind`, `CascadeScope`, `ArtifactReference`, `SkippedDeactivated`,
  `SharedSkip`, `EffectiveGraphNode`, `EffectiveGraphEdge`, `EffectiveArtifactState`, `EffectiveArtifactReason`,
  `ArtifactPolicy`), the vendored `PageInfo` (now used) and the new `RequiringPack` and `RefusalViolation`, the
  shared hypermedia components `Links` and `Link`
  (ADR-0019 D3, shared with #126), and the conditional headers: `ETag` (the quoted revision, `sha256:` of the
  RFC 8785 serialization of the canonical document), an opaque `ETag` on the effective reads that changes with the
  revision or the offering, `If-Match`, `If-None-Match: *`, and `Location` on a `201`. The conditional writes
  declare `412` (a stale or failed precondition), every write with a body `415` (a body that is not JSON), and a
  sub-resource write without `If-Match` `428`, each a `Problem` with no code. Four questions stay open until the
  operations are built (ADR-0020, open questions 4 to 7): the validator of an `Activation` answer, a stored document
  an offering change made stale, what a preset sets, and conditional reads.
- Added: six refusal codes to `CharterRefusalCode`, each `raised: false`: `activation_not_found`,
  `artifact_not_found`, `preset_not_found` (404), `artifact_required`, `artifact_locked` (409) and
  `activation_invalid` (422). A closed enum gaining values is breaking for a generated client. Core's seam:
  regenerate; nothing raises them yet.
- Changed: the shared `Problem` gains two optional members, `requiredBy` (a list of `RequiringPack`, only on a 409
  `artifact_required`/`artifact_locked`) and `violations` (a list of `RefusalViolation`, only on a 422
  `activation_invalid`), because `CharterRefusal` extends the closed `Problem` (`_shared/VENDORED.md`). Every
  generated `Problem` gains them; no answer of this version carries them. Core's seam: regenerate.
- Changed: `mission_type_not_found` and `action_not_found` are reserved for the effective context of an activation
  again, not described as gone.
- Changed: the answer to a method the service does not support (`TRACE`) lists `DELETE` in `Allow`: the reserved
  activation set routes it.
- Changed: the bundled contract core vendors (`charter-openapi.yaml`, every `$ref` inlined) grows from about
  2.5 MB to about 5.2 MB. A YAML reader with snakeyaml's default limit (3 MiB of code points) refuses it; the
  build's own readers raise the limit. Core's seam: raise the limit where it parses the bundle, if it does.
- Lint: new rules `x-reserved-shape`, `reserved-operation-declares-501`, `built-operation-declares-no-501` and
  `no-content-has-no-body`; `error-responses-are-problems` admits `204` (ADR-0020 D9 supersedes ADR-0018 D10's
  "200 only"). The closure check exempts `_links`, a map of `Link`, as the one open map (ADR-0019 D3).
- Scenarios: `activations.feature` holds one `@contract @reserved` 501 pin per operation, a second for selecting a
  mission type, and one for a malformed body; `errors.feature` pins the six codes as declarations. The `@reserved` tag now has two forms, a declaration pin
  and a 501 pin (`scenarios/README.md`). The functional scenarios of `docs/architecture/offering-and-activation.md`
  stay proposed until the operations are built.

Toolguide guide over HTTP (mission procedure-antipattern-toolguide-round-trip, issue #194; operator rulings of
2026-10-28 and 2026-10-29). Breaking on the 0.x line, which ADR-0022 allows; no release carried the old shape.

- Removed: `guidePath` from `ToolguideRegistration` (the request of `registerPackToolguide` and `putPackToolguide`)
  and from `Toolguide` (the response of `listPackToolguides`, `getPackToolguide` and both registrations). The API
  no longer assumes a filesystem shared between client and service.
- Added: `ToolguideRegistration.guide` (the guide's Markdown) and `ToolguideRegistration.guideUri` (a string,
  `format: uri`; the schema carries no pattern, and the service enforces `https`, since a `pattern` on a field the
  generator types `URI` would be a server error once bean validation is on the classpath). A registration carries
  exactly one of them; the schema says so with a `oneOf` of two `required` branches. The generator this repository
  builds with ignores that `oneOf` (it emits two optional fields), so the service enforces the rule itself: both,
  neither, an empty `guideUri` or an explicit `null` (also beside a valid other field) is refused 422
  `validation_failed`, whatever a generated client lets through. `guide` declares `minLength: 1`: an empty `guide`
  breaks a constraint the schema declares and is refused 400 Bad Request (the contract's `default` problem,
  `{"title":"Bad Request","status":400}`) before the controller runs, since bean validation runs on the generated
  DTOs (ADR-0041 decision 2). `guideUri` declares no `minLength`, since the generator types it `URI` and a `@Size`
  on a `URI` would be a server error; its empty value stays a 422. A `guideUri` that is not a URI at all fails to
  deserialize and is a 400 plain `Problem`, like any unreadable body.
- Added: `maxItems: 256` on `ToolguideRegistration.commands`, `appliesToLanguages` and `references`; a longer list
  breaks a constraint the schema declares and is refused 400 Bad Request before the controller (ADR-0041 decision 2).
- Added: `Toolguide.guide` (required): the stored guide's Markdown, its H1 written from `title`. An incoming guide
  may omit the H1; one that has an H1 must have the title as it, or it is refused.
- Added refusals, all with existing codes (no new refusal code): 422 `validation_failed` for the exactly-one rule
  above, for an inline guide over the guide cap of UTF-8 (1 MiB by default, configurable via
  `charter.guide-fetch.max-size`; refused before it is parsed), for an inline guide holding an unpaired surrogate
  (refused, never stored as `?`), for a registration body over six times the guide cap plus 1 MiB (7 MiB by
  default; refused before it is read), for a toolguide id with a trailing or doubled hyphen (`a-`, `a--b`: the
  id is `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, the grammar of its guide file `<id>.guide.md`), for a title that cannot
  be written as the guide's H1 (for example one ending in a closing `#`), for a guide that is not a valid
  guide (frontmatter, text before the first H2, no `## Overview`, fixed blocks out of order, an H1 that differs from
  the title; the detail names the rule), and for a refused fetch of `guideUri` (the detail names the typed reason,
  for example `HOST_NOT_ALLOWED` or `TOO_LARGE`, and the host, never the URI's path or query); 409
  `registration_conflict` when the guide's file `<id>.guide.md` is already claimed by another artifact of the pack.
  The 409 and 422 response descriptions of both registration paths name them.
- Fetch posture of `guideUri`: the service fetches the guide once per registration (a PUT with the same URI fetches
  again), after the refusals that need no guide (an unknown pack, a stale `If-Match`, a POST naming an existing id,
  a write the policy refuses; none of them costs a fetch) and before it takes the pack's write lock, and reads the
  fetched text exactly like an inline guide. The fetch is direct, never through a proxy; HTTPS on port 443 only;
  the host must be exactly on the service's allowlist, which is empty by default, so every `guideUri` is refused
  until an operator allowlists its host; only global unicast addresses, checked at resolution and after every
  redirect. By default (configurable via `charter.guide-fetch.*`): at most 3 redirects, a 10-second deadline over
  the whole exchange and a 1 MiB cap, streamed, with a body whose `Content-Length` passes it refused unread. The
  answer must be `text/markdown`, `text/x-markdown` or `text/plain` (parameters such as `charset` allowed; an
  absent `Content-Type` is refused), with identity encoding and UTF-8 only; no credentials are forwarded. A
  deployment whose service reaches the guide host only through a proxy must send the guide as content.
- Migrating a client: stop sending `guidePath`. Send the guide file's Markdown as `guide`, or host it on an
  allowlisted HTTPS host and send `guideUri`; leave out the field you do not send. Read the guide from the
  response's `guide`, whose H1 is the title, instead of resolving `guidePath`. A pack renames each guide file to
  `<id>.guide.md` beside its toolguide.
- Scenarios: `toolguides.feature` registers with `guide` content instead of `guidePath` and reads the guide back
  (H1 added from the title, or kept when it equals the title); new scenarios cover an H1 that differs from the
  title, a guide without an overview, both, neither, null (alone or beside the other field) and an empty `guideUri`
  (an outline, 422), an empty `guide` (400 Bad Request, ADR-0041 decision 2), an inline guide over 1 MiB
  against one of exactly 1 MiB, a `guideUri` fetched, stored and fetched again on a later PUT, and a `guideUri`
  whose host is not allowlisted. `journeys/journey-05-remove-python.feature` registers its toolguide with `guide`
  content. Three steps are new (scenario README): `When I PUT "<path>" with the body, its "<field>" padded to <n>
  bytes:`, `Then the field "<field>" of the response body is the lines:` and `Given the guide host serves "<uri>"
  with:`; a binding answers the guide host itself and opens no socket. The scenario check now implements
  `maxItems`.

Gate-A read surface (mission gate-a-read-surface, #126, #175, #176; operator rulings of 2026-10-31). Breaking on
the 0.x line, which ADR-0022 allows; no release carried the old shape. A client discovers the API from its root
(`GET /`), reads the configured packs at `GET /offering`, and reads and writes a pack's artifacts of every served kind
in one tree, `/packs/{packId}/artifacts`. Every JSON success answer carries `_links`. The source fingerprint is
declared exactly where the service sends it, and the refusal examples name only codes the service raises. The
contract has 15 operations.

- Removed (FR-006), with no alias: every per-kind route and its operation.
  - The reads `listPackTactics` (`GET /packs/{packId}/tactics`), `getPackTactic`
    (`GET /packs/{packId}/tactics/{tacticId}`), `listPackDirectives` (`GET /packs/{packId}/directives`),
    `getPackDirective` (`GET /packs/{packId}/directives/{directiveId}`), `listPackStyleguides`
    (`GET /packs/{packId}/styleguides`), `getPackStyleguide` (`GET /packs/{packId}/styleguides/{styleguideId}`),
    `listPackParadigms` (`GET /packs/{packId}/paradigms`), `getPackParadigm`
    (`GET /packs/{packId}/paradigms/{paradigmId}`), `listPackToolguides` (`GET /packs/{packId}/toolguides`) and
    `getPackToolguide` (`GET /packs/{packId}/toolguides/{toolguideId}`). A pack's artifacts are read through
    `listPackArtifacts` and `getPackArtifact` only.
  - The eight writes at their per-kind paths (moved below, operationIds kept).
  - Each removed path, `/packs/{packId}/{tactics,directives,styleguides,paradigms,toolguides}` and the same with an
    id, answers the 404 of a path no operation defines: the shared `Problem` without a code, whatever the method and
    the body.
  - The tag `Tactics`, with its only operations; the parameter `TacticId` and the response `TacticNotFound`, which
    only the tactic reads referenced (`ArtifactNotFound` keeps the `tactic-not-found` example). `DirectiveNotFound`,
    `StyleguideNotFound`, `ParadigmNotFound` and `ToolguideNotFound` go too: the moved writes answer 404 only for
    an unknown pack, so their 404 is `PackNotFound` (`pack_not_found`); a PUT creates the artifact it does not find,
    and no write raises a kind's not-found code.
  - The declaration of `Charter-Source-Fingerprint` on every response that is not the 200 of a verified read (#175,
    ruling 4): on the refusals (401, 403, 404, 409, 422, 500), on the shared `default` `Problem`, on the 200 and 201
    of every write, and on the 200 and 304 of `getOpenApiDocument`. The service already sent it on none of them.
- Moved (FR-005, ruling 2): the eight writes, each keeping its operationId, tag, request body, `If-Match`, headers
  and refusals (404 `pack_not_found`, now as `PackNotFound`; 409 `registration_conflict`, 412, 422
  `validation_failed`, 500). The item parameter of every PUT is now `artifactId` (`parameters/ArtifactId.yaml`).
  Each 200 and 201 answers `Artifact` (below):
  - `registerPackDirective`: `POST /packs/{packId}/directives` → `POST /packs/{packId}/artifacts/directive`;
  - `putPackDirective`: `PUT /packs/{packId}/directives/{directiveId}` →
    `PUT /packs/{packId}/artifacts/directive/{artifactId}`;
  - `registerPackStyleguide`: `POST /packs/{packId}/styleguides` → `POST /packs/{packId}/artifacts/styleguide`;
  - `putPackStyleguide`: `PUT /packs/{packId}/styleguides/{styleguideId}` →
    `PUT /packs/{packId}/artifacts/styleguide/{artifactId}`;
  - `registerPackParadigm`: `POST /packs/{packId}/paradigms` → `POST /packs/{packId}/artifacts/paradigm`;
  - `putPackParadigm`: `PUT /packs/{packId}/paradigms/{paradigmId}` →
    `PUT /packs/{packId}/artifacts/paradigm/{artifactId}`;
  - `registerPackToolguide`: `POST /packs/{packId}/toolguides` → `POST /packs/{packId}/artifacts/toolguide`;
  - `putPackToolguide`: `PUT /packs/{packId}/toolguides/{toolguideId}` →
    `PUT /packs/{packId}/artifacts/toolguide/{artifactId}`.

  The literal write paths sit beside the templated `/packs/{packId}/artifacts/{kind}/{artifactId}`. A `GET` on a
  write collection (for example `/packs/{packId}/artifacts/directive`) and a `PUT` on the item of a kind without a
  write (a tactic) are a 405 `Problem` without a code, not `kind_not_found`. A 201's `Location` path is the answer's
  `_links.self.href`. `PUT /packs/{packId}/relationships` (`putPackRelationship`) stays where it is. Generic
  OpenAPI tooling matches the path item before the method, so a third-party validator or mock resolves
  `GET /packs/p/artifacts/directive/X` to the literal, PUT-only path item and refuses it; the service and this
  repository's conformance harness match the method first and answer it from `getPackArtifact` (ruling 2 stands).
- Added: operations.
  - `getApiRoot` (`GET /`, tag `Service`; FR-001, ADR-0019 D2 and D4). The root is `/api/v1/`, with the trailing
    slash. It answers `ApiRoot`, `_links` only: `self` (`/api/v1/`), `info`, `offering`, `artifacts` (the RFC 6570
    template `/api/v1/packs/{packId}/artifacts{?kind}`, `templated: true`) and `service-desc`
    (`/api/v1/openapi.yaml`). It reads no pack file, so its 200 declares no fingerprint; it answers 401, 403 and
    `default`, no 500. `/api/v1`, without the slash, is not mapped to the root: it answers the 404 of a path no
    operation defines.
  - `getOffering` (`GET /offering`, new tag `Offering`; FR-002, ADR-0020 D1), the configured packs in force. It
    answers `Offering`: `packs` (each an `OfferingPack`) and `_links.self`. The order is the merge order: by
    configured level (built-in, org, project), then by `packId` in code-point order. The level is the one storage
    serves the pack at: the `pack_level` its `pack.yaml` declares, `org` when it declares no valid one (ADR-0038);
    the offering never recomputes it from `pack.yaml`. Merging is not applied yet (#10, #11). The deferred offering reads
    (the action context, the merged graph, presets, mission types and the action index, `/offering/kinds`) and
    `GET /packs/{packId}` are not part of this version, and no link points to them (ruling 3).
  - `listPackArtifacts` (`GET /packs/{packId}/artifacts`, new tag `Artifacts`; FR-003). It answers `ArtifactPage`:
    `items` (each an `ArtifactSummary`), `page` (the shared `PageInfo`) and `_links`: `self` (the request with its
    query), `first` (without `pageCursor`) and `next` (only when `page.hasNextPage`). The order is the served-kind
    order (tactic, directive, styleguide, paradigm, toolguide), then by id in code-point order. `kind` filters on
    one served kind; `pageSize` is 1 to 200, 50 by default; `pageCursor` names the position the page starts
    strictly after, so a deleted anchor still pages on.
  - `getPackArtifact` (`GET /packs/{packId}/artifacts/{kind}/{artifactId}`, tag `Artifacts`; FR-004). It answers
    `Artifact`, with `_links.self` and `collection` (the pack's artifacts of that kind, `…/artifacts?kind=<kind>`).
    Its 200 declares an optional `ETag`, which the service sends for the writable kinds only.
- Added: schemas, parameters, responses and a header.
  - `Link` (closed: `href` required, an absolute path under `/api/v1` or an RFC 6570 template; `templated`;
    `method`, a string matching `^(GET|POST|PUT|DELETE)$`, no enum, where `DELETE` is for activations only (ADR-0020;
    ADR-0026 D2 rules out `DELETE` and `PATCH` for pack and artifact writes) and no link uses `PATCH`; `title`) and `Links` (an open map from a relation key
    to a `Link`), the hypermedia components of ADR-0019 D3.
  - `ArtifactKindToken`: the served kinds, `tactic`, `directive`, `styleguide`, `paradigm` and `toolguide`. It
    types the `kind` query parameter only, a request-side filter. Other kernel kinds (`procedure`, `agent_profile`
    and the rest) are not served.
  - `ArtifactSummary` (`kind`, `id`, `title`, `_links.self`; the title is a tactic's or a paradigm's name and the
    other kinds' title), `ArtifactPage` and `Artifact`: a closed
    envelope of `kind`, `id`, `packId`, `_links` and exactly one kind-named member (`tactic`, `directive`,
    `styleguide`, `paradigm` or `toolguide`) holding that kind's existing schema unchanged (ruling 1). The
    exactly-one rule is a `oneOf` of `required` branches, as for `ToolguideRegistration`; the generator ignores it,
    and the service fills the one member. `ArtifactSummary.kind` and `Artifact.kind` are open strings, the slug
    pattern of the `ArtifactKind` path parameter, not `ArtifactKindToken`: this version serves the five kinds, and
    a client tolerates a kind it does not know by skipping the item, so a generated client does not throw on a kind
    a later version serves (`glossary`, `procedure`; ADR-0019).
  - `ApiRoot`, `Offering` and `OfferingPack` (`packId`, the configured `level` as `PackLevel`, `name` and `version`
    from `pack.yaml`, all required, and `_links.artifacts`; no `self`, since `GET /packs/{packId}` is not built).
  - `RelationshipRegistered` (R-13), a response-only schema: `relationship`, the registered
    `RelationshipRegistration` unchanged, and `_links.artifacts`, the list of the pack's artifacts
    (`/api/v1/packs/{packId}/artifacts`). It has no `self` and the answer no `Location`: the relationships path has
    no `GET`.
  - The parameters `ArtifactKind` (a `Slug`, no enum), `ArtifactId` and `ArtifactKindFilter`; the response
    `ArtifactNotFound` (`pack_not_found`, then `kind_not_found`, then the kind's own not-found code).
  - The header `Link` (`headers/ServiceDescLink.yaml`), required on the 200 of `getServiceInfo` only:
    `</api/v1/openapi.yaml>; rel="service-desc"` (RFC 8631, the #208 hand-over). No other response declares or
    sends it.
  - The examples `ApiRoot.links`, `Offering.three-levels`, `ArtifactPage.first-page`, `Artifact.<kind>` for each
    served kind and `RelationshipRegistered.requires`, so every JSON success response has a linked example.
- Added: the refusal code `kind_not_found` (404, raised; FR-007, ruling 6b): the path names an artifact kind the
  service does not serve. The checks run in this order: a path kind that is not a slug (`Tactic`, `-tactic`) is a
  400 `Problem` without a code, before the pack is judged; an identifier that matches no configured pack is 404
  `pack_not_found`, so `GET /packs/nope/artifacts/bogus/x` is `pack_not_found`; then a slug that names no served
  kind is `kind_not_found`; then an id the pack does not hold is the kind's own not-found code (`tactic_not_found`,
  …), so no raised code is retired. An id the kind's identifier rules refuse (a lower-case directive id) is the
  kind's not-found, not a server error. An unknown `kind` filter, a malformed cursor, a cursor for another kind than
  the filter, and a `pageSize` out of range or not a number are each a 400 `Problem` without a code.
- Added: a required `_links` on every JSON success schema (FR-008): `ServiceInfo._links` holds `self`
  (`/api/v1/info`) and `service-desc`; the new schemas above carry theirs. The writes answer `Artifact` and the
  relationships PUT answers `RelationshipRegistered`, where they answered the kind's bare schema and the bare
  `RelationshipRegistration`. A client of the relationships PUT reads the edge at `relationship` now.
  `RelationshipRegistration`, the request body and the `relationships` items of the kinds, carries no `_links`.
- Added: `Charter-Source-Fingerprint` declared `required: true` on the 200 of every verified read, where it was
  optional (#175, ruling 4; FR-012): `getServiceInfo`, `getOffering`, `listPackArtifacts` and `getPackArtifact`.
  These four responses are the only ones that declare it.
- Added: a raised-code check, `checkRefusalExamples` in `:contracts:check` (FR-014, R-22). It reads the raised codes
  from `CharterRefusalCode`'s `x-refusal` map and fails a `CharterRefusal` example whose `code` is not raised, and
  a response description that names a code of the map that is not raised (#176).
- Changed (operator ruling of 2026-10-31, option A of the question the WP03 review raised): a configured pack whose
  `pack.yaml` is missing, unreadable or invalid is not a readable canonical pack. `getOffering`, `listPackArtifacts`
  and `getPackArtifact` refuse it with a 500 `pack_unreadable`, whatever kind and id the item names, as they do a
  configured pack whose directory is missing. `OfferingPack.name` and `OfferingPack.version` are therefore required.
  404 `pack_not_found` is answered only for an identifier that names no configured pack. The item judges
  `pack.yaml` and reads the artifact inside one verified read, so a `pack.yaml` that changes during it is
  `stale_input`, never a fingerprinted 200. The ruling supersedes plan research R-18's optional name and version.
- Changed: `OfferingUnreadable` (the 500 of every operation that reads the packs) carries the examples
  `pack-unreadable` and `stale-input`, and its description names only the codes the service raises with 500: `pack_unreadable`,
  `stale_input` and `internal_error`. It named `pack_level_mismatch` and `drg_schema_error`, which no operation
  raises (#176). The `stale-input` example reads "The inputs changed while the artifacts were read; read them
  again.", the detail of the generic list. The toolguide registrations' 409 no longer names the reserved
  `governance_policy_violation`.
- Changed (descriptions and citations only, no shape change): `Charter-Source-Fingerprint` names the four verified
  reads; `PackNotFound`, `ArtifactKind` and `getPackArtifact` describe the order of the checks above; `PackLevel`
  describes a declared or a configured level and cites `service/kernel/.../PackLevel.java` (its `x-source` named
  the retired `service/charter-domain/...`). The vendored `PageInfo` cites each property, and itself, with
  `x-derived` (`_shared/VENDORED.md`); the generator types `nextPageCursor` as a nullable string, so it is not
  narrowed. `Links`, `RelationshipRegistration` and the five kind schemas carry a top-level citation, which
  `resource-property-cited` reads where they are a property of another schema.
  `CharterRefusalCode`'s description follows the 2026-10-31 ruling (a configured pack whose `pack.yaml` is
  missing or invalid is `pack_unreadable` on the offering and every artifact read) and names the four verified
  reads; `info.summary` no longer calls the contract read-only.
- Changed (closure check, FR-010): the one exemption ADR-0019 D3 names. A schema reached as the property `_links`,
  typed `object` alone, whose `additionalProperties` is a schema titled `Link` that the checker proves closed, is
  closed. Every other map is still reported; fixtures pin the exemption and three lookalikes (another property name,
  another value title, an open `Link`).
- Changed (lint): `charter-headers-on-every-response` is narrowed to `Charter-Contract-Version` and
  `Charter-Worktree`; its `missing-fingerprint` fixture is now `missing-worktree`. Five rules are new, each with
  planted fixtures, and `clean.yaml` and every earlier fixture now carry `_links` and a linked example:
  - `fingerprint-on-verified-reads`: the 200 of every `GET` other than `/` and `/openapi.yaml` declares the
    fingerprint `required: true`;
  - `fingerprint-only-on-verified-reads`: no other response declares it, the root's and `HEAD`, `OPTIONS` and
    `TRACE` answers included;
  - `success-schemas-carry-links` (FR-008): every 2xx `application/json` schema requires `_links`;
  - `success-responses-have-link-examples`: every 2xx `application/json` response has an example, and every example
    carries `_links` at its top level;
  - `link-relations-are-known` (FR-011): every key of a `_links` in an example is a relation of the table in
    `docs/development/hypermedia-hateoas.md`. `RelationTableParityTest` (`contracts` tests) fails when the rule's
    list and the table differ. The table gains `service-desc`, `first` and `next`.
- Not yet linked: registration is not advertised by a link in this minor. No answer carries an `edit` link (R-24):
  a client reaches the writes through the paths above.
- Migrating a client (core's seam). Core does not consume the HTTP contract today: its seam is the in-process
  `charter.offering.api`, so no core code calls the removed routes. Core's generated client (#13, ADR-0021) will be
  generated from this version. It types `Links` and `Link`, starts at `GET /` and follows `_links` by relation name
  (ADR-0019's requests to core), ignoring a relation it does not know. It reads an artifact of any served kind
  through the `Artifact` envelope, from the member its `kind` names, and lists a pack's artifacts through
  `ArtifactPage`, following `next` or sending `pageCursor` until `page.hasNextPage` is false. It sends a
  registration to `/packs/{packId}/artifacts/<kind>[/{artifactId}]` and reads the registered artifact from the
  envelope. To replace one, it sends the item's `ETag` from `getPackArtifact` as the PUT's `If-Match`. It reads a
  registered relationship at `relationship`. It reads `Charter-Source-Fingerprint` only on the 200 of a verified
  read. A summary's or an artifact's `kind` is an open string: it skips an item whose kind it does not know, so a
  kind a later version serves is an additive change, not a breaking one.
- Scenarios (NFR-004). The JUnit count of `CharterContractSuite` (outline examples expanded) is 106 before the
  mission and 189 after. No scenario is removed or renamed; each one that called a per-kind route calls its
  successor on the artifact tree under the same name, so it is its own successor.
  - Migrated (every scenario of each): `directives.feature`, `styleguides.feature`, `paradigms.feature` and
    `toolguides.feature` write to `POST /packs/qa/artifacts/<kind>` and `PUT /packs/qa/artifacts/<kind>/<id>`, read
    from `GET /packs/qa/artifacts/<kind>/<id>`, and read fields in the envelope's member. "A duplicate id is a
    conflict on POST while a distinct id is created" of each kind also pins the exact envelope of the 201, its
    `ETag`, and that the path of `Location` is `_links.self.href`. `tactics.feature` lists with
    `GET /packs/qa/artifacts?kind=tactic` and reads `GET /packs/qa/artifacts/tactic/<id>`; the stale list's detail is
    the generic list's; its pack `qa` gains a `pack.yaml`. `relationships.feature` (both scenarios),
    `service.feature` ("A refusal carries the contract version and the worktree, not the fingerprint":
    `GET /packs/no-such-pack/artifacts`), `journeys/journey-01` ("The starter pack's directives can be read"),
    `journey-02` (both runnable scenarios), `journey-03` ("The tactics a recipe can draw on are readable"; its pack
    gains a `pack.yaml`), `journey-04`, `journey-05` and `journey-06` (every runnable scenario): the same paths.
  - Changed expectations: `relationships.feature`'s "A relationship from a mission type of the pack is registered"
    pins the exact `RelationshipRegistered` body and that it has no `self` link, instead of the field `from`.
    `errors.feature`'s "The refusal codes the contract marks raised are exactly those an operation raises" lists
    `kind_not_found` and says the generic item raises the kinds' not-found codes. `service.feature`'s "When the
    packs cannot be read the refusal carries no fingerprint but still carries the other headers" also pins that the
    500 has no `Link` header.
  - New in `tactics.feature`: "One tactic of a pack whose directory is missing is refused with pack_unreadable
    (#176)" (FR-015). New in `service.feature`: "The info answer links to itself and to the contract document, and
    says so in a Link header".
  - New `artifacts.feature`: "A tactic is read in the envelope, with no ETag" and the same for a directive, a
    styleguide, a paradigm and a toolguide (with an ETag); "A list filtered by a served kind answers that kind's
    summaries (the positive control of the kind filter)"; "A kind filter the service does not know is a 400 Problem
    without a code"; "A summary's title is the name of a tactic or a paradigm and the title of the other kinds"; "A
    list without a filter is in the served-kind order, then by id in code-point order"; "An empty pack answers an
    empty page"; "A kind the pack holds none of answers an empty page"; "A page of exactly pageSize items is the
    last page: no next link and no trailing empty page"; "Pages of one item cross the kind boundary and serve every
    artifact exactly once"; "A next link keeps the kind filter and the page size, and its page keeps first without
    the cursor"; "The anchor of a cursor deleted between two pages still pages on from its position"; "A list whose
    inputs change while it is answered is refused with stale_input" and the same for an item; and the outlines "A
    bad page cursor is a 400 Problem without a code" (another kind, not base64url, a kind not served, no slash), "A
    page size out of range or not a number is a 400 Problem without a code" (0, 201, `abc`), "An unknown pack is
    pack_not_found, on the list and on the item" (four paths), "A path kind the service does not serve is
    kind_not_found" (`procedure`, `bogus`), "An unknown id of a served kind is that kind's not-found refusal" (five
    kinds), "A configured pack without pack.yaml is pack_unreadable, on the list and on the item" (the list, the
    list filtered by `tactic`, the item of an existing tactic, of a missing one, and of an unserved kind) and "A
    path kind that is not a slug is a 400 Problem without a code, before the pack is judged" (`Tactic`, `-tactic`,
    and `Tactic` on an unknown pack).
  - New `removed-routes.feature`: the outlines "A removed per-kind read answers 404 without a code" (10 paths) and
    "A removed per-kind write answers 404 without a code, whatever its body" (8 method and path pairs, each with a
    body the successor accepts, and the kind's list still empty after it), and the scenarios "A GET on a write path
    is a 405 Problem without a code" and "A PUT on the item of a kind that has no write is a 405 Problem without a
    code, not kind_not_found".
  - New `root.feature`: "The root answers its links and carries no fingerprint" (the exact body, no `Link` header);
    "The info link of the root answers the service info", "The offering link of the root answers the offering", "The
    service-desc link of the root answers the contract document"; "The artifacts template of the root, expanded with
    a configured pack, answers its artifacts" and the same "with a pack and a kind"; "The root needs the worktree
    token"; "The server prefix without its trailing slash is no operation".
  - New `offering.feature`: "The packs are ordered by configured level, then by pack id in code-point order" (six
    packs configured out of order, `Zeta` before `alpha` at each level, the exact body); "The configured level wins
    over the level pack.yaml declares"; "A pack whose pack.yaml declares no level is listed at its configured level";
    "Without configured packs the offering is empty"; "The artifacts link of every pack answers its artifacts"; "A
    pack whose pack.yaml is invalid refuses the offering with pack_unreadable"; "A configured pack without pack.yaml
    refuses the offering with pack_unreadable"; "A configured pack whose directory is missing refuses the offering
    with pack_unreadable"; "An offering whose inputs change while it is answered is refused with stale_input"; "The
    offering needs the worktree token".
  - The scenario hooks. The fingerprint rule is the hook's own, not read from the contract (R-08 revised): a `GET`
    answered 200 with a JSON body on any path but `/` carries exactly one fingerprint, equal to `/info`'s; every
    other `GET` answer carries none; writes stay exempt. Every 2xx JSON answer's links are checked statically
    (R-20): `_links.self.href` is `/api/v1` plus the request path (and query, for a `GET`) when the path has a
    `GET`, the `Location`'s path on a 201 without one, and absent otherwise; every link at any depth resolves to a
    path template of the contract that defines its `method`; `templated` matches the `href`; every relation key is
    in the guide's table. After the last scenario, the suite fails when a 2xx status of an operation was exercised
    by no scenario; all 20 are exercised. The conformance check validates each value of a schema-valued
    `additionalProperties`, matches a response on path template and method, reads a YAML answer as text, and
    refuses an `additionalProperties` mapping that holds only annotations.
  - Steps (scenario README). Gone: `the response body is a list with an element whose field "<field>" is "<text>"`
    (its uses read `the field at "/items/0/id" is …`). Renamed: `the response body has <n> relationship(s)` and
    `a relationship of the response body points to "<urn>"` become `the artifact in the response body has <n>
    relationship(s)` and `a relationship of the artifact in the response body points to "<urn>"`; `the field
    "<field>" of the response body is the lines:` becomes `the field at "<json-pointer>" is the lines:`. New:
    `the field at "<json-pointer>" is ...` and `... starts with "<text>"`, `the answer has no link "<relation>"`,
    `the problem has no code`, `I follow the link "<relation>"`, `I follow the templated link "<relation>" with:`
    (a `| name | value |` table), `following "<relation>" links from the answer, the "<member>" ids are:` (at most
    100 pages, so a looping `next` fails instead of hanging), `following the link "<relation>" of every element of
    "<member>" answers <n>`, `the file "<path>" of the pack "<name>" is deleted`, `the path of the Location header
    is the href of the link "<relation>"` and `When I send <method> "<path>" as JSON with the body:`.
- Integrated with the activation set (#125, landed first). One `Link` and one `Links`, this mission's: `Link.method`
  is `^(GET|POST|PUT|DELETE)$`, widened over #125's `^(GET|POST|PUT)$`, and `Links` keeps #125's sentences that no
  relation points at an unbuilt operation and what `self` is on an activation answer. The closure check keeps this
  mission's one `_links` exemption; #125's second, which also let an `allOf`-wrapped `_links` by, is dropped with its
  fixtures. The fingerprint follows #175 on the activation set too: the 200 of each reserved `GET`
  (`getActivation`, `listEffectiveArtifacts`, `getActivationSkippedReport`, `getEffectiveGraph`,
  `getEffectiveActionContext`) declares it `required: true`, the verified read it becomes once built, and no write,
  refusal, `501` or other activation response declares it; while reserved they answer `501` and send none. Every 2xx of
  the activation set has a linked example (`Activation.minimal`, `ActivationChange.selected`,
  `EffectiveArtifactList.first-page`, `ActivationSkippedReport.nothing-skipped`, `EffectiveGraph.one-edge`,
  `EffectiveActionContext.implement`), each with `self` only. Reserved operations (`x-reserved`, ADR-0020 D8) are
  excluded from the success coverage of the scenario hook, since a `501` exercises no 2xx, and are never a link
  target: a link that resolves to one is reported. `checkRefusalExamples` does not judge a reserved operation's
  refusals: they describe it once built, and its codes stay `raised: false` until the marker goes. The contract has
  28 operations; the JUnit count of `CharterContractSuite` is 210 with the activation set's scenarios.
### One wording for a pack file that is not UTF-8 (issue #273)

No schema, operation, parameter, response or header changes in this contract. A pack file whose bytes are not valid
UTF-8 is refused by validate mode with the detail `<pack>/<file> is not valid YAML: the file is not valid UTF-8`, as
before. The service's own load now words the refusal the same way: it said `the file cannot be read as UTF-8 text`.
That reason is not answered by any operation of this contract.

### The charter schema declares `activated_skills` and `activated_anti_patterns` (issue #283)

No schema, operation, parameter, response or header changes in this contract; the change is to the pack schema
`contracts/packs/schemas/charter.schema.json`, which now declares all thirteen activation keys core writes.

- Added: `activated_skills` and `activated_anti_patterns`, root keys core's activation writer puts in `charter.yaml`,
  in the shape of the other `activated_*` keys: absent or `null`, `[]` (none) or a list of ids. An absent or `null`
  `activated_skills` puts only the org packs' required skills in force, not every skill; the key's description says
  so. Additive, not breaking (ADR-0022). A `charter.yaml` found inside a pack that holds either key was refused as
  `artifact_schema_error`, `unknown key '<key>'`; it now validates, and a value that is not a list of strings is
  refused at the key.

### The charter schema declares `activated_glossary_packs` (issue #131)

No schema, operation, parameter, response or header changes in this contract; the change is to the pack schema
`contracts/packs/schemas/charter.schema.json`.

- Added: `activated_glossary_packs`, the root key core's activation writer puts in `charter.yaml`, in the shape of the
  other `activated_*` keys: absent or `null` (every glossary pack is in force), `[]` (none) or a list of glossary pack
  ids. Additive, not breaking (ADR-0022). A `charter.yaml` found inside a pack (`file-kinds.yaml`, `**/charter.yaml`)
  that holds the key was refused as `artifact_schema_error`, `unknown key 'activated_glossary_packs'`; it now
  validates, and a value that is not a list of strings is refused at the key.

### Mission types are an artifact kind (issue #194)

No schema, operation, parameter, response or header changes (mission mission-type-consolidation, ADR-0044).

- Changed: `putPackRelationship` (`PUT /packs/{packId}/relationships`) registers an edge whose `from` is
  `mission_type:<id>` when the pack holds that mission type's file, `missions/<id>/<id>.mission-type.yaml`. Until
  now every such edge was refused 409 `registration_conflict`: a mission type was a node kind only, so no artifact of
  the pack had that URN. A `from` naming a mission type the pack does not hold is still 409. The
  `NodeKind` schema's description, "every artifact kind plus action and mission_type", is kept: `mission_type` is
  now one of the artifact kinds, so the enum is unchanged. Recorded as DIV-503 in the divergence register.
- Changed: the source fingerprint and the pack revision (`ETag`) of a pack with mission content change once, as the
  mission-type file replaces the retired mission files.
- Scenarios: `relationships.feature` is new ("A relationship may be authored from a mission type") and holds "A
  relationship from a mission type of the pack is registered (ADR-0044, NFR-001)" and "A relationship from a mission
  type the pack does not hold is still a conflict". No step is new.

### CRG tension and graph-node fields (issues #235 and #236)

- Changed (description only): the `Relation` description no longer says that a graph document's
  `in_tension_with` edge is read as `impacts` with strength -1. The service holds it as a tension
  beside the relationships and writes it back as written (ADR-0028, ADR-0045); it never answers
  `in_tension_with` as a relationship, as before. No enum value, path or wire shape changes. The
  `Removed` entry and the reserved-`impacts` item under `Added` below record the reading as it was
  then. The graph-node fields of #236 have no contract surface.

### Pack-level files round trip (issue #195)

- Changed: the `DiagnosticCode` value `pack_unknown_key` is retired and never raised (ADR-0036
  decision 3). A `pack.yaml` key the pack descriptor schema does not declare, a retired field such
  as `accompanies_doctrine_pack` included, no longer loads with this warning: the schema is closed,
  and the pack is refused as `pack_unreadable`. The value stays in the enum, so no shape changes; no
  operation references `DiagnosticCode`. Operator approval: #195, 2026-10-28.

### The contract document (issue #208)

- Added: `getOpenApiDocument` (`GET /openapi.yaml`, Service tag) answers the bundled contract the
  service was built from, byte for byte, as `application/yaml` (RFC 9512), with a strong `ETag`: the
  SHA-256 digest of those bytes, `"sha256:<64 hex>"` (the new `ContractETag` header). An optional
  `If-None-Match` is compared weakly; a match, or `*`, is a `304` without a body that carries the
  `ETag` again. It needs the worktree token like every operation (`401` and `403` as the shared
  responses) and never sends `Charter-Source-Fingerprint`, which now says so. No version bump: the
  contract is unreleased, so this is part of 0.1.0. The `service-desc` link (RFC 8631) to it is left
  to #126, which sets how the contract expresses links.
- Lint: `error-responses-are-problems` exempts a `304`, which RFC 9110 forbids a body, and the new
  rule `not-modified-has-no-body` holds every `304` to no content, with a planted fixture.
- The service serves it (#209): the deployable packages the `bundleContract` output, so the answer
  is the bundle a release publishes, and the `ETag` equals its `.sha256` checksum. A value of
  `If-None-Match` that is not an entity-tag list matches nothing and is answered `200`. (Between
  #208 and #209 the service answered a `501` `Problem`.) No scenario calls it yet; its scenarios
  are #210.

### Contract tooling (issues #119 and #45)

- No shape changes. Code generated from the contract now has one class per schema: the
  generator's parser read some `additionalProperties: false` as a schema object standing for
  `false`, which the generator took for an open map, and it emitted that reading as a copy
  (`Problem1`, `Tactic1`, `Directive1`, `DirectiveRegistration1` and seven more). The service's
  build reads such a boolean schema back as its boolean (issue #119), so a client that generates
  from the split contract with the same generator should do the same.
- A pushed `contract-charter-v<version>` tag publishes the release: the workflow
  `contract-release.yml` attaches `charter-openapi.yaml`, its `.sha256` and
  `charter-scenarios.tar.gz` (the contract scenarios) to a GitHub Release of the tag, and
  publishes nothing unless the tag, `info.version` and the top heading name one released
  version (issue #45). No release is cut yet.

### User journey scenarios (mission charter-conceptual-model-and-journeys)

- Scenarios: `journeys/journey-01-getting-started.feature` to `journeys/journey-11-human-reviews.feature` are new, one
  per user journey (`@journey-NN`). Their runnable scenarios use only the existing step vocabulary; the scenarios that
  need a surface the contract does not have yet are tagged `@WIP`, a new tag the contract suite excludes. The scenario
  archive a release publishes includes them, so a consumer that runs the scenarios must exclude `@WIP` too. No
  operation, schema or existing expectation changes.

### Regular artefact read and registration (mission regular-artefact-read-registration)

- Added: per-kind reads and registrations for directive, styleguide, paradigm and toolguide:
  `listPack<Kind>s`, `getPack<Kind>`, `registerPack<Kind>` (POST, create; a duplicate id is 409
  `registration_conflict`) and `putPack<Kind>` (PUT, create or replace). Registration is functional
  (ADR-0026): there is no `DELETE` or `PATCH`. A registration answers 201 (create) or 200 (replace)
  with the resource and an `ETag` (the pack revision); its `Location` is the created resource's full
  request URI. An item read sends the `ETag` of the snapshot its body was read from. `If-Match` is
  optional on an artifact write and compared strongly: a weak tag never matches, `*` matches, and a
  list matches when any member does; a stale one is 412.
- Added: `putPackRelationship` (`PUT /packs/{packId}/relationships`) registers one relationship,
  idempotent on the unordered `{from, to}` pair (ADR-0027); it requires `If-Match` (428 without it)
  and answers 201 when it creates the pair, 200 when it replaces it.
- Added: refusal codes `directive_not_found`, `styleguide_not_found`, `paradigm_not_found` and
  `toolguide_not_found` (404, a pack without that artifact), `validation_failed` (422) and
  `registration_conflict` (409), with their responses. A list endpoint's 404 is the new
  `PackNotFound` response: an unknown pack is its only cause.
- Changed: `governance_policy_violation` is answered with 409, not 500. ADR-0018 D10 is amended: a
  registration answers 200 or 201, and the problem-only lint rule exempts both successes.
  `Charter-Source-Fingerprint` names every read that sends it and the `*.toolguide.yaml` files it
  digests; a registration does not send it.
- Scenarios: `directives.feature`, `paradigms.feature`, `styleguides.feature` and `toolguides.feature`
  are new ("<Kind>s are read and registered functionally"). `errors.feature` lists the new raised
  codes and expects `governance_policy_violation` with 409.

### Verified source fingerprint on every read (mission verified-source-fingerprint)

Description text only: no schema, operation, parameter, response or header declaration changes.

- Fixed: `listPackTactics` and `getPackTactic` send `Charter-Source-Fingerprint` on 200, the digest the
  read verified; they refuse with `stale_input` when their inputs change while they are read (#173). The
  source fingerprint covers the tactic, directive, styleguide and paradigm files the service loads
  (#120).
- Changed: a configured pack whose directory is missing, a pack holding a file the fingerprint covers
  that cannot be read, or a pack directory that cannot be listed, refuses every read with 500
  `pack_unreadable`: a tactic read answered 404 `pack_not_found` before, and `/info` answered 200
  for an unreadable tactic, directive, styleguide or paradigm file or an unlistable subdirectory,
  because it digested only `pack.yaml` and the root of each pack. A file that vanishes while a pack
  is walked (an atomic save) is not an error. A load
  refusal whose inputs changed meanwhile is `stale_input`. The fingerprint's value changes for packs
  that hold loaded-kind files. The descriptions of `Charter-Source-Fingerprint`,
  `ServiceInfo.sourceFingerprint`, `CharterRefusalCode`, `TacticNotFound` and both tactic operations
  state this; the dead `x-source` path of `ServiceInfo.worktree` now names `ServiceInfoQuery` in the
  `packs` module.
- Scenarios: `service.feature` gains "The source fingerprint follows a tactic file". `tactics.feature`
  is new ("The tactic reads answer with the source fingerprint they verified") and holds "A tactic
  list answers with the source fingerprint it verified", "One tactic answers with the source
  fingerprint it verified", "A tactic list whose tactic file changes while it is answered is refused
  with stale_input", "One tactic whose file changes while it is answered is refused with
  stale_input", "A read of an absent tactic whose inputs change is stale, not not-found", "A tactic
  read that watches for a change and sees none answers with the digest it verified", "A tactic read
  of a pack whose directory is missing is refused with pack_unreadable" and "A missing sibling pack
  directory refuses a tactic read of a healthy pack".

### Tactic retrieval; pack listing and action context removed (mission application-edge-read-path)

The inbound contract changes (C-004) while it is still `0.1.0-SNAPSHOT`.

- Added: `listPackTactics`, `GET /packs/{packId}/tactics` (tag `Tactics`), a `200` array of `Tactic`;
  `getPackTactic`, `GET /packs/{packId}/tactics/{tacticId}`, a `200` `Tactic`. Both answer `404` with the new
  `TacticNotFound` response, a `CharterRefusal`.
- Added: schemas `Tactic`, `TacticStep`, `TacticReference`; parameters `PackId`, `TacticId`.
- Added: `CharterRefusalCode` values `pack_not_found` (the pack is absent or unreadable; since the verified
  source fingerprint, an identifier that matches no configured pack, and an unreadable pack is
  `pack_unreadable`) and `tactic_not_found`
  (the pack has no such tactic), both `404`, not retryable, raised.
- Removed: `listPacks`, `GET /packs`; `refreshPacks`, `POST /packs/refresh`; the action-context operation,
  `GET /mission-types/{missionType}/actions/{action}/context`. With them go the schemas `PackListing`,
  `PackSummary`, `ActionContext`, `GuidanceEntry`, the parameters `Action`, `Depth`, `MissionType`, their
  examples, and the scenario features `packs.feature`, `refresh.feature` and `action_context.feature`.
- Kept: `GET /info`. The vocabulary schemas `Diagnostic`, `DiagnosticCode`, `NodeKind`, `PackLevel`, `Relation`,
  `Urn` and `Slug` stay although no operation references them now: the load-path vocabulary test reads them.
  `mission_type_not_found` and `action_not_found` stay in `CharterRefusalCode`, now marked not raised: the
  queries behind them are gone. So are `pack_level_mismatch` and `drg_schema_error`, which only the removed
  offering load raised. The `errors.feature`, `service.feature` and `security.feature` probes moved to `/info` and
  the tactic paths.

### Added

- The `@contract` tag on the contract scenarios in `scenarios/`, which selects them, and the
  `@provisional` tag on `action_context.feature`.
- Provenance on every resource property: `x-source` names the Java producer of a read field,
  `x-derived` the rule and contract inputs of a computed one (`ActionContext.action`,
  `ActionContext.missionType`, `ActionContext.guidance`, `GuidanceEntry.via`); a citation on a
  referenced enum or scalar schema holds wherever that schema is used.
- `x-provisional` on the action-context operation and on `PackSummary.diagnostics`.
- Descriptions of `PackListing.packs`, `Diagnostic.detail` and `ActionContext.guidance`.
- Scenario "A directive scoped by both an action and its mission type is answered once": pins
  the built-in `DIRECTIVE_042` case (one entry, through the action). *(Replaced, see "Action
  context and the seam surface" below: the mission-type node is no longer walked.)*
- Scenario "A scope edge to a node the graph does not declare is answered without a label":
  pins today's answer (the entry is listed with a `null` label).
- Scenario "A context read over a pack whose graph shard is malformed is refused": pins
  today's `500` with `drg_schema_error`.
- Feature `errors.feature`, "Failures outside the charter's refusals": scenario "A path the
  contract does not define is answered with a not-found problem" pins `404` with the body
  `{"title": "Not Found", "status": 404}`, and scenario "A request that accepts no
  representation the operation has is answered with a not-acceptable problem" pins `406` with
  `{"title": "Not Acceptable", "status": 406}`, served as `application/problem+json`. Both
  bodies are the closed `Problem`; until now these failures answered with the framework's own
  error document (`timestamp`, `error`, `path`) or an empty body.
- The step `When I GET "<path>" accepting "<media type>"`, for the `406` scenario.
- `CharterRefusalCode` value `internal_error` (operator ruling DM
  01M599MEAJS44RYEY0X387BY4H): every 500 is a `CharterRefusal`, and a failure no other code
  names is refused with `internal_error` and a generic detail that names nothing of the
  failure. The `OfferingUnreadable` 500 response says so. Scenario "An unexpected failure is
  refused with internal_error and says nothing about it" in `errors.feature` pins the body,
  with the step `Given the service fails unexpectedly while it reads the packs`.
- The scenario suite checks every response against the contract: the content type must be
  one the operation declares for the status (or its `default`), and the body must validate
  against that content's schema. A path no operation defines must answer the shared
  `Problem` response.
- What the cutover needs, reserved before the release (ADR-0009, operator decisions of
  2026-10-19, provisional): a response field added after 1.0 is a breaking change, so these are
  in 1.0 from the start.
  - `Relation` value `impacts`: a directed, signed edge (core ADR 2026-07-26-3). A graph edge
    with relation `impacts` carries its strength in `impacts`, from -1 (sabotages) to +1
    (reinforces); a negative strength is a tension claim. A strength outside [-1, 1], a missing
    one, or one on another relation is refused as `drg_schema_error`.
  - `GuidanceEntry.impacts`: the strength of the edge that brought the artifact in, `null` for
    every relation but `impacts`; the walk follows no `impacts` edge, so it is `null`
    throughout this version.
  - `CharterRefusalCode` values `governance_policy_violation` and `stale_input`, reserved when
    they were added. `governance_policy_violation` is for the pack mutability policy
    (ADR-0012) and is still raised by nothing; `stale_input` is now raised (see "Action context
    and the seam surface" below) and may be retried, like `internal_error`.
  - Query parameter `depth` on the action-context operation and `ActionContext.depth`, which
    echoes it. Its range and meaning are under "Action context and the seam surface" below. A
    depth outside the range is a `400` Problem, not a refusal.
  - `ActionContext.governanceFingerprint`: a `sha256:` digest of the packs in force and the
    merged graph the answer was computed from, independent of host paths, formatting and
    declaration order. New schema `Sha256Digest`.
  - Operation `getServiceInfo`, `GET /info` (tag `Service`), answering the closed `ServiceInfo`:
    `contractVersion`, `worktree` (an opaque id, `charter.worktree-id` or one derived from the
    pack directories, never a host path) and `sourceFingerprint` (a digest of the sources the
    offering is loaded from; its inputs are under "Action context and the seam surface" below).
    A pack that cannot be read refuses it with a `500`.
  - Response headers `Charter-Contract-Version`, `Charter-Worktree` and
    `Charter-Source-Fingerprint`, defined once in `headers/`. Which responses carry the
    fingerprint is under "Action context and the seam surface" below.
- Scenarios in `action_context.feature`:
  - "The depth of the walk is echoed, and this version answers depth 1 only" *(replaced, see
    "Action context and the seam surface" below)*;
  - "A depth this version does not answer is a bad request, not a charter refusal" *(renamed
    "A depth outside 0 to 2 is a bad request, not a charter refusal")*;
  - "The governance fingerprint is the same while the governance is, and changes with it";
  - "An impacts edge brings nothing into scope" (which also reads a legacy `in_tension_with`
    edge);
  - "An impacts edge without a strength in [-1, 1] is refused";
  - "A strength on an edge that is not impacts is refused".
- Feature `service.feature`, "What the service says about itself": the info operation, the
  three headers on an answer, and the source fingerprint following the files. *(The refusal
  half changed: a refusal carries the contract version and the worktree, never the
  fingerprint. See "Action context and the seam surface" below.)*
- Steps for digests and headers: `Then the response body, apart from "<field>", is:`, the
  field and header steps, and remembering a field to compare it later (`scenarios/README.md`).
- `DiagnosticCode` value `pack_unknown_key` (#114, operator ruling R5): a key in `pack.yaml` that
  the pack descriptor schema does not declare, a retired field included, is reported as a
  diagnostic and ignored, not refused. Scenario "A pack descriptor with an undeclared key loads
  with a diagnostic" in `packs.feature` pins the answer.
- Scenario "A pack descriptor whose requires_core is not a version specifier is refused" in
  `packs.feature` (#114, R3): `requires_core: banana` is refused as `pack_unreadable`, with the
  detail `pack.yaml of <name>: requires_core '<v>' is not a PEP 440 version specifier`.
- Scenario "An org pack without a drg/fragment.yaml is refused" in `packs.feature` (#114, R16): an
  org pack with no `drg/fragment.yaml` is refused as `pack_unreadable`, with the detail
  `org pack <name> has no drg/fragment.yaml`. Before, it loaded with an empty graph. The scenario
  "The answer carries the load findings of the offering, as the pack listing does" in
  `action_context.feature` gives its org pack `qa` a `drg/fragment.yaml` instead of a root
  `qa.graph.yaml`; its answer is unchanged.
- Scenario "A graph shard that is not valid YAML is refused as a DRG document" in `packs.feature`
  (#114, R17): pins `drg_schema_error` for a shard that is not YAML; the body is compared apart from
  `detail`, which is the parser's own words.
- Scenario "A pack descriptor whose pack_level is an explicit null is refused" in `packs.feature`
  (#114, R19): pins `pack_unreadable` with the detail `pack.yaml of <name>: pack_level must be a
  string`.
- Scenario "A fragment that carries a header field is refused" in `packs.feature` (#114, R7): a
  fragment with `pack_name` is refused as `drg_schema_error`, with the detail
  `<pack>/drg/fragment.yaml: unknown key 'pack_name'`.

### Changed

- `info.version` is `0.1.0-SNAPSHOT` (was `1.0.0-SNAPSHOT`; ADR-0022). The examples of
  `ServiceInfo.contractVersion` read `0.1.0` (was `1.0.0`; description and example only, no
  schema change).
- The release gate (`ReleaseGate`) treats a 0.x minor as the breaking boundary while the major
  version is 0 (ADR-0022): the baseline is the newest release of the same `0.y`, a minor bump
  (`0.y` to `0.(y+1)`) needs a `### Breaking` subsection, and a patch is compared against the
  newest release of its minor and fails on a breaking change. On a minor bump the build reports
  what breaks against the previous minor, for information only. From 1.0.0 the major version is
  the boundary, as before.
- The three action-context scenarios that answer `200` also expect `depth` and `impacts: null`
  on every entry, and compare the body apart from `governanceFingerprint` (ADR-0009).
- Every `CharterRefusalCode` and `DiagnosticCode` value is snake_case on the wire:
  `mission_type_not_found`, `action_not_found`, `pack_level_mismatch`, `pack_unreadable`,
  `drg_schema_error`, `pack_level_missing` (`unresolved_edge_endpoint` is unchanged). The
  integration seam in core maps them to core's own spellings. The scenarios in
  `packs.feature` and `action_context.feature` assert the new spellings.
- The action-context answer is sorted by full URN, the order core gives the resolved
  artifacts of an action; it was the order of the edges in the merged graph. Scenario "Action
  scope comes first, then mission-type scope, without repeats" is renamed "The scope of the
  action and of its mission type is answered once each, sorted by URN" and expects that order.
  *(Replaced, see "Action context and the seam surface" below.)*
- `GuidanceEntry.via` is described as the node the edge starts from, so it stays true when the
  resolution walks further than one scope edge. `ActionContext.action` and `missionType` have
  descriptions of their own. The three keep their `Urn` type.
- Scenario "An action the mission type does not have is refused, not answered empty" is
  renamed "An action that is not a node of the graph is refused, not answered empty"; the
  answer is the same `404`.
- Every refusal scenario (three in `action_context.feature`, three in `packs.feature`) also
  asserts the whole body with `Then the response body is:`. A refusal body omits an absent
  optional member; it carried `"type": null` and `"instance": null`, which the closed
  `Problem` schema does not allow.
- The `ActionContext.guidance` derivation rule reads "an artifact in both scopes keeps its
  action entry" (wording only). *(Replaced: the rule is now the five-step walk, see "Action
  context and the seam surface" below.)*
- Scenario "A pack descriptor without pack_level loads at its configured level, with a
  warning" is renamed "A pack descriptor without pack_level loads at its configured level,
  with a diagnostic", the name the answer gives it (`diagnostics`); the answer is unchanged.

- `CharterRefusalCode` states the consumer dispatch rules (description only, no scenario or
  schema change): a body with a `code` is a charter refusal; a body without one is a transport or
  routing failure; `internal_error` is a fault and may be retried; every other code is a
  deterministic refusal.
- The shared `default` `Problem` response (`contracts/_shared/responses/Problem.yaml`) of every
  operation declares the response headers `Charter-Contract-Version`, `Charter-Worktree` and
  `Charter-Source-Fingerprint`, by reference to `headers/`. The service already sent them
  there; the contract did not say so (#90, ADR-0011). A header declaration is not a response
  property, so no schema changes. `contracts/lint/ruleset.yaml` gains the rule
  `charter-headers-on-every-response`, which fails any response of any operation that lacks one
  of the three. `_shared/VENDORED.md` records that this repository's copy is canonical.
- Scenario "A bad request carries the headers too" in `service.feature`: the `400` for a depth
  this version does not answer carries the three headers. *(Renamed "A bad request carries the
  contract version and the worktree, not the fingerprint", and it asserts there is no
  fingerprint. See "Action context and the seam surface" below.)*
- `CharterRefusalCode` value `unresolved_edge_endpoint` is no longer raised (#114, R6): graph edge
  endpoints are URNs, and an endpoint that is not one is refused as `drg_schema_error`. The value
  stays in the enum; its description changed and its `x-refusal` entry says `raised: false`.
  Scenario "Every other refusal code is raised by an operation" in `errors.feature` no longer lists
  it and is renamed "The refusal codes the contract marks raised are exactly those an operation
  raises".
- The `OfferingUnreadable` response description gives `drg_schema_error` instead of
  `unresolved_edge_endpoint` as its example code, since the latter is no longer raised (description
  only, #114).
- Scenario "A bare edge endpoint the fragment does not declare is refused" in `packs.feature` is
  renamed "A bare edge endpoint is refused as not a URN" and expects `drg_schema_error` ("Invalid
  DRG document") with the detail `qa/drg/fragment.yaml: edge endpoint 'QA_404' is not a URN`
  (#114, R6). It was `unresolved_edge_endpoint`.
- Scenario fixtures in `packs.feature` (#114, R6, R7, R16): fragment edge endpoints are URNs
  (`directive:QA_001`), the `pack_name` header is dropped from fragments (the fragment schema has
  no such key), and the org packs `internal` and `rogue` carry a minimal `drg/fragment.yaml`. Their
  pinned answers are unchanged.
- A graph shard or fragment that is not valid YAML is refused as `drg_schema_error` (#114, R17;
  behavior change, no schema change: the code is already a `CharterRefusalCode` value). It was
  `pack_unreadable`. The detail `<pack>/<file> is not valid YAML: <first line of the parser's
  message>` is unchanged. A `pack.yaml` that is not valid YAML keeps `pack_unreadable`.
  The scenarios "A pack file rewritten while it is parsed is stale, not a schema error" (renamed
  from "..., not unreadable", which the answer no longer contrasts with) in
  `errors.feature` and "A refresh of a pack that cannot be loaded is refused with the pack's code"
  in `refresh.feature` expect `drg_schema_error` for their invalid shard (was `pack_unreadable`).
- An explicit `null` in a `pack.yaml` plain-string field is no longer read as absent (#114, R19;
  behavior change, no schema change: the codes are already `CharterRefusalCode` and
  `DiagnosticCode` values). It covers the four fields the `pack-descriptor` schema types as a plain
  string: `pack_id`, `pack_version`, `name` and `pack_level`. `pack_level: null` used to load with a
  `pack_level_missing` diagnostic (`200`); it is now refused with `pack_unreadable` (`500`) and the
  detail `pack.yaml of <name>: pack_level must be a string`. An explicit `null` `pack_id`,
  `pack_version` or `name` is refused with `pack.yaml of <name>: <key> must be a string` instead
  of `pack.yaml of <name> has no <key>`; the code is unchanged. An absent key keeps its answer.
  `parent_pack`, `requires_core`, `active_charter` and `pack_drg` are nullable in the schema and
  still accept `null`.
- A plain `~`, `Null` or `NULL` in any pack file is now null (#114, R18; behavior change, no schema
  change), as core's parser reads it. In `pack.yaml` a null in a required string field is therefore
  refused like an explicit `null`: `pack_unreadable`, `pack.yaml of <name>: <key> must be a
  string`. Before, it was read as the string `~`, `Null` or `NULL`. Quoted forms stay strings.
- A pack file whose bytes are not valid UTF-8 is now a parse failure, coded by its file kind (#114,
  R17; behavior change, no schema change). A shard or the fragment is refused as
  `drg_schema_error`, `<pack>/<file> is not valid YAML: the file is not valid UTF-8`; a
  `pack.yaml` as `pack_unreadable`, `pack.yaml of <name> is not valid YAML`. Before, every such file
  was `pack_unreadable`, `<pack>/<file> cannot be read`. An I/O failure keeps `cannot be read`.
- Pack files on the load path are validated against the schemas in `contracts/packs/schemas`
  (#114; behavior change, no schema change in this contract: the codes are already
  `CharterRefusalCode` values). Each of these used to load (`200`) and is now refused. In a
  graph shard or the fragment, with `drg_schema_error`:
  - a fragment that carries a header field (`pack_name`, `source_kind`, `layer_index`, and any
    other key the fragment schema does not declare), `<pack>/drg/fragment.yaml: unknown key
    '<key>'` (R7, which the scenario "A fragment that carries a header field is refused" pins);
  - a fragment edge with `when` (a shard edge keeps it), `<pack>/drg/fragment.yaml edge: unknown
    key 'when'`;
  - a fragment node id that is not `<kind>:<id>`-safe, such as `a:b`, `<pack>/drg/fragment.yaml:
    node id 'a:b' is not <kind>:<id>-safe`;
  - an explicit `edges:` with no value (`null`) in a fragment, `<pack>/drg/fragment.yaml edges:
    expected a list`; an absent `edges` key still loads;
  - a shard without `schema_version`, `generated_at` or `nodes`, `<pack>/<file>: missing '<key>'`;
  - a shard `schema_version` that is not the string `1.0` (`2` is not a string, `'2.0'` does not
    match the pattern `^1\.0$`), `'schema_version' must be a string` or `'schema_version' does not
    match its pattern`;
  - a shard `generated_by` or `generated_at` that is not a string, `'<key>' must be a string`;
  - a shard node `tags` that is not a list, `<pack>/<file> tags: expected a list`.

  In `pack.yaml`, with `pack_unreadable`: a `requires_core` or `active_charter` that is not a string
  (`requires_core: 3`), `pack.yaml of <name>: <key> must be a string`.

  One shape is accepted that was refused: a shard node with `provenance`, which the shard schema
  declares (it was `drg_schema_error`, `unknown key 'provenance'`).
- An empty `pack_version` in `pack.yaml` is refused with its own detail (#114, R4; behavior
  change, no schema change: the code is already `pack_unreadable`). The `pack-descriptor` schema
  gives `pack_version` a `minLength` of 1, so `pack_version: ''` is `pack_unreadable` (`500`) with
  the detail `pack.yaml of <name>: pack_version must not be empty`. The detail is the same for
  any other plain-string field the schema gives a `minLength`; it has one today.
- `CharterRefusalCode` names the mutability-policy epic #55 (ADR-0012, steps #57 to #61), not #48,
  as the work that will raise `governance_policy_violation` (description only, #114): #48 is the
  pack-level policy and raises nothing. The scenario "governance_policy_violation is reserved" in
  `errors.feature` and `scenarios/README.md` say the same.

### Action context and the seam surface

Decided in ADR-0018 (provisional, 2026-10-19) and built before the 1.0 tag, while a shape change
is still compatible. By oasdiff's rules several of these are breaking for a strict consumer of the
closed responses; no tag exists, so no baseline is broken. The divergences from core are in
[`docs/conformance/divergence-register.md`](../../docs/conformance/divergence-register.md).

**The walk.**

- `getActionContext` answers the offering's walk in five breadth-first steps: scope from the
  action node only (the mission-type node's scope is no longer walked); `requires` from the
  scoped artifacts, transitively; `suggests` from the scoped artifacts only, up to `depth`;
  `requires` of the suggested artifacts, transitively (DIV-110, operator-confirmed); `vocabulary`,
  one hop. An artifact is listed by the first step that reaches it, and the answer is sorted by
  the full URN string. The operation applies no activation filter, for good (activation is the
  effective-context operation of #103).
- `depth` (parameter): minimum 0, maximum 2, default 2 (was 1 to 1, default 1). It now counts
  `suggests` hops. Core's `depth` means the same, so the seam maps a first load to 2, a recorded
  load to 1 and the public bundle read to 0.
- `ActionContext.depth`: minimum 0, still no maximum, so raising the request maximum later is
  compatible. The compat fixture `compatible-depth-max-raised` pins that, and
  `breaking-response-depth-max-raised` pins that a maximum on the response would make it
  breaking.
- `GuidanceEntry.relation` answers `scope`, `requires`, `suggests` or `vocabulary`.
  `GuidanceEntry.via` is the parent in the walk that listed the entry: the action node for a
  scope entry, otherwise another entry of the answer.
- `GuidanceEntry.hops` (required, integer, minimum 1): 1 for a scope entry, `hops(via) + 1`
  otherwise. Following `via` reaches the action in exactly `hops` edges.
- The walk goes through a node the graph does not declare (documented behavior, pinned by a unit
  test): the declared artifacts behind it are listed with `via` set to that node, and the node
  itself has a `null` label.
- `ActionContext.diagnostics` (required): the offering's load findings, as
  `PackSummary.diagnostics` carries them per pack.
- The path description and the `x-provisional` decision are rewritten and link the divergence
  register. The `software-dev/plan` example is regenerated from the service over open-packs.
- Scenarios: `action_context.feature` is rewritten (12 to 22 scenarios). The scenarios "The
  scope of the action and of its mission type is answered once each, sorted by URN", "A
  directive scoped by both an action and its mission type is answered once" and the echo
  scenario are replaced by the ones below, because the mission-type node is no longer walked:
  depth, each walk, the cycle and diamond cases, the DIV-110 cases both ways, precedence, the
  parent tie-breaks, the `via`/`hops` invariant, vocabulary, the undeclared-target case and `diagnostics`.
  `ContractShapeTest` checks that `ActionContext.depth` has no maximum.

**Service info and the fingerprint.**

- `ServiceInfo.serviceVersion` (required): the build's own version (`gradle.properties`
  `version`, `0.1.0-SNAPSHOT`), with a semantic-version pattern. It is separate from
  `contractVersion`, which names the whole document: an additive contract set is new operations
  and a minor bump. Every module jar name now carries the version.
- The source fingerprint covers every pack file the service loads, plus `config.yaml` and
  `charter.yaml` under the configured `charter.project-config-dir`, each under a domain-tagged
  label with a presence byte. A configured directory that does not exist fails startup. It names
  no host path.
- `Charter-Source-Fingerprint` is on **200 answers only**, once, with the digest that read
  verified. No refusal, 401 and 403 included, carries it (was: every response). The header stays
  optional and declared on every response, so the lint rule
  `charter-headers-on-every-response` passes.
- Scenarios in `service.feature` (6 to 10): the build version, the fingerprint following the project
  configuration, an edit of `config.yaml`, an absent file against an empty one, and the packs-only
  control; refusals and bad requests assert no fingerprint.

**Refusals.**

- `stale_input` is **raised** by every read, including `/info`, `/packs`, the refresh and the
  context read: inputs are digested before and after the answer, and also when a load fails or the
  second digest is refused. A change after the second digest, or one reverted between them, goes
  unseen.
- `/info` refuses `stale_input` when its second digest is refused, as the other reads do.
  `stale_input` is logged at INFO, without a stack trace.
- `governance_policy_violation` stays reserved: a declared pin, enforced by a unit test, until the
  mutability-policy epic #55 raises it.
- `CharterRefusalCode` carries the machine-readable `x-refusal` map (`status`, `retryable`,
  `raised`) for all nine codes. The description adds the 401 and 403 dispatch sentence: they have
  no charter code and are transport failures.
- Scenarios in `errors.feature` (3 to 9, 13 with their examples): `pack_unreadable` over a project
  configuration file that is a directory (rows for `/info`, `/packs`, the context read and
  `POST /packs/refresh`), the `internal_error` scenario retitled, `stale_input` on `/packs`, the
  context read, `POST /packs/refresh` and `/info`, a pack rewritten while it is parsed, a negative
  control, the `@contract @reserved` scenario for `governance_policy_violation` and the list of
  raised codes.
- `pack_unreadable` is no longer pinned on a shard that is not valid YAML: a shard parse failure
  becomes `drg_schema_error` under ruling R17.

**Security.**

- `components.securitySchemes.charterWorktreeToken` (HTTP bearer) and a root `security`
  requirement: every operation needs the token. New shared responses `Unauthorized` (401, with
  `WwwAuthenticate`) and `Forbidden` (403), declared on every operation. Both are `Problem` bodies
  without a charter code and carry `Charter-Contract-Version` and `Charter-Worktree`.
- 403 is a browser request: an `Origin`, `Sec-Fetch-Site` or `Sec-Fetch-Mode` header, or a `Host`
  whose name (not port) is not `localhost`, `127.0.0.1` or `[::1]`. An unknown path answers 401
  before 404. Container-level 400s answer before the guard and carry no Charter header. `TRACE`
  now allows `GET,HEAD,OPTIONS,POST`.
- Service behavior behind it: a token of at least 32 characters is required unless
  `charter.insecure-no-token=true`, and a token from argv or a system property (including through
  `spring.application.json`) refuses startup.
- Scenarios: `security.feature` (13 scenarios, 30 with their examples) and the steps for POST,
  headers and the suite token.

**Refresh.**

- Operation `refreshPacks`, `POST /packs/refresh` (tag `Packs`; operator ruling): no request body (a
  non-empty body is a `400`), answering `200` with the refreshed `PackListing` and one
  `Charter-Source-Fingerprint`; refuses with `401`, `403` and `500` like a read. Idempotent. It
  replaces the `POST /refresh` of an earlier draft, which was never released.
- Scenarios: `refresh.feature` (11).

**Other.**

- Compat fixtures: `compatible-depth-max-raised` and `breaking-response-depth-max-raised`
  (`compatibilityFixtures` now has 5 breaking and 2 compatible).
- Known contract tooling issue: the generator duplicates `Problem` as `Problem1` and `PackListing`
  as `PackListing1` (a swagger-parser `additionalProperties: false` bug). The wire shape is
  unaffected; the generator-side dedupe is issue #119. Fixed: see Contract tooling above.
- `scenarios/README.md` lists every new step.

### Removed

- `Relation` value `in_tension_with`, replaced by `impacts` (ADR-0009, core ADR 2026-07-26-3).
  `reconciles_tension` now points at the sides of a pair joined by a negative `impacts`. Until
  core migrates its graphs, the service reads a legacy `in_tension_with` edge as `impacts` with
  strength -1; it never answers `in_tension_with`.

### Provisional

- The action-context operation (`x-provisional`): the registered divergences from core are
  decided by the conformance corpus (issue #9). `action_context.feature` is tagged
  `@provisional`. The divergences (DIV-101 to DIV-110) live in the
  [divergence register](../../docs/conformance/divergence-register.md). The earlier note that the
  `requires`, `suggests` and `vocabulary` walks were unfinished is superseded by the walk
  described under "Action context and the seam surface" above.
- An action that is not a node of the graph answers `404 action_not_found`; for the same input
  core falls back to its non-bootstrap context (`charter/activation/context.py`). The shadow
  comparison (roadmap step 7) maps the charter's `404 action_not_found` to core's fallback and
  counts the pair as matching. The scenario "An action that is not a node of the graph is
  refused, not answered empty" says so. Registered as
  [DIV-101](../../docs/conformance/divergence-register.md).
- A scope edge to a node the graph does not declare is listed with a `null` label; core skips
  that artifact (`charter/activation/context_renderers/delivery_table.py`). The scenario "A
  scope edge to a node the graph does not declare is answered without a label" pins the
  charter's answer and says core omits it. Registered as
  [DIV-102](../../docs/conformance/divergence-register.md).
- `PackSummary.diagnostics` (`x-provisional`): `pack_level_missing` becomes a refusal when core
  ADR 2026-10-11-2 closes its deprecation window.
