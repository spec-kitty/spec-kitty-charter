---
okf_version: "0.2"
title: "One kind model, a replace that drops, and a cascade depth (PR A0)"
description: "The activation document has no kind gate, mission types are ordinary kinds, a replace drops stale ids and pins, and a cascade takes a depth. Core regenerates its DTOs."
doc_status: active
updated: 2026-11-07T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0059-switch-gate-cascade-edges-and-activation-rulings
  - conformance/divergence-register
  - conformance/ruleset-scenario-trace
audience:
  - profile: core-maintainer
    guidance_text: Read this before regenerating core's strict DTOs; the hand-over below is for core's migration notes.
---

# 2026-11-07 — One kind model, a replace that drops, and a cascade depth (PR A0)

PR A0 of the layer-merge mission changes what #399 built. Decisions: the
[ADR-0059 amendment](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/#amendment-2026-11-07) and the
amendments to ADR-0020, ADR-0042 and ADR-0050. Contract: the `2026-11-07-a0-*.md` fragments. Divergences: DIV-209 to
DIV-215, with DIV-203, DIV-206 and DIV-501 updated ([register](/conformance/divergence-register/)).

- **No kind gate.** The document is `packs`, `selected`, `deactivated` and `languages`. `activatedKinds` and
  `missionTypes` are refused (`422`, `removed_member`). Mission types are selected under `selected.mission_type` and
  denied unless required or locked; a cascade from one selects what its steps reach. Presets name any kind and carry
  no `activated_kinds`.
- **Storage version 2.** A version 1 document reads as its upgrade and is stored as version 2 on the next write, so
  every `ETag` changes once (one `412` per client).
- **A replace drops** an id or a version pin no pack holds, reported in `dropped` and `droppedPins`, and judges the
  floor only on the deny entries it adds. `includeDeactivated` keeps the deny entry of a lineage ancestor it passes.
- **`depth`** on a cascading select or deactivate counts `suggests` hops only (`0` follows none; absent is
  unlimited), and `skippedByDepth` lists the next frontier. A bad `depth` is `422` at `/depth` (`unreadable_body`,
  `below_minimum`, `above_maximum`). A deactivate undoes a select at the same depth only in what it removes.

## Hand-over to core

Nothing is edited or filed in core (C-003). For the pull request description:

1. Regenerate the strict DTOs from this contract; `Activation` is closed and gains `dropped` and `droppedPins`.
2. `activatedKinds` and `missionTypes` are refused; select mission types under `selected.mission_type`.
3. `activated_kinds` narrowing is gone: a narrowing project sees a larger effective set.
4. Read `dropped` and `droppedPins` on a replace instead of handling a `422`; expect one `412` per activation.
5. The client's optional-list getters return `null` when a list is absent (`containerDefaultToNull`); keep it.
