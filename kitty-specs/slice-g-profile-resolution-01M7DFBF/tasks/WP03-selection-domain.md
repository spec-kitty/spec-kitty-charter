---
work_package_id: WP03
title: Profile selection domain service
dependencies:
- WP02
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-008
planning_base_branch: slice-g-538-profile-resolution
merge_target_branch: slice-g-538-profile-resolution
branch_strategy: Planning artifacts were generated on the mission branch kitty/mission-slice-g-profile-resolution-01M7DFBF (target slice-g-538-profile-resolution). Completed changes merge back into slice-g-538-profile-resolution; the slice lands as one PR (#573), held until slice F lands.
subtasks:
- T013
- T014
- T015
- T016
phase: Phase 2 - Selection
agent: claude
history:
- at: '2026-11-15T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/selection/
create_intent:
- service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/selection/ProfileSelection.java
- service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/selection/SelectionContext.java
- service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/selection/SelectionAnswer.java
- service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/selection/SelectionCandidate.java
- service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/selection/package-info.java
- service/profiles/src/test/java/ai/speckitty/charter/profiles/domain/selection/ProfileSelectionTest.java
execution_mode: code_change
owned_files:
- service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/selection/**
- service/profiles/src/test/java/ai/speckitty/charter/profiles/domain/selection/**
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP03 — Profile selection domain service

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

A pure domain service that answers which profile fills a role, in ADR-0069 d4's order as ruled in D2, D3 and D4
(`research.md` §6a; plan P1). There is no Spring, wire or storage code here. WP05 feeds it the candidates in force
under an activation.

## The order (ruled)

Inputs: `role`, a `SelectionContext(Optional<String> language, Optional<String> framework)`, and `Candidates`:
- a list of `SelectionCandidate(AgentProfile profile, String pack, int closurePosition, int depth)`;
- `Optional<String> pinnedProfileId`, plus the ids of the pinned profile's in-force specializations;
- `Optional<SelectionCandidate> fallback`.

Each candidate is a non-enhancer, in-force profile. `depth` is the longest in-force `specializes_from` chain (WP04
supplies it).

1. **Reserved roles.** `generic` throws `IllegalArgumentException`; the path pattern refuses it before this. When the
   role is `operator`, keep only `humanPerformed()` candidates and disable the fallback.
2. **Pin.** When a pin is present, keep only the pinned profile and its in-force specializations.
3. **Performer.** Drop a human-performed candidate unless the role is `operator` or the candidate is the pinned
   profile.
4. **Role fit.** Drop `profile.roleFit(role) == NONE`.
5. **Context.** Read *declared* fields only (D6 ruling: selection never reads inherited values). A candidate is
   filtered only when `depth > 0` and it declares `languages` or `frameworks` in `specializationContext()`, and it is
   not the pinned profile. It fits when, for every asked dimension it declares, the list contains the asked value,
   and at least one asked dimension matches. Roots, descriptor-free specializations and the pinned profile fit any
   context.
6. **Tier.** Keep the best `RoleFit` left (`PRIMARY` before `ALT`).
7. **Depth.** Keep the greatest `depth`.
8. **Priority.** Keep the highest `routingPriority().orElse(50)`.
9. **Tie.** If more than one candidate is left, answer all of them, sorted by `closurePosition` then id, with
   `tie = true`. No pin tie-break exists, since it is unreachable (D2); assert that in a test, don't code a branch.
10. **None.** Answer the fallback marked `FALLBACK`. With no fallback (or for `operator`), answer
    `SelectionAnswer.NoMatch`.

`SelectionAnswer` is a sealed type:
- `Matched(List<Pick> picks, boolean tie)`, where `Pick(String profileId, String pack, Fit fit)` and
  `Fit = PRIMARY | ALT | FALLBACK`;
- `NoMatch`.

Conflicts (fallback or pin) are judged before selection, by the caller (WP05), and never reach here.

## Subtasks
- **T013** Value types: `SelectionContext`, `SelectionCandidate`, `SelectionAnswer`, `Fit`, `Candidates`, with
  `package-info.java`. Javadoc cites ADR-0069 d4 and ADR-0078 (written in WP07).
- **T014** Steps 1 to 8, as small private methods. Reuse `AgentProfile.roleFit` and `RoleFit.outranks`.
- **T015** Steps 9 and 10, the tie and the fallback.
- **T016** `ProfileSelectionTest`, one test per step, using the example-pack profiles as plain builders. Cases:
  - `implementer` → ivan;
  - `implementer`, `language=java` → jenny;
  - `implementer`, `language=kotlin` → ivan;
  - **alt-only**: every primary is filtered out by context, so the alt candidate is answered and not the fallback;
  - the renata and rita tie, in a stable order;
  - a pin to renata: `project-reviewer` is excluded;
  - a pin to a profile with a deeper specialization that fits: the specialization wins;
  - a human reviewer without a pin is excluded, and pinned it is answered;
  - `operator` → human-operator;
  - `operator` with none → `NoMatch`, never the fallback;
  - `facilitator` → fallback;
  - no fallback → `NoMatch`;
  - `routing-priority` absent counts as 50.

## Definition of Done
- 100% branch coverage of `ProfileSelection`. Mutation testing is configured in the repository, so check that no
  mutant survives in this package.
- ArchUnit is green: the domain depends on nothing outside the domain and the kernel.

## Branch Strategy

Planning base and final merge target: `slice-g-538-profile-resolution` (mission branch
`kitty/mission-slice-g-profile-resolution-01M7DFBF`). The execution workspace is allocated per computed lane from
`lanes.json`; do not pick a base branch by hand. Sign every commit. Add no Co-Authored-By trailer and no model names.

```bash
spec-kitty agent action implement WP03 --agent claude --mission slice-g-profile-resolution-01M7DFBF
```

## Read first
`spec.md`, `plan.md`, `research.md` §6a (rulings) and `research/squad-post-plan.md`, all in
`kitty-specs/slice-g-profile-resolution-01M7DFBF/`. Also `.kittify/charter/charter.md` ("Designing during the
rewrite": canonical shape only).
