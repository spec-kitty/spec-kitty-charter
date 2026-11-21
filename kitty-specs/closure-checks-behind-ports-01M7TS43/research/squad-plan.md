# Plan-point squad (#556), 2026-11-19

Three read-only delegates ran at the post-plan point-cut, each with its profile loaded:

- `architect-alphonso`, on boundaries, sessions and the onion rules;
- `paula-patterns`, on the fault model and the mappers;
- `reviewer-renata`, on spec fidelity, scope and testability.

All three verdicts were **SOUND WITH CHANGES**. Every finding has one disposition, under the procedure's
findings-disposition contract. "Plan" means `plan.md` and "spec" means `spec.md` of this Mission.

## Adjudicated from the source

- **`CompositionFault`** (`packs/domain/rules/CompositionRules.java:54`). On `main` it is used only by
  `CompositionRules` and its test. #478's open PR #608 plans to consume `List<CompositionFault>` in its CLI
  (`WP03-pack-set-check.md`, `WP04-cli-module.md`, `plan.md`'s `Problems.java`). Folding it into `ClosureFault` now
  would change #478's CLI, which the brief rules out.
- **The rules reach application types.** `RolePinRule` → `ClosureRoleFacts.of(ClosureView)` and
  `ProfileEnhancementRule` → `ClosureView.MergedView` / `ProfileEnhancers.of(MergedView, …)`, as the architect found.

## Findings and dispositions

| # | Lens | Sev | Finding | Disposition | Where |
|---|------|-----|---------|-------------|-------|
| P1 | patterns | HIGH | `ClosureFault` duplicates `CompositionFault` | **deferred_with_rationale, pending the orchestrator.** #478 consumes `CompositionFault` (PR #608), and the brief rules out a CLI change. `ClosureFault.of(CompositionFault)` lifts it with no copy of the logic. Ruled (a) by the operator on 2026-11-20. Unify the two types once #478 lands, tracked in #614. | plan "Fault model", #614 |
| P2 | patterns | HIGH | `Violation` and three `Breach` records kept beside `ClosureFault` | accepted: every rule returns `List<ClosureFault>`, and `ImpactRelations.Violation`, `FallbackProfileRule.Breach`, `RolePinRule.Breach` and `ProfileEnhancementRule.Breach` are deleted | plan "Implementations" |
| P3 / A5 | patterns, architect | HIGH | `reason()` is not total over the codes `of(CharterRefusal)` can lift | accepted: `PackCodes.closure(RefusalCode)` looks up a closed set. `ClosureFault` accepts only that set, and a test maps every code in it. | plan "Fault model" |
| P4 | patterns | MEDIUM | the code is held twice; compose on a `Reason` instead | accepted: `ClosureFault(Reason reason, packs, violations, requiredBy)`, with the `RefusalCode` derived from the reason's token | plan "Fault model" |
| P5 | patterns | MEDIUM | a third mapper, in the domain | accepted: `ClosureVerdict` holds no refusal mapping. `PackRefusals.of(ClosureVerdict)` (made public) is the closure edge's mapper, and the ADR-0075 amendment names it. | plan "Fault model" |
| P6 | patterns | MEDIUM | D1 drops data: `requiredBy` and the fault's own code | accepted: `requiredBy` is unioned. With two or more faults, each fault gives a header violation `("", code, detail)` before its own violations. One fault is answered as today. | plan "Fault model" |
| P7 / R9 | patterns, reviewer | LOW | two styles of count in the detail | accepted: the multi-fault detail follows `PackRefusals.of`'s style: `<first detail> (N fault(s); every one is in violations)` | plan "Fault model" |
| A1 | architect | HIGH | the `OwnClosure` surface is too small; a cast would hide the seam | accepted: `OwnClosure` is a domain record over a domain interface `ClosureFacts`, which `ClosureView` implements with domain types (`holds`, `winnerPackOf`, `isLocked`, the role facts, the profile enhancers). `ClosureRoleFacts` and `ProfileEnhancers` are reworked to read `ClosureFacts`. A merge refusal is a `ClosureFault`. This is IC-02's first task and its main risk. | plan "Ports", IC-02 |
| A2 | architect | HIGH | collected `pack_unreadable` breaks #517's dry run | accepted: a read failure (`pack_unreadable`, unrewritten) is never a `ClosureFault` and always propagates. Test: a dry run of a removal with an unreadable closure member rethrows. | plan "Fault model", "Order" |
| A3 / R7 | architect, reviewer | MEDIUM | the uninstall's session cannot move into the port | changed: the removal subject is `Removal(ConfiguredPack, Optional<PackDescriptor>, Set<Urn> held)`. `ImpactCheck.judge` opens a session. `ClosureImpactCheck.judge(Session, PackChange)`, an application overload, lets the uninstall judge in the session it already holds, so #517's NFR-001 holds. A counting test for the uninstall is added. | plan "Ports" |
| A4 | architect | MEDIUM | the `Rule` views have no consumer, and decision 6 diverges | accepted: the slice-7 amendment names these as seams that the service composes, records that "pack-validation's rules call the ports" is superseded by the 2026-11-16 amendment (no judging port in the validation edge), and records where the references and the floor went | plan "Contract and documents" |
| A6 | architect | LOW | `CommitStorePackWriteStore`'s fallback names the concrete class | accepted: the fallback goes, and the adapter names only `ImpactCheck` (wired in `deployable` and the test roots) | plan "Implementations" |
| A7 | architect | LOW | `Writes(..., boolean staged)` is a flag argument | accepted: the subject is sealed, `Live(Pack, List<Write>)` or `Staged(Pack, List<Write>)` | plan "Ports" |
| R1 | reviewer | HIGH | collect-all duplicates faults (ungrounded on a cycle; self-loop as `override_cycle`) | accepted: FR-003 drops implied faults. Places on a reported enhances cycle are not judged ungrounded, a self-override edge is not an override cycle, and each cycle is reported once. Tested with violation counts. | spec FR-003, plan |
| R2 | reviewer | HIGH | the census test (`containsExactly`) | accepted: one census `Finding` per pack carries its list of faults, and R1 keeps the `CYCLE` fixture at one fault | plan "Implementations" |
| R3 | reviewer | MEDIUM | FR-009 reorders today's phases | accepted: today's phase order is kept. All closures are composed and judged for relations, then the walk of every single-root closure, then the activations gate. | spec FR-009, plan "Order" |
| R4 | reviewer | MEDIUM | the snapshot after a relation fault is unspecified | accepted: a closure whose compose or relations fail gets no snapshot, walk or notices. A snapshot refusal is that closure's compose-step fault. | plan "Order" |
| R5 | reviewer | MEDIUM | mutability runs before impact | accepted: the mutability check runs first and its refusal ends the write. It is a different status (409) and a different class of refusal. The spec's invariant reads "one refusal per check". | spec Invariants, FR-009 |
| R6 | reviewer | MEDIUM | compose phases not classified | accepted: every compose phase collects its own faults. Phases stay sequential, because a later one reads an earlier one's result. In: `resolve`'s `faults()`, `checkDelegations`, `checkReferences`, `checkStepEnhancementTargets`, `presetContests`, `refuseCaseOnlyMatches`. Out: read failures (A2); `ReferenceSubjects.firstRefusal` (#355, the pack's own references, already collected by `content()` at a registration; a per-file write of one artifact can break only that artifact); the dependents finder's first unreadable pack (a read failure). | spec FR-006, plan "Implementations" |
| R8 | reviewer | LOW | `EdgeIndex` checks a list per pack | accepted: mapped through `PackRefusals.of` | plan "Implementations" |
| R10 | reviewer | LOW | the activations gate is first-fault | deferred_with_rationale: it is the activations slice's own refusal (`prompt_slot_missing`), outside the packs ports. Recorded in the ADR-0075 amendment as out of scope. | ADR-0075 amendment |
| R-c1 | reviewer | (concession) | A1, no floor rule, is correct; `GraphWalk` and `Reachability` are not needed | recorded in the ADR-0075 amendment | ADR-0075 amendment |
