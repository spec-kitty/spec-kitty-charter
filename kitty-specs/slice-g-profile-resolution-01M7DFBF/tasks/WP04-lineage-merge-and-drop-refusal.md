---
work_package_id: WP04
title: Lineage chain, enhancement merge, inheritance and the drop refusal
dependencies:
- WP02
requirement_refs:
- C-002
- FR-009
- FR-010
planning_base_branch: slice-g-538-profile-resolution
merge_target_branch: slice-g-538-profile-resolution
branch_strategy: Planning artifacts were generated on the mission branch kitty/mission-slice-g-profile-resolution-01M7DFBF (target slice-g-538-profile-resolution). Completed changes merge back into slice-g-538-profile-resolution; the slice lands as one PR (#573), held until slice F lands.
subtasks:
- T017
- T018
- T019
- T020
- T021
phase: Phase 2 - Resolution
agent: claude
history:
- at: '2026-11-15T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/resolution/
create_intent:
- service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/resolution/ProfileEnhancementMerge.java
- service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/resolution/ProfileInheritance.java
- service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/resolution/ProfileEnhancementDropsField.java
- service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/resolution/package-info.java
- service/profiles/src/test/java/ai/speckitty/charter/profiles/domain/resolution/ProfileEnhancementMergeTest.java
- service/profiles/src/test/java/ai/speckitty/charter/profiles/domain/resolution/ProfileInheritanceTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ProfileEnhancementRule.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ProfileEnhancementImpactTest.java
- service/packs/src/testFixtures/resources/example-packs/org-base/agent_profiles/reviewer-renata-org.agent.yaml
execution_mode: code_change
owned_files:
- service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/resolution/**
- service/profiles/src/test/java/ai/speckitty/charter/profiles/domain/resolution/**
- service/packs/src/main/java/ai/speckitty/charter/packs/application/GovernanceWalk.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ProfileEnhancementRule.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/GovernanceWalkTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ProfileEnhancementImpactTest.java
- service/packs/src/testFixtures/resources/example-packs/org-base/agent_profiles/reviewer-renata-org.agent.yaml
- service/packs/src/testFixtures/resources/example-packs/org-base/drg/fragment.yaml
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP04 — Lineage chain, enhancement merge, inheritance and the drop refusal

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Build the effective profile's two folds and the #380 refusal (rulings D5, D6 and D7, `research.md` §6a; plan P5),
plus the ordered lineage chain that both selection (`depth`) and the effective read (`lineage`) need.

Ruled:
- **D5.** An enhancer is a complete profile. Its identity fields are never merged: `profile-id`, `name`,
  `purpose`, `role`, `alt-roles`, `performer`, `routing-priority`. Every other top-level field the enhancer declares
  **replaces** the base's. The replacement is refused when it loses base content:
  - a base list item missing from the enhancer's list (order-insensitive);
  - an object sub-field missing or changed (recursively);
  - a scalar changed.

  Refusal: `profile_enhancement_drops_field`, naming the enhancing pack first, then the field path (for example
  `specialization`).
- **D7.** An enhancer that declares a non-empty `excluding` is the same refusal, field `excluding`.
- **D6.** Inheritance along `specializes_from`, root to leaf, by **per-field replacement**: a top-level field the
  descendant declares replaces the ancestor's, and an absent field is inherited. Identity fields are never
  inherited. With several parents, the parents fold in closure order, then the child (derived, flagged in ADR-0078).
- **Composition order** (derived, flagged): each profile on the chain is first merged with its own in-force
  enhancers, then the chain is folded.

## Subtasks

### T017 Lineage chain
- Extend `GovernanceWalk`'s LINEAGE step (:62, :211-229) to also yield an ordered chain:
  `List<LineageLink(id, pack, depth)>`, nearest first, depth = the **longest** in-force path. Do not add a second
  `specializes_from` traversal (C-002).
- The governance entries it answers today must not change: assert that in `GovernanceWalkTest`.

### T018 `ProfileEnhancementMerge`
- Pure domain: `merge(AgentProfile base, List<Enhancer(AgentProfile profile, String pack)> inClosureOrder)`. It
  returns the merged `AgentProfile` or throws `ProfileEnhancementDropsField(pack, field)`.
- Field-by-field over the schema's top-level fields. Generate the field list from one table, so a new schema field
  cannot silently skip the merge, and add a test that fails when the table and `AgentProfile` disagree.

### T019 `ProfileInheritance`
- `fold(List<AgentProfile> rootToLeaf)` → `AgentProfile`, per-field replacement, identity fields from the leaf.
- `required` fields (`name`, `purpose`, `specialization`) are always the leaf's own, because the schema requires
  them.
- Test with `implementer-ivan` → `java-jenny`: jenny's `languages` stay `[java]`, and a field jenny leaves absent
  comes from ivan.

### T020 Registration
- `ProfileEnhancementRule`, in `RegistrationImpact` beside the WP02 rules: for every in-force enhancer
  (`enhances` agent_profile → agent_profile, read through the existing relation reads, `InForceResolution` :77-91)
  run the merge and turn `ProfileEnhancementDropsField` into a 422 breach.
- Add `PROFILE_ENHANCEMENT_DROPS_FIELD` to `RefusalCode.java` and `CharterRefusalCode.yaml`, as a recorded out-of-map
  edit (WP02 owns the files):
  - status 500 by default, since a stored pack is faulty when the fault is met on a read, as the placeholder
    `profiles.feature` states;
  - a 422 `operations:` override for the registration operations, as the `pack_reference_*` codes do.
- Tests in `ProfileEnhancementImpactTest`: a `specialization` that leaves out the base's is refused; a list item
  dropped; a scalar changed; `excluding` on an enhancer; a positive control that only adds.

### T021 Example
- New `org-base/agent_profiles/reviewer-renata-org.agent.yaml`. It restates `reviewer-renata`'s non-identity fields
  it declares, unchanged, and adds one directive reference that exists in the closure.
- Add the `enhances` edge `agent_profile:reviewer-renata-org → built-in::agent_profile:reviewer-renata` in
  `org-base/drg/fragment.yaml`. Restamp the org-base manifest; that file is owned by WP01, so this is a recorded
  out-of-map edit.
- Check that the edge passes `ImpactRelations` (enhancer without root, cycle, enhances-and-overrides).

## Definition of Done
- Domain tests cover every field kind (list, object, scalar) for both folds.
- The whole-pack round trip is green with the new example. `./gradlew verifyAll` is green.
- Enhancers are excluded from candidates and lineage by the callers (WP05, WP06). Expose an
  `isEnhancer(id)` helper on the relation reads if that makes it simpler.

## Branch Strategy

Planning base and final merge target: `slice-g-538-profile-resolution` (mission branch
`kitty/mission-slice-g-profile-resolution-01M7DFBF`). The execution workspace is allocated per computed lane from
`lanes.json`; do not pick a base branch by hand. Sign every commit. Add no Co-Authored-By trailer and no model names.

```bash
spec-kitty agent action implement WP04 --agent claude --mission slice-g-profile-resolution-01M7DFBF
```

## Read first
`spec.md`, `plan.md`, `research.md` §6a (rulings) and `research/squad-post-plan.md`, all in
`kitty-specs/slice-g-profile-resolution-01M7DFBF/`. Also `.kittify/charter/charter.md` ("Designing during the
rewrite": canonical shape only).
