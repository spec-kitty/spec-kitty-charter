---
okf_version: "0.2"
title: Activation is explicit, and multi-word URN kind tokens move to kebab case
description: "An absent activation key means none, so an empty or partly empty charter puts nothing in force, and multi-word URN kind tokens move to kebab case with no migration reader."
doc_status: active
updated: 2026-11-02T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0054-guards-are-enforced-checks-implemented-by-executable-assets
  - adr/0033-local-active-charter-is-source-of-truth
  - plans/pack-ecosystem-work-breakdown
audience:
  - profile: governance-lead
    guidance_text: Read this for the two remaining pack-ecosystem rulings and the breaking change to activation.
---

# 2026-11-02 — Activation is explicit, and multi-word URN kind tokens move to kebab case

Epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142).

**Before:**

- ADR-0053 left two questions open with recommendations: what an absent activation key means in a partly empty charter,
  and whether multi-word URN kind tokens follow the kebab path token.
- The empty-charter ruling covered only a wholly empty charter; a charter that set one key kept the per-kind defaults
  for the rest, which kept the "empty means all" code paths alive.
- ADR-0054 left the interim trust mark for asset-backed guards without an owner.

**Now:**

- **Breaking (at the split).** An absent activation key means **none**. An empty active charter puts nothing in force,
  and a partly empty one puts only the keys it names in force. A project activates a preset or artifacts explicitly;
  deactivation is expressed by activating nothing, which is what the profiles and presets system exists for.
- **Breaking (contract).** The multi-word URN kind tokens move to kebab case directly
  (`agent-profile:<id>`, `mission-step-contract:<id>`, `mission-type:<id>`, `anti-pattern:<id>`,
  `event-registry:<id>`), in the same change as the kebab path token. There is no split-migration reader: the service
  has no consumers yet. The glossary context token stays `glossary_context`, a deliberate split of a URN prefix from
  its `glossary` kind.
- The interim trust mark for an asset-backed guard is the project's activation file, where the pack ids and versions
  are already named. #157 may widen the policy, not move the mark.

ADR-0053, ADR-0033 and ADR-0054 record the rulings where the questions stood.
