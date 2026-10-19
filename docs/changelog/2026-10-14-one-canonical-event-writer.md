---
title: Events go through the core's one canonical event writer
description: The event pages follow the operator's 2026-10-14 clarification of one canonical event writer in the core CLI; the charter stays outside the event model.
doc_status: active
updated: 2026-10-14T00:00:00Z
type: Changelog
kind: Changelog
related:
  - architecture/events-and-ledger
  - context/system-context
  - context/product
  - plans/open-questions
  - plans/roadmap
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what changed on this date.
---

# 2026-10-14 — Events go through the core's one canonical event writer

## What changed

- [Events and the ledger](/architecture/events-and-ledger/) describes the operator's
  clarification of 2026-10-14: the core CLI has one canonical event writer that the runtime
  calls, and it writes only `spec-kitty-events` contract events at the latest version, each
  carrying its `schema_version`. Recorded in core ADR
  [`4.x/2026-10-14-1`](https://github.com/spec-kitty/spec-kitty/pull/6217)
  (in review).
- The same page cites core ADR `4.x/2026-10-13-2` (Accepted): the ledger is the publication
  seam, on by default, so "ledger by default" is no longer described as under consideration.
- The README, [Product](/context/product/), [System context](/context/system-context/),
  [Open questions](/plans/open-questions/) and the [Roadmap](/plans/roadmap/) say that
  charter-related events, if any, go through that writer. The separate ledger-row schema is
  no longer named as the dependency.

The charter service is unchanged: it is not an event producer, and it does not depend on
`spec-kitty-events`.
