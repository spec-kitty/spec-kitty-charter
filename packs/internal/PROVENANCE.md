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

Re-vendored from `spec-kitty-open-packs` `packs/internal` at commit `5df92a5d02e47e8877364de5e2deb06c016d30a9` (main, after PR #91 landed: procedures and toolguide guides in the charter's sanitized shapes) on 2026-10-29. This file and `README.md` are local and differ from upstream on purpose. One more file differs from upstream at that commit, and everything else in this directory is the upstream copy:

- `assets/validate-pr-body.py`: the closing-link pattern also accepts GitHub's colon form (`Closes: #5`, `\s*:?`), the edit this repository's `main` made in commit `dd86daa`. Taking it upstream is tracked as [spec-kitty/spec-kitty-open-packs#92](https://github.com/spec-kitty/spec-kitty-open-packs/issues/92).

The earlier local edit of commit `19cc37f` went upstream in [spec-kitty/spec-kitty-open-packs#59](https://github.com/spec-kitty/spec-kitty-open-packs/pull/59); `assets/validate-pr-body.py.asset.yaml`, `drg/fragment.yaml` and `procedures/semi-auto-delivery.procedure.yaml` are the upstream copy again at this commit. When the colon form lands upstream, `openPacksRef` in `gradle.properties` moves to a commit that has it and this list empties. Until then `VendoredPacksTest` (run by `./gradlew :packs:openPacksRoundTrip`) fails on any other difference from the pinned checkout, and on a listed file that has become identical, so this list cannot go stale (ruling R21).

The commit is pinned by `openPacksRef` in `gradle.properties`.
