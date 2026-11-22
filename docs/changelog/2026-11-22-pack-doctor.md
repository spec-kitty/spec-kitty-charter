---
okf_version: "0.2"
title: pack-check is pack-doctor, and registration and pack-doctor give one answer
description: The checker is pack-doctor, with validate and remediate; registration and pack-doctor refuse preset items, stale manifest hashes and inline anti-patterns alike.
doc_status: active
updated: 2026-11-22T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0083-one-answer-on-every-surface-preset-items-stale-manifests-and-pack-doctor
  - guides/check-packs
audience:
  - profile: pack-author
    guidance_text: Read this if your CI runs pack-check, or a pack that registered before is now refused.
---

# 2026-11-22 — pack-check is pack-doctor, and registration and pack-doctor give one answer

Registration and the standalone checker now refuse the same faults with the same codes
([ADR-0083](/adr/0083-one-answer-on-every-surface-preset-items-stale-manifests-and-pack-doctor/)).

## What changed

- **The checker is pack-doctor.** `pack-check-<version>.jar` is now `pack-doctor-<version>.jar`, with no alias, and it
  takes a command first: `java -jar pack-doctor-<v>.jar validate --config-root x` replaces
  `java -jar pack-check-<v>.jar --config-root x`.
- **Newly refused, on every surface:**
  - a preset item its pack's own closure lacks: `preset_item_not_held` (a `must_have` no pack holds as a file) or
    `preset_item_undeclared` (an item no pack declares). A composing read still reports `requiredNotHeld`;
  - a stale `content_hash` or `manifest_hash`: `manifest_stale`, one per field. Every write still stamps;
  - an invalid `anti_patterns/<id>.anti-pattern.yaml`, an `anti_pattern` graph node with no file
    (`drg_schema_error`), and an inline `anti_patterns` key in a styleguide or procedure (`artifact_schema_error`).
- **`validate --full`** adds the check `REFERENCES`: the substep delegations, references and step enhancements'
  targets of each pack's own closure, with registration's codes.
- **`validate --report <file>`** writes the report as YAML, or as XML with `--format xml`. Standard output gains
  `tool`, `inputs` and `summary`.
- **`remediate`** rewrites a pack in canonical form and restamps its manifest.
- **`EXCLUDED_WRITTEN` is retired** in the round trip's detail, with no alias: it is `NOT_HELD_WRITTEN`.

## What to do

Repin to the first `service-v*` release that carries pack-doctor. Run `remediate` over a pack whose manifest is stale,
curate inline anti-patterns into files, then gate on `validate --full`. See [Check packs](/guides/check-packs/).
