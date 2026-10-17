# Provenance: spec-kitty-internal Charter Pack

This is the in-house (org-tier) **internal** Charter Pack, vendored into
`spec-kitty-charter` and loaded via `.kittify/config.yaml` →
`charter_packs.org.packs`.

## Identity and guards are owned upstream

`pack.yaml` pins a canonical `pack_id`. That identity was minted in the
originating Spec Kitty pack source, which also owns the drift and packaging
guards for it. This repository holds a **vendored mirror**: it does not re-mint
the id and does not reproduce those guards here, so the descriptor must not be
read as asserting tests or packaging rules that run in this repository.

## Open: authoritative ownership

Which repository is the single minting authority for the `spec-kitty-internal`
identity — the originating source or this one — is not yet settled. It is
tracked as a landing follow-up (PR #35) and should be fixed by an ADR or an
open question before the pack is put in force (synthesis + per-entry activation,
tracked by #32).
