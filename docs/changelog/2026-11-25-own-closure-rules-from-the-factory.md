---
okf_version: "0.2"
title: The own-closure rules come from the rule factory and run in the one ValidationWalker
description: The fallback-profile, role-pin and profile-enhancement rules move into packs.domain.rules, PackRulesets.ownClosure hands them out, and the ClosureWalk port and OwnClosureWalk are deleted; answers are unchanged.
doc_status: active
updated: 2026-11-25T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0082-the-domain-owns-every-graph-walk-and-ports-transform-and-interface
  - architecture/architectural-patterns-applied
  - changelog/2026-11-25-pack-graph-owns-the-walks
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you add a check of a pack's own closure; add a rule to PackRulesets.ownClosure.
---

# 2026-11-25 — The own-closure rules come from the rule factory and run in the one ValidationWalker

**One walker and one factory for the own-closure rules** (ADR-0082 slice 3, #638). A pack's own closure was judged
through a port, `ClosureWalk`, and its one implementation in the application layer, `OwnClosureWalk`, which called
three rules that lived beside it.

## What changed

- **`ValidationWalker`** in `packs.domain` runs phases of rules. Each rule carries the predicate that selects the
  subjects it runs on. A phase runs all its rules on every subject in one traversal, the faults go into one ledger in
  phase, subject and rule order, and a phase that finds a fault stops the later phases (the phase gate).
- **`PackRulesets.ownClosure(..)`** hands out the own-closure phase: the fallback profile (only when the pack
  declares one), then the role pins, then the profile enhancements. `FallbackProfileRule`, `RolePinRule` and
  `ProfileEnhancementRule` move to `packs.domain.rules`, and they judge `ClosureFacts`. The profile reader the port
  carried is now `ProfileReader`.
- **Deleted:** the `ClosureWalk` port (with `Profiles` and `OwnClosure`) and `OwnClosureWalk`. `ClosureImpactCheck`
  takes no closure walk any more: registration, every write and the startup census get the rules from the factory.

## What did not change

No refusal code, detail, origin or order of findings changes: the fallback's breaches come first, then every pin's,
then every enhanced profile that loses content, all collected. The tests of the walk moved to `OwnClosureRulesTest`
with their cases as they were.
