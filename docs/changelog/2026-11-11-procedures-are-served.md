---
okf_version: "0.2"
title: Procedures are served, and an action's context links their reads
description: Procedures are read and removed by pack and read under an activation; the action-context entries of procedures newly link their reads.
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
- **A procedure can be removed.** `DELETE /packs/{packId}/artifacts/procedure/{artifactId}` works like the removal of
  the other served kinds, with the pack revision (the item's `ETag`) as `If-Match`. There is still no `PUT` for it.
- **A procedure entry of the action context links its reads.** The context already listed the procedures,
  styleguides and toolguides an action scopes and the activation selects, and a contract scenario now pins that,
  beside the out-of-scope and unselected controls. Styleguide and toolguide entries already linked their reads; a
  procedure entry now carries the `effective-artifact` and `artifact` links it lacked.
- **Example packs**: the built-in example pack's `implement` action scopes a procedure, beside its styleguide and
  toolguide.
