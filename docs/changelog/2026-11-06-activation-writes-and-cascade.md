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
    guidance_text: Read this before building the effective reads.
---

# 2026-11-06 — The service serves the per-artifact activation writes and their cascade

The second half of [#291](https://github.com/spec-kitty/spec-kitty-charter/issues/291) builds the five per-artifact
writes of [ADR-0020](/adr/0020-offering-and-activations/) D6 under
[ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) D2 and D6:

- **Built:** `selectActivationArtifact`, `unselectActivationArtifact`, `deactivateActivationArtifact`,
  `reactivateActivationArtifact` and `applyActivationPreset`. Each requires `If-Match` and answers an
  `ActivationChange`. A bad body is `422`, after `428`, `404` and `412`. Only a deactivate answers `409`: a stored
  deny entry on the floor is tolerated.
- **The cascade:** walks six relations forward and passes through a profile's ancestors, `requires` and `scope`
  only. A deactivate walks the same way, so the two round-trip; it denies the named artifact alone and unselects
  what only it kept; the floor is never removed.
- **`enhances` is a relation like the others:** walked from the enhancer to its target, with no pull at write
  or read. `includeDeactivated` clears only the deny entries the walk passes through within the `cascade` scope.
- **Presets:** each key the preset lists replaces that key; `force` clears its members' deny entries. A member
  the closure no longer holds is `422`.
- **Still `501`:** the four effective reads.
- **Differences from core:** DIV-206 and DIV-207 ([divergence register](/conformance/divergence-register/)).
- **Stale documents:** an id a pack upgrade removed makes every per-artifact write `422`; a full `PUT` without it
  recovers the document.

[Offering and activation](/architecture/offering-and-activation/#the-cascade) has the rules.
