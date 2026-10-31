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
- `Then the response status is <n>`
- `Then the response body is:` — the JSON body, compared exactly (order of arrays and the
  `detail` sentences included).
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
- `Then the response body is a list with an element whose field "<field>" is "<text>"` — for a JSON array body.
- `Then the contract declares the refusal code "<code>"`, `... is answered with status <n>`, `... may not be
  retried` and `... is raised by no operation` — read the machine-readable `x-refusal` map of
  `../schemas/CharterRefusalCode.yaml` (`status`, `retryable`, `raised` per code). `Then the contract says exactly
  these refusal codes are raised by an operation:` with a one-column table lists the codes whose `raised` is true.
  After every scenario, each refusal it observed must have the status and `raised: true` the map declares.
- `Then the problem code is "<code>"` — the `code` of a `Problem` body.
- `Then the problem has no code` — an `application/problem+json` body without a `code` member (absent, not empty): a
  transport or routing failure, not a charter refusal.
- `Then the field at "<json-pointer>" is "<text>"`, `... is <integer>` and `... is true` or `... is false` — the
  member an RFC 6901 JSON Pointer names (for example `/page/hasNextPage` or `/items/0/id`), which must be there and
  of that JSON type.
- `Then the answer has no link "<relation>"` — the body's `_links` has no member of that relation key.
- `When I follow the link "<relation>"` — GET the `href` of that relation in the last answer's `_links`, with the
  `/api/v1` server prefix taken off as the request steps expect.
- `Then following "<relation>" links from the answer, the "<member>" ids are:` followed by a table with the header
  `| kind | id |` — collects the `kind` and `id` of every element of `<member>` on the last answer and on every page
  its `<relation>` link leads to, following it until a page has none, and compares them with the table in order, so
  every element is served exactly once. Each followed page must answer 200; the last one is the step's answer.
- `When the file "<path>" of the pack "<name>" is deleted` — deletes one existing file of a configured pack, between
  two requests (a cursor's anchor that goes away between pages).
- `When I POST "<path>" as JSON with the body:`, `When I PUT "<path>" with the body:`, `When I PUT "<path>" with
  If-Match "<value>" and the body:`, `When I remember the ETag` and `When I PUT "<path>" with the remembered ETag and the
  body:` — a registration with a JSON doc string, and its `If-Match` precondition.
- `When I PUT "<path>" with the body, its "<field>" padded to <n> bytes:` — the same PUT, after appending `a` to that
  top-level string field of the JSON doc string until it is exactly `<n>` bytes of UTF-8; for a size cap no doc string
  can spell out.
- `Then the field "<field>" of the response body is the lines:` followed by a doc string — the field's text is the
  doc string's lines, each ending in a newline; for a Markdown guide.
- `Given the guide host serves "<uri>" with:` followed by a doc string — a `guideUri` registration that names this URI
  fetches the doc string, for the rest of the scenario; a later step for the same URI replaces it. A URI no step names
  is refused as a host off the allowlist (`HOST_NOT_ALLOWED`), as the service's empty-by-default allowlist refuses it.
  A binding answers these fetches itself and opens no socket.
- `Then following "via" from every guidance entry of "<path>" reaches the action in exactly "hops" edges`
  — the last response must be the answer to that path; walks the `via` chain of every entry back to
  the action node, through entries of the answer only, in exactly the entry's `hops` steps.

Every scenario that calls an operation is also checked against `../openapi.yaml` once it ends,
for every request it sent, not only the last: the response's content type must be one the operation declares for the status, or for its
`default` response, and the body must validate against that content's schema. A path no
operation defines must answer the shared `Problem` response. A binding of the steps in another
language makes the same check. It also checks the fingerprint header of every exchange: a 200 carries
exactly one `Charter-Source-Fingerprint`, equal to its own `sourceFingerprint` for `/info` and to what
`/info` answers right after for another read; no other answer carries one.

## Features

- `service.feature`, `errors.feature`, `security.feature`: the service info, the failures outside the refusals, and
  the worktree guard.
- `toolguides.feature`: the toolguide reads and registrations, with the guide sent as content or fetched from a
  `guideUri`, and their refusals.
- `artifacts.feature`: the generic artifact reads, the paged list of a pack's artifacts of the served kinds and one
  artifact in its envelope, with their paging edge cases and refusals (FR-003, FR-004, FR-007).
- `tactics.feature`: the tactic reads answer with the `Charter-Source-Fingerprint` they verified, and refuse with
  `stale_input` or `pack_unreadable` as ADR-0018 D11 requires.
- `relationships.feature`: a registered relationship from a mission type the pack holds, and the refusal of one it
  does not hold (#194, ADR-0044).
- `journeys/journey-NN-<slug>.feature`: the eleven user journeys, each a mix of runnable and `@WIP` scenarios.

## Tags

- `@contract`: the scenario is part of the charter contract. The suite runs only scenarios with
  this tag, so a file placed here is not picked up until it is bound and tagged.
- `@provisional`: the behavior is not settled yet; the conformance corpus (issue #9) decides
  it. It is still asserted exactly, so a change to it is deliberate, and it matches an
  `x-provisional` part of `../openapi.yaml`.
- `@reserved`: the scenario pins a *declaration*, never behavior. It must also carry `@contract`, call no
  operation, and name in its description the unit test that enforces "raised by no operation of this version" and
  the issue that will raise the code (for `governance_policy_violation`: `ReservedRefusalCodesTest` and #55). The
  charter's refusal score is therefore reported as eight behavioral codes plus one declared.
- `@WIP`: the scenario needs a surface the contract does not have yet (activation, presets, roles and profiles, pins,
  removal, mission types, values and creeds, upgrades, hosting, cold start). The suite excludes it
  (`@ExcludeTags("WIP")` beside `@IncludeTags("contract")`), so its steps need not be bound; when the surface lands,
  the tag comes off, the steps are bound with the vocabulary above, and the change gets a line in `../CHANGELOG.md`.
  The released scenario archive includes `@WIP` scenarios: **any implementation that runs these scenarios must
  exclude `@WIP`**, as this repository's suite does.
- `@journey-NN`: the feature concretizes user journey NN. The journey features live in `journeys/`, one per journey
  page (`journeys/journey-NN-<slug>.feature` for `docs/context/user-journeys/NN-<slug>.md`, named on the file's second
  line). Each holds runnable scenarios for the steps today's contract supports and `@WIP` scenarios for the rest.

## Rules

- Fixtures stay inline, as doc strings in the scenario. A YAML file under the contract tree is
  read as part of the contract by its checks, and a separate fixture hides what the scenario
  is about.
- A scenario change (added, removed, renamed, or a changed expectation) gets a line in
  `../CHANGELOG.md`: the OpenAPI diff cannot see it, and the scenarios are half of the
  contract.
