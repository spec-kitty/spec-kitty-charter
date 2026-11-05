---
okf_version: "0.2"
title: The whole-pack round trip is a required check, and epic #186 closes
description: CI gate / gate now runs the whole-pack round trip; mission step texts refuse bad Markdown when built, and validate mode reads a pack root spelled with '..'.
doc_status: active
updated: 2026-11-05T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
  - guides/contributing
  - adr/0023-ci-gate-required-check
  - adr/0040-content-form-and-payload-kinds
  - adr/0044-mission-types-consolidated-into-one-sanitized-shape
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if CI gate / gate fails on the whole-pack round trip, or a mission step text is refused when you build it.
---

# 2026-11-05 — The whole-pack round trip is a required check, and epic #186 closes

**Every pull request that changes the contracts, the service or the build now has to keep the packs round-tripping**
(epic [#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186), operator ruling of 2026-11-05).
`CI gate / gate` calls `whole-pack.yml` for a new `wholepack` group, beside `service`, `native` and `docs`
([ADR-0023](/adr/0023-ci-gate-required-check/), extended). The required check's name is unchanged. The workflow no
longer runs on its own for a pull request, so nothing runs twice; it still runs on `main`. Its paths now cover every
contract and `gradlew`, because `:packs` also reads `contracts/charter` and `contracts/offering`.

Two gaps closed first:

- **A mission step's text is checked when it is built**
  ([#299](https://github.com/spec-kitty/spec-kitty-charter/issues/299)). The `markdown` form's line rules moved to
  the kernel (`kernel.markdown`), so `StepDocument` and `StepSection` refuse a bad title, preamble, section or opening
  with a constraint breach, as `PackDocument` already does at build, rather than only when the store writes them.
  Pack documents behave as before ([ADR-0040](/adr/0040-content-form-and-payload-kinds/), note of 2026-11-05).
- **Validate mode reads a pack root spelled with `..`**
  ([#319](https://github.com/spec-kitty/spec-kitty-charter/issues/319)). A root such as `<dir>/x/../pack` used to
  report every mission type whose templates exist as unreadable ("leaves the pack"). Validate mode now normalizes the
  root once, as the load already did; a linked root keeps its configured name. The root is normalized by its text,
  so a `..` after a linked folder (`<dir>/link/../pack`) resolves as written, not through the link, as in the load.
