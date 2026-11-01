---
okf_version: "0.2"
title: governance_policy_violation is marked raised, and the reserved-code test reads the contract
description: The x-refusal map marks governance_policy_violation raised, and ReservedRefusalCodesTest takes the codes marked not raised from the contract.
doc_status: active
updated: 2026-11-01T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0012-pack-mutability-policy
  - api/service-contract
  - development/http-status-codes
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you raise a refusal code or mark one raised.
---

# 2026-11-01 — governance_policy_violation is marked raised, and the reserved-code test reads the contract

Issue [#293](https://github.com/spec-kitty/spec-kitty-charter/issues/293).

**Before:**

- The `x-refusal` map of `CharterRefusalCode` marked `governance_policy_violation` `raised: false`, though the
  registration writes raise it (`PackMutabilityPolicy`, [ADR-0012](/adr/0012-pack-mutability-policy/)).
- `ReservedRefusalCodesTest` kept its own list of unraised codes, so the contract and the test could drift apart.

**Now:**

- **`governance_policy_violation` is `raised: true`** (409). The 409 descriptions of the writes name it. No policy
  file is read yet, so every target is open and no write is refused with it until one is.
- **`drg_schema_error` stays `raised: false`.** The whole-pack load raises it, but no operation reaches that load.
- **`ReservedRefusalCodesTest` reads the contract.** It takes every code the `x-refusal` map marks `raised: false`.
  For each, it fails when a main class names the code. The enum and `RefusalHandler` are excepted, as are two classes
  no operation reaches: `WholePackStore` and `PackFindingCode`. A companion rule fails if a main class starts calling
  either of them, so the map is corrected in the same change.
