---
title: Behavior and acceptance scenarios
description: The contract and acceptance scenarios that define the charter offering's role and boundary, and how they become the conformance net that survives the repository split.
doc_status: draft
updated: 2026-11-05T00:00:00Z
type: Architecture
kind: Explanation
related:
  - architecture/overview
  - context/charter-offering
  - plans/overhaul-approach
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the behavior the implementation must reproduce and where the scenarios live.
  - profile: core-maintainer
    guidance_text: Read this for how the offering suite relates to core's acceptance scenarios and ownership.
---

# Behavior and acceptance scenarios

The charter offering's role and boundary are defined **behaviorally**, by promise-first
acceptance scenarios. Each scenario states a guarantee a consumer relies on — *what the system
should do for me* — phrased so it stays true regardless of which command or implementation
delivers it. Because they describe outcomes and not mechanism, they are the **conformance net**
that both today's Python charter and any future implementation must pass, so a capability can
migrate across the [strangler stages](/architecture/strangler-transition/) without changing what
a consumer observes. This is why, in the [overhaul approach](/plans/overhaul-approach/), the
scenarios come before the contract and before any implementation choice.

## The offering promises

The suite is organized by consumer outcome. The three offering outcomes are:

| Outcome | Guarantee |
|---|---|
| **The governance in force is what I chose** | The governance in force is the set the charter puts in force — sharing a pack does not silently add its commands everywhere, and a team's org-wide requirement is honored on top of the project's own choices. |
| **The system refuses unsound governance with a clear reason** | Governance that would silently weaken a profile, a mistyped reference between pieces of governance, or a broken charter is refused and the reason named — never quietly accepted or half-applied. |
| **I can share governance across a team or project safely** | An unsound shared pack is not reported healthy, a project's own choice wins over a team pack it adopts, and reserved namespaces and pack-escaping assets are refused. |

Each promise has an id, `PR-CHARTER-<AREA>-NNN`, and the suite's `PROMISES.md` index names what
accounts for it now: the contract scenario that covers it, the placeholder that holds its place, or the
issue that tracks it.

## Where they live

Every scenario now lives in `contracts/charter/scenarios/`, and `tests/scenarios/charter/` keeps only
pointers (operator rulings of 2026-10-18 and 2026-11-05; [open question 16](/plans/open-questions/); #374):

| Home | What it holds | State |
|---|---|---|
| `contracts/charter/scenarios/` | The **contract scenarios**: the behavior half of the charter contract; each names pack files, calls one operation of `contracts/charter/openapi.yaml` and states the answer | Bound. The Cucumber suite in `charter-service` runs every scenario tagged `@contract` in `./gradlew verifyAll` and leaves out every one tagged `@WIP`; a scenario tagged `@provisional` pins behavior the conformance corpus has yet to decide. Its rules are in the folder's `README.md` |
| `contracts/charter/scenarios/registration/`, `trust/`, `integrity/`, `distribution/` | The pack registration and distribution scenarios of #43 (ADR-0005, ADR-0019), moved from `src/test/resources/features/` | Not executed, by decision: tagged `@WIP`, because the surface is decided but not built and outside the cutover scope (ADR-0055 D1). Their fixtures stay in `src/test/resources/fixtures/` |
| `tests/scenarios/charter/` | Pointers only: `PROMISES.md` names, for each of the nine acceptance promises copied from core, the contract scenario that covers it, the `@WIP` scenario or reserved pin that holds its place, or the follow-up issue that tracks it | No scenarios left to run; two promises are now `@contract` scenarios of `offering.feature`, and two are gaps ([#380](https://github.com/spec-kitty/spec-kitty-charter/issues/380), [#381](https://github.com/spec-kitty/spec-kitty-charter/issues/381)) |

The three acceptance outcomes above are still the way the promises are grouped in
`tests/scenarios/charter/PROMISES.md`. A scenario placed in `contracts/charter/scenarios/` is not run until it
is tagged `@contract`, and not while it is tagged `@WIP`.

## From scenario to contract

The scenarios describe the boundary in the consumer's language; the
[OpenAPI contract](/architecture/contract-and-dtos/) will state that same boundary as a typed,
versioned surface. The order is deliberate: describe the behavior, publish the contract, then
choose an implementation — each step constrained by the one before it.

## Authoring and ownership

New scenarios follow the `feature-and-scenario-authoring` styleguide and the
`charter-offering-scenario-extraction` procedure (authored and vendored in the org packs, to be
put in force in P1 — see [the overhaul approach](/plans/overhaul-approach/)): outcomes rather
than output surfaces, one
promise per scenario, the consumer's language only, and desired behavior rather than a defect
reproduction. In core, the charter scenarios are co-owned by QA and core; the same split applies
as the suite grows here.
