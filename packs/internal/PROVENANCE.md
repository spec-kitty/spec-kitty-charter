# Provenance: spec-kitty-internal Charter Pack

This is the in-house (org-tier) **internal** Charter Pack, vendored into
`spec-kitty-charter` and loaded via `.kittify/config.yaml` →
`charter_packs.org.packs`.

## Identity and guards are owned upstream

`pack.yaml` pins a canonical `pack_id`. The single minting authority for the
`spec-kitty-internal` identity — including that `pack_id` and its drift and
packaging guards — is `spec-kitty-open-packs`. This repository holds a
**vendored mirror**: it does not re-mint the id and does not reproduce those
guards here, so the descriptor must not be read as asserting tests or packaging
rules that run in this repository.

## Keeping the mirror in sync

Homing the canonical pack upstream in `spec-kitty-open-packs` and re-vendoring it
here — with the `pack_id` matching upstream exactly, never diverged — is tracked
by #37, to be done before the pack is put in force (synthesis + per-entry
activation, tracked by #32).

## Vendored copy

Re-vendored from `spec-kitty-open-packs` `packs/internal` at commit `01edb4ebab308249a10ed4a4a01d77741ac1d14b` (main, after PR #52 landed: URN-only fragment endpoints) on 2026-10-19. This file and `README.md` are local and differ from upstream on purpose; everything else in this directory is the upstream copy. The commit is pinned by `openPacksRef` in `gradle.properties`.
