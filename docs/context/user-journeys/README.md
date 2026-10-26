---
title: User journeys
description: Eleven journeys through the charter, one per persona goal, each with its steps, the service operations it needs, how core does it today, and its scenarios.
doc_status: active
updated: 2026-10-26T00:00:00Z
type: Context
kind: Hub
related:
  - context/conceptual-model
  - context/audience
audience:
  - profile: charter-service-contributor
    guidance_text: Use this index to find the journey that exercises the part of the service you are changing.
  - profile: governance-lead
    guidance_text: Use this index to find the journeys that show how an organization governs its projects.
---

# User journeys

A user journey follows one persona through one goal, from the first command to the result. Each
journey page names the concepts it uses, lists its steps, draws them, and says which service
operations the journey needs: whether each one exists in the contract today, is envisioned in an
ADR, or is missing. It also says how the core CLI does the same thing today, and where that falls
short of the conceptual model.

## How journeys relate to the model and the scenarios

The [conceptual model](/context/conceptual-model/) says what packs, the active charter, the three
levers, mission types and profiles mean. A journey puts those concepts to work for one persona, so
a gap in the model shows up as a step that cannot be explained. Each journey has a scenario file
under `contracts/charter/scenarios/journeys/` that states the same journey as executable contract
scenarios; a scenario that needs a surface the service does not offer yet is tagged `@WIP`. The
chain is: concept, then journey, then scenario, then contract.

## Persona and journey

| Journey | Persona | Goal |
| --- | --- | --- |
| [01 Getting started](/context/user-journeys/01-getting-started/) | [Individual developer](/context/audience/individual-developer/) | Start with a minimal built-in pack and a sane-defaults preset, then run a first Mission |
| [02 Adopt a pack](/context/user-journeys/02-adopt-a-pack/) | [Individual developer](/context/audience/individual-developer/) | Adopt a pack from open-packs and deactivate one directive |
| [03 Plain-language request](/context/user-journeys/03-plain-language-request/) | [Non-engineer developer](/context/audience/non-engineer-developer/) | Ask for something in plain words and have a recipe and profile chosen |
| [04 Org house rules](/context/user-journeys/04-org-house-rules/) | [Governance lead](/context/audience/governance-lead/) | Ship an org pack with a required preset and a locked directive |
| [05 Remove Python](/context/user-journeys/05-remove-python/) | [Governance lead](/context/audience/governance-lead/) | Remove the Python artifacts from an org's offering, one at a time |
| [06 Publish a pack](/context/user-journeys/06-publish-a-pack/) | [Pack author](/context/audience/pack-author/) | Publish a pack with a paradigm, rules, a procedure and a supporting asset |
| [07 Add a mission type](/context/user-journeys/07-add-a-mission-type/) | [Pack author](/context/audience/pack-author/) | Add a mission type whose steps name roles, with specialist profiles and a fallback |
| [08 Resolve a step](/context/user-journeys/08-resolve-a-step/) | [Contract consumer](/context/audience/contract-consumer/) | Resolve a step against an activation context |
| [09 Upgrade packs](/context/user-journeys/09-upgrade-packs/) | [Core maintainer](/context/audience/core-maintainer/), [Individual developer](/context/audience/individual-developer/) | Register a new pack version and let a project opt in |
| [10 Host centrally](/context/user-journeys/10-host-centrally/) | [Governance lead](/context/audience/governance-lead/) | Host the service for a team, with local and hosted options |
| [11 Human reviews](/context/user-journeys/11-human-reviews/) | [Governance lead](/context/audience/governance-lead/) | Have every review done by a human, through a role pin |
