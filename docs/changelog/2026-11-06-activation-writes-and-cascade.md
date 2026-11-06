---
okf_version: "0.2"
title: "The service serves the per-artifact activation writes and their cascade"
description: "Core can select, unselect, deactivate and re-activate one artifact of an activation and apply a preset, with a cascade. Only the four effective reads still answer 501."
doc_status: active
updated: 2026-11-06T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0020-offering-and-activations
  - adr/0059-switch-gate-cascade-edges-and-activation-rulings
  - adr/0013-persistent-deactivation
  - architecture/offering-and-activation
  - conformance/divergence-register
audience:
  - profile: core-maintainer
    guidance_text: Read this for the writes core's seam can now route to the service, how their cascade differs from core's, and what still answers 501.
  - profile: charter-service-contributor
    guidance_text: Read this before building the effective reads and the read-time enhances pull.
---

# 2026-11-06 — The service serves the per-artifact activation writes and their cascade

The second half of [#291](https://github.com/spec-kitty/spec-kitty-charter/issues/291) builds the five per-artifact
writes of [ADR-0020](/adr/0020-offering-and-activations/) D6 under
[ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) D2 and D6:

- **Built:** `selectActivationArtifact`, `unselectActivationArtifact`, `deactivateActivationArtifact`,
  `reactivateActivationArtifact` and `applyActivationPreset`. Each requires `If-Match`, reads one view of the
  activation's closure and answers an `ActivationChange`; an id or a preset the closure does not hold is `404`.
- **The cascade:** a select or a deactivate with a `cascade` walks `requires`, `suggests`, `refines`, `scope` and
  `instantiates` forward, each artifact once, stops at a deny entry and reports it. It passes through a profile's
  `specializes_from` ancestors, `requires` and `scope` only, never putting one in force. A deactivate denies the
  named artifact alone and unselects what only it kept; what is shared stays (`skippedShared`).
- **`enhances` is not written:** a write never adds an enhancer to `selected`. With `includeDeactivated`, a select
  clears the deny entries the `enhances` pull would reach from what it selects. The pull runs at read time.
- **Presets:** each key the preset lists replaces that key of the document, `activatedKinds` and `missionTypes`
  included; a key it does not list is left as it is. `force` clears the deny entries of the preset's members. A member
  the closure no longer holds is refused `422 activation_invalid`, naming the artifact.
- **Still `501`:** the four effective reads, which the resolution mission builds with the read-time `enhances` pull
  and the layer merge.
- **Differences from core** ([divergence register](/conformance/divergence-register/)): the lineage pass-through
  (DIV-206) and `enhances` at write (DIV-207).

[Offering and activation](/architecture/offering-and-activation/#the-cascade) has the rules.
