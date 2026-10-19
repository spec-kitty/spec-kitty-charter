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
- `When I GET "<path>"` — a path below the `/api/v1` server prefix.
- `Then the response status is <n>`
- `Then the response body is:` — the JSON body, compared exactly (order of arrays and the
  `detail` sentences included).
- `Then the problem code is "<code>"` — the `code` of a `Problem` body.

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
