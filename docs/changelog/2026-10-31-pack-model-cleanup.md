---
okf_version: "0.2"
title: The Markdown rules have one home, companions are classified case-folded, and a step's output template is resolved
description: Guide and pack document check Markdown when built; case-folded companions; one not-UTF-8 wording; a step's template_path resolves to its template.
doc_status: active
updated: 2026-10-31T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0039-a-kind-binding-can-carry-companion-files
  - adr/0044-mission-types-consolidated-into-one-sanitized-shape
  - changelog/2026-10-30-pack-document-round-trip
  - changelog/2026-10-30-mission-types-consolidated
audience:
  - profile: pack-author
    guidance_text: Read this for the companion paths and output templates the load now refuses.
  - profile: charter-service-contributor
    guidance_text: Read this for where the Markdown model's rules live, the shared UTF-8 wording and how a template path is resolved.
---

# 2026-10-31 — The Markdown rules have one home, companions are classified case-folded, and a step's output template is resolved

Follow-ups of [the pack document round trip](/changelog/2026-10-30-pack-document-round-trip/) and
[the mission-type consolidation](/changelog/2026-10-30-mission-types-consolidated/). Nothing at the
open-packs pin is refused by any of them.

**The Markdown model's rules have one home**
([#271](https://github.com/spec-kitty/spec-kitty-charter/issues/271)). The model and its scanner
moved into the domain (`packs.domain.markdown`). A toolguide's guide and a pack's document check
their content through that one scanner when they are built, so they refuse what the codec refuses.
The guide's hand copy, which missed indented headings and underlined (`===`, `---`) headings, is gone.

**A companion named in another letter case is refused at load**
([#272](https://github.com/spec-kitty/spec-kitty-charter/issues/272)). A companion path is
classified case-folded, as the write's claim check folds it: `body_path: PACK.md` is refused like
`pack.md`, instead of failing the whole-pack write.

**One wording for a file that is not UTF-8**
([#273](https://github.com/spec-kitty/spec-kitty-charter/issues/273)). The store's load and
validate mode share one decoder and one reason, `the file is not valid UTF-8`.

**A step output's `template_path` resolves to the type's own template**
([#277](https://github.com/spec-kitty/spec-kitty-charter/issues/277),
[ADR-0044](/adr/0044-mission-types-consolidated-into-one-sanitized-shape/) as amended 2026-10-31).
The path stays as written. A mission type whose output names none of its own templates is left out
at load with a violation naming the step and the path, and a whole-pack write of it is refused.
