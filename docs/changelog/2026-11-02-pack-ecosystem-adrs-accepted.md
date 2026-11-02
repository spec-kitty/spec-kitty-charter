---
okf_version: "0.2"
title: Pack ecosystem rulings accepted as ADR-0048 to ADR-0052
description: "The operator rulings of 2026-11-02 on epic #142 are formalized as five Accepted ADRs, with a work breakdown, glossary terms and notes on the current-state pages."
doc_status: active
updated: 2026-11-02T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0049-glossary-contexts-and-terms
  - adr/0050-mission-types-and-lifecycles-as-declared-state-machines
  - adr/0051-event-registry-a-thin-provider-neutral-consistency-check
  - adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts
  - plans/pack-ecosystem-work-breakdown
audience:
  - profile: governance-lead
    guidance_text: Read this for which pack-ecosystem decisions are taken, which are still open, and that no code has changed.
---

# 2026-11-02 — Pack ecosystem rulings accepted as ADR-0048 to ADR-0052

Epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142).

**The operator's rulings of the 2026-11-02 design session are now Accepted decisions.** Nothing in the schemas, the
contract or the service changes yet; the open questions in each record still await a ruling.

**Before:**

- The rulings were recorded only on the tracker (the rulings comment on #145), and no ADR or plan page held them.

**Now** (no schema, contract or service code changes):

- [ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/): pack composition and the named-packs split,
  with the census and recommended defaults.
- [ADR-0049](/adr/0049-glossary-contexts-and-terms/): glossary contexts and terms.
- [ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/): mission types and lifecycles as
  declared state machines, guards and the `gate` kind.
- [ADR-0051](/adr/0051-event-registry-a-thin-provider-neutral-consistency-check/): the thin event registry.
- [ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/): procedures tightened,
  anti-patterns as authored artifacts.
- The older records they change in part (ADR-0017, 0019, 0020, 0033, 0037, 0039, 0042, 0043 and 0044) carry a
  "superseded in part" note, and the current-state architecture pages point to the new design where the code still
  differs.
- The glossary gains the new terms, and the [work breakdown](/plans/pack-ecosystem-work-breakdown/) orders the
  charter, open-packs and core work.
