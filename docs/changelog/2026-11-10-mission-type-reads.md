---
okf_version: "0.2"
title: Mission types, their steps, step contracts and skeletons are readable through the API
description: A client reads a mission type, each of its steps, the steps' contracts and the mission's skeletons from a pack or under an activation, and the effective reads link to them.
doc_status: active
updated: 2026-11-10T00:00:00Z
type: Changelog
kind: Changelog
related:
  - api/service-contract
  - development/hypermedia-hateoas
  - architecture/hexagonal-service
audience:
  - profile: contract-consumer
    guidance_text: Read this to see which mission type reads a client can follow, how a skeleton is named, and what a mission type's removal takes with it.
---

# 2026-11-10 — Mission types, their steps, step contracts and skeletons are readable through the API

**Breaking (0.x) for consumers.** Two refusal codes, `mission_step_contract_not_found` and `skeleton_not_found`, and
two `Artifact` `oneOf` members, `missionType` and `missionStepContract`, are new, with two kind tokens and an enum:
core regenerates its DTOs, and an exhaustive `switch` over `Artifact` or the refusal codes stops compiling. A removal of
any kind now also prunes the directories it leaves empty: removing a pack's last directive removes `directives/` too.
The effective mission type reads send no `ETag` and answer no `304`, while the effective profile reads do; that is
deliberate and deferred.

**Mission types are readable through the API** ([#99](https://github.com/spec-kitty/spec-kitty-charter/issues/99),
[#148](https://github.com/spec-kitty/spec-kitty-charter/issues/148),
[#100](https://github.com/spec-kitty/spec-kitty-charter/issues/100),
[#96](https://github.com/spec-kitty/spec-kitty-charter/issues/96)). Before, no client could read a mission type. Now a
client follows links from the API root to `software-dev`, its steps, their step contracts and its skeletons, from a pack
or under an activation. The details are in the [service contract](/api/service-contract/).

- **Pack reads.** `mission_type` and `mission_step_contract` are listed, filtered, referenced and removable.
  `getPackMissionType` answers a type with its steps and skeletons; `getPackMissionStep` a step with its stored prompt
  and guidelines byte for byte; the generic item read a step contract.
- **Skeletons.** A file under a type's `templates/` is a skeleton. `getPackMissionSkeleton` names it by one segment: its
  path with `~` written `~0` and `/` written `~1` (RFC 6901).
- **Activation reads.** `getEffectiveMissionType`, `getEffectiveMissionStep`, `getEffectiveMissionSkeleton` and
  `getEffectiveMissionStepContract` answer the layer-merge winner, and effective lists, graph nodes and the action
  context link them.
- **Removal.** Removing a mission type removes its step files and skeletons too, and is refused `409` while a step
  contract or a graph edge names it. A removal now prunes the directories it leaves empty, for every kind.
