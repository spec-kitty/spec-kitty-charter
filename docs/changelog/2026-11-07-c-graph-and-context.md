---
okf_version: "0.2"
title: The effective graph and action context answer, and a deny entry can be satisfied by an override
description: The last two effective reads are served, no operation is reserved, and a deny entry on a required artifact is accepted when an active override satisfies it.
doc_status: active
updated: 2026-11-09T00:00:00Z
type: Changelog
kind: Changelog
related:
  - architecture/offering-and-activation
  - architecture/composition-and-activation-rules
  - conformance/divergence-register
  - conformance/ruleset-scenario-trace
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what PR C of the layer-merge mission changed in the reads and the write rule.
  - profile: contract-consumer
    guidance_text: Read this for the reads that no longer answer 501 and the write that now answers 200 where it answered 409.
---

# 2026-11-09 — The effective graph and action context

PR C of the layer-merge mission (#10, #11, #103, #357, #374) builds the last two effective reads and one write rule. The
contract changes are the `2026-11-07-c-*` and `2026-11-09-c-*` fragments under `contracts/charter/changes/`.

- **The two reads answer.** `getEffectiveGraph` and `getEffectiveActionContext` no longer answer `501`, and no operation of
  the activation set is reserved. The graph holds the nodes in force, the merged edges among them and `layerOverrides`; the
  context is ADR-0018's walk from the held step, with suggestions, `enhancements` and the skipped report. An unknown
  mission type or action is `404`, before a `304`
  ([Offering and activation](/architecture/offering-and-activation/#the-graph-and-the-action-context)).
- **A deny entry can be satisfied by an override.** A deny entry on a required, not locked artifact is accepted when an
  active override of it satisfies the requirement in the document as it will be stored, where #399 refused every one with
  `409 artifact_required`. An enhancer alone does not, and a locked artifact is still refused. A closure whose layer merge
  is refused now reaches such a write, as `500 pack_unreadable`
  ([DIV-204](/conformance/divergence-register/), rule W26).
- **Regenerate for the breaking changes.** The two reads gain required members, and `EnhancerWithoutTarget.targets` becomes
  a list of `NodeReference {kind, id}`.
- **Rulings of 2026-11-09.** A step enhancer is in force exactly when the mission type of its step is (OD-6); one predicate
  decides which steps are held, so the cascade follows held steps only (OD-9); the floor holds activatable kinds only (D16).
  They are DIV-239 to DIV-244 of the [register](/conformance/divergence-register/).
- **Explicit rulings.** Five pages cite, for each sentence about a contest, a winner or a tie, the ruling or the refusal
  that settles it; the one tie no ruling covers, `satisfiedBy` by smallest URN, is tracked in #439. The
  [ruleset scenario trace](/conformance/ruleset-scenario-trace/) is complete.
