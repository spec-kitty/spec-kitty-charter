# Contract scenarios

The behavior half of the charter contract (operator decision, 2026-10-18: the Java rewrite is
built against the contract scenarios and the OpenAPI document). Each scenario names packs by
their files, calls one operation of `../openapi.yaml` and states the answer. The scenarios are
language-neutral: the Java service runs them through `bootstrap`'s Cucumber suite, and
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
- `Given a pack file changes while the next read is answered` — the next read that digests the inputs (a read that
  loads the offering, a refresh, or `/info`) sees a pack file rewritten (a node added to the first graph shard) after
  its first digest and before its second.
  `Given a pack file is rewritten to invalid YAML while the next read is answered` — the same, with the shard
  rewritten to text that is not YAML and left so. `Given no pack file changes while the next read is answered` —
  the same seam, changing nothing: the negative control. The change is made to a real file on disk, once; the
  service computes both digests itself.
- `Then the contract declares the refusal code "<code>"`, `... is answered with status <n>`, `... may not be
  retried` and `... is raised by no operation` — read the machine-readable `x-refusal` map of
  `../schemas/CharterRefusalCode.yaml` (`status`, `retryable`, `raised` per code). `Then the contract says exactly
  these refusal codes are raised by an operation:` with a one-column table lists the codes whose `raised` is true.
  After every scenario, each refusal it observed must have the status and `raised: true` the map declares.
- `Then the problem code is "<code>"` — the `code` of a `Problem` body.
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

## Rules

- Fixtures stay inline, as doc strings in the scenario. A YAML file under the contract tree is
  read as part of the contract by its checks, and a separate fixture hides what the scenario
  is about.
- A scenario change (added, removed, renamed, or a changed expectation) gets a line in
  `../CHANGELOG.md`: the OpenAPI diff cannot see it, and the scenarios are half of the
  contract.
