---
title: Behavior and acceptance scenarios
description: The contract and acceptance scenarios that define the charter offering's role and boundary, and how they become the conformance net that survives the repository split.
doc_status: draft
updated: 2026-10-19
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

Each scenario is tagged `@promise:PR-CHARTER-<AREA>-NNN` and mapped to the authority that states
it (an FR or ADR) in the suite's `PROMISES.md` index, so the set doubles as a frozen-contract
map for the surface under test.

## Where they live

Scenarios live in three homes, each with its own state (operator ruling, 2026-10-18;
[open question 16](/plans/open-questions/)):

| Home | What it holds | State |
|---|---|---|
| `contracts/charter/scenarios/` | The **contract scenarios**: the behavior half of the charter contract; each names pack files, calls one operation of `contracts/charter/openapi.yaml` and states the answer | Bound. The Cucumber suite in `charter-service` runs every scenario tagged `@contract` in `./gradlew verifyAll`; a scenario tagged `@provisional` pins behavior the conformance corpus has yet to decide. Its rules are in the folder's `README.md` |
| `src/test/resources/features/` | The pack registration and distribution suites of #43, one folder per behavior cluster | Not bound. Each cluster moves to `contracts/charter/scenarios/<cluster>/` when its harness is bound, renamed for the terms guard, moved and bound in one change |
| `tests/scenarios/charter/` | The **acceptance scenarios**, copied from core's charter-offering suite, one folder per outcome | Not bound; they bind with the harness of [#31](https://github.com/spec-kitty/spec-kitty-charter/issues/31) |

The acceptance scenarios are grouped by the three outcomes above:

- `governance-in-force/` — the governance in force is what I chose.
- `refuses-unsound-governance/` — the system refuses unsound governance with a clear reason.
- `share-governance-safely/` — I can share governance across a team or project safely.
- `PROMISES.md` — each `@promise` id mapped to its offering authority.

The unbound suites are implementation-free Gherkin: they import no code and are valid the
moment they land. A suite that is placed in `contracts/charter/scenarios/` before it is bound
is not run, because the suite selects only the `@contract` tag.

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
