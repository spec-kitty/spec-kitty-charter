# Changelog

All notable changes to the `charter` contract are recorded here, newest first. The module
version is the `info.version` of `openapi.yaml`. The contract scenarios under `scenarios/` are
half of the contract, and an OpenAPI diff cannot see them, so every scenario change gets a line
here too.

## 0.1.0-SNAPSHOT

The first contract version core will vendor, not yet released: an unreleased contract version
carries the `-SNAPSHOT` suffix. A first `0.1.0-SNAPSHOT` draft landed before the operator rulings
of 2026-10-18, which renumbered it `1.0.0-SNAPSHOT`. The operator ruling of 2026-10-20 (ADR-0022)
withdrew that label: the contract is published as 0.x while it iterates with core's generated
client, and 1.0.0 is minted once it is stable. The version is `0.1.0-SNAPSHOT` again. No release
was cut under either label. `Added` lists what this version holds that the first draft did not;
`Changed` lists what differs from that draft.

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
  The scenarios "A pack file rewritten while it is parsed is stale, not unreadable" in
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
- `governance_policy_violation` stays reserved: a declared pin, enforced by a unit test, until #48
  raises it.
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
  unaffected; the generator-side dedupe is issue #119.
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
