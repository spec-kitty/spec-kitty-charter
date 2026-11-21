---
affected_files: []
cycle_number: 1
mission_slug: pack-doctor-coverage-01M7YMQK
reproduction_command:
reviewed_at: '2026-11-21T14:04:49Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP01
---

# WP01 review, cycle 1 (reviewer-renata): changes requested

Verdict: changes requested, for ONE design issue (Issue 1). Everything else is sound: CompositionFault and the bridge
are gone, the test diffs change only call sites (no expected code, status, detail or order changed), and the gates are
green at 2debe6d (compileJava compileTestJava OK; kernel 3114, packs 7158/6 skipped, pack-validation 465/1 skipped,
pack-check 251 tests, 0 failures; spotlessCheck OK on the four modules).

**Issue 1 [HIGH] Origin duplicates Reason.rule and carries no information.**
Evidence:
- Every production construction of a Reason sets `origin.name()` equal to `rule`: Constraint.applyTo
  (`new Origin(description)`), Reason.passed/failed, Findings (`new Origin(code.value())`), LevelRules.finding,
  WholePackRoundTripRule (`ORIGIN = new Origin(RULE)`), and ClosureFault.reason, which derives the rule from the
  origin (`new Reason(origin.name(), origin, ...)`). Origin is therefore always a function of rule.
- No production code reads Origin to decide anything. The only readers (ClosureImpactCheck:368/583/644) pass it back
  into ClosureFault.of.
- It does not even meet #614's own goal, "a fault records which check produced it": 18 production sites
  (FallbackProfileRule, RolePinRule, ProfileEnhancementRule, PackMutabilityPolicy, RemovalDependents, ImpactRelations,
  EdgeIndex, OfferingReadService phases 1/5/6, EnhancementCompose, PackCompositionResolver) all pass
  `ClosureFault.ORIGIN` = "closure". Only the CLOSURE preset phase differs ("closure.presets"), and the ruleset
  already told those two apart through `rule` before this change.
- ADR-0082 decision 4 (accepted) puts "the identifier of the element it came from" in the ledger, and the
  architectural-patterns page defines Origin as an opaque element identifier (a pack id, or a pack id and a URN).
  A check-name Origin is a shape that a later slice must re-mean and rewrite across about 40 Reason sites. That goes
  against "each slice replaces the old shape outright" (#636) and the charter's canonical-model rule.

Fix (recommended): make Origin the element identifier that ADR-0082 decision 4 names.
- Keep it in the kernel as an opaque value (`Origin(String id)`, non-blank, pure java plus JSpecify). The kernel
  never names a pack.
- packs.domain fills it. For a ClosureFault, use the pack that broke the rule first (`packs.getFirst()`, already
  documented as such), or pack id plus URN where the check has one.
- A reason with no element (a kernel invariant, Constraint, a value object) uses one named constant, for example
  `Origin.NONE` or `Origin.UNATTRIBUTED`, in the way Location has NOWHERE. Do not copy the rule name into it.
- Decouple `rule` from Origin in ClosureFault.reason. `rule` keeps the check name (closure, closure.presets), so
  PackRulesetsClosureTest's rule assertions stay as they are.
- Update ReasonTest and ClosureFaultTest so they assert the element identifier. Keep the "relocated reason keeps its
  origin" and "blank refused" cases.

If the planner deliberately wants the check-name meaning instead, that is a plan-level decision. In that case the
fix is different: give each closure check its own origin (closure.compose, closure.references, closure.relations,
closure.walk, closure.mutability, closure.dependents...), as T001's own examples suggest. Also record in the WP review
notes and for ADR-0083 why Origin is not redundant with `rule`. Either way, do not leave a field that is always equal
to another field.

**Issue 2 [MEDIUM] T001 asks for the Origin choice in the WP's review notes. It is only in the commit message.**
Record it in the WP file (Activity Log / review notes) so that ADR-0083 (WP08) can cite it.

**Issue 3 [MEDIUM] ClosureVerdict is not really a view over Rationale.**
It still owns `List<ClosureFault>`, and `rationale()` is derived from those faults. The view runs the other way, and
nothing in production calls `rationale()` except `isPassing()`. This is acceptable for slice 1: packs, violations and
requiredBy have no slot in Rationale, and slice 7 removes this. But the Javadoc should say exactly that, for example
"the faults, with their Rationale derived", and not claim to be a view over Rationale.

**Issue 4 [LOW] The same lambda is repeated.**
`refusal -> ClosureFault.of(ClosureFault.ORIGIN, refusal.failure().code(), refusal.detail(), refusal.packs())`
appears 5 times (EdgeIndex:666, OfferingReadService x4). Once Issue 1 changes what the origin is, put it in one
helper next to the resolver's Refusal, in the application layer, so that ClosureFault keeps a single `of`. Do not
reintroduce `of(Refusal)`.

Not issues (checked):
- No behavior change: Problems.ofFault maps through PackCodes.refusalOf, an equivalent mapping. Every closure code is
  a VIOLATION, so ClosureVerdict.isPassing is unchanged.
- The of(CompositionFault) bridge had no production caller, so the "closure.presets" rule names are not new on any
  live path.
- OneClosureFaultTypeTest is meaningful: an ArchUnit check on production classes, plus a reflection guard that every
  `of` takes Origin first. `lift` survives until slice 6, as #636 plans.
- The out-of-map edits are minimal. PackSetCheck changes the type only. PackRulesets drops the Phase record because
  the phase now travels on the fault. The packs callers only add the origin argument.

After the fix, re-run the pins (CompositionRulesTest, PackRulesetsClosureTest, ClosureFaultTest, PackSetCheckTest,
ProblemsTest, PackCheckReportContractTest, CompositionGoldenTest). No expected value may change.
WP02 depends on WP01 and must rebase after this cycle.
