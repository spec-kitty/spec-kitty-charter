---
okf_version: "0.2"
title: Doctrine is retired as a term; the glossary and docs say charter
description: The glossary and docs retire "doctrine" as a Spec Kitty term — Charter Domain, Charter Reference Graph (CRG) and charter artifact replace the doctrine-era names.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: Changelog
kind: Changelog
related:
  - glossary/charter
  - context/domain
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the retired doctrine vocabulary and the charter terms that replace it.
  - profile: pack-author
    guidance_text: Read this for the glossary terms that changed name — Charter Domain and Charter Reference Graph (CRG).
---

# 2026-10-27 — Doctrine is retired as a term; the glossary and docs say charter

**The published glossary and docs no longer name governance objects "doctrine".** Spec Kitty's core retired the word as a term (core `retire-doctrine-term`, [#3664](https://github.com/spec-kitty/spec-kitty/issues/3664)): `charter` is the one canonical name for the governance layer, and the parallel "doctrine" vocabulary is gone. This repository follows suit across the glossary and docs.

What changed name: the glossary term **Doctrine Domain → Charter Domain** (anchor `#charter-domain`), **Doctrine Reference Graph (DRG) → Charter Reference Graph (CRG)**, and "doctrine artifact / kind / pack / catalog" → the charter equivalents. The `terms` gate (`docs/_meta/terminology.yaml`) now refuses the retired spellings so they cannot creep back in prose.

What did **not** change: the word survives only in its plain-English sense, and literal code symbols that still carry the old name — `DoctrineGraph`, the `charter.drg` / `offering/drg` paths, `drg/fragment.yaml`, `drg_schema_error` — stay verbatim until the code and open-packs cutover renames them. The docs reference the code that exists today.
