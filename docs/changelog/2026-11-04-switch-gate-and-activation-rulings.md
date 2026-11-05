---
okf_version: "0.2"
title: The design's BDD scenarios and contract tests gate each core switch
description: ADR-0059 records the operator's rulings of 2026-11-04 on the switch gate, the cascade edges, the layer-override grain and ADR-0020's open questions 4 to 7.
doc_status: active
updated: 2026-11-04T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0059-switch-gate-cascade-edges-and-activation-rulings
  - adr/0055-cutover-sequencing-the-java-service-first
  - adr/0020-offering-and-activations
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before building the activation writes, the cascade or the layer merge.
---

# 2026-11-04 — The design's BDD scenarios and contract tests gate each core switch

**[ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) records six operator rulings** that
clear the way for the activation writes ([#291](https://github.com/spec-kitty/spec-kitty-charter/issues/291)) and
the resolution domain ([#10](https://github.com/spec-kitty/spec-kitty-charter/issues/10),
[#11](https://github.com/spec-kitty/spec-kitty-charter/issues/11)):

- An operation is ready for core to switch to it when its design-derived BDD scenarios and contract tests pass. No corpus, shadow or
  observation period gates the switch (ADR-0055 open question 1).
- The cascade follows `requires`, `suggests`, `refines`, `scope` and `instantiates`, brings an artifact's enhancers
  in with it, and passes through `specializes_from` without putting the parent in force.
- A layer override takes the place of the whole artifact; there is no field merge.
- A document a pack upgrade made stale is served and flagged; writing it back unchanged is refused.
- The `Activation` answer's `ETag` is its revision: `summary` moves to an effective read and `governanceFingerprint`
  leaves `Activation`. `getActivation` and the four effective reads are to declare `If-None-Match` and `304` in
  the contract change.
- A preset replaces each key it lists.

ADR-0010, ADR-0013, ADR-0020, ADR-0021 and ADR-0055 point to the rulings, and the logical model and the glossary
use them.
