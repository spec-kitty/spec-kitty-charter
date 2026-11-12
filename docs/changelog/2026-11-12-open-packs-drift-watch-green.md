---
okf_version: "0.2"
title: The open-packs drift watch turns green, and the open-packs pin moves to the slice B packs
description: The round trip writes an org pack's drg/fragment.yaml, a fragment edge keeps its when, and openPacksRef moves to the open-packs commit where every pack registers as it is.
doc_status: active
updated: 2026-11-12T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0070-charter-tests-own-example-packs-not-open-packs-content
  - adr/0073-real-packs-register-as-they-are
  - plans/next-slices-2026-11-12
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you touch the whole-pack round trip, the graph writers or the open-packs pin.
---

# 2026-11-12 — The open-packs drift watch turns green

**Slice B's deferred proof** ([#466](https://github.com/spec-kitty/spec-kitty-charter/issues/466)). With open-packs #248
merged (#241 and #242), every open-packs pack loads, writes and reads back through charter's loader, and the nightly
drift watch ([#509](https://github.com/spec-kitty/spec-kitty-charter/issues/509)) has nothing left to report.

- **The round trip writes an org fragment.** `drg/fragment.yaml` (gate kind `org-fragment`) was not a modelled kind, so
  the round trip never wrote it. An org pack's step enhancements take their `enhances` edge from that fragment, so the
  written `spdd` pack read back with none, and all five of its enhancements were refused. The round trip now writes the
  fragment through `GraphShardWriter.writeFragment` before the read-back, as it writes the `*.graph.yaml` shards, and
  compares it by meaning.
- **A fragment edge keeps its `when`.** The org-fragment schema's edge (`_OrgDRGEdge`) declares `when`, and open-packs'
  `spdd` fragment carries one, but `writeFragment` refused it. It now writes it, and the edge key order the reader
  reports a finding by follows the schema. A fragment node still refuses provenance and tags, which the schema's node
  does not hold.
- **The gate covers the org example pack.** `theOrgPackRoundTripsWholeWithItsFragment` round-trips `org-base`, which
  carries the fragment the built-in pack has no file of. The fragment's one `impacts` edge is still red, because a
  domain `Relationship` carries no strength:
  [#477](https://github.com/spec-kitty/spec-kitty-charter/issues/477) owns that, and the test expects exactly that red.
  No open-packs pack has an `impacts` edge.
- **The pin.** `openPacksRef` moves from `a2a9379` to open-packs `1692a3b`, the `main` commit that carries #248, and
  `packs/open-packs.lock.yaml` is re-synced, so `packs/internal` and `packs/qa` arrive without `org-charter.yaml`.
