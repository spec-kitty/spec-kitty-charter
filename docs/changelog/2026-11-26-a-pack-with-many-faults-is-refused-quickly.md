---
okf_version: "0.2"
title: A pack with many faults is refused in seconds, not minutes
description: Checking a pack that holds tens of thousands of faults no longer stalls pack-doctor validate or a whole-pack registration; the faults reported are unchanged.
doc_status: active
updated: 2026-11-26T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/check-packs
  - changelog/2026-11-22-pack-doctor
audience:
  - profile: pack-author
    guidance_text: Read this if pack-doctor validate or a pack upload hung on a large, broken pack.
---

# 2026-11-26 — A pack with many faults is refused in seconds, not minutes

**A large broken pack no longer stalls the check** (#648, #669). A pack within the size cap that held tens of thousands of
faults kept `pack-doctor validate`, or the request thread of a whole-pack registration, busy for tens of seconds to
about a minute before it answered. Each fault was compared against every other fault, so the cost grew with the square of
their number.

## What changed

Each step of the check now looks faults up through an index built once. A pack with 20 000 faults is refused in about a
second, where `validate` took 44 s before. A pack that has 14 000 self-joins beside four files of 32 000 steps each is
refused in a few seconds, where it took about 50 s (46–53 s) before. What time remains is spent reading the YAML, the same
as for a valid pack of that size.

## What did not change

The refusal is the same: the same violations, in the same order and with the same wording, so the first 100 listed are
unchanged. The contract and the pack schemas are unchanged.

See [Check packs](/guides/check-packs/).
