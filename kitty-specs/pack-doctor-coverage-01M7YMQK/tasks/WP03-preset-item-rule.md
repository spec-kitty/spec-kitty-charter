---
work_package_id: WP03
title: 'The preset-item rule (#629, #632 item 1)'
dependencies:
- WP02
- WP10
requirement_refs:
- FR-002
- FR-003
- FR-010
- FR-011
- NFR-003
- SC-001
planning_base_branch: feat/pack-doctor-coverage
merge_target_branch: feat/pack-doctor-coverage
branch_strategy: Planning artifacts for this mission were generated on feat/pack-doctor-coverage. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-doctor-coverage unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-doctor-coverage-01M7YMQK
base_commit: 8a68e805241224476862fa0cd56f4e58a4721505
created_at: '2026-11-21T17:59:01.488553+00:00'
subtasks:
- T011
- T012
- T013
- T014
- T015
- T016
phase: Phase 2 - The rules
history:
- at: '2026-11-21T13:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PresetItemRules.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PresetItemRulesTest.java
- contracts/charter/changes/2026-11-21-preset-item-refusals.md
- contracts/charter/scenarios/registration/preset-items.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/RequiredNotHeldCoverageTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CompositionFactsBuilder.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/WriteFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PresetItemRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PackRulesets.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCodes.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureImpactCheck.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RefusalCode.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/set/PackSetCheck.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/set/PackSetCheckTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PresetItemRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompositionFactsBuilderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/WriteFactsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/golden/CompositionGoldenTest.java
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/changes/2026-11-21-preset-item-refusals.md
- contracts/charter/scenarios/activation-ruling-changes.feature
- contracts/charter/scenarios/activation-register.feature
- contracts/charter/scenarios/activation-selections.feature
- contracts/charter/scenarios/activation-deny-satisfied.feature
- contracts/charter/scenarios/activation-kind-model.feature
- contracts/charter/scenarios/composition.feature
- contracts/charter/scenarios/effective-context-scoping.feature
- contracts/charter/scenarios/effective-overriders.feature
- contracts/charter/scenarios/effective-reads.feature
- contracts/charter/scenarios/layer-merge.feature
- contracts/charter/scenarios/pack-presets.feature
- contracts/charter/scenarios/pack-removal.feature
- contracts/charter/scenarios/registration/preset-items.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/PackRegistrationSteps.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/RequiredNotHeldCoverageTest.java
- service/packs/src/testFixtures/resources/example-packs/built-in/presets/**
- service/packs/src/testFixtures/resources/example-packs/built-in/styleguides/**
- service/packs/src/testFixtures/resources/example-packs/org-base/presets/**
- service/packs/src/testFixtures/resources/example-packs/org-base/tactics/**
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP03 - The preset-item rule (#629, #632 item 1)

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`AGENTS.md`, `.kittify/charter/charter.md`, ADR-0082, the mission `spec.md` and `plan.md` (the decisions this WP
names), and `research/squad-dispositions.md`.

## Objective

One domain rule refuses a preset item that the declaring pack's **own closure** lacks (rulings R-a, R-k, R-l, R-m;
spec FR-002, FR-003, FR-010, FR-011; plan D3, read it whole):

- a `must_have` item that no closure pack **holds** as an artifact file answers `preset_item_not_held`;
- any item that no closure pack **declares** as its kind (a graph node or a file) answers `preset_item_undeclared`.

Each item gets one fault, and the case-only answer wins. The rule runs in pack-doctor's default CLOSURE and on every
registration and write, **never on a composing read**.

## Branch Strategy

- Planning branch and merge target: `feat/pack-doctor-coverage` (PR to `main`). Mission topic branch:
  `kitty/mission-pack-doctor-coverage-01M7YMQK`.
- The workspace comes from `lanes.json`. Implement with
  `spec-kitty agent action implement WP03 --agent claude --mission pack-doctor-coverage-01M7YMQK`.
- Commits: no model names and no Co-Authored-By trailer (repository rule). Never push to `main`.

## Context
- The own closure is `PackCompositionResolver.compositionClosure` rooted at the declaring pack (`depends_on` +
  `parent_pack`, not dependents). Do not write a new traversal (NFR-003, ADR-0082).
- `PackCompositionFacts.defined` mixes artifact files with graph-only nodes. `CompositionFactsBuilder` adds
  `anti_pattern` and `action` graph nodes into it (`CompositionFactsBuilder.java` around 212, 237 and 446-460).
  After WP10 an `anti_pattern` can also be a file; `declaredWithoutFile`, its `withoutFile` set and its javadoc ("never
  a file of its own") go with T011, since `declared` takes every graph node.
- `PackCompositionResolver.caseOnlyMatches` (around line 1110) is the existing case-only check. Today it runs only in
  `OfferingReadService.judged` phase 6, over the composed closure.
- `RequiredFloor` stays the read model (A10/C31 for reads). Do not change it to refuse.

## Subtasks

### T011 — `held` and `declared` replace `defined`
- Red first: a `CompositionFactsBuilderTest` case where a pack holds `directive:X` as a file and only a graph node for
  `anti_pattern:Y`. Assert `held` = {X}, and `declared` ⊇ {X, Y}.
- Replace `PackCompositionFacts.defined` outright with `held` (artifact files) and `declared` (every graph-node URN
  plus `held`). Move every reader of `defined` to the one it means: `PackRegistrationService`,
  `PackUninstallService`, `OfferingReadService`, `StepHolding`, `PackReadout`, `WriteFacts` and the tests. Read each
  use; most mean held.
- `WriteFacts` (the write path's rebuild: `withArtifact`, `withoutArtifact`, `withRegisteredEdges`) maintains both
  fields, with a `WriteFactsTest` case per method.
- The files outside this WP's ownership that only rename a field read are justified out-of-map edits; list them in the
  review notes. `src/test/resources/facts/example-packs.json` is WP10's: re-record it for `held`/`declared` as one of
  them.

### T012 — New codes
- `RefusalCode.PRESET_ITEM_NOT_HELD` and `PRESET_ITEM_UNDECLARED` (tokens `preset_item_not_held` and
  `preset_item_undeclared`), status 422, raised at registration. Add them to `PackCodes.CLOSURE` (one owner per token,
  `PACK_CODES_HAVE_ONE_OWNER`).
- In `Problems.statusOf` (pack-doctor; owned by WP06), map both to 422. That is a justified out-of-map edit, or the
  status table throws.
- `CharterRefusalCode.yaml`: the two enum values with descriptions. Change fragment
  `2026-11-21-preset-item-refusals.md` under `### Breaking`: a registration that was accepted is now refused, and core
  regenerates its refusal enum.

### T013 — `PresetItemRules`
- `packs.domain.rules.PresetItemRules`, a public static factory method returning the phase. It takes, per pack:
  - its presets;
  - its own closure (computed once, from `compositionClosure`);
  - the closure's `held` and `declared` sets.
- In one phase function: first run `caseOnlyMatches` over the own closure, then judge every preset item it did not
  name.
  - A `must_have` item that is not held answers `PRESET_ITEM_NOT_HELD`.
  - Any other item that is not declared as its kind answers `PRESET_ITEM_UNDECLARED`.
- An item of another kind never satisfies the entry. Mission types are items too (`Preset.required()` covers
  `mission_type`).
- Faults are `ClosureFault`s (WP01's factory) with the `Origin` `closure.preset-items`. The violation pointer is the
  preset file and `/activated_<kinds>/<index>`. The detail names the preset, the `<kind>:<id>`, whether it is
  required, and the own closure's packs in `compositionClosure` order. Order: pack id, preset path, item index.
- `PresetItemRulesTest` cases, red first:
  - unheld `must_have`, held by no pack;
  - held only outside the closure, by a dependent;
  - held by the `parent_pack` (accepted);
  - declared but not held and not `must_have` (accepted);
  - undeclared;
  - wrong kind;
  - a case-only mismatch, which gets the case-only fault only;
  - two faults in one preset, with their order.

### T014 — Wiring
- `PackRulesets.closure(...)` and `closureFaults(...)` take each pack's presets, replacing `presetIdsOf` outright.
  The preset phase runs the contests, then case-only, then preset items. `PackSetCheck.closure` passes the presets.
- `ClosureImpactCheck.walkFaults`: call the same `PackRulesets` phase with `ComposedClosure.facts`, beside
  `OwnClosureWalk`, so registration and every write refuse. Read how `walkFaults` places faults per closure, and keep
  that.
- `OfferingReadService.judged` does **not** call it. A test asserts that a composing read of a stored pack breaking
  the rule succeeds and reports `requiredNotHeld`.

### T015 — Example packs and positive controls
- `built-in/presets/default.yaml` requires `anti_pattern:indirection-for-repetition`. WP10 (rulings R-p and R-q,
  plan D12) builds the `anti_pattern` file kind and adds
  `built-in/anti_patterns/indirection-for-repetition.anti-pattern.yaml`, so the item is held by a file and its graph
  node only declares it. Check that the file is there; if it is not, WP10 has not landed in this lane: stop and
  report. The positive control below must find it held, not merely declared.
- `org-base/presets/spdd.yaml` activates the tactics `reasons-canvas-fill` and `reasons-canvas-review`: add the two
  tactic files to org-base (gain, not drop, to keep the preset's intent).
- A positive-control test asserts that every preset item of every example pack is held or declared within its own
  closure. A planted variant of each fixture (in the test, never in the example packs) asserts the exact code.
- WP04 restamps the manifests. If the manifests' membership changes here (the new files), registration answers
  `manifest_membership`, so add the new files to `pack-manifest.yaml` with WP04's stamp helper, or coordinate: WP04
  runs after this WP in the lane order.

### T016 — Scenarios
- `activation-ruling-changes.feature:513-540` (A10) and the other `must_have` scenarios listed in this WP's owned
  files register packs that would now be refused. For each:
  - a scenario whose intent is the read report (`requiredNotHeld`) seeds the stored pack through a **test-only store
    seed** step in `PackRegistrationSteps` that writes the pack tree straight into storage, bypassing registration
    (an invalid-content path, which the charter allows);
  - a scenario whose intent is a successful registration gets a held item.

  Keep each scenario's intent (delete-the-assertion-not-the-test).
- `RequiredNotHeldCoverageTest` counts the scenarios still asserting `requiredNotHeld`, and fails if the count drops
  to zero.
- New `registration/preset-items.feature`:
  - registration refuses `preset_item_not_held` and `preset_item_undeclared`, with the code, pointer and detail;
  - the case-only answer keeps its code;
  - a control registers the same pack once the item is held.

## Test commands

```bash
./gradlew :packs:test :pack-validation:test :pack-doctor:test :activations:test
./gradlew :deployable:test     # the contract scenarios
./gradlew :pack-validation:wholePackRoundTrip
```

## Definition of Done
- [ ] Both codes are answered identically by `pack-doctor validate` and registration for the planted cases.
- [ ] Composing reads are unchanged, and `requiredNotHeld` is still asserted by at least one scenario.
- [ ] `defined` is gone, and `held` and `declared` are maintained on read and write paths.
- [ ] Example packs pass with positive controls, and no fixture is exempted.
- [ ] A `### Breaking` fragment, and the contract enum updated.

## Risks and reviewer guidance
- Open-packs content will break: expected, and drafted by WP09.
- A reviewer checks that no new closure traversal was written, and that case-only precedence holds.
