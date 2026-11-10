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

**Mission types are readable through the API** ([#99](https://github.com/spec-kitty/spec-kitty-charter/issues/99),
[#148](https://github.com/spec-kitty/spec-kitty-charter/issues/148),
[#100](https://github.com/spec-kitty/spec-kitty-charter/issues/100),
[#96](https://github.com/spec-kitty/spec-kitty-charter/issues/96)). Before, mission types were stored and validated, but no
client could read one. Now a client starts at the API root and follows links to `software-dev`, its 13 steps, the 5 step
contracts bound to them and its skeletons, from a pack or under an activation. The details are in the
[service contract](/api/service-contract/).

- **Pack-scoped reads.** Before: `mission_type` and `mission_step_contract` were no served kinds. After: both are listed,
  filtered with `?kind=`, referenced and removable. `getPackMissionType` answers a mission type in the `Artifact`
  envelope, with its steps then its on-demand steps and its skeletons, and `getPackMissionStep` answers one step with its
  stored `prompt.md` and `guidelines.md` byte for byte, its expected artifacts and links to its step contract and the
  skeletons its outputs bind. A step contract is read through the generic item read.
- **Skeletons.** A file under a mission type's `templates/` is a skeleton: the starting shape of a document a step
  produces (operator ruling). `getPackMissionSkeleton` answers it by a one-segment name, its path with `~` written `~0`
  and `/` written `~1` (RFC 6901), as text when its bytes are UTF-8 and as base64 otherwise. A pack's top-level
  `templates/` is a generic asset template and is not served here.
- **Activation-scoped reads.** `getEffectiveMissionType`, `getEffectiveMissionStep` and `getEffectiveMissionSkeleton`
  answer the layer-merge winner, its skeletons and step files from the pack that holds it, and the step contracts the
  merged closure binds to it.
- **Links.** Effective list, graph and action-context items of the two kinds link `effective-artifact` (and `artifact`
  where the pack is known); a step node `action:<type>/<step>` and the action context link the step read. New relations:
  `mission-type`, `step`, `step-contract` and `skeleton`.
- **Removal.** Removing a mission type also removes its step files and skeletons in one removal, and is refused with
  `409 artifact_referenced` while a step contract binds it or a graph edge names it or one of its action nodes. A removal
  now prunes the directories it leaves empty, for every kind (operator ruling).
- **Breaking on the 0.x line.** The refusal codes `mission_step_contract_not_found` and `skeleton_not_found`, the
  `Artifact` members `missionType` and `missionStepContract`, the two kind tokens and the `MissionSkeleton.encoding` enum
  are new: core regenerates its DTOs.
- **Build.** The missions slice's application and adapter rings live in the new `:missions-edge` module, gated by
  ArchUnit like `:profiles-edge`.
- **Usability run.** `scripts/usability/service-usability-run.sh` walks `software-dev` in both scopes: 131 of 131 hops
  pass at open-packs `a2a9379`.
