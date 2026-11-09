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
DIV-215 ([register](/conformance/divergence-register/)).

- **No kind gate.** The document is `packs`, `selected`, `deactivated` and `languages`. Mission types are selected
  under `selected.mission_type` and denied unless required or locked; a cascade from one selects what its steps reach.
  Presets name any kind and carry no `activated_kinds`.
- **Storage version 2.** A version 1 document reads as its upgrade and is stored as version 2 on the next write, so
  every `ETag` changes once (one `412`). A write with `schemaVersion: 1` is `422`.
- **A replace drops** an id or a version pin no pack holds, reported in `dropped` and `droppedPins`.
- **`depth`** on a cascading select or deactivate counts `suggests` hops only (absent is unlimited); a bad one is `422`.

## Hand-over to core

Nothing is edited or filed in core (C-003). For the pull request description:

1. Regenerate the strict DTOs; `Activation` is closed and gains `dropped` and `droppedPins`, and
   `ActivationChange.droppedPins` is required. `SelectableKind` becomes `ActivatableKind`; response kinds can carry
   `mission_type`.
2. `activatedKinds` and `missionTypes` are refused on a create or replace, `422 validation_failed`
   (`removed_member`), not `activation_invalid`. Select mission types under `selected.mission_type`.
3. `activated_kinds` narrowing is gone: a narrowing project sees a larger effective set.
4. Read `dropped` and `droppedPins` on a replace instead of handling a `422`. Expect one `412` per activation, and a
   replace without `If-Match` can get `412` after eight lost attempts: send `If-Match` or handle it.
5. `containerDefaultToNull` is new to client-java in this PR: getters of absent optional lists change from `[]` to
   `null` (NPE risk). Keep it, and null-check.
6. `depth` is optional; `skippedByDepth` reports the next frontier. An older server answers `422 unknown_member`.
