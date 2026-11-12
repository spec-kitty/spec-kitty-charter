# Contract scenarios

The behavior half of the charter contract (operator decision, 2026-10-18: the Java rewrite is
built against the contract scenarios and the OpenAPI document). Each scenario names packs by
their files, calls one operation of `../openapi.yaml` and states the answer. The scenarios are
language-neutral: the Java service runs them through `deployable`'s Cucumber suite, and
any other implementation may bind the same steps.

## Step vocabulary

Keep it small; add a step only when a scenario cannot be written without it.

- `Given the <level> pack "<name>" with the file "<path>":` followed by a doc string. Adds one
  file to a pack; the pack is configured at the given level in the order first named.
- `Given the service fails unexpectedly while it reads the packs` — plants a failure that no
  refusal names, for the `internal_error` scenario.
- `When I GET "<path>"` — a path below the `/api/v1` server prefix, with its query if it has
  one.
- `When I GET "<path>" accepting "<media type>"` — the same, with that `Accept` header.
- `When I send <method> "<path>"` — any method name, for an outline whose examples differ in the method as well as the
  path (for example `GET` and `POST`).
- `When I POST "<path>"` and `When I POST "<path>" with the body:` (a doc string) — the same, with POST.
- `When I remember the response body`, then `Then the response body is the remembered one` or `... differs from the
  remembered one`; `When I remember the header "<name>"`, then `Then the response header "<name>" is the remembered
  header` or `... differs from the remembered header` — for a whole answer, such as a listing, and a header that must
  stay or change with the files.
- Every request carries the worktree token as `Authorization: Bearer <token>`, the token the suite configures the
  service with, unless one of these steps says otherwise. `<method>` is any HTTP method name, for example `GET` or
  `POST`, and `{token}` in a value stands for the suite's token:
  - `When I <method> "<path>" without the worktree token`;
  - `When I <method> "<path>" with the Authorization "<value>"` — that `Authorization` header instead of the token's;
  - `When I <method> "<path>" with the header "<name>": "<value>"` — an extra header, with the token still sent
    (`Host` replaces the default host `localhost`);
  - `When I <method> "<path>" without the worktree token and with the header "<name>": "<value>"`;
  - `When I <method> "<path>" with the headers "<name>": "<value>" and "<name>": "<value>"`.
- `When I GET "<path>" accepting "<media type>" with If-None-Match the remembered header "<name>"` — the same, with the
  `If-None-Match` a header remembered earlier with `When I remember the header "<name>"` (the `ETag` of an answer the client
  now holds).
- `Then the response status is <n>`
- `Then the response body is:` — the JSON body, compared exactly (order of arrays and the
  `detail` sentences included).
- `Then the response body is the text:` — a body that is text, not JSON (the `text/markdown` answer of the pack document
  read), compared exactly with the doc string and the final newline that the `Given the <level> pack ... with the file`
  step writes after its own. `Then the response body is empty` — no body at all, as a `304` has none.
- `Then the response body, apart from "<field>", is:` — the same, after taking that top-level
  field out of the body, which must have it; for a digest whose value no scenario pins.
- `Then the field "<field>" of the response body is "<text>"`, `... is a sha256 digest` and
  `... is the contract version` (the `info.version` of `../openapi.yaml`).
- `Then the field "<field>" of the response body starts with "<text>"` — for a detail whose tail is a third-party
  parser's text (a YAML syntax error), which no scenario pins: only the part the charter wrote.
- `When I remember the field "<field>" of the response body`, then `Then the field "<field>" of
  the response body is the remembered one` or `... differs from the remembered one` — for a
  digest that must stay or change with the files.
- `Then the response header "<name>" is "<text>"`, `... is a sha256 digest` and `... is the
  contract version`; `Then the response has no "<name>" header`.
- `Given the project configuration file "<name>":` followed by a doc string, and `Given the project
  configuration file "<name>" is absent` — write or delete `config.yaml` or `charter.yaml` in the
  scenario's project configuration directory (`<root>/.kittify`), which always exists and holds
  neither file at the start. The service digests the two files and never reads them, so the content
  is free.
- `Given the project configuration file "<name>" is a directory` — an empty directory stands where the file would be, so
  the service cannot read it (an I/O failure that holds for root too, unlike a permission change).
- `Then the field "<field>" of the response body is a semantic version` — matches the semantic
  version pattern of `ServiceInfo.serviceVersion`.
- `Then the response header "<name>" appears once` — exactly one value for the header, not a repeated one.
- `Then the response header "<name>" is the remembered "<field>"` — the header equals a field remembered earlier
  with `When I remember the field "<field>" of the response body`, for example the `/info` fingerprint a later
  read must report for unchanged inputs.
