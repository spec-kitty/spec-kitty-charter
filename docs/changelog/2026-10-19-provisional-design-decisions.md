---
title: Provisional design decisions for the cutover, pack governance, deactivation and the creed
description: Six provisional ADRs record the operator's 2026-10-19 rulings on the 1.0 contract, the 4.x cutover, the seam runtime, pack policy, deactivation and the creed.
doc_status: active
updated: 2026-10-19T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - adr/0010-cutover-scope-and-seam-boundary
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
  - adr/0012-pack-mutability-policy
  - adr/0013-persistent-deactivation
  - adr/0014-foundational-values-and-creed-rulings
  - architecture/strangler-transition
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what changed on this date.
---

# 2026-10-19 — Provisional design decisions

The operator ruled on the open behavioral questions after a squad review. Every ruling is
provisional: each ADR says what would make it be revisited.

## What changed

- [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/): contract 1.0
  models the signed `impacts` relation (dropping `in_tension_with`) and reserves the
  `governance_policy_violation` and `stale_input` refusals, an integer `depth` parameter, an
  info endpoint, version and fingerprint headers, and a governance fingerprint. Activation and
  the creed arrive later as additive contract sets.
- [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/): the 4.x cutover routes every
  offering read, including activation's, through this API; compile is exempt until stage 5.
  Stages 3 and 4 ship together, gated by the conformance corpus alone. Core's strangler ADR is
  to govern only the seam routing.
- [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/): the seam fails closed,
  the service always runs as a daemon, and freshness is fingerprinted with a push after each
  write.
- [ADR-0012](/adr/0012-pack-mutability-policy/): pack authors declare `open`, `extendable` or
  `locked` in a policy file that `pack.yaml` references; it supersedes
  `replaceable-builtins.yaml`.
- [ADR-0013](/adr/0013-persistent-deactivation/): deactivation is a deny-list in one
  activation ledger; cascade skips and reports excluded artifacts.
- [ADR-0014](/adr/0014-foundational-values-and-creed-rulings/): the creed decisions D-1 to D-5
  are recorded here, with refinements.
- [Strangler transition](/architecture/strangler-transition/),
  [Service lifecycle](/architecture/service-lifecycle/),
  [Resolution model](/architecture/resolution-model/),
  [Logical model](/architecture/logical-model/), [Descriptors](/architecture/descriptors/),
  [Contract and DTOs](/architecture/contract-and-dtos/), the [Roadmap](/plans/roadmap/) and
  [Open questions](/plans/open-questions/) follow these rulings. The pages now record that the
  import move is done (spec-kitty#6188).
