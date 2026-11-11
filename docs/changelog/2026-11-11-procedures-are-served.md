---
okf_version: "0.2"
title: Procedures are served, and an action's context links every kind it lists
description: Procedures read by pack and under an activation; action-context entries of procedures, styleguides and toolguides link their reads.
doc_status: active
updated: 2026-11-11T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0071-procedures-are-a-served-read-kind
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the procedure reads and what the action context now answers.
---

# 2026-11-11 — Procedures are served

An activation could select procedures that no read answered
([#484](https://github.com/spec-kitty/spec-kitty-charter/issues/484)).

- **Procedures are a served kind** ([ADR-0071](/adr/0071-procedures-are-a-served-read-kind/)). Before: every
  procedure read was `404 kind_not_found`. After: the pack list (`?kind=procedure`), the item (member `procedure`,
  with its steps, roles and anti-patterns), its `/references`, and the activation item and listing answer it, with the
  source fingerprint and an `ETag`. A procedure the pack does not hold is `404 artifact_not_found`.
- **The action context links every kind it lists.** The context already listed the procedures, styleguides and
  toolguides an action scopes and the activation selects; a contract scenario now pins that, beside the out-of-scope
  and unselected controls. A procedure entry now carries the `effective-artifact` and `artifact` links it lacked.
- **Example packs**: the built-in example pack's `implement` action scopes a procedure, beside its styleguide and
  toolguide.
