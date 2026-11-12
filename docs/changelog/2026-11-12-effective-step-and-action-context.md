---
okf_version: "0.2"
title: The effective step prompt and the ownership rule of the action context
description: Slice C serves the prompt a step gets, refuses a step enhancement that would misplace a fragment, and lists every directive in force that governs an action.
doc_status: active
updated: 2026-11-12T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0066-a-step-enhancement-may-contribute-prompt-fragments
  - architecture/composition-and-activation-rules
audience:
  - profile: contract-consumer
    guidance_text: Read this for the new prompt read, the wider action context and the new refusals.
---

# 2026-11-12 — The effective step prompt and the ownership rule of the action context

Slice C ([#467](https://github.com/spec-kitty/spec-kitty-charter/issues/467)) changes the contract on the 0.x line.

- **The effective step prompt is served** ([#438](https://github.com/spec-kitty/spec-kitty-charter/issues/438)).
  `getEffectiveStepPrompt` answers the prompt in force as stored and, per slot, the fragments in force, in order. A
  mission type the activation does not put in force is `409 mission_type_not_in_force`, a new refusal code. The action
  context links the read as `prompt`.
- **A step enhancement that would misplace a fragment is refused** (rules C37, C38 and the inherited lock). An
  enhancement in force of an undeclared slot is `prompt_slot_missing`; an unheld step and a step of a locked mission
  type are refused.
- **The action context lists every directive in force that governs the action**
  ([#450](https://github.com/spec-kitty/spec-kitty-charter/issues/450)): beside the action's walk, the directives,
  tactics and paradigms its mission type scopes and those nothing scopes, each with its `appliesThrough` (DIV-245). The 16 directives core lists for `software-dev/implement` are
  explained in the
  [scoping scenario](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/scenarios/effective-context-scoping.feature).
- **Every overrider is named** ([#439](https://github.com/spec-kitty/spec-kitty-charter/issues/439)): `satisfiedBy` and
  `overriddenBy` are lists, by level then URN.
- **An `overrides` edge comes from an activatable artifact**
  ([#447](https://github.com/spec-kitty/spec-kitty-charter/issues/447)): one from a template, an asset, a guard or a
  lifecycle is refused.
- **A step only a replaced version keeps is read** from its place, as the context and the prompt read it.
- **Every effective `ETag` changes once** (recipe `v3`). The closure fingerprint digests each pack in closure order,
  so it no longer depends on the configured order of the packs.
- **R3 is decided** ([#97](https://github.com/spec-kitty/spec-kitty-charter/issues/97)): the effective graph, the
  reference read and the registration checks answer core's graph reads.

The rules and their tests are on the [rules page](/architecture/composition-and-activation-rules/).
