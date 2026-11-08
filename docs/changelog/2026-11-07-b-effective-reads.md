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

PR B of the layer-merge mission builds the read side. Divergences: DIV-216 to DIV-237
([register](/conformance/divergence-register/)). Contract: the `2026-11-07-b-*.md` and `2026-11-08-b-*.md` fragments.
The file is dated with the mission's PR B, as the PR A page is; it landed on 2026-11-08.

- **Layer merge.** The version from the level that applies replaces the artifact whole, and #357's settlement puts the
  named member's version in force. See [Resolution model](/architecture/resolution-model/#the-layer-merge).
- **Two reads answer.** `listEffectiveArtifacts` and `getActivationSkippedReport` no longer answer `501`. What is in
  force is four strata to a fixed point; the list carries the state and reason of each artifact, the report a typed
  bucket. The graph and the action context are PR C.
- **Writes.** `warnings` on a write, `activationsAffected` on a registration, and a cascade that walks the merged edges.
  A write over a closure that does not merge answers `500` (`pack_unreadable` or `pack_sibling_conflict`), a response the
  write paths already declare (`2026-11-08-b-write-cascade-merged-edges.md`).
- **Tags.** Each read's `ETag` names one representation and the closure. `If-Match` takes either tag.

## Breaking

- `EffectiveArtifact` gains the required `packId` and `level`; `EffectiveArtifactReason` loses `kind_default` and
  `kind_not_activated`.
- `SkippedReport` gains ten required buckets, and `ActivationChange` the required `warnings`. `Activation` gains
  `denyNotApplied` and `PackRegistrationNotices` gains `activationsAffected`, both optional.
- The `ETag` of `getActivation` is no longer the revision: it covers the revision and the closure's packs, and the
  read can answer `500`. A write's `If-Match` takes the revision or that tag; a stale closure tag is `412`.
- **From PR A.** Create and replace now refuse a wrong-type scalar and a duplicate member with `422`; they were coerced
  or last-wins before.

Core must regenerate its strict DTOs first. Nothing is edited or filed in core (C-003).
