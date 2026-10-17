---
title: Behaviour and acceptance scenarios
description: The promise-first BDD scenarios that define the charter offering's role and boundary, and how they become the conformance net that survives the repository split.
doc_status: draft
updated: 2026-10-17
type: Architecture
kind: Explanation
related:
  - architecture/overview
  - context/charter-offering
  - plans/overhaul-approach
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the behaviour the implementation must reproduce and where the scenarios live.
  - profile: core-maintainer
    guidance_text: Read this for how the offering suite relates to core's acceptance scenarios and ownership.
---

# Behaviour and acceptance scenarios

The charter offering's role and boundary are defined **behaviourally**, by promise-first
acceptance scenarios. Each scenario states a guarantee a consumer relies on — *what the system
should do for me* — phrased so it stays true regardless of which command or implementation
delivers it. Because they describe outcomes and not mechanism, they are the **conformance net**
that both today's Python charter and any future implementation must pass, so a capability can
migrate across the [strangler stages](/architecture/strangler-transition/) without changing what
a consumer observes. This is why, in the [overhaul approach](/plans/overhaul-approach/), the
scenarios come before the contract and before any implementation choice.

## The offering promises

The suite is organised by consumer outcome. The three offering outcomes are:

| Outcome | Guarantee |
|---|---|
| **The governance in force is what I chose** | The governance in force is the set the charter puts in force — sharing a pack does not silently add its commands everywhere, and a team's org-wide requirement is honoured on top of the project's own choices. |
| **The system refuses unsound governance with a clear reason** | Governance that would silently weaken a profile, a mistyped reference between pieces of governance, or a broken charter is refused and the reason named — never quietly accepted or half-applied. |
| **I can share governance across a team or project safely** | An unsound shared pack is not reported healthy, a project's own choice wins over a team pack it adopts, and reserved namespaces and pack-escaping assets are refused. |

Each scenario is tagged `@promise:PR-CHARTER-<AREA>-NNN` and mapped to the authority that states
it (an FR or ADR) in the suite's `PROMISES.md` index, so the set doubles as a frozen-contract
map for the surface under test.

## Where they live

The scenarios are in `tests/scenarios/charter/`, one folder per outcome, copied from core's
charter-offering suite as the starting contract for this repository:

- `governance-in-force/` — the governance in force is what I chose.
- `refuses-unsound-governance/` — the system refuses unsound governance with a clear reason.
- `share-governance-safely/` — I can share governance across a team or project safely.
- `PROMISES.md` — each `@promise` id mapped to its offering authority.

They are implementation-free Gherkin: they import no code and are valid the moment they land. The
**executable harness** that runs them (a Cucumber-JVM runner and its step definitions, as in
core's `acceptance-jvm/`) is not yet wired up in this repository; standing it up, and pointing it
at whatever answers the charter commands, is tracked in the issue tracker.

## From scenario to contract

The scenarios describe the boundary in the consumer's language; the
[OpenAPI contract](/architecture/contract-and-dtos/) will state that same boundary as a typed,
versioned surface. The order is deliberate: describe the behaviour, publish the contract, then
choose an implementation — each step constrained by the one before it.

## Authoring and ownership

New scenarios follow the `feature-and-scenario-authoring` styleguide and the
`charter-offering-scenario-extraction` procedure (authored and vendored in the org packs, to be
put in force in P1 — see [the overhaul approach](/plans/overhaul-approach/)): outcomes rather
than output surfaces, one
promise per scenario, the consumer's language only, and desired behaviour rather than a defect
reproduction. In core, the charter scenarios are co-owned by QA and core; the same split applies
as the suite grows here.
