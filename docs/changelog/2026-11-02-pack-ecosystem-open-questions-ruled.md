---
okf_version: "0.2"
title: Pack ecosystem open questions ruled; guards backed by executable assets
description: "The operator ruled every open question of ADR-0048 to ADR-0052 (ADR-0053) and replaced the proposed gate kind with asset-backed guards (ADR-0054)."
doc_status: active
updated: 2026-11-02T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0054-guards-are-enforced-checks-implemented-by-executable-assets
  - plans/pack-ecosystem-work-breakdown
audience:
  - profile: governance-lead
    guidance_text: Read this for what the operator ruled on the pack-ecosystem open questions.
---

# 2026-11-02 — Pack ecosystem open questions ruled; guards backed by executable assets

Epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142).

**Before:**

- ADR-0048 to ADR-0052 were accepted with 32 open questions, and the work breakdown planned with their recommended
  defaults.
- ADR-0050 proposed a declared `gate` kind with a severity.

**Now:**

- [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) records every ruling. Among the
  changes from the defaults:
  - cross-pack references are written `<pack-name>::<urn>`;
  - after the split, an empty active charter puts nothing in force;
  - range grammar is PEP 440;
  - the glossary context URN is `glossary_context:<id>`;
  - the `{kind}` path segment uses the kebab operator token;
  - a step enhancement is an additions artifact plus a CRG `enhances` edge;
  - an anti-pattern requires a bad example and has no default severity.
- [ADR-0054](/adr/0054-guards-are-enforced-checks-implemented-by-executable-assets/) replaces the `gate` kind:
  - a guard is an artifact `implemented_by` an executable asset or a closed built-in;
  - it runs to the guard/1 interface and fails closed.
- ADR-0048 to ADR-0052 carry an Amendment 2026-11-02, the work breakdown lists the rulings, and the glossary
  deprecates "Gate" for "Guard".
