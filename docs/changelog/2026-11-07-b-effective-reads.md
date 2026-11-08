---
okf_version: "0.2"
title: "Layer merge and effective reads (PR B)"
description: "The layer merge is built and two effective reads answer: the artifact list and the skipped report. Breaking items for a client that regenerates from the contract."
doc_status: active
updated: 2026-11-08T00:00:00Z
type: Changelog
kind: Changelog
related:
  - architecture/resolution-model
  - architecture/offering-and-activation
  - conformance/divergence-register
  - conformance/ruleset-scenario-trace
audience:
  - profile: core-maintainer
    guidance_text: Read this before you regenerate core's DTOs from the contract; the Breaking list is core's migration notes.
---

# 2026-11-07 — Layer merge and effective reads (PR B)

PR B builds the read side. Divergences: DIV-216 to DIV-237 ([register](/conformance/divergence-register/)). Contract: the
`2026-11-07-b-*.md` and `2026-11-08-b-*.md` fragments. Landed on 2026-11-08.

- **Layer merge.** The version from the level that applies replaces the artifact whole. See
  [Resolution model](/architecture/resolution-model/#the-layer-merge).
- **Two reads answer.** `listEffectiveArtifacts` and `getActivationSkippedReport` no longer answer `501`. The graph and
  the action context are PR C.
- **Writes.** `warnings` on a write, `activationsAffected` on a registration, and a cascade over the merged edges.
- **Tags.** Each read's `ETag` names one representation and the closure. `If-Match` takes either tag.

## Breaking

- `EffectiveArtifact` gains the required `packId` and `level`; `EffectiveArtifactReason` loses `kind_default` and
  `kind_not_activated`. `SkippedReport` gains ten required buckets, `ActivationChange` the required `warnings`;
  `Activation` gains `denyNotApplied` and the notices `activationsAffected`, both optional.
- The `ETag` of `getActivation` covers the revision and the closure's packs, and the read can answer `500`. A write's
  `If-Match` takes the revision or that tag; a stale closure tag is `412`.
- **The A1 floor in writes and notices.** The floor is the union over every governing copy of a preset id, as the reads
  judge it. A deny of an artifact any copy requires is `409 artifact_required` (it was `200` for a copy that did not
  win), and `floorAdded` and `floorRemoved` use the union (DIV-204, DIV-205).
- **Reclassified from PR A.** Create and replace refuse a wrong-type scalar and a duplicate member with `422`
  (`2026-11-08-b-activation-body-strictness-breaking.md`).

## Changed

- `warnings` name additions only; a removal warns of nothing (DIV-237).
- A pruned required or locked artifact is skipped once, in `requiredNotInForce` (DIV-228).
- `activationsAffected` counts a change of the version in force (DIV-237).
- The facts cache keys on a path-inclusive digest, so a rename misses (DIV-236).

Core must regenerate its strict DTOs first. Nothing is edited or filed in core (C-003).
