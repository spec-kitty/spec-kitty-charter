# Research: Charter headers on the default Problem response

## Where the declaration goes

- **Decision**: edit `contracts/_shared/responses/Problem.yaml` in place, referencing `../../charter/headers/*.yaml`.
- **Rationale**: operator ruling (DM `01M5A4PP44NQ3KWG4APT0PEJCT`), matching issue #90's AC1, and the operator's general ruling of 2026-10-19: spec-kitty-charter is the canonical owner of the charter structure, responses, contracts and pack schemas; vendored content changed here is canonical, and consumers, core included, adapt. Stay close to the source, but deviate when there is good reason rather than keep parity with a weaker original. Only the charter contract consumes `_shared/` in this repository, and no build gate pins `_shared/` to core's bytes (`contracts/build.gradle.kts` reads it only to bundle and generate).
- **Alternatives considered**: a charter-local `default` response (keeps `_shared/` byte-identical to core; not chosen by the operator); asking core to change its shared response (core's `Problem` has no reason to know charter headers).

## Where the guard goes

- **Decision**: a rule in `contracts/lint/ruleset.yaml`, `charter-headers-on-every-response`, over `$.paths[*][*].responses[*]`, requiring the three header keys.
- **Rationale**: the lint runs on the bundle with every `$ref` inlined, so the `default` response is judged with its shared content. The rule mechanism already proves each rule with a planted fixture (`lintFixtures`).
- **Alternatives considered**: a Java checker like `checkClosedResponseObjects` (heavier for a key-presence check); a scenario-only check (only sees the responses a scenario happens to exercise).

## Required-ness

- **Decision**: the rule checks presence of the three keys only; required-ness comes from the shared header files (`Charter-Source-Fingerprint` is `required: false`, ADR-0011 fail-closed).
- **Rationale**: one definition (C-002); a rule restating required-ness would duplicate it.

## Post-tasks adversarial squad (2026-10-19)

One strict-reviewer pass, correctness and scope lenses, verified empirically with vacuum 0.30.6 on a scratch copy. Confirmed: the bundle inlines `_shared/responses/Problem.yaml` → `charter/headers/*` → `Sha256Digest` with no residual `$ref`; the rule fails exactly the three `default` responses today and passes after; adding headers is not breaking (no release tag yet); the scenario steps exist; code generation is unaffected.

| # | Severity | Finding | Disposition |
|---|----------|---------|-------------|
| 1 | MAJOR | `docs/guides/service-build.md` and `docs/api/service-contract.md` still say `_shared/` is core's | Folded: `service-contract.md` added to WP01 (T005); `service-build.md` is changed by PR #91 (ADR-0015) |
| 2 | MINOR | The scenario hook does not check headers | Folded: T004 and IC-03 corrected; the header steps are the runtime proof |
| 3 | MINOR | Every fixture fails the new rule on all 4 responses | Folded: T002 states the scale, uses a YAML anchor per fixture, runs `:contracts:test` too |
| 4 | MINOR | One planted fixture proves only the `truthy` half | Folded: second fixture `.missing-fingerprint` |
| 5 | MINOR | No dated docs changelog entry | Folded: T006 adds it, avoiding the forbidden term |
| 6 | MINOR | VENDORED.md would cite a ruling without an ADR | Folded: ADR-0015 opened as PR #91; VENDORED.md cites it |
| 7 | NOTE | The red-first commit leaves `lintContract` red for one commit | Accepted: no repo rule requires every commit green; the red run is also quoted in the PR body |
| 8 | NOTE | The 400 request is already pinned in `action_context.feature` | Accepted: the new scenario adds only the header assertions |
