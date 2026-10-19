---
title: The default Problem response declares the Charter headers
description: The shared default Problem response of every operation now declares the three Charter response headers, and a lint rule keeps every response declaring them.
doc_status: active
updated: 2026-10-19T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
  - api/service-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what the contract says about headers on a failed request.
  - profile: core-maintainer
    guidance_text: Read this for the change to the shared Problem response you vendor.
---

# 2026-10-19 — The default Problem response declares the Charter headers

The service already sent `Charter-Contract-Version`, `Charter-Worktree` and, when the packs
can be read, `Charter-Source-Fingerprint` on every response, as
[ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/) says. The contract
declared them on the refusals and the successes but not on the shared `default` `Problem`
response that every operation returns. It does now (issue #90).

## What changed

- **The shared `Problem` response declares the three headers.** `contracts/_shared/responses/Problem.yaml`
  references the header definitions in `contracts/charter/headers/`, which stay the one
  source of whether each is required. A header declaration is not a response property, so no
  schema changes.
- **A lint rule keeps it so.** `charter-headers-on-every-response` fails the contract lint when
  any response of any operation does not declare all three headers, the fingerprint included.
  It failed on the three `default` responses before the fix.
- **A service contract scenario** pins that a `400` for a depth this version does not answer
  carries the headers.
- **This repository owns the shared vocabulary.** The change is made to the vendored copy
  itself; `contracts/_shared/VENDORED.md` lists it, and a refresh from core never overwrites it.

The contract `CHANGELOG.md` and the [service contract](/api/service-contract/) record the detail.
