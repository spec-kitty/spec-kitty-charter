# Decision Moment `01M584Q3NNK0B78H9NKHV7VJDK`

- **Mission:** `charter-contract-and-build-retro-01M57QHJ`
- **Origin flow:** `plan`
- **Slot key:** `plan.contract.closure-checker`
- **Input key:** `closed_response_objects_enforcement`
- **Status:** `resolved`
- **Created:** `2026-10-18T18:34:51.445555+00:00`
- **Resolved:** `2026-10-18T18:35:01.993242+00:00`
- **Resolved by:** `stijn`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

How is the closed-response-objects convention enforced after five review rounds found holes in the Spectral rule?

## Options

- Recursive checker in Java
- Keep patching the Spectral rule
- Drop the convention

## Final answer

Recursive checker in Java

## Rationale

Operator ruling 2026-10-18. Spectral's JSONPath rules cannot classify composed schemas (allOf/anyOf/oneOf, tuples, boolean schemas, constraint-only members) recursively; five review rounds each found a new hole. The closure convention moves to a small, unit-tested Java checker in the :contracts project that walks the bundled contract with a positive recursive classification and runs in check. The Spectral rule closed-response-objects is removed from contracts/lint/ruleset.yaml.

## Change log

- `2026-10-18T18:34:51.445555+00:00` — opened
- `2026-10-18T18:35:01.993242+00:00` — resolved (final_answer="Recursive checker in Java")
