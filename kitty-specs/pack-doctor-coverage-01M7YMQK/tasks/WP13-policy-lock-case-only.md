---
work_package_id: WP13
title: The case-only policy lock in pack-doctor's CLOSURE (ruling R-y)
dependencies:
- WP03
- WP05
- WP08
requirement_refs:
- FR-016
- FR-012
- NFR-001
- NFR-003
- SC-001
- C-001
- C-002
planning_base_branch: feat/pack-doctor-coverage
merge_target_branch: feat/pack-doctor-coverage
branch_strategy: Planning artifacts for this mission were generated on feat/pack-doctor-coverage. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-doctor-coverage unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-doctor-coverage-01M7YMQK
base_commit: 46fd28f39ee488541977374e3560840f0c5b4cc9
created_at: '2026-11-23T07:31:35.902563+00:00'
subtasks:
- T051
- T052
- T053
- T054
- T055
phase: Phase 2 - The rules
history:
- at: '2026-11-23T08:00:00Z'
  actor: planner-priti
  action: Prompt generated for ruling R-y (the case-only policy lock folded into this PR) and R-x (FR-001's ClosureVerdict clause deferred to issue 636 slice 7)
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PolicyLockRules.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PolicyLockRulesTest.java
- contracts/charter/changes/2026-11-23-pack-doctor-policy-lock-case-only.md
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PolicyLockRules.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PolicyLockRulesTest.java
- contracts/charter/changes/2026-11-23-pack-doctor-policy-lock-case-only.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP13 - The case-only policy lock in pack-doctor's CLOSURE (ruling R-y)

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`AGENTS.md`, `.kittify/charter/charter.md`, ADR-0082 (rules in `PackRulesets`, the domain owns the walks), ADR-0083,
the mission `spec.md` (FR-016, US1 scenario 5), `plan.md` (**D15, read it whole**, and D3 for the preset-item phase it
follows), `data-model.md` ("Case-only policy lock") and the rulings R-x (`DM-01M83MFVJZX16G6NBM4YPB9XYG`) and R-y
(`DM-01M83MFXWH2NZQ8CJWFW1JTPY6`) in `decisions/`.

## Objective

`pack-doctor validate` answers a policy lock that names an artifact of the pack's own closure only by letter case
exactly as registration does (ruling R-y): `pack_reference_artifact_missing`, naming the mentioning pack then the
holder, with the case-only detail and no violation of its own. The rule is one `CLOSURE` phase of the domain factory,
after the preset items. Registration, every write and the composing read do not change. WP13 also records R-x and R-y
in ADR-0083 and the guide.

## Branch Strategy

- Planning branch and merge target: `feat/pack-doctor-coverage` (PR to `main`). Mission topic branch:
  `kitty/mission-pack-doctor-coverage-01M7YMQK`.
- The workspace comes from `lanes.json`. Implement with
  `spec-kitty agent action implement WP13 --agent claude --mission pack-doctor-coverage-01M7YMQK`.
- Commits: no model names and no Co-Authored-By trailer (repository rule). Never push to `main`.

## Context

- **Registration already refuses it.** `OfferingReadService.judged` phase 6 runs
  `PackCompositionResolver.caseOnlyMatches(composed, mentionsOf(facts))` (`OfferingReadService.java:450-475`);
  `mentionsOf` is every preset item URN and every URN key of `facts.policy().urnEntries()`. The fault is the resolver
  refusal lifted as it is (`ResolverFaults::of`), and `ClosureImpactCheck.place` prefixes "In the closure rooted at
  ...". The contract scenario `activation-register.feature:334` ("A policy lock that names a held id only by case
  refuses the closure") pins it on the read side.
- **pack-doctor does not.** `PackRulesets.closureFaults` (`PackRulesets.java:95-110`) is compose, preset contests,
  preset items. The case-only check runs only per preset item, inside `PresetItemRules.itemFault` (`:152-197`), whose
  fault-building (`ClosureFault.of(PackOrigins.of(pack, urn), RULE, refusal.failure().code(), refusal.detail(),
  refusal.packs())`) is the model for this WP.
- **Own closure.** A pack's own closure is `PackCompositionResolver.compositionClosure` rooted at it (R-k), computed by
  `PresetItemRules.ownClosure`. Share that helper (package-private); do not copy or write a traversal (NFR-003).

## Owned files and justified out-of-map edits

`owned_files` lists only the files this WP creates. The other files it edits belong to approved WPs (WP02, WP03,
WP05, WP08); owning them here would merge those started lanes into one, which `finalize-tasks` refuses
(`LANE_MEMBERSHIP_FROZEN`). WP13 runs in its own lane, `lane-l`, which depends on `lane-h` (all integrated code), so
it starts from the files below as they landed. They are **justified out-of-map edits**, each minimal and listed in the
review notes:

- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PackRulesets.java` (WP03): the fourth stream of
  `closureFaults` and the Javadoc of `closure(Function)` and `closureFaults`.
- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PresetItemRules.java` (WP03): `ownClosure`
  becomes package-private, nothing else.
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/FullPassParityTest.java` (WP05): one row in
  `closureRows()`, and a planted-policy helper if `acme(...)` needs one.
- `docs/adr/0083-one-answer-on-every-surface-preset-items-stale-manifests-and-pack-doctor.md` (WP08): R-x, R-y.
- `docs/guides/check-packs.md` (WP02/WP08): the `CLOSURE` text.
- `service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/set/PackSetCheck.java` (WP03): its class
  Javadoc (`:42-43`, "the contests, then the preset items") gains "then the policy locks", if you touch nothing else.

## Subtasks

### T051 — Red first: the domain test and the parity row
- New `PolicyLockRulesTest` (`packs.domain.rules`), built as `PresetItemRulesTest` builds its packs and facts. Each case
  calls `PolicyLockRules.phase(factsOf)` and asserts code, packs, detail and violations exactly:
  - a lock `directive:directive_001` where the pack holds `directive:DIRECTIVE_001`: one fault,
    `pack_reference_artifact_missing`, packs = [the pack, the holder], the resolver's case-only detail, no violation,
    rule `closure.policy-locks`;
  - the same where the holder is a `depends_on` pack: the same fault, the holder second;
  - a lock held exactly: no fault;
  - a lock no pack of the own closure holds in any case: no fault (it changes no write);
  - a case match held only by a pack that depends on the declaring pack (outside its own closure): no fault;
  - a pack with no policy: no fault.
- `PackRulesetsClosureTest` is WP03's, so put the order case in `PolicyLockRulesTest` instead: one pack with a
  case-only preset item and a case-only lock, through `PackRulesets.closureFaults`, gives the preset item's fault and
  then the lock's (registration's phase 6 order for one pack, plan D15).
- `FullPassParityTest.closureRows()`: a row "a policy lock that names a held artifact only by case" on `acme` (its
  `pack.yaml` gains `mutability_policy: policy.yaml`, and `policy.yaml` is `schema_version: 1` with
  `artifacts: {directive:a: locked}` where acme holds `directive:A`), code `pack_reference_artifact_missing`,
  violations empty, `fullReports` true. Read how the case-only preset row plants acme, and follow it.
- Run them: the domain test fails to compile or fails, and the parity row fails because `validate` passes the pack while
  registration refuses it. Record the red output in the review notes.

### T052 — `PolicyLockRules`
- `packs.domain.rules.PolicyLockRules`, final, no instances. `public static final String RULE =
  "closure.policy-locks"`, and `public static Function<List<PackUnderResolution>, List<ClosureFault>>
  phase(Function<PackUnderResolution, PackCompositionFacts> factsOf)`, as `PresetItemRules.phase`.
- For each pack in pack-id order whose `factsOf.apply(pack).policy().urnEntries()` is not empty: its own closure from
  `PresetItemRules.ownClosure` (skip the pack when it is empty, as the preset-item phase does), then
  `PackCompositionResolver.caseOnlyMatches(closure, member -> member is the pack ? its lock URNs as written : none)`.
  Each `Refusal` becomes `ClosureFault.of(PackOrigins.of(pack id, lock urn), RULE, refusal.failure().code(),
  refusal.detail(), refusal.packs())`, no violation.
- Class Javadoc: the rule, R-y, the own closure (R-k), that registration's phase 6 answers the same, that a composing
  read and registration are unchanged, and that the read's `mentionsOf` moves into the factory with #636 slice 7.
- No new code, no `PackSetVerdict.Check` value, no walk (NFR-003), no application or infra import (ArchUnit).

### T053 — Wired into `CLOSURE`
- `PackRulesets.closureFaults`: `PolicyLockRules.phase(factsOf).apply(packs)` is the fourth stream, after
  `presetItemFaults`. `closure(Function)` takes it through `closureFaults` (one derivation). Update both Javadocs
  (the phase list, the rule names).
- Do not add it to `ClosureImpactCheck` or `OfferingReadService`: registration already answers it in phase 6.

### T054 — Every surface green
- `PolicyLockRulesTest`, `PresetItemRulesTest`, `PackRulesetsClosureTest`, `CompositionGoldenTest` and the `PackSetCheck`
  tests green with no expected-value edit (a set that passed still passes; the new phase is appended last).
- `FullPassParityTest` green, every row (registration and `validate --full` give the same code).
- The contract scenarios green unchanged, `activation-register.feature:334` included.
- `pack-doctor validate` over the example packs still exits 0 (no example policy lock is case-only); the whole-pack
  gate stays green.

### T055 — Records
- ADR-0083 (WP08's):
  - Status: "rulings R-a to R-y" (2026-11-21 to 2026-11-23).
  - The rulings table: rows R-x (`01M83MFVJZX16G6NBM4YPB9XYG`, "Defer to #636 slice 7 (Recommended)", with the
    rationale per the table's quoting rule; what it decides: decision 15) and R-y (`01M83MFXWH2NZQ8CJWFW1JTPY6`, "Fold
    into this PR", rationale; what it decides: the new decision below). Read the decision files for the exact text.
  - Decision 15: "(#614, slice 1 of #636; R-o, R-x)": `ClosureVerdict` stays a fault list with a derived `Rationale`
    by ruling R-x, and #636 slice 7 removes it.
  - One new decision, appended after decision 19 (no renumbering): "**The case-only policy lock** (R-y)". pack-doctor's
    `CLOSURE` answers a policy lock that names an artifact of the own closure only by case as registration's phase 6
    does (`PolicyLockRules`, after the preset items); registration and the composing read are unchanged; the read's
    `mentionsOf` moves into the factory with #636 slice 7. Decision 4's pack-doctor bullet may name it in one clause.
  - Alternatives, "Batch 5 (2026-11-23)": R-x's "Do it in this mission" and R-y's "Follow-up via #636 (Recommended)"
    were declined.
  - References: `plan.md` (D1 to D15).
- `docs/guides/check-packs.md`: after the preset-item table, one short paragraph: a lock of the pack's policy file
  (`mutability_policy`) that names an artifact of its own closure only in other letter case is
  `pack_reference_artifact_missing`, naming the packs, as registration answers it; a lock on an id the closure does not
  hold is not a problem.
- New `contracts/charter/changes/2026-11-23-pack-doctor-policy-lock-case-only.md`, one `### Changed` block (as the
  `--full` fragment is): `pack-doctor validate` (`CLOSURE`) now reports a case-only policy lock as
  `pack_reference_artifact_missing`, as registration already refused it (ruling R-y); no code, schema or HTTP behaviour
  changes; core's seam: none. Do not edit WP03's preset-item fragment.
