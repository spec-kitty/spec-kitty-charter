# BDD contracts

The behavior half of the charter contract (operator decision, 2026-10-18: the Java rewrite is
built against BDD contracts and the OpenAPI document). Each scenario names packs by their files,
calls one operation of `../openapi.yaml` and states the answer. The scenarios are
language-neutral: the Java service runs them through `charter-service`'s Cucumber suite, and
any other implementation may bind the same steps.

Step vocabulary (keep it small; add a step only when a scenario cannot be written without it):

- `Given the <level> pack "<name>" with the file "<path>":` followed by a doc string. Adds one
  file to a pack; the pack is configured at the given level in the order first named.
- `When I GET "<path>"` — a path below the `/api/v1` server prefix.
- `Then the response status is <n>`
- `Then the response body is:` — the JSON body, compared exactly (order of arrays included).
- `Then the problem code is "<code>"` — the `code` of a `Problem` body.
