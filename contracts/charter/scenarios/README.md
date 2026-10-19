# Contract scenarios

The behavior half of the charter contract (operator decision, 2026-10-18: the Java rewrite is
built against the contract scenarios and the OpenAPI document). Each scenario names packs by
their files, calls one operation of `../openapi.yaml` and states the answer. The scenarios are
language-neutral: the Java service runs them through `charter-service`'s Cucumber suite, and
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
- `Then the response status is <n>`
- `Then the response body is:` — the JSON body, compared exactly (order of arrays and the
  `detail` sentences included).
- `Then the response body, apart from "<field>", is:` — the same, after taking that top-level
  field out of the body, which must have it; for a digest whose value no scenario pins.
- `Then the field "<field>" of the response body is "<text>"`, `... is a sha256 digest` and
  `... is the contract version` (the `info.version` of `../openapi.yaml`).
- `When I remember the field "<field>" of the response body`, then `Then the field "<field>" of
  the response body is the remembered one` or `... differs from the remembered one` — for a
  digest that must stay or change with the files.
- `Then the response header "<name>" is "<text>"`, `... is a sha256 digest` and `... is the
  contract version`; `Then the response has no "<name>" header`.
- `Then the problem code is "<code>"` — the `code` of a `Problem` body.
- `Then following "via" from every guidance entry of "<path>" reaches the action in exactly "hops" edges`
  — reads the context operation again and walks the `via` chain of every entry back to the action
  node, through entries of the answer only, in exactly the entry's `hops` steps.

Every scenario that calls an operation is also checked against `../openapi.yaml` once it ends:
the response's content type must be one the operation declares for the status, or for its
`default` response, and the body must validate against that content's schema. A path no
operation defines must answer the shared `Problem` response. A binding of the steps in another
language makes the same check.

## Tags

- `@contract`: the scenario is part of the charter contract. The suite runs only scenarios with
  this tag, so a file placed here is not picked up until it is bound and tagged.
- `@provisional`: the behavior is not settled yet; the conformance corpus (issue #9) decides
  it. It is still asserted exactly, so a change to it is deliberate, and it matches an
  `x-provisional` part of `../openapi.yaml`.

## Rules

- Fixtures stay inline, as doc strings in the scenario. A YAML file under the contract tree is
  read as part of the contract by its checks, and a separate fixture hides what the scenario
  is about.
- A scenario change (added, removed, renamed, or a changed expectation) gets a line in
  `../CHANGELOG.md`: the OpenAPI diff cannot see it, and the scenarios are half of the
  contract.
