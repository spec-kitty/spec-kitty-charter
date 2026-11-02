---
okf_version: "0.2"
title: Procedure authoring checklist; one writer per pack
description: "A how-to with the review checklist for the tightened procedure (ADR-0052), and the documented rule that only one process writes a pack (#183)."
doc_status: active
updated: 2026-11-02T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/author-a-procedure
  - architecture/service-lifecycle
  - adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts
  - plans/pack-ecosystem-work-breakdown
audience:
  - profile: pack-author
    guidance_text: Read this for where the procedure checklist lives and what the schema already refuses.
  - profile: governance-lead
    guidance_text: Read this for the one-writer rule and the two follow-ups the operator asked for.
---

# 2026-11-02 — Procedure authoring checklist; one writer per pack

Epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142), work breakdown item 6 and the #183 part of item 14.

**Before:**

- [ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/) asked for a written checklist for the tightened procedure definition, and none existed.
- One writer daemon per worktree was the design notes' default for #183, but no page said that only one process
  may write a pack.

**Now** (docs only; no schema, contract or service code changes):

- [Author a procedure](/guides/author-a-procedure/) separates what the closed `procedure.schema.json` refuses at
  load from the three refusals that review applies: no Mission deliverable, no spanning of steps, no rule.
  - Review applies the checklist (ADR-0052 decision 3); the operator confirmed on 2026-11-02 that no CI check does.
  - The schema refuses `type: action` but not an `action:` URN as a reference id. Tightening that is
    [#350](https://github.com/spec-kitty/spec-kitty-charter/issues/350).
- [Service lifecycle](/architecture/service-lifecycle/#one-writer-per-pack) states the rule: one writer process per
  pack, the daemon of its worktree. It also says what a second writer breaks.
  - The per-pack write lock is per JVM.
  - A second process can lose an acknowledged registration without an error.
  - Nothing enforces the rule yet: the launcher (#70) is not built, and the service has no cross-process lock.
- The container guide adds the limit: one container per configuration root.
- A fail-fast guard against a second process is
  [#349](https://github.com/spec-kitty/spec-kitty-charter/issues/349) (operator, 2026-11-02).
