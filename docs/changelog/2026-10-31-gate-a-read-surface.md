---
okf_version: "0.2"
title: The API has a root, an offering and one artifact tree, and every answer links onward
description: The contract adds GET / and GET /offering, reads and writes every served kind under /packs/{packId}/artifacts, and removes the per-kind routes.
doc_status: active
updated: 2026-10-31T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0019-packs-resource-model-and-hypermedia
  - adr/0026-write-surface-is-functional-registration
  - adr/0018-action-context-read-and-seam-access-surface
  - api/service-contract
audience:
  - profile: core-maintainer
    guidance_text: Read this before generating core's client from the contract; the per-kind routes are gone without an alias.
  - profile: contract-consumer
    guidance_text: Read this for the new entry point, the artifact envelope and where the fingerprint is sent.
  - profile: charter-service-contributor
    guidance_text: Read this for the new operations, the lint rules and the scenario hooks that check every link.
---

# 2026-10-31 — The API has a root, an offering and one artifact tree, and every answer links onward

**A client needs one address.** The contract adds the API root, `GET /api/v1/`, for
[#126](https://github.com/spec-kitty/spec-kitty-charter/issues/126). Every JSON success answer carries a required
`_links`, and every link points to a built operation
([ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/)), never to a reserved activation operation
(ADR-0020 D8). `GET /offering` lists the configured packs in the
merge order, each linked to its artifacts; merging itself is not applied yet.

**One tree for every served kind.** Tactics, directives, styleguides, paradigms and toolguides are read at
`GET /packs/{packId}/artifacts` (paged, filtered by `kind`) and `GET /packs/{packId}/artifacts/{kind}/{artifactId}`,
which answers a common envelope. The eight registrations move into the same tree with their operationIds
([ADR-0026](/adr/0026-write-surface-is-functional-registration/) D1.2, amended). The per-kind routes are removed
without an alias. An unserved path kind is the new refusal `kind_not_found`.

**Headers and refusals say what the service does.** `Charter-Source-Fingerprint` is declared, and required, only
on the 200 of a verified read ([#175](https://github.com/spec-kitty/spec-kitty-charter/issues/175),
[ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/) as amended). Refusal examples name only raised
codes ([#176](https://github.com/spec-kitty/spec-kitty-charter/issues/176)). By an operator ruling of the same day,
a configured pack without a readable `pack.yaml` is a 500 `pack_unreadable`, and 404 `pack_not_found` means no
configured pack has that id.

The contract stays the unreleased `0.1.0-SNAPSHOT`; the full list, scenarios included, is under `### Breaking`
in the contract [CHANGELOG](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/CHANGELOG.md).
