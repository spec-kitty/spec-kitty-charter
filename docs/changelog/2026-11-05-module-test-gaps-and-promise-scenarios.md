---
okf_version: "0.2"
title: "Module-local test gaps closed; every promise-first scenario has a verdict"
description: "Kernel validation, agent profiles and the fetcher's body cap get their own tests; each of the 27 unrun promise-first scenarios gets a verdict."
doc_status: active
updated: 2026-11-05T00:00:00Z
type: Changelog
kind: Changelog
related:
  - plans/engineering-notes/test-suite-assessment-2026-11-03
  - architecture/behavior-and-scenarios
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for where the promise-first scenarios went and which module tests now pin kernel validation, profiles and the body cap.
---

# 2026-11-05 — Module-local test gaps closed; every promise-first scenario has a verdict

Two follow-ups from the [test suite assessment](/plans/engineering-notes/test-suite-assessment-2026-11-03/).

**Each module now tests its own guards**
([#370](https://github.com/spec-kitty/spec-kitty-charter/issues/370)). Before, other modules' tests ran this code
without asserting on it.

- `kernel.validation` has tests of its own for `Constrained`, `Rationale`, `Checks` and `ModularRuleset`. Mutation
  testing kills all 45 of its mutants, up from 24.
- `profiles` checks that an agent profile keeps every field it is built with. Its mutation score rises from 53 % to
  97 %.
- The guide fetcher's body cap is pinned at, over and under the limit, and on stream failure.

A planted break proved each new test, and no main code changed.

**Every promise-first scenario has a verdict**
([#374](https://github.com/spec-kitty/spec-kitty-charter/issues/374)). The 27 scenarios that no runner executed now
each have one:

- The 18 pack registration and distribution scenarios move under `contracts/charter/scenarios/` as work in
  progress, because that surface is decided but not built.
- Two sharing promises are now contract scenarios: a pack holding a file that fails its schema, and an asset whose
  path leaves `assets/`.
- The rest point to the contract scenario that covers them, to a reserved placeholder, or to a follow-up issue:
  [#380](https://github.com/spec-kitty/spec-kitty-charter/issues/380) and
  [#381](https://github.com/spec-kitty/spec-kitty-charter/issues/381).
