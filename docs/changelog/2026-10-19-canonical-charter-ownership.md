---
title: This repository is canonical for the charter structure, responses, contracts and pack schemas
description: ADR-0015 records the operator ruling that charter shapes changed here are canonical, even in files vendored from core.
doc_status: active
updated: 2026-10-19
type: Changelog
kind: Changelog
related:
  - adr/0015-charter-repository-is-canonical-for-charter-structure
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what changed on this date.
---

# 2026-10-19 — This repository is canonical for charter shapes

## What changed

- [ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/) records the
  operator's ruling: this repository owns the charter structure, its responses, its contracts
  and the Charter Pack schemas. A change made here is canonical, even to a file vendored from
  core, and consumers, core included, adapt. Files stay close to their origin unless there is a
  reason to change them, and every change from an origin is listed in its `VENDORED.md`.
- The service build guide no longer says to change `contracts/_shared/` only in core.
