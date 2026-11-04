---
okf_version: "0.2"
title: The SPDD/REASONS blocks need no separate operation
description: Cutover read R17 is decided. The blocks come with the mission-type read and the activation answer with R9, so the contract gains no separate shape for them.
doc_status: active
updated: 2026-11-04T00:00:00Z
type: Changelog
kind: Changelog
related:
  - api/service-contract
  - plans/triage-2026-11-02-rescopes
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you plan the cutover reads or the split of the built-in pack.
---

# 2026-11-04 — The SPDD/REASONS blocks need no separate operation

**Cutover read R17 is decided: the contract gains no separate shape for the SPDD/REASONS blocks**
([#111](https://github.com/spec-kitty/spec-kitty-charter/issues/111)). The blocks are text in the built-in pack's
`software-dev` step prompts, so they arrive with the mission-type read that serves those prompts (R5). Whether a
project should see them depends on its effective artifacts, which R9 answers.

The re-scope proposal reached the same conclusion for a wrong reason, that the blocks are bodies of the `spdd` pack's
artifacts. The [proposal](/plans/triage-2026-11-02-rescopes/#applied-since) now carries the correction, and the
[service contract](/api/service-contract/) lists the decision.