- `Given a pack file changes while the next read is answered` — the next read that digests the inputs (any built read:
  `/info` or a tactic read) sees a pack file rewritten (a node added to the first graph shard) after
  its first digest and before its second.
  `Given a pack file is rewritten to invalid YAML while the next read is answered` — the same, with the shard
  rewritten to text that is not YAML and left so. `Given no pack file changes while the next read is answered` —
  the same seam, changing nothing: the negative control. The change is made to a real file on disk, once; the
  service computes both digests itself.
- `Given a tactic file changes while the next read is answered` — the same seam, with the one `*.tactic.yaml` of the
  scenario's packs rewritten (its `name:` line; never a graph shard). The step refuses a scenario that configures no
  tactic file or more than one.
- `Given the directory of the pack "<name>" is missing` — deletes the configured pack's whole directory, so the first
  digest cannot read it. To remove a sibling, first write any file to it with the pack step above.
- `Then the contract declares the refusal code "<code>"`, `... is answered with status <n>`, `... may not be
  retried` and `... is raised by no operation` — read the machine-readable `x-refusal` map of
  `../schemas/CharterRefusalCode.yaml` (`status`, `retryable`, `raised` per code). `Then the contract says exactly
  these refusal codes are raised by an operation:` with a one-column table lists the codes whose `raised` is true.
  `Then the contract says "<code>" is answered with status <n> by the operation "<operationId>"` reads the
  `operations` map of a code's entry, the status that one operation answers the code with in place of the entry's
  `status`. After every scenario, each refusal it observed must have a status the map declares for its code (the
  entry's, or an operation's) and `raised: true`.
- `Then the problem code is "<code>"` — the `code` of a `Problem` body.
- `Then the problem has no code` — an `application/problem+json` body without a `code` member (absent, not empty): a
  transport or routing failure, not a charter refusal.
- `Then the field at "<json-pointer>" is "<text>"`, `... is <integer>` and `... is true` or `... is false` — the
  member an RFC 6901 JSON Pointer names (for example `/page/hasNextPage` or `/items/0/id`), which must be there and
  of that JSON type. `Then the field at "<json-pointer>" starts with "<text>"` and `... ends with "<text>"` — the
  same for the start or the end of a string, where the rest is opaque (a remembered page cursor in a detail).
- `Then the path of the Location header is the href of the link "<relation>"` — a created resource's `Location`
  (absolute or not) has, as its path, the `href` of that relation in the answer's `_links`.
- `Then the answer has no link "<relation>"` — the body's `_links` has no member of that relation key.
- `When I follow the link "<relation>"` — GET the `href` of that relation in the last answer's `_links`, with the
  `/api/v1` server prefix taken off as the request steps expect.
- `When I follow the templated link "<relation>" with:` followed by a two-column table of `| name | value |` rows —
  the link must carry `templated: true`; its RFC 6570 `href` is expanded with the table's values (a simple `{name}`
  becomes the percent-encoded value, a form-style query `{?a,b}` becomes `?a=..&b=..` for the names the table gives,
  and nothing when it gives none) and followed like `I follow the link`.
- `Then following the link "<relation>" of every element of "<member>" answers <n>` — GETs the `href` of that
  relation in the `_links` of every element of the array `<member>` of the last answer, which must hold at least one,
  and expects each to answer `<n>`.
- `Then following "<relation>" links from the answer, the "<member>" ids are:` followed by a table with the header
  `| kind | id |` — collects the `kind` and `id` of every element of `<member>` on the last answer and on every page
  its `<relation>` link leads to, following it until a page has none (at most 100 pages, so a looping `next` fails
  the step instead of hanging it), and compares them with the table in order, so
  every element is served exactly once. Each followed page must answer 200; the last one is the step's answer.
