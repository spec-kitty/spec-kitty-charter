---
work_package_id: WP02
title: Role pins, closure facts and registration rules
dependencies:
- WP01
requirement_refs:
- FR-004
- FR-006
- FR-008
- NFR-002
planning_base_branch: slice-g-538-profile-resolution
merge_target_branch: slice-g-538-profile-resolution
branch_strategy: Planning artifacts for this mission were generated on slice-g-538-profile-resolution. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-g-538-profile-resolution unless the human explicitly redirects the landing branch.
subtasks:
- T007
- T008
- T009
- T010
- T011
- T012
phase: Phase 1 - Profile model
history:
- at: '2026-11-15T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/RolePins.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureRoleFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RolePinRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/SpecializationCycleRule.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureRoleFactsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RolePinImpactTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/SpecializationCycleImpactTest.java
- service/packs/src/testFixtures/resources/example-packs/project/policy.yaml
execution_mode: code_change
owned_files:
- contracts/packs/schemas/policy.schema.json
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MutabilityPolicySource.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/RolePins.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PolicyYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureRoleFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RolePinRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/SpecializationCycleRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/FallbackProfileRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationImpact.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalCode.java
- contracts/charter/schemas/CharterRefusalCode.yaml
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PolicyRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PolicyYamlTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureRoleFactsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RolePinImpactTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/SpecializationCycleImpactTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/FallbackProfileImpactTest.java
- service/packs/src/testFixtures/resources/example-packs/project/pack.yaml
- service/packs/src/testFixtures/resources/example-packs/project/policy.yaml
- service/packs/src/testFixtures/resources/example-packs/project/pack-manifest.yaml
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02 — Role pins, closure facts and registration rules

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Give `policy.yaml` its role pins (ADR-0030, ADR-0032 d4, ruling D3), and add one place that answers the fallback and
the pins **in force** for a set of packs. Add the registration rules D2, D3 and D4 ask for.

Ruled (`research.md` §6a):
- **D3, the shape.** `role_pins: {<role-token>: <profile-id>}`. Pins are **tighten-only**: two packs in one closure
  that pin the same role to different profiles are `role_pin_conflict`, whatever their derivation.
- **D3, registration refusals** (422, judged over the declaring pack's closure with the write in place, like
  `FallbackProfileRule`):
  - `role_pin_unknown`: no profile with that id in the closure;
  - `role_pin_not_fitting`: the pinned profile's `role` is not the pinned role and its `alt-roles` do not list it
    (no human exemption);
  - `role_pin_conflict`.
- **D4.** A fallback profile that is human-performed is refused: `fallback_profile_not_agent` (422).
- **D2.** A `specializes_from` cycle among agent profiles is refused: `specialization_cycle` (422).
- **ADR-0068 d5.** The fallback in force is the most derived declaring pack's. Unrelated packs that declare different
  fallbacks give `fallback_profile_conflict`, which is raised at selection (WP05).

## Subtasks

### T007 Schema and storage
- `policy.schema.json`: add `role_pins` (`type: object`, `propertyNames` with the role pattern
  `^[a-z][a-z0-9-]*$`, max 64; values with the identifier pattern `^[a-z0-9][a-z0-9_-]*$`, max 64; `generic` is
  not allowed as a key). Amend the description's last paragraph: pins are now declared; role locks stay reserved.
- `MutabilityPolicySource` (builder :101-136) and `PolicyYaml` (:102-126): read and write `role_pins` in authored
  order. New value object `RolePins` in `packs/domain`.
- `PolicyRoundTripTest` and `PolicyYamlTest`: add a pin.
- Note: #106 also edits `policy.schema.json`. Keep the change to one property and one description sentence.

### T008 `ClosureRoleFacts`
- Application-layer service over `ClosureView`. It answers:
  - `fallbackInForce(): FallbackFact`, one of `Declared(profileId, pack)`, `None`, or `Conflict(packs)`. The most
    derived declaring pack wins; two declaring packs where neither is derived from the other, with different ids,
    give a conflict.
  - `pinFor(role): PinFact`, one of `Pinned(profileId, pack)`, `None`, or `Conflict(packs)`.
- Reuse `ClosureView.holds`, `winnerPackOf` and the derivation order the closure already knows; do not write a
  second closure walk. Unit tests: `ClosureRoleFactsTest`.

### T009 `RolePinRule`
- Mirrors `FallbackProfileRule.check` (its `Profiles` port, `Breach`, the order of checks). Call it from
  `RegistrationImpact` beside `judgeFallbacks` (:416-472), for every pack whose closure holds the written pack.
- Add `ROLE_PIN_UNKNOWN`, `ROLE_PIN_NOT_FITTING` and `ROLE_PIN_CONFLICT` to `RefusalCode.java`, and to
  `CharterRefusalCode.yaml` with status 422 and `raised: true`. Follow the file's conventions: description, the
  operations it applies to, and the detail sentence. `ReservedRefusalCodesTest` must stay green.
- Tests: `RolePinImpactTest`, one test per refusal plus a positive control.

### T010 `fallback_profile_not_agent`
- Extend `FallbackProfileRule.check` with a fourth breach after "not role-neutral": `profile.humanPerformed()` gives
  `FALLBACK_PROFILE_NOT_AGENT`. Add the code (422, raised) in both registries. Extend `FallbackProfileImpactTest`.

### T011 `specialization_cycle`
- `SpecializationCycleRule`: a DFS over the closure's `specializes_from` relationships between agent profiles,
  judged at registration. The refusal names the cycle's ids in order. Add the code (422, raised) in both registries.
  Tests: `SpecializationCycleImpactTest`.
- The relationships are *read* only; build none in main code. Test fixtures that build `Relationship` use the
  existing constructor until slice F lands, then F's typed factories (plan P3).

### T012 Example pack
- `project/pack.yaml`: add `mutability_policy: policy.yaml`.
- New `project/policy.yaml`: `schema_version: 1` and `role_pins: {reviewer: reviewer-renata}`, with **no**
  `kind_defaults`, since #106 renames a kind token. Restamp `project/pack-manifest.yaml`.
- `reviewer-renata` is held by built-in, which is in project's closure, and has primary role `reviewer`, so the pin
  is valid.
- Update existing project-pack scenarios that list the pack's files, if any, in the same change.
- Invalid packs for the refusals go in test fixtures, not in the example packs.

## Definition of Done
- Pins round-trip, and `ExamplePackCoverageTest` covers `role_pins`.
- Each refusal is raised with a detail naming the pack, role or profile, and the closure.
- `./gradlew verifyAll` is green.

## Risks
- #556 moves `FallbackProfileRule` behind ports. Whichever lands second moves `RolePinRule` and
  `SpecializationCycleRule` with it.
- `RefusalCode.java` and `CharterRefusalCode.yaml` are owned here. WP04 and WP05 add codes as recorded out-of-map
  edits.

## Branch Strategy

Planning base and final merge target: `slice-g-538-profile-resolution` (mission branch
`kitty/mission-slice-g-profile-resolution-01M7DFBF`). The execution workspace is allocated per computed lane from
`lanes.json`; do not pick a base branch by hand. Sign every commit. Add no Co-Authored-By trailer and no model names.

```bash
spec-kitty agent action implement WP02 --agent claude --mission slice-g-profile-resolution-01M7DFBF
```

## Read first
`spec.md`, `plan.md`, `research.md` §6a (rulings) and `research/squad-post-plan.md`, all in
`kitty-specs/slice-g-profile-resolution-01M7DFBF/`. Also `.kittify/charter/charter.md` ("Designing during the
rewrite": canonical shape only).
