---
okf_version: "0.2"
title: pack-doctor judges the relations and the specialization cycle
description: pack-doctor validate --full adds RELATIONS, the relation rules and the specializes_from cycle per root's composed closure, with registration's codes.
doc_status: active
updated: 2026-11-27T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0083-one-answer-on-every-surface-preset-items-stale-manifests-and-pack-doctor
  - guides/check-packs
  - changelog/2026-11-26-the-relation-rules-run-on-the-walker
audience:
  - profile: pack-author
    guidance_text: Read this to know which relation faults pack-doctor validate --full now reports before you register.
---

# 2026-11-27 — pack-doctor judges the relations and the specialization cycle

**`--full` adds the check `RELATIONS`** (#632 item 3, FR-012).

- Per root of the set whose closure passed `REFERENCES`, over the closure the root composes, `RELATIONS` runs the
  factory's relation rules: the relations phase registration runs. It reports `self_override`,
  `conflicting_relations`, `override_not_permitted`, `override_cycle`, `enhances_cycle` and `specialization_cycle`,
  each 422 at registration. A fault several roots find is reported once.
- It runs after `REFERENCES`, and a root with a `REFERENCES` fault is not judged: registration judges a closure's
  relations only once its references pass.
- `PackRulesets.relationFaults(closure, factsOf)` runs the phase over one closure. pack-validation's `RelationsPhase`
  calls it.
- `PackDoctorReport` gains `RELATIONS` in `checks` and `skipped[].check`.
- The edge-index rules judge the layer merge, which pack-doctor does not compose, so they stay registration's.
  `FullPassParityTest` pins a re-pointed self-override as the one known gap (#636 slice 7).
- open-packs can retire its Python copies of the `specializes_from` cycle and relation checks
  (`validate_pack_schemas.py`, ADR-0083).
