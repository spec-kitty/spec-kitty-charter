---
okf_version: "0.2"
title: pack-check is pack-doctor, and registration and pack-doctor give one answer
description: The checker is pack-doctor, with validate and remediate; registration and pack-doctor refuse preset items, stale manifest hashes and inline anti-patterns alike.
doc_status: active
updated: 2026-11-23T00:00:00Z
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

- **The checker is pack-doctor**, with no alias, and takes a command first:
  `java -jar pack-doctor-<v>.jar validate --config-root x` replaces `java -jar pack-check-<v>.jar --config-root x`.
- **Newly refused, on every surface:**
  - a preset item its own closure lacks: `preset_item_not_held` or `preset_item_undeclared`;
  - a stale `content_hash` or `manifest_hash`: `manifest_stale`. Every accepted write restamps the manifest;
  - an invalid anti-pattern file, an `anti_pattern` node with no file, and an inline `anti_patterns` key;
  - a special file such as a FIFO, and a YAML document over 1 048 576 code points: `unreadable`.
- **The case-only answer has registration's scope:** a dependency's item or lock that a pack depending on it holds
  only by case is refused when you check them together.
- **`validate --full`** adds `REFERENCES`, registration's delegation, reference and enhancement-target checks. Only a
  closure that does not compose stops it.
- **`validate --report <file>`** writes the report as YAML or XML, outside the packs, in one rename; the XML escapes
  what XML 1.0 cannot carry. Standard output gains `tool`, `inputs` and `summary`.
- **`remediate`** rewrites a pack in canonical form and restamps its manifest; a canonical pack needs no write access.
- **The round trip never crashes on a file**, and `EXCLUDED_WRITTEN` is `NOT_HELD_WRITTEN`, with no alias.

## What to do

Repin to the first `service-v*` release that carries pack-doctor. Run `remediate` over a pack whose manifest is stale,
curate inline anti-patterns into files, then gate on `validate --full`. See [Check packs](/guides/check-packs/).
