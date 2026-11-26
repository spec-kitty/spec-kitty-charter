---
okf_version: "0.2"
title: The edge-index rules answer one code per fault
description: A read and a registration of one closure now answer each fault of its layer merge with one code, from edge-index rules that run on the walker after the relations.
doc_status: active
updated: 2026-11-26T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0082-the-domain-owns-every-graph-walk-and-ports-transform-and-interface
  - adr/0056-a-refusal-code-may-declare-a-status-per-operation
  - changelog/2026-11-26-the-relation-rules-run-on-the-walker
audience:
  - profile: contract-consumer
    guidance_text: Read this to know which codes a read of a closure whose layer merge has a fault answers now.
  - profile: charter-service-contributor
    guidance_text: Read this before you add or change a rule over a closure's layer merge.
---

# 2026-11-26 — The edge-index rules answer one code per fault

**The edge-index phase is built** (ADR-0082 slice 4, #639; closes #667). This is a wire change.

- Four faults of a closure's layer merge are now rules of a second relations phase, `PackRulesets.edgeIndex`, over
  the merged closure (`MergedRelations` in `packs.domain`):
  - a layer override the overridden artifact's level forbids (C21) and an enhancement of a step whose mission type is
    locked: `override_not_permitted`;
  - an `overrides` edge the merge re-points to its own source (C36): `self_override`;
  - another pack's step link that names no step of the type: `pack_reference_artifact_missing`, with the violation
    `mission_step_unlinked`.
- A read answered each `500 pack_unreadable`, the first only; it answers every fault with its code, 500. Registration
  and the impact-checked writes answer the same codes as 422 (ADR-0056), so they now refuse three shapes they accepted.
  A stored pack that replaces a locked artifact is then cleared only by its `DELETE`: re-registering it keeps the
  override (refused, C21) or drops a locked artifact (refused by the write gate).
- Only these four faults refuse a write: a merge that refuses with its own code (an unsettled step contest) leaves a
  write as it was. On a read, the fault the merge meets first stays the answer, a C21 fault before a later contest.
- `ClosureImpactCheck` runs one walker per closure over the relations, edge-index and own-closure phases; the walker's
  phase gate replaces the hand-written gates, and `relationFaults` is deleted. A registration whose closure declares a
  fallback or a pin beside such a fault answered `500 pack_unreadable` (#667); it answers the fault's 422.
- `EdgeIndex` stays in the application as the merge's index and judges nothing: its qualified-endpoint faults were the
  compose's references check's already, and its merge refuses nothing.
