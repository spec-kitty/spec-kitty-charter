---
okf_version: "0.2"
title: Directive, styleguide and paradigm are now first-class pack artefacts
description: The charter service models directive, styleguide and paradigm pack artefacts as first-class entities with full YAML round-trip, unified with tactic under a CharterArtifact family.
doc_status: active
updated: 2026-10-23T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0006-onion-vertical-slices-archunit
  - adr/0017-pack-schemas-imported-as-canonical-json-schema
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the three new pack entities, their repository ports and the CharterArtifact family interface.
  - profile: core-maintainer
    guidance_text: Read this for how the service now round-trips three more charter artefacts against the canonical open-packs schemas.
---

# 2026-10-23 — Directive, styleguide and paradigm are now first-class pack artefacts

Before, the charter service modelled only the **tactic** as a rich pack entity; a
pack's directives, styleguides and paradigms were read as generic facts and could
not be loaded, validated or written back faithfully. Now each of those three kinds
is a first-class pack child entity with a typed identifier, invariants, a per-type
repository port and full YAML round-trip against the canonical open-packs schemas —
the same treatment the tactic already had.

The four kinds are unified by a thin **`CharterArtifact`** family interface
(`kind`, `packId`, `localId`, `schemaVersion`, `label`, `description`, `edges`,
`urn`) in the packs domain, so the aggregate, the doctrine-reference-graph view and
reports can treat them uniformly while each kind keeps its own concept-bearing body
(a directive's enforcement and rules, a styleguide's scope, principles and
machine-lintable config, a paradigm's summary and the directives it is realized by).
The existing `Tactic` is retrofitted onto the same interface, and a pack now holds
its directives, styleguides and paradigms with the same belongs-to-pack and
no-duplicate-identifier invariants as its tactics.

Scope is deliberately the three "prose" kinds; toolguide and procedure are a planned
fast follow-up, and assets, agent profiles and the mission-type system are left for
their own modelling work.
