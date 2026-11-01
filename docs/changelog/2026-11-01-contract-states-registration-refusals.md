---
okf_version: "0.2"
title: The contract states the registration null-list, null-item and body-size refusals
description: The contract states that a registration's null list or null list item is refused, and a directive, paradigm or styleguide body over 1 MiB.
doc_status: active
updated: 2026-11-01T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0026-write-surface-is-functional-registration
audience:
  - profile: contract-consumer
    guidance_text: Read this if a client sends a null list, a null list item or a large body on a registration.
  - profile: charter-service-contributor
    guidance_text: Read this for where the contract states the registration refusals the service already makes.
---

# 2026-11-01 — The contract states the registration null-list, null-item and body-size refusals

**The contract now states what the service already refuses** on a directive, paradigm, styleguide or
toolguide registration ([#310](https://github.com/spec-kitty/spec-kitty-charter/issues/310)). The
service has done this since #311 and #318:

- It refuses a `null` list, or a `null` item in a list, with `422 validation_failed`.
- It refuses a directive, paradigm or styleguide body over 1 MiB with `422 validation_failed`, before
  the body is read.

The eight registration 422 descriptions now say so, and so do the four `*Registration` schema
descriptions. The toolguide's 7 MiB body limit is unchanged.

Each kind's scenario file gains a scenario with a null-list row and a null-item row, each pinning the
exact `detail`. The directive, paradigm and styleguide scenario files also gain a body-limit scenario.
The contract fragment is `contracts/charter/changes/2026-11-01-registration-null-list-and-body-bound.md`.
