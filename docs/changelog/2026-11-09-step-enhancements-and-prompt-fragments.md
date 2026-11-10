---
okf_version: "0.2"
title: Step enhancements and prompt fragments are held, checked at load, and selectable
description: The service holds step enhancements and their prompt fragments, refuses invalid ones and malformed slot markers with coded errors, and lists warnings in registration diagnostics.
doc_status: active
updated: 2026-11-09T00:00:00Z
type: Changelog
kind: Changelog
related:
  - architecture/composition-and-activation-rules
  - architecture/pack-schemas/step-enhancement
  - architecture/pack-schemas/prompt-fragment
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0066-a-step-enhancement-may-contribute-prompt-fragments
audience:
  - profile: pack-author
    guidance_text: Read this if a pack with step enhancements or step prompts is now refused, or a registration result carries diagnostics.
---

# 2026-11-09 — Step enhancements and prompt fragments

**The service now holds step enhancements and checks them** ([#428](https://github.com/spec-kitty/spec-kitty-charter/issues/428),
[#424](https://github.com/spec-kitty/spec-kitty-charter/issues/424)). Before, a load ignored the files and a
registration refused them `422` as unmodelled.

- **Held and selectable.** A step enhancement and its `*.prompt-fragment.md` companions register, round-trip and are
  selectable like any artifact, also through a preset.
- **Checked at load.** A bad enhancement or fragment is refused `prompt_fragment_invalid`, a malformed slot marker
  `prompt_slot_malformed`, and text in a slot `prompt_slot_not_empty` when the descriptor declares
  `schema_version: "1.0"`. A registration answers `422 validation_failed` with one violation per fault; a read answers
  `500 pack_unreadable` naming the first file. An edge write that breaks the rules answers `422 prompt_fragment_invalid`.
- **Every graph fault is listed.** A refused registration lists each graph fault at its file; the pointer moves from
  the pack to the file.
- **Registration `diagnostics`.** A succeeded registration lists the `prompt_slot_not_empty` warning of an unversioned
  descriptor. At the pin, the built-in pack loads with five, one per software-dev step prompt (open-packs#175).
- **Breaking, sibling conflict.** Two packs at one level that define the same step enhancement id are refused
  `500 pack_sibling_conflict` on every composing read. A descriptor `overrides` entry settles it; a higher level
  replaces the lower.
- **Raised definition.** A violation code of a `422 validation_failed` counts as raised, `drg_schema_error` too.

Rules: [Load rules](/architecture/composition-and-activation-rules/#load-rules). Rulings:
[ADR-0053 question 2](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) (layout) and
[ADR-0066 annotations](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/#annotations) (guards and emits,
load codes). Compose checks and the effective-prompt read are planned in [#438](https://github.com/spec-kitty/spec-kitty-charter/issues/438).
