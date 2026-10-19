---
title: Contract 1.0 reserves the cutover surface and models impacts
description: Contract 1.0 replaces in_tension_with with a signed impacts edge, reserves two refusal codes and a depth bound, and adds a governance fingerprint, /info and response headers.
doc_status: active
updated: 2026-10-19
type: Changelog
kind: Changelog
related:
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - api/service-contract
  - architecture/logical-model
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what the contract reserves and why.
  - profile: core-maintainer
    guidance_text: Read this for the seam changes the 4.x cutover depends on.
---

# 2026-10-19 — Contract 1.0 reserves the cutover surface and models impacts

The contract is `1.0.0-SNAPSHOT` and its response schemas are closed, so anything
added after 1.0 is breaking. This change reserves now what the 4.x cutover needs,
implementing [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/).

## What changed

- **`impacts` replaces `in_tension_with`.** An `impacts` edge carries a signed strength in
  [-1, 1]; no other relation may. A missing, out-of-range or non-numeric strength, or a
  strength on another relation, is refused with `drg_schema_error`. The service reads a legacy
  `in_tension_with` edge as `impacts` with strength -1 until core migrates its graphs.
- **Reserved refusal codes** `governance_policy_violation` and `stale_input` (both 500;
  `stale_input` is retryable). Nothing raises them yet.
- **`depth`** on the action-context read is an integer from 1 to 1 (default 1), echoed back;
  an out-of-range value is a 400. Raising the maximum later stays compatible.
- **`ActionContext.governanceFingerprint`** — a domain digest of the packs in force and the
  merged graph, independent of host paths and file formatting.
- **`GET /info`** answers a closed `ServiceInfo`, and every response now carries
  `Charter-Contract-Version`, `Charter-Worktree` (an opaque id) and
  `Charter-Source-Fingerprint` (left out when a pack file cannot be read, so the refusal still
  answers).

The contract `CHANGELOG.md`, [service contract](/api/service-contract/) and
[logical model](/architecture/logical-model/) record the detail.
