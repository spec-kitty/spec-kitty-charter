---
okf_version: "0.2"
title: A step enhancement may contribute prompt fragments
description: ADR-0066 lets an enhancement pack put text into named slots of a base mission's step prompts, which lets the SPDD blocks move out of the built-in pack. Sibling enhancers compose.
doc_status: active
updated: 2026-11-08T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0066-a-step-enhancement-may-contribute-prompt-fragments
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0050-mission-types-and-lifecycles-as-declared-state-machines
  - adr/0064-graph-documents-are-the-only-source-of-relationships
  - api/service-contract
audience:
  - profile: pack-author
    guidance_text: Read this if you write a step enhancement or a mission-type step prompt.
  - profile: charter-service-contributor
    guidance_text: Read this for the validation and read work the new record adds.
---

# 2026-11-08: A step enhancement may contribute prompt fragments

**The operator ruled that the `spdd:reasons-block` text in the built-in `software-dev` step prompts moves into the
opt-in `spdd` pack** ([spec-kitty-open-packs#73](https://github.com/spec-kitty/spec-kitty-open-packs/issues/73),
from [#111](https://github.com/spec-kitty/spec-kitty-charter/issues/111)). ADR-0053 decision 22 let an enhancement
add only guards and emits, so there was no route. The rules are a new record,
[ADR-0066](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/); ADR-0053 and ADR-0050 annotate decision 22.
The operator also ruled that sibling step enhancers compose and sibling overrides are refused (ADR-0053 decision 21).

For pack authors:

- **A base prompt declares a slot** as an empty pair of markers, `<!-- <namespace>:<name>:start -->` and
  `<!-- <namespace>:<name>:end -->`. The marker syntax matches the markers the `software-dev` prompts carry today, but
  those five blocks hold text. The move empties them; until then they would fail `prompt_slot_not_empty`, so a
  descriptor version gate switches the check on with the move.
- **An enhancement adds a fragment to a slot** of the step it enhances, as a Markdown companion. It is content and
  not an edge; the `enhances` edge stays in the enhancing pack's graph documents
  ([ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/)).
- **A fragment shows only while its enhancement is active.** Several into one slot compose, in a fixed order.
- **A fragment cannot replace or delete base text, add a slot, carry frontmatter, or enhance a locked step.**

For the charter and its clients: the charter will validate slots, fragments and the slot a compose names, and the
effective-prompt read will return the base text and the active fragments per slot, for core's renderers to place.
The schema and codes come with the schema work. This change touches no contract file and no schema. The order of
work is this record, core support, then moving the blocks in open-packs.
