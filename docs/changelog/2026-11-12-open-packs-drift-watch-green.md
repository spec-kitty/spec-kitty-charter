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
  - plans/engineering-notes/service-usability-run-2026-11-12-slice-b-pin
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you touch the whole-pack round trip, the graph writers or the open-packs pin.
---

# 2026-11-12 — The open-packs drift watch turns green

**Slice B's deferred proof** ([#466](https://github.com/spec-kitty/spec-kitty-charter/issues/466)). With open-packs #248
merged, every open-packs pack loads, writes and reads back through charter's loader, and the nightly drift watch
([#509](https://github.com/spec-kitty/spec-kitty-charter/issues/509)) has nothing left to report.

- **The round trip writes an org fragment.** `drg/fragment.yaml` (gate kind `org-fragment`) was not a modelled kind, so
  the round trip never wrote it, so the written `spdd` pack read back with none of its five step enhancements. The
  round trip now writes it through `GraphShardWriter.writeFragment` and compares it by meaning.
- **A fragment edge keeps its `when`.** The org-fragment schema's edge declares `when`, and open-packs' `spdd` fragment
  carries one, but `writeFragment` refused it. It now writes it. A fragment node still refuses provenance and tags.
- **The gate covers the org example pack**, which carries the fragment `built-in` has none of. Its one `impacts` edge
  stays red, since a `Relationship` carries no strength
  ([#477](https://github.com/spec-kitty/spec-kitty-charter/issues/477)).
- **The pin.** `openPacksRef` moves from `a2a9379` to open-packs `1692a3b`, which carries #248, and
  `packs/open-packs.lock.yaml` is re-synced.
- **The usability run follows open-packs' split `built-in`**: 146 of 146 hops, and `internal` and `qa` register as they
  are ([run record](/plans/engineering-notes/service-usability-run-2026-11-12-slice-b-pin/)).
- **After #542, the watch is red until open-packs regenerates three manifests.** Once
  [#542](https://github.com/spec-kitty/spec-kitty-charter/issues/542) (manifest membership derived and checked) merges,
  the drift watch at `1692a3b` refuses the hand-written manifests of open-packs `built-in` (it leaves out its 2
  presets), `foundation` (31 constituents) and `writing` (21 constituents), as `:packs:driftTest` with #542 merged
  confirms. The watch goes green again when
  [spec-kitty-open-packs#250](https://github.com/spec-kitty/spec-kitty-open-packs/issues/250) regenerates them and
  `openPacksRef` is raised.
