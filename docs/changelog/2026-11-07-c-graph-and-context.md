---
okf_version: "0.2"
title: The effective graph and action context answer, and a deny entry can be satisfied by an override
description: The last two effective reads are served, and a deny entry on a required artifact is accepted when an active override satisfies it (breaking for clients of the 409).
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
- **A deny entry can be satisfied by an override (breaking).** Where #399 refused every deny entry on a required artifact,
  `createActivation`, `replaceActivation` and `deactivateActivationArtifact` answer `200` or `201` when an active
  override satisfies the requirement, and the declared `500 pack_unreadable` when the layer merge refuses, where both were
  `409 artifact_required`. An enhancer alone does not satisfy; a locked artifact is still refused
  ([DIV-204](/conformance/divergence-register/), rule W26). Core still refuses it.
- **The effective-read tags change.** The tags move to domain `v2`: a client holding a PR B tag gets a `200` with the
  new body, not a `304`.
- **The write cascade follows held steps only** (OD-9, [DIV-242](/conformance/divergence-register/)).
- **Regenerate.** The two reads gain required members; `EnhancerWithoutTarget.targets` is a list of `NodeReference`.
- **Rulings of 2026-11-09.** A step enhancer is in force exactly when the mission type of its step is (OD-6), and the
  floor holds activatable kinds only (D16): DIV-239 to DIV-244 of the [register](/conformance/divergence-register/).
- **Explicit rulings.** Five pages cite the ruling behind each contest, winner or tie; the one tie no ruling covers is
  tracked in #439. The [ruleset scenario trace](/conformance/ruleset-scenario-trace/) is complete.
