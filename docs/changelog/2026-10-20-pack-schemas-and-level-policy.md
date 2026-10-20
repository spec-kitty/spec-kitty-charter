---
title: Packs are validated against the canonical pack schemas, and the domain owns the pack-level policy
description: Every pack file is validated against the canonical pack schemas, the domain judges each finding, and a validate mode collects them all.
doc_status: active
updated: 2026-10-20T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0017-pack-schemas-imported-as-canonical-json-schema
  - adr/0015-charter-repository-is-canonical-for-charter-structure
  - conformance/divergence-register
  - architecture/descriptor-schemas
  - api/service-contract
audience:
  - profile: core-maintainer
    guidance_text: Read this for the pack schemas core now consumes and the pack shapes the service refuses.
  - profile: pack-author
    guidance_text: Read this for which pack.yaml, shard and fragment shapes are now refused, and why.
  - profile: charter-service-contributor
    guidance_text: Read this for where pack findings are judged and how validate mode differs from loading.
---

# 2026-10-20 — Packs are validated against the canonical pack schemas, and the domain owns the pack-level policy

This change closes #48 and #114. The decisions are in
[ADR-0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/) and its amendments.

## What changed

- **The pack schemas are canonical here.** `contracts/packs/schemas/` holds 14 JSON Schema
  documents, imported once from core and edited here from now on; core consumes them.
- **The YAML adapter validates every pack file against them,** with networknt
  json-schema-validator, offline.
- **The domain judges every finding.** The adapter reports what a file says; `PackPolicy`
  chooses each code and text, including both pack-level outcomes (#48).
- **A validate mode collects every finding.** `PackValidation` reads all of a pack's files and
  returns every finding; loading stays fail-fast. The pack doctor parity work (#115) builds on it.
- **Rules a schema cannot express are domain rules:** `requires_core` as a PEP 440 range,
  duplicate step and artifact ids, asset paths that leave the pack, and asset MIME types.
- **Some pack shapes are now refused.** Among them: edge endpoints that are not URNs, fragment
  header fields, an explicit `null` in a `pack.yaml` string field, and shards or fragments that are
  not valid YAML (now `drg_schema_error`). Unknown `pack.yaml` keys are a new `pack_unknown_key`
  warning. The contract `CHANGELOG.md` lists every change with its ruling.
- **Open-packs is pinned and checked in full:** all 516 YAML files at `openPacksRef` are
  classified, 409 validated and 107 reviewed exclusions.
- **The divergence register gains the pack-schemas block** (DIV-001..025), and
  `divergences.yaml` is its machine-readable form.

The [descriptor schemas](/architecture/descriptor-schemas/) page has the detail.
