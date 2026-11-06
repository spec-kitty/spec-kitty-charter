---
okf_version: "0.2"
title: "The service stores activations, and an activation's packs have a read of their own"
description: "Core can store, read, replace and delete an activation on the service and read its closure of packs. A pack can lock artifacts in policy.yaml. Absent means none."
doc_status: active
updated: 2026-11-05T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0020-offering-and-activations
  - adr/0059-switch-gate-cascade-edges-and-activation-rulings
  - adr/0063-pack-mutability-policy-file
  - architecture/offering-and-activation
  - conformance/divergence-register
audience:
  - profile: core-maintainer
    guidance_text: Read this for what core's seam can now route to the service, what still answers 501, and what changes when it switches.
  - profile: charter-service-contributor
    guidance_text: Read this before building the per-artifact activation writes or the effective reads.
---

# 2026-11-05 — The service stores activations, and an activation's packs have a read of their own

The first half of [#291](https://github.com/spec-kitty/spec-kitty-charter/issues/291) and
[#360](https://github.com/spec-kitty/spec-kitty-charter/issues/360) builds the activation register of
[ADR-0020](/adr/0020-offering-and-activations/) under [ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/):

- **Built:** core can store (`POST`, `PUT`), read (`GET`, with `304`), replace and delete an activation document, and
  read its closure, `GET /activations/{activationId}/packs`: the `depends_on` and `parent_pack` closure of its `packs`, in dependency
  order. Writes are checked against that closure alone (`422`, and `409` for a deny entry on a required or locked
  artifact). The `Activation` answer drops `summary` and `governanceFingerprint`.
- **Still `501`:** the five per-artifact writes (the second pull request of this mission) and the four effective reads
  (the resolution mission).
- **Locks:** a pack may name a `policy.yaml` with `mutability_policy` ([ADR-0063](/adr/0063-pack-mutability-policy-file/)).
  No pack is required to ship one, and the open packs ship none yet.
- **Differences from core** ([divergence register](/conformance/divergence-register/)): absent means none, `skill`
  included (DIV-203); a deny entry on a required or locked artifact is refused at write (DIV-204); the required floor
  is closure-scoped (DIV-205). ADR-0020 D6, ADR-0013 D2 and ADR-0012 D2 carry amendment notes.

**#360's acceptance, re-pointed.** #360's acceptance says a pack an activation does not build on is "absent from the
offering". That acceptance is met by the closure read, `GET /activations/{activationId}/packs`, not by `/offering`:
the offering stays the catalog of every configured pack, and an activation's closure lists only the packs its
document builds on, in dependency order (decision `DM-01M6NXNBA1J808JV4SHT743SCF`).

[Offering and activation](/architecture/offering-and-activation/) has the design.