- `When the file "<path>" of the pack "<name>" is deleted` — deletes one existing file of a configured pack, between
  two requests (a cursor's anchor that goes away between pages).
- `Then the pack "<name>" holds no file "<path>"` — nothing is stored at the path inside the configured pack, neither a
  file nor a directory (a mission type's removal takes its step files, its skeletons and their directories).
- `When I POST "<path>" as JSON with the body:`, `When I PUT "<path>" with the body:`, `When I PUT "<path>" with
  If-Match "<value>" and the body:`, `When I remember the ETag` and `When I PUT "<path>" with the remembered ETag and the
  body:` — a registration with a JSON doc string, and its `If-Match` precondition. `When I send <method> "<path>" as
  JSON with the body:` — the same with any method, for an outline whose examples differ in the method.
- `Then the field at "<pointer>" is absent` — nothing stands at the JSON Pointer, for example a member a shape no
  longer carries (an artifact's `relationships`, ADR-0064).
- `When I PUT "<path>" with the body, its "<field>" padded to <n> bytes:` — the same PUT, after appending `a` to that
  top-level string field of the JSON doc string until it is exactly `<n>` bytes of UTF-8; for a size cap no doc string
  can spell out.
- `Then the graph holds exactly the nodes:`, `... the edges:` and `... the layer overrides:` followed by a doc string, one
  line per member, in the answer's order: a URN; `<source> <relation> <target>`; `<artifact> won by <pack>@<level> over
  <pack>@<level>`. `Then the context holds exactly the artifacts:` (`<kind>:<id> <state>/<reason>`, then
  `suggested when "<when>" because "<reason>"` after a space for a suggestion) and `... the enhancements:` (`<kind>:<id> is enhanced by <kind>:<id>[,
  ...]`) read the action context the same way (`effective-graph-and-context.feature`, bound in `EffectiveGraphSteps`).
- `Then the field at "<json-pointer>" is null` — the member is there and is JSON `null`, as a required nullable member is
  written; a member the answer leaves out is `is absent` (agent-profile-reads.feature, bound in `ProfileSteps`).
- `Then the governance holds exactly the entries:` followed by a doc string, one entry of the `governance` list of the last
  answer per line, in the answer's order (sorted by urn): `<urn> <step> via <urn> hops <n>`, then ` in <packId> linked` for
  a held entry whose `_links.artifact` is the pack-scoped read of it in the pack that holds it, ` in <packId> unlinked` for
  a held entry with no `artifact` link (a kind the generic route does not serve), or ` not held unlinked` for an entry no
  pack of the closure holds (`held` false, a null `packId`, no link); then ` edge {}` for an entry whose `edge` states
  neither word, or ` edge {when "<when>" because "<reason>"}` for one that states any (one word alone is written alone):
  every entry has an `edge`, whatever its relation (agent-profile-reads.feature, bound in `ProfileSteps`).
- `Then the field at "<json-pointer>" is an empty object` — the member is there and is `{}` (agent-profile-reads.feature
  and effective-agent-profile.feature, bound in `ProfileSteps`).
- `Then the effective governance holds exactly the entries:` followed by a doc string, one entry of the `governance` list of
  the last answer per line, in the answer's order (sorted by urn): `<urn> <step> via <urn> hops <n> <state>/<reason> in
  <packId> linked`, where `<state>/<reason>` are the state and the reason the activation gives the entry's winner,
  `<packId>` is the pack that holds that winner, and `linked` says its `_links.artifact` is the pack-scoped read of the
  entry in that pack (`unlinked` for an entry without the link, a kind the generic route does not serve); then ` edge {}` for an `edge` that states neither word or ` edge {when "<when>" because "<reason>"}` for one that
  states any, whatever the entry's relation and state (effective-agent-profile.feature, bound in `ProfileSteps`).
- `Then the operation is refused as not implemented` — the last request's operation is declared in `../openapi.yaml`
  with `x-reserved` and a `501` of its own (read from the declaration, with no `default` fallback), and the answer is
  `501` with an `application/problem+json` body whose `status` is 501 and which has no `code` member, with the
  `Charter-Contract-Version` and `Charter-Worktree` headers and no `Charter-Source-Fingerprint` (ADR-0020 D8).
- `Given the project activates the pack "<id>"` (or `the packs "<id>, <id>"`) — stores the activation `project`
  with a plain `PUT /activations/project` whose `packs` are those configured ids, in that order, and expects `201`.
  `Given the activation "<id>" holds:` followed by a JSON doc string — the same `PUT` of that document under that id,
  expecting `201`; the binding keeps the `ETag` it was stored with, and `When I GET "<path>" with If-None-Match the
  stored ETag` sends it back. Every scenario starts with no activation stored.
- `Given the stored file of the activation "<id>" holds:` followed by a doc string — holds those bytes as the store
  keeps the activation's file, past the API and every check, so a `schemaVersion: 1` document can be seeded.
  `When I remember the stored file of the activation "<id>"`, then `Then the stored file of the activation "<id>" is
  the remembered one` or `... differs from the remembered one` — the stored bytes are the same, or not: a read
  writes nothing, a write that changed nothing stores nothing. `Then the stored file of the activation "<id>" has
  the field "<json-pointer>" equal to <n>` — an integer member of the stored bytes, such as `/schemaVersion`.
- `Then the field at "<json-pointer>" contains "<text>"` — the string member holds the text, such as the replacement
  a violation's `detail` names.
- `When I PUT "<path>" with the header "<name>": "<value>" and the body:` and `When I PUT "<path>" with the headers
  "<name>": "<value>" and "<name>": "<value>" and the body:` — a JSON `PUT` with those extra headers, for the
  conditional writes (`If-None-Match`, both validators at once).
- `When I PUT "<path>" with the stored ETag and the body:` and `When I DELETE "<path>" with the stored ETag` — a JSON
  `PUT`, or a `DELETE`, whose `If-Match` is the `ETag` the last `Given the activation "<id>" holds:` stored; for the
  per-artifact and preset writes, which require `If-Match`.
- `Then the response header "<name>" is the stored ETag` and `... differs from the stored ETag` — the header equals,
  or is present and differs from, that stored `ETag`: a write that changed nothing, or one that stored a new revision.
- `Then the field at "<json-pointer>" is the stored revision` — the body member is the revision the last `Given the
  activation "<id>" holds:` stored (the stored `ETag` without its quotes): a read that stored nothing. `Then the field
  at "<json-pointer>" is the remembered header "<name>" without its quotes` — the same for a header remembered with
  `When I remember the header "<name>"`. getActivation's `ETag` is the closure tag, not the revision, so these state
  the revision where a scenario used to compare it with the header.
- `When I GET "<path>" with If-None-Match the remembered header "<name>"` and `When I follow the link "<relation>" with
  If-None-Match the remembered header "<name>"` — a read (or a followed link) that sends the header remembered with
  `When I remember the header "<name>"` as `If-None-Match`. `When I PUT "<path>" with If-Match the remembered header
  "<name>" and the body:` sends it as `If-Match`. `When I GET "<path>" with the remembered cursor` appends
  `pageCursor=<value>` (with `?` or `&`), the value being the field remembered with `When I remember the field
  "/pageInfo/nextPageCursor" of the response body` (a field of the body is named by a name or by a JSON pointer).
- `Then the field at "<json-pointer>" holds exactly the ids:` followed by a doc string, one id per line — the member
  is an array whose ids are those lines, in order: a string item as it is, and an item with a `kind` and an
  `artifactId` (an `ArtifactReference`, a skipped entry) as `<kind>:<artifactId>`.
- `Then the response body has no field at "<json-pointer>"` — the pointer names no member of the body: a key a write
  dropped, such as an emptied deny list.
- `Then the field at "<json-pointer>" holds exactly <n> item(s)` — the member is an array of exactly that size; `Then
  the answer holds exactly <n> packs` is the same for `/packs`.
- `Then the response header "<name>" is the quoted field "<json-pointer>"` — the header is the string member that the
  pointer names, in double quotes: an `ETag` that quotes a `revision`.
- `Then the field at "<json-pointer>" is the lines:` followed by a doc string — the text the pointer names is the doc
  string's lines, each ending in a newline; for a Markdown guide.
- `Given the guide host serves "<uri>" with:` followed by a doc string — a `guideUri` registration that names this URI
  fetches the doc string, for the rest of the scenario; a later step for the same URI replaces it. A URI no step names
  is refused as a host off the allowlist (`HOST_NOT_ALLOWED`), as the service's empty-by-default allowlist refuses it.
  A binding answers these fetches itself and opens no socket.
- `Then following "via" from every guidance entry of "<path>" reaches the action in exactly "hops" edges`
  — the last response must be the answer to that path; walks the `via` chain of every entry back to
  the action node, through entries of the answer only, in exactly the entry's `hops` steps.
- `When I send <method> "<path>" as "<media type>" with the body:` and `When I send <method> "<path>" as "<media type>"
  with If-Match "<value>" and the body:` — any method, with that `Content-Type` (and that `If-Match`); for the 415 of
  a body that is not `application/json`, and for a write whose control sends the same body as JSON.
- `When I POST "<path>" as JSON with the body, its "<field>" padded to <n> bytes:` — the POST twin of the padded PUT:
  the top-level string field is padded with `a` to exactly `<n>` bytes of UTF-8; for the 413 of a body over the
  request body limit.

Every scenario that calls an operation is also checked against `../openapi.yaml` once it ends,
for every request it sent, not only the last: the response's content type must be one the operation declares for the status, or for its
`default` response, and the body must validate against that content's schema. A path no
operation defines must answer the shared `Problem` response. A binding of the steps in another
language makes the same check. It also checks the fingerprint header of every `GET` exchange, by its own rule
and never from the contract's declarations: a verified read, a `GET` answered 200 with a JSON body on any path
but the root `/`, carries exactly one `Charter-Source-Fingerprint`, equal to its own `sourceFingerprint` for
`/info` and to what `/info` answers right after for another read; every other `GET` answer (a refusal, the root,
`/openapi.yaml`, whose body is YAML) carries none. A write (`POST`, `PUT`) is exempt.

A selected scenario (`@contract`, not `@WIP`) with a step no binding defines, or a pending one, fails the run
(`UndefinedStepGuard`), naming the scenario and the step: Cucumber would report it as skipped and leave the build
green. A scenario that is not bound yet is tagged `@WIP`.

Every 2xx answer with a JSON body is also checked for its links (R-20), statically, with no further request:

- **Self.** When the contract has a `GET` for the request path, `_links.self.href` is `/api/v1` plus the request path,
  plus the query for a `GET` with one. Otherwise a 201 with a `Location` has the `Location`'s path as its `self`, and
  any other answer (the relationships `PUT`, whose path has no `GET`) has no `self`.
- **Resolution.** Every link, at any depth, has an `href` under `/api/v1/` that, with its query and a trailing
  `{?...}` expression cut off, matches a path template of `../openapi.yaml` defining the link's `method` (`GET` when
  absent); a whole-segment `{name}` matches a template variable. Another RFC 6570 form fails the check.
  `templated` is `true` exactly when the `href` holds an expression.
- **Location.** On a 201 with a `Location`, its path is `_links.self.href`.
- **Relations.** Every key of every `_links` is a relation of the table in
  `docs/development/hypermedia-hateoas.md`, which the build copies onto the test classpath.

## The switch gate

`switch-gate.yaml` says, per operation of `../openapi.yaml`, whether core may switch to it (ADR-0059 D1, as amended
on 2026-11-12): `{switch: ready}`, or `{switch: held, statuses: [<declared statuses>], reason: <why>}`. The suite records every exchange of every
scenario by the operation that answers it, and after the last scenario (`SwitchGate`) it fails:

- when a success answer the contract declares, a 2xx status of a built operation, was exercised by no scenario (R-20),
  whatever the register says: a new operation or success status needs a scenario that calls it;
- when an operation of the contract has no entry, or an entry names no operation: a new operation gets an entry in
  the change that adds it;
- when a `ready` operation has a status its responses declare by number (not `default`) that no scenario asserts on
  that operation; a success answer counts from any exchange, and any other answer only where `Then the response
  status is <n>` asserts it, so a setup request's incidental refusal covers nothing;
- when a `held` operation would pass as ready, when the statuses no scenario asserts on it are not exactly the
  `statuses` its entry names (an unnamed gap fails, and so does a named status that is asserted now), and when a
  reserved operation (`x-reserved`, a 501 pin) is ready.

Each run writes the scenarios that cover each answer of each operation to
`service/deployable/build/reports/switch-gate/switch-gate.md`. A run that selects only some scenarios reports the
others' answers as gaps. Ready is necessary for a switch, not sufficient: the first switched operation needs contract
1.0.0 (ADR-0022 D6). The register ships in `charter-scenarios.tar.gz`, so a change to its keys, or an operation moved
from ready to held, is a breaking change of the contract.

## Features

- `service.feature`, `errors.feature`, `security.feature`: the service info (with its `self` and `service-desc` links
  and the `Link` header only it sends), the failures outside the refusals, and the worktree guard.
- `root.feature`: the API root, `GET /` (`/api/v1/`, with the trailing slash), its links and that each one resolves
  (FR-001). The server prefix without the slash, `/api/v1`, is not mapped to the root: it answers the 404 of a path
  no operation defines, a `Problem` without a code, and no second mapping is added for it.
- `offering.feature`: the configured packs in force, `GET /offering`, in the merge order (configured level, then
  pack id in code-point order), at their configured level, and its refusals (FR-002).
- `directives.feature`, `styleguides.feature`, `paradigms.feature`, `toolguides.feature`: the registrations of the
  writable kinds under the artifact tree (`POST /packs/{packId}/artifacts/<kind>`, `PUT
  /packs/{packId}/artifacts/<kind>/{artifactId}`), each answering the envelope of the generic item, their read-back
  through the generic item and their refusals; for a toolguide, the guide sent as content or fetched from a
  `guideUri`.
- `artifacts.feature`: the generic artifact reads, the paged list of a pack's artifacts of the served kinds and one
  artifact in its envelope, with their paging edge cases and refusals (FR-003, FR-004, FR-007).
- `tactics.feature`: the tactic reads, through the generic list and item, answer with the
  `Charter-Source-Fingerprint` they verified, and refuse with `stale_input` or `pack_unreadable` as ADR-0018 D11
  requires (the item's `pack_unreadable`: #176).
- `removed-routes.feature`: every per-kind route of the earlier contract answers the 404 of a path no operation
  defines, a `Problem` without a code, with no alias (FR-006); a `GET` on a write path and a `PUT` on the item of a
  kind without a write (a tactic) are a 405 `Problem` without a code. A write to a kind that cannot be written is
  not `kind_not_found`: the path names a served kind, and the method is what the path does not define.
- `pack-id-length.feature`: the id length caps of the pack schemas (#440): a directive id of exactly 64 characters
  is read, and one of 65 refuses the pack as `500 pack_unreadable`; an executable entry of 255 characters is read
  and one of 256 refuses it; an expected artifact key of 256 refuses it; and a directive `PUT` whose id is 65
  characters is refused as `422 validation_failed` and leaves the pack readable.
- `request-id-length.feature`: the id length caps of the request schemas (#443): a pack id of 65 characters on a
  path is a `400` without a code and one of 64 is judged as any other (`404 pack_not_found`); an artifact id of 129
  is a `400` and one of 128 is its kind's not-found; a relationship whose `to` is longer than 255 characters is
  a `400` and nothing is written, and so is a relationship `DELETE` naming one.
- `every-operation.feature`, `unexpected-failure.feature`, `read-refusals.feature`, `activation-write-refusals.feature`,
  `pack-write-refusals.feature` and `registration/pack-source-refusals.feature`: the answers each operation declares,
  asserted on that operation for the switch gate, each refusal with its control. The worktree guard's 401 and 403 on
  every operation; `500 internal_error` on every operation whose declared 500 a pack read can raise, the failure planted
  after any stored activation the operation names; and the 304, 400, 404, 409, 412, 413, 415, 422, 428 and 503 answers the feature files above
  leave out for one operation or another. A new operation gets a row in `every-operation.feature`.
- `activation-register.feature`: the activation register (ADR-0020 D3-D6, ADR-0059 D4-D5, #125, #360): create,
  register, read and remove an activation document in canonical form under its revision, its `ETag`, `If-Match` and
  `If-None-Match`, its refusals (`activation_invalid` with `violations`, `artifact_required` and `artifact_locked`
  with `requiredBy`, `activation_not_found`), and the closure read `GET /activations/{activationId}/packs`.
- `activation-register-guards.feature`: the guards of the register: the worktree token (401), a browser request
  (403), and the preconditions judged before the document (412 on a stale `If-Match`, on `If-None-Match: *` over a
  stored id and on an `If-Match` over an id nothing is stored under).
- `activation-selections.feature`: the per-artifact writes (ADR-0020 D6, ADR-0059 D2, #291): select and unselect
  one artifact or mission type under `…/selections/{kind}/{artifactId}`, deactivate and re-activate one under
  `…/exclusions/{kind}/{artifactId}`, the cascade (`enhances` among its relations), the `ActivationChange` each answers, their
  preconditions (`428`, `412`, `400`) and their refusals.
- `activation-presets.feature`: applying a preset, `PUT …/presets/{presetId}` (ADR-0059 D6, #291): the keys it
  replaces, `force`, `preset_not_found` and a stale preset member.
- `activation-kind-model.feature`: the one kind model of the activation document (IC-20, FR-040 to FR-042): mission
  types selected and denied as a kind, the removed `activatedKinds` and `missionTypes` refused `422
  validation_failed`, a stored `schemaVersion: 1` document upgraded on read and stored as version 2 on the next write,
  and `[]` registering as absent for a selected kind.
- `activation-ruling-changes.feature`: one scenario per ruling of the ruleset review that PR A0 changes (A4, B1, B5,
  A10, A14, A15, B8; SC-005), each with a control in the same answer; the A10 report and `DELETE` half is `@WIP`.
- `activation-cascade-depth.feature`: the optional `depth` of a cascading select and deactivate (IC-21, FR-043; #408): the
  count of `suggests` hops, `skippedByDepth`, the round trip of a deactivate and its gap, and the refusals at `/depth`.
- `effective-reads.feature` and `layer-merge.feature`: the effective artifact list, the skipped report and the layer
  merge as they answer (PR B). `effective-graph-and-context.feature`: the effective graph and the effective action
  context (PR C, IC-11, FR-018 to FR-021, US5 AS1 to AS5): an edge with an endpoint out of force is absent, a layer
  override is listed (a held step's, not a template's), an enhancer is read from the node's side, a suggestion only
  through `suggests`, a mission type not in force answers an empty context, an unknown mission type or action is `404`,
  and each read has an `ETag` of its own. No operation is reserved any more, so no scenario pins a `501`
  (`ReservedOperationCoverageTest` keeps the machinery, with a planted contract as its control).
- `relationships.feature`: a registered relationship from a mission type the pack holds, and the refusal of one it
  does not hold (#194, ADR-0044).
- `composition.feature`: the compose of the configured packs' `depends_on` closure on `GET /offering`, in the
  resolver's order with each entry's resolved dependencies, and the eleven pack-composition refusals, each with a
  negative control (ADR-0048, ADR-0053).
- `journeys/journey-NN-<slug>.feature`: the eleven user journeys, each a mix of runnable and `@WIP` scenarios.
- `registration/whole-pack-registration.feature`: whole-pack registration (ADR-0065, FR-001 to FR-004, FR-024,
  FR-025): `PUT /packs/{packId}` and `POST /packs`, the status resource, the version rule, the preconditions, the
  impact check and the load's refusals, each refusal with a control. The file is `@contract` and each scenario is
  `@WIP` until the serving work package binds it (the comment above a scenario names which); see "Registration steps".
- `pack-removal.feature`: the artifact `DELETE` and the relationship `DELETE` (ADR-0065 D4, FR-005, FR-006), their
  `404`, `409`, `412`, `422` and `428` answers, each with a control. `@contract`, bound by WP19.
- `mission-types.feature`: the mission type, step, skeleton and step contract reads (#99, #100, #96), and the `DELETE`
  of both kinds: a step contract as the five kinds, a mission type with its step files and skeletons as one removal,
  refused `409` while a step contract binds it or an edge names one of its action nodes (D10).
- `effective-mission-types.feature`: the same mission type, step and skeleton reads under an activation (#99, FR-007):
  the layer-merge winner with the holding pack's skeletons and step files, the merged closure's step contracts, links
  under the activation and an `artifact` link to the winner's pack read, the refusals in order, and the generic
  activation read of a step contract.
- `trust/`, `integrity/`, `distribution/`, and the rest of the pack registration and distribution scenarios of
  ADR-0005 and ADR-0019 (#43), every one `@WIP` and none `@contract`. The surface is decided, not built, and
  outside the cutover scope (ADR-0055 D1), so they are not executed, by decision (#374); each file's header says
  what changes when they are bound. Their fixtures stay in `src/test/resources/fixtures/`.
- `step-enhancements.feature`: a held step enhancement (ADR-0066, #428): its file and Markdown companions register with
  the pack, it is selectable directly and through a preset, two packs of one level that define one id are refused
  `pack_sibling_conflict`, and two enhancers of one step with different ids compose.
- `prompt-fragment-refusals.feature`: a malformed step enhancement or prompt fragment, refused `prompt_fragment_invalid`
  at its file (a violation of a `422 validation_failed` registration, `500 pack_unreadable` on a read, and the top-level
  code of `putPackRelationship`'s `422`), each refusal beside its passing twin.
- `prompt-slots.feature`: the slots a step prompt declares (ADR-0066, #424): `prompt_slot_malformed` whatever the
  descriptor declares, `prompt_slot_not_empty` only behind the descriptor's `schema_version: "1.0"` gate, each at the
  prompt file and beside its passing twin.
- `profiles.feature`: the refusal of an agent profile enhancement that would drop fields of the profile it enhances
  (`PR-CHARTER-REFUSAL-001`), `@WIP` until the effective profile is built (#380); its header states the provisional
  target answer.

## Registration steps

The steps of `registration/whole-pack-registration.feature` and `pack-removal.feature` that the serving work packages
bind (WP15 to WP19). Until a step is bound, the scenario that uses it is `@WIP`. Each response step is the existing
vocabulary above.

- `Given registration is switched on` and `Given registration is switched off`: `charter.registration.enabled`, which is
  off by default. `Given the filesystem source is switched off` and `... is switched on`: the source kind's own flag.
- `Given the archive "<name>" holds the file "<path>":` followed by a doc string: a file of the named archive, which is
  built as a `tar.gz` of those files (the entry name is the path as written, so a path that leaves the pack can be
  tested). A path named twice holds the later text. `Given the archive "<name>" is a copy of the archive "<other>"` and
  `Given the archive "<name>" lacks the file "<path>"`.
- `When I PUT "<path>" with the archive "<name>"` sends the archive with `Content-Type: application/gzip`;
  `... as a zip archive` sends it as `application/zip`; `... as "<media type>"` sends it with that `Content-Type`;
  `When I PUT "<path>" with the gzip archive "<name>" as "<media type>"` sends the gzip bytes under a media type that
  does not match them; `When I PUT "<path>" with an archive larger than the body limit`. Each also takes `and the header
  "<name>": "<value>"`, `and the headers "<name>": "<value>" and "<name>": "<value>"`, `and the remembered ETag` and
  `without the worktree token`.
- `When I POST "<path>" as JSON with the header "<name>": "<value>" and the body:` is the `POST` with an extra header.
- `When I poll the registration until it ends` follows the `Location` of the last answer, polling until the state is
  `succeeded` or `refused`, and leaves the last status as the answer. `When I remember the Location header`,
  `When I GET the remembered registration` and `When I poll the remembered registration until it ends` do the same for a
  registration accepted earlier.
- `Then the path of the Location header starts with "<text>"`, `Then the field at "<json-pointer>" is present`,
  `Then the field at "<json-pointer>" is absent` and `Then the field at "<json-pointer>" is the remembered one` (the
  remember step is `When I remember the field "<field>" of the response body`).
- The `RegistrationJobs` seam: `Given the registration jobs are held before they run`, `Given the registration queue is full` (two
  held jobs run and eight wait, ADR-0065 D8), `When I release the held
  registration jobs`, `When I release the held registration jobs and hold them while they run` (the job is then
  `running`) and `When the service forgets the ended registrations` (the clock passes the hour the status is kept).
- `Given the pack registry is held by another write` takes the registry's write lock on a background thread, as a
  registration or a per-artifact pack write does, and `When the held pack registry is released` frees it (a scenario
  that ends with it held releases it). A write that meets the held lock waits its five seconds and answers `503` with
  `Retry-After`: a per-artifact pack write and an activation write alike.
- `Then the field at "<json-pointer>" holds exactly <n> item(s)` counts the elements of an array in the response body
  (the literal `item(s)` is the step's wording). A `202`'s `Location` counts as the response's own `self` for the link
  checks, and a registration status is not fingerprinted.
- The archives are built with the JDK alone (`tar.gz` and `zip`, no archiver library); `Given the archive "<name>" holds
  the file` adds a file, a path named twice holds the later text, and an archive is sent as `application/gzip` or, as
  a zip archive, `application/zip`. The staging directory of the scenarios is a directory of the scenario's own root.
- One seam per source kind: `Given the https host "<host>" is allowed`, `Given the https host "<host>" serves "<url>" with
  the archive "<name>"` (a stub exchange, never the network); `Given the git host "<host>" is allowed`, `Given the git
  repository "<url>" holds the archive "<name>" at the ref "<ref>"` and `Then the field at "<json-pointer>" is the commit
  of the ref "<ref>"` (a local bare repository); `Given the filesystem source root holds the directory "<path>" with the
  archive "<name>"` (the files of the named archive, written under the scenarios' own filesystem root). Allowing an https
  host and filling the filesystem root also switch that kind's own flag on, as an operator does, and
  `Given the filesystem source is switched off` and `... is switched on` move it; every seam, every flag and the
  filesystem root are put back at their defaults (off, empty) when the scenario ends. The https stub serves a URL only to
  an allowed host and refuses any other host as `host_not_allowed`, so the archive fetcher above it (digest, media type,
  extraction, the check that the pack's name is the target) runs for real.
- `When I DELETE "<path>" with the remembered ETag`: a `DELETE` whose `If-Match` is the `ETag` last remembered with `When I
  remember the ETag`, from `getPack` or from a write's answer.

## Tags

- `@contract`: the scenario is part of the charter contract. The suite runs only scenarios with
  this tag, so a file placed here is not picked up until it is bound and tagged. A file whose steps are not bound
  carries no `@contract`: only `@WIP`, plus its cluster's tag for the four cluster folders (`@registration`,
  `@trust`, `@integrity`, `@distribution`). `profiles.feature` carries `@WIP` alone. The two files of the whole-pack
  registration (`registration/whole-pack-registration.feature` and `pack-removal.feature`) are the exception: they carry
  `@contract` on the feature, as `activation-ruling-changes.feature` does, and tag each scenario `@WIP` until the work
  package that serves it binds its steps and removes the tag (`pack-removal.feature` is bound).
- `@provisional`: the behavior is not settled yet; a ruling on the design decides it (ADR-0055 D3; the
  conformance corpus over core's CLI is not built, ADR-0059 D1). It is still asserted exactly, so a change to it is deliberate, and it matches an
  `x-provisional` part of `../openapi.yaml`.
- `@reserved`: the scenario pins something the contract declares before it is built. It must also carry
  `@contract`. It takes one of two forms:
  - A *declaration pin* pins a reserved refusal code, never behavior. It calls no operation, and names in its
    description the unit test that enforces "raised by no operation of this version" and the issue that will raise
    the code (for `activation_not_found`: `ReservedRefusalCodesTest` and #291). `ReservedRefusalCodesTest` reads
    the codes from the `x-refusal` map, so a code the map marks `raised: false` is enforced without editing it.
  - A *501 pin* pins a reserved operation (ADR-0020 D8). It sends one well-formed request to the operation and ends
    with `Then the operation is refused as not implemented`. Its feature names `ReservedOperationCoverageTest`, which
    keeps the 501 pins and the operations that carry `x-reserved` one to one, contract set by contract set, and the issue that will build the
    operation. The pin is removed, with the operation's `501` and `x-reserved`, when the operation is built.

  The charter's refusal score counts behavioral codes apart from the declared ones.
- `@WIP`: the scenario needs a surface the contract does not have yet (activation, presets, roles and profiles, pins,
  removal, mission types, values and creeds, upgrades, hosting, cold start, pack registration and distribution). The suite excludes it
  (`@ExcludeTags("WIP")` beside `@IncludeTags("contract")`), so its steps need not be bound; when the surface lands,
  the tag comes off, the steps are bound with the vocabulary above, `@contract` goes on where the file had none, and
  the change gets a changelog fragment under `../changes/` (ADR-0047).
  The released scenario archive includes `@WIP` scenarios: **any implementation that runs these scenarios must
  exclude `@WIP`**, as this repository's suite does.
- `@journey-NN`: the feature concretizes user journey NN. The journey features live in `journeys/`, one per journey
  page (`journeys/journey-NN-<slug>.feature` for `docs/context/user-journeys/NN-<slug>.md`, named on the file's second
  line). Each holds runnable scenarios for the steps today's contract supports and `@WIP` scenarios for the rest.

## Rules

- Fixtures stay inline, as doc strings in the scenario. A YAML file under the contract tree is
  read as part of the contract by its checks, and a separate fixture hides what the scenario
  is about.
- A scenario change (added, removed, renamed, or a changed expectation) gets a changelog fragment
  under `../changes/` (ADR-0047), never a direct edit of `../CHANGELOG.md`: the OpenAPI diff cannot see
  it, and the scenarios are half of the contract.
