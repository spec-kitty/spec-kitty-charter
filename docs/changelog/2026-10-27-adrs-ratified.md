---
title: The decision log is ratified
description: ADR-0005, ADR-0007 and ADR-0039 are accepted, the provisional band ADR-0009 to ADR-0022 is firmed, and ADR-0036 and ADR-0037 point to ADR-0039.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0005-service-registers-and-distributes-packs
  - adr/0007-rest-hateoas-contract-of-record
  - adr/0039-a-kind-binding-can-carry-companion-files
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for which records now bind, and that the decision log no longer reads as provisional.
  - profile: evaluator
    guidance_text: Read this for the ratification sweep across the decision log; no behavior changes.
---

# 2026-10-27 — The decision log is ratified

**The decision log now reads as ratified.** On the operator's ruling (Stijn Dejongh), a sweep of
records moves to full **Accepted**. Nothing on the wire or in the build changes; only the status of
the records does.

- [ADR-0005](/adr/0005-service-registers-and-distributes-packs/),
  [ADR-0007](/adr/0007-rest-hateoas-contract-of-record/) and
  [ADR-0039](/adr/0039-a-kind-binding-can-carry-companion-files/), which stood as **Proposed**, are
  now **Accepted**. Each keeps its original proposal date as a parenthetical.
- The provisional band [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/)
  through [ADR-0022](/adr/0022-contract-versions-start-at-0-x/) is firmed from
  **Accepted (provisional)** to **Accepted**. Each record records when it was taken and that it is
  still revisited as the system evolves; amendments and supersession notes are unchanged.
- [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/) and
  [ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/) gain a forward pointer: both
  are extended in part by ADR-0039, which lets a kind binding carry companion files beside its YAML.

The [ADR index](/adr/) records the new statuses.
