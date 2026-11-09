---
okf_version: "0.2"
title: Prompt-fragment and slot contracts for step enhancements
description: The pack schemas now define ADR-0066 prompt fragments, slot rules, their descriptor version gate and activation vocabulary; service loading and composition remain follow-up work.
doc_status: active
updated: 2026-11-08T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0066-a-step-enhancement-may-contribute-prompt-fragments
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - api/service-contract
  - changelog/2026-11-08-enhancements-contribute-prompt-fragments
audience:
  - profile: pack-author
    guidance_text: Read this for the step-enhancement and prompt-fragment file shapes, slot grammar and descriptor version gate.
  - profile: core-maintainer
    guidance_text: Read this before consuming step enhancements or the reserved prompt-slot refusal codes.
  - profile: charter-service-contributor
    guidance_text: Read this for the contract boundary; #424 implements the raw-text checks, compose validation and effective-prompt read.
---

# 2026-11-08: Prompt-fragment and slot contracts for step enhancements

The pack contracts now hold the schema part of
[ADR-0066](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/), so
[spec-kitty-open-packs#175](https://github.com/spec-kitty/spec-kitty-open-packs/pull/175) can validate its SPDD move:

- `missions/<type>/enhancements/<id>.step-enhancement.yaml` holds `prompt_fragments: [{slot, path}]`; a slot is
  `<namespace>:<name>`, each part matching `[a-z][a-z0-9-]*`.
- The companion is `<id>.prompt-fragment.md` beside its enhancement, with the new `prompt-fragment` Markdown
  structure schema: non-blank content, no frontmatter and, by the raw-text rule recorded beside the schema, no slot
  markers.
- `mission-step-prompt` records the slot-marker rules. Descriptor `schema_version: "1.0"` enforces empty slots; an
  unversioned descriptor keeps legacy text with a warning.
- Graph documents use `step_enhancement` / `step_enhancements`; presets use `activated_step_enhancements`, selected
  as `step_enhancement` in an activation.
- The reserved codes are `prompt_slot_malformed`, `prompt_slot_not_empty`, `prompt_fragment_invalid` and
  `prompt_slot_missing`. Existing pack-reference and integrity codes cover the remaining refusals.

[The pack-schema reference](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/packs/README.md)
is the authoritative field and layout description. The raw Markdown checks, compose validation
and effective-prompt read are [#424](https://github.com/spec-kitty/spec-kitty-charter/issues/424). Until then the
service validates step-enhancement and fragment files but stores neither, so a step enhancement cannot be selected
yet; guard and emit additions are [#428](https://github.com/spec-kitty/spec-kitty-charter/issues/428).
The activation kind and refusal-code enums advance on the 0.x line, so consumers regenerate their DTOs.