- `check_docs.py`, markdownlint-cli2 and cspell over the changed docs.

## Test commands

```bash
./gradlew :packs:test --tests '*PolicyLockRulesTest' --tests '*PresetItemRulesTest' --tests '*PackRulesetsClosureTest'
./gradlew :packs:test :pack-validation:test :pack-doctor:test
./gradlew :deployable:test     # FullPassParityTest and the contract scenarios
./gradlew :pack-validation:wholePackRoundTrip
./gradlew spotlessCheck
```

No PIT (C-003).

## Definition of Done
- [ ] `PolicyLockRulesTest` and the parity row were red before T052/T053 (output in the review notes) and are green.
- [ ] `PolicyLockRules` is the only new rule body; it calls `caseOnlyMatches` over the own closure from the shared
      `ownClosure`, with no new walk and no new code.
- [ ] `PackRulesets.closureFaults` is compose, contests, preset items, policy locks; earlier findings and goldens are
      unchanged.
- [ ] Registration, `ClosureImpactCheck`, `OfferingReadService` and the composing read are untouched.
- [ ] ADR-0083 records R-x and R-y; the guide and a `### Changed` fragment describe the check.
- [ ] Every out-of-map edit is listed in the review notes.

## Stop conditions
- Validate mode's facts carry an empty policy for a pack whose `pack.yaml` names one (the planted row stays green
  after T053): the policy is not read on the validate path. Stop and report; the fix is at the storage edge and is not
  this WP's.
- Registration does not refuse the planted lock at whole-pack registration (the row's registration side passes): the
  premise of R-y is wrong. Stop and report.
- Parity needs a change to registration, the composing read or `PresetItemRules`' judgement: stop and report.
- A test can pass only by excluding a field from the parity comparison or exempting a fixture: stop (charter).

## Risks and reviewer guidance
- A reviewer checks that the fault is built from the resolver's refusal as `PresetItemRules` builds the preset item's
  case-only fault (code, detail, packs), that no violation is added, that the own closure is shared not copied, and that
  the phase is appended last.
- Open-packs: a pack whose policy locks an id by case only was already refused at registration; `validate` now says so.
  No new draft is needed beyond WP09's repin draft.
