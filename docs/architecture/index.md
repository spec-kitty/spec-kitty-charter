---
title: Architecture
description: Explanation of why the charter offering module exists, its public-contract boundary, and placeholder C4-style container notes for the module.
doc_status: draft
updated: '2026-10-13'
related:
- docs/index.md
- docs/api/index.md
---

# Architecture

This section is the living architectural design for the charter offering
module. It is unversioned: it describes the module as it is intended to be, and
is curated in place rather than snapshotted per release. Point-in-time analyses
and dated decisions belong under decision records, not here.

## Why this module exists

The charter is the governance and doctrine authority for the Brain Kitty product
line. It is being extracted out of the Spec Kitty core CLI so it can be released
and versioned as an independent contract package — the same shape
`spec-kitty-events` and `spec-kitty-tracker` already have. Extracting it lets
open-core consumers depend on the charter directly, at its own version, without
pulling in the whole CLI.

## Boundary

The module is consumed **only** through its public surface
`charter.offering.api`. Callers never reach into its internals. This single
public-surface rule is the invariant the extraction exists to harden.

## Container notes (C4) — placeholder

> **To be written.** This page will carry C4-style container and component
> notes for the module: the charter-pack retrieval container, its dependencies
> (it depends only on foundation primitives), and how the Build Kitty surfaces
> consume it across the `charter.offering.api` contract. A container diagram
> will accompany the notes once the extracted shape is settled.
