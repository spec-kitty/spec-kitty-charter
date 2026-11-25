---
work_package_id: WP04
title: 'Slice 4a: the relations phase (#639)'
dependencies:
- WP03
requirement_refs:
- FR-008
- NFR-001
planning_base_branch: feat/adr-0082-graph-walk-slices
merge_target_branch: feat/adr-0082-graph-walk-slices
branch_strategy: Planning artifacts for this mission were generated on feat/adr-0082-graph-walk-slices. WP04 and WP05 land on main together as the slice 4 pull request; execution worktrees are allocated per computed lane from lanes.json.
subtasks:
- T017
- T018
- T019
phase: Phase 3 - Slice 4
history:
- at: '2026-11-25T22:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/RelationRules.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/RelationRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ComposedRelations.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/RelationRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/RelationRulesEnhancementTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/RelationRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ComposedRelations.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ImpactRelations.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/SpecializationCycleRule.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ImpactRelationsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ImpactRelationsEnhancementTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/RelationRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/RelationRulesEnhancementTest.java
---

# Work Package Prompt: WP04 - Slice 4a: the relations phase (#639)

## Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else.

## Objective

The relation rules (self-override, conflicting relations, override not permitted, a locked step enhanced, override
cycle, enhances cycle, ungrounded enhancer) and the specialization cycle come from `PackRulesets.relations()` and run on
the walker over a domain subject of the composed closure. `ImpactRelations` and `SpecializationCycleRule` are deleted.
Codes, details and order unchanged.

Requirements: FR-008, NFR-001, C-001. Design: `research.md` R-3; squad S1, S4.

## Subtasks

### T017 The relations subject

- `ComposedRelations` in `packs.domain`: built by `PackGraph` (or from a composed closure's packs and facts) with the
  `enhances` and `overrides` edges in writer-then-declared order (today's `ImpactRelations.collect`), each endpoint
  placed through `PackCompositionResolver.holderOf`; the `specializes_from` graph between profiles; the enhances
  cycles computed once, so `ungrounded` reads them from the subject, never from a field another rule filled.
- It replaces `OfferingReadService.ComposedClosure` in the rule signatures; the scoped mutability policies come from
  the closure's facts as `ImpactRelations.scoped()` reads them.

### T018 RelationRules and relations()

- One check per rule, declared in today's order: selfOverride, conflicting (OnClose), notPermitted,
  lockedStepEnhanced, overrideCycle (OnClose; self-overrides left out), enhancesCycle (OnClose), ungroundedEnhancer
  (OnClose), specializationCycle (OnClose). Today's empty-edges short circuit becomes a predicate on the relation
  checks only; the specialization cycle runs regardless.
- The fault builders (`violation`, `fault`, naming packs once each) move with them, unchanged.
- `PackRulesets.relations()` returns `GraphCheck(RELATIONS, one phase)`. `PackRulesets` is owned by WP03: out-of-map
  edit, recorded.

### T019 Delete the application rules; move their tests

- `ImpactRelationsTest`, `ImpactRelationsEnhancementTest` move to `domain/rules` as `RelationRulesTest`,
  `RelationRulesEnhancementTest`, with every case unchanged; add red-first a mixed-rule fixture that pins rule-first
  order (a self-override and a conflict: the self-override first).
- `ClosureImpactCheck#relationFaults` calls `relations()` for now (WP05 deletes it): out-of-map edit, recorded.

## Definition of Done

- No class in `packs.application` builds a relation refusal; `:packs:test` green; `verifyAll`.

## Risks

- `notPermitted` reads the closure's scoped policies with the source's own pack left out (B8, A15): keep both.
- `lockedStepEnhanced` shares `EnhancementCompose.lockedTargets` with the read path; WP05 unifies the read's answer.

Implementation command: `spec-kitty agent action implement WP04 --agent claude --mission adr-0082-graph-walk-slices-01M8A867`
