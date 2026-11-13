---
okf_version: "0.2"
title: Presets, assets and skills are served
description: Presets, assets with their payload, and skills are read in a pack and under an activation; references get their activation twin; an activation links its writes and reads.
doc_status: active
updated: 2026-11-13T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0074-the-remaining-cutover-reads-and-their-paths
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - api/service-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the reads slice A2 added, the new edge module, and the breaking contract entries.
  - profile: core-maintainer
    guidance_text: Read this for the new operations, the asset id rule, and the effective ETag version.
---

# 2026-11-13: Presets, assets and skills are served

Slice A2 ([#536](https://github.com/spec-kitty/spec-kitty-charter/issues/536)), built by the paths of
[ADR-0074](/adr/0074-the-remaining-cutover-reads-and-their-paths/) decisions 9 to 14. The glossary read (#106) is a
mission of its own.

- **Presets** (#98): `listPackPresets` and `getPackPreset`, in a pack only. A pack links `presets`, and
  `preset_not_found` covers a pack too.
- **Assets** (#105):
  - `listPackAssets` and `getPackAsset`.
  - `getPackAssetPayload`: the exact bytes, with the asset's mime as `Content-Type` when it is well-formed and
    `application/octet-stream` otherwise, plus `X-Content-Type-Options: nosniff` and
    `Content-Security-Policy: sandbox; default-src 'none'`.
  - An asset id is now a kebab slug of at most 64 characters (breaking).
- **Skills** (#107): `listPackSkills` and `getPackSkill`. A prompt skill's body is answered verbatim.
- **Under an activation:**
  - **Assets:** the asset and skill reads have twins. An asset is in force when a pack of the closure holds it, the
    layer merge picks the copy, and its payload is read against the session fingerprint. `EffectiveAssetRead` carries
    no state. The ADR-0074 amendment of 2026-11-12 records this; it is open for operator review.
  - **References:** `listEffectiveArtifactReferences` (#532) answers the pack read of the winner's pack.
  - **ETag:** the effective ETag recipe is now v3.
- **Activation links** (#409): an activation links `offering`, `edit`, `delete`, `select`, `exclude`,
  `apply-preset`, `packs`, `graph`, `skipped`, `effective-context`, `assets`, `asset` and `skills`.
- **A new edge module, `:artifacts-edge`**, holds the asset, skill and references reads (ADR-0037 amendment of
  2026-11-13).
- **ETag offering:** `PackDocumentETag` becomes the shared `DeferredETag` in web-transport.
