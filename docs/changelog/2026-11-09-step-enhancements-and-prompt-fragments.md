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
[#424](https://github.com/spec-kitty/spec-kitty-charter/issues/424)). Before, it dropped them.

- **Held and selectable.** A step enhancement and its `*.prompt-fragment.md` companions register, round-trip and are
  selectable like any artifact, directly and through a preset.
- **Checked at load.** A bad enhancement or fragment is refused `prompt_fragment_invalid`, a malformed slot marker
  `prompt_slot_malformed`, and text in a slot `prompt_slot_not_empty` when the descriptor declares
  `schema_version: "1.0"`. A registration answers `422 validation_failed` with one violation per fault; a read answers
  `500 pack_unreadable` naming the first file. An edge write that breaks the rules answers `422 prompt_fragment_invalid`.
- **Registration `diagnostics`.** A succeeded registration lists the `prompt_slot_not_empty` warning of an unversioned
  descriptor. The five pinned built-in prompts give five.
- **Breaking, sibling conflict.** Two packs at one level that define the same step enhancement id are refused
  `500 pack_sibling_conflict` on every composing read. A descriptor `overrides` entry settles it, and a higher level
  replaces the lower by layer override.
- **Raised definition.** A code counts as raised when it is a violation code of a `422 validation_failed`, so
  `drg_schema_error` is raised too.
- **Id caps.** See [pack id length caps](/changelog/2026-11-09-pack-id-length-caps/).

Rules: [Load rules](/architecture/composition-and-activation-rules/#load-rules). Rulings:
[ADR-0053 question 2](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) (layout) and
[ADR-0066 annotations](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/#annotations) (guards and emits,
load codes). Compose checks and the effective-prompt read are planned in [#438](https://github.com/spec-kitty/spec-kitty-charter/issues/438).
