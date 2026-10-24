---
title: Every read answers with the source fingerprint it verified
description: The source fingerprint covers every pack file the service loads, from one file list shared with the load; the tactic reads verify it and send it on their 200.
doc_status: active
updated: 2026-10-24T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
  - adr/0018-action-context-read-and-seam-access-surface
  - architecture/service-lifecycle
  - api/service-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the one pack file list and the verified-read rule every new artifact read must use.
  - profile: core-maintainer
    guidance_text: Read this for the fingerprint the tactic reads now send and the refusals that changed.
---

# 2026-10-24 — Every read answers with the source fingerprint it verified

Core's seam checks the source fingerprint to know whether an answer is fresh. The tactic reads
did not send it, and the fingerprint did not cover the tactic, directive, styleguide and paradigm
files the service loads. Both gaps are closed ([#120](https://github.com/spec-kitty/spec-kitty-charter/issues/120),
[#173](https://github.com/spec-kitty/spec-kitty-charter/issues/173)).

## What changed

- **One pack file list.** A single declaration in the storage ring names the kinds the service
  loads and the graph inputs it digests. The load and the fingerprint both take their files from
  it, so a kind cannot be loaded without being digested. A generated-tree test observes the load
  and the digest independently and fails when one kind is left out.
- **Verified reads.** `/info`, `listPackTactics` and `getPackTactic` digest their inputs before and
  after loading. A 200 carries the digest it verified as `Charter-Source-Fingerprint`, once, equal
  to `/info`'s `sourceFingerprint`. A read whose inputs changed meanwhile is refused with
  `stale_input`, also when the load itself refused.
- **Records.** ADR-0018 gains a dated amendment to D8 and D11; the freshness page, the service
  contract page and the contract descriptions use one definition of the fingerprint.

## Who is affected

- **Core's seam.** The tactic reads now send the fingerprint and can answer `stale_input`, which
  may be retried. A configured pack whose directory is missing, a pack holding a file the
  fingerprint covers that cannot be read, or a pack directory that cannot be listed, refuses every
  read with 500 `pack_unreadable`, `/info` included; a tactic read of a missing pack directory
  answered 404 `pack_not_found` before. A file that vanishes while a pack is walked, as in an
  atomic save, is not an error. The fingerprint's value changes for packs that hold
  tactic, directive, styleguide or paradigm files. No contract shape changed.
- **Contributors.** A new artifact read goes through the shared verified read, and a new pack
  file kind is declared in the one file list.
