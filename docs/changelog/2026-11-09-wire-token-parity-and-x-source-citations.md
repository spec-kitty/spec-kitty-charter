---
okf_version: "0.2"
title: Every contract enum is pinned against the Java that fills it, and every x-source citation names a real file
description: New parity rows catch a token added on one side only, a named exemption keeps the retired pack_unknown_key honest, and a test fails on any x-source path that does not exist.
doc_status: active
updated: 2026-11-09T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if a parity row or XSourceCitationsTest fails after you add an enum value or move a source file.
---

# 2026-11-09 — Wire-token parity and x-source citations

**No contract enum can drift from its Java type unnoticed any more** (#430). Each mapper reads its enum with
`fromValue`, so a value added on one side only used to surface as a `500` at run time, not as a failing test.

- **New parity rows.** `WireTokenParityTest` now also pins `NodeKind`, `DiagnosticCode` and the four
  `ActivatableKind` copies that are inlined in other schemas. A new `ActivationWireTokenParityTest` pins the
  activation enums against the domain enums that fill them: `CascadeKind`, `EffectiveArtifactState`, the
  `EffectiveArtifact` reason, `ArtifactPolicy`, `DenyNotApplied.reason`, `RequiredNotInForce.reason` and
  `ActivationWarning.code`. Every row has a control that shows the check fails on a skewed pair.
- **Deliberate differences are named.**
  - The `NodeKind` row compares only the kinds that are authored in graph documents (`authoredInGraphs()`), because
    the contract does not list `guard` or `lifecycle`.
  - `pack_unknown_key` stays in the contract as a retired value (ADR-0036 decision 3, ADR-0042). The row exempts it by
    name, and the exemption fails if the value is removed from the contract or the Java constant comes back.
  - `RequiredNotInForce.reason` is compared with the two pruning reasons, the only ones that bucket ever carries.
- **x-source citations.** `XSourceCitationsTest` fails on any `x-source.path` in the charter contract that names a file
  that does not exist. Four citations pointed at the removed `service/charter-domain` module. They now name the real
  sources, a metadata-only change recorded in `contracts/charter/changes/`.

No wire shape changes.
