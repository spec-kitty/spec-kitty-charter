---
affected_files: []
cycle_number: 1
mission_slug: closure-checks-behind-ports-01M7TS43
reproduction_command:
reviewed_at: '2026-11-20T09:33:29Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP05
---

# WP05 review (claude::reviewer-renata) - changes requested

The design and the code are right. The rename, the port, the removal's dependents, the one-session uninstall, the
census and the caller wiring all hold. The single-fault paths (`inClosure`, `withinClosure`) map exactly as the old
`RegistrationImpact` did, and `pack_has_dependents` is field-for-field identical. The targeted suites pass.

The rejection is about two test gaps. Both are behaviour this WP added, and both are cheap to close.

## MEDIUM 1: US3.2 has no test, and the removal's own collection path is untested

- **Where.** `ClosureImpactCheck.judgeRemoval`, lines 223-241: its own `faults` list, the early return before the
  gate, and `Notices.NONE`.
- **Why it matters.** T022 lists US3.2, and SC-001 requires every US scenario to map to a passing test. WP07 does not
  cover US3. The declared deviation ("no dedicated fixture") leaves the scenario with no test anywhere in the mission.
- **Why "two closures" cannot be built.** By ADR-0081 D6, once there are no dependents, the only closures left are
  those of activations that list no packs. They all root at every configured pack, so `ClosureRoots` gives one closure.
  Record this in the WP history or the spec. Do not try to build a second closure.
- **The intent can be tested cheaply** in `PackUninstallServiceTest`:
  1. `rig.register("qa", rig.stage(qa()), Preconditions.none())`.
  2. `rig.install("acme", ...)`, raw, with two separate enhances cycles (the fixture of
     `ClosureImpactCheckTest.theCensusKeepsOneFindingPerPackWithEveryFaultOfItsClosure`). `acme` does not depend on
     `qa`.
  3. `rig.activations.add(Optional.empty())`.
  4. A COMMIT `uninstall(rig, "qa", "*", ...)` gives `422 enhances_cycle`, its message ends
     `(2 fault(s); every one is in violations)`, its violations are `[enhances_cycle, enhances_cycle]`, and `qa` is
     still a directory.
  5. A DRY_RUN gives the same refusal as `refused()`.

## MEDIUM 2: FR-009 step 3 (the gate runs only when nothing failed) is not pinned

- **Where.** `ClosureImpactCheck.judgeWrite`, lines 280-285.
- **The gap.** Before this WP, the gate could not run after a fault, because the fault threw. Now an explicit
  `if (!faults.isEmpty()) return` is all that keeps it out. If that line is deleted, every test still passes, and a
  refusing gate would replace the closure's refusal with `prompt_slot_missing`.
- **The fix.** In `ClosureImpactCheckTest`, reuse `Gate(Optional.of(SLOT_MISSING))` and `gated(rig, gate)`. Judge a
  write that breaks a relation, for example
  `ImpactCheck.Write(base, facts -> <facts with a self-override edge>, Optional.empty())` or a `Registration` whose
  replacement carries a self-override. Assert:
  - the verdict fails with `self_override`, or `refusal(...)` is `self_override` and not `SLOT_MISSING`;
  - `gate.judged` is empty.

## LOW (fold or note, no action required for approval)

- **CommitStorePackWriteStore.java:124-125.** The `@Autowired` constructor still falls back to
  `new ClosureImpactCheck(...)` through `ObjectProvider<ImpactCheck>.getIfAvailable`. The commit message says the
  Spring injection names only the port. Either inject `ImpactCheck` directly, which is A6 as planned, or correct the
  declared deviation to include this fallback. It is dead in production, because `ClosureImpactCheck` is a
  `@Component`.
- **PackRegistrationService.java.** It names `ClosureImpactCheck`, not `ImpactCheck`, because it needs `committed()`.
  T020 says the callers name `ImpactCheck`. This is acceptable, but declare it.
- **ClosureImpactCheckTest.java:1198-1201.** The two assertions are now identical. The old `check(pack, write)` and
  `check(pack, write, Optional.empty())` overloads both map to the same `Write`. Drop one.
- **Stale `RegistrationImpactTest` references.** `docs/architecture/composition-and-activation-rules.md:505,585-586`,
  `docs/conformance/divergences.yaml:2166` and `docs/conformance/divergence-register.md:868` still name the old test.
  DIRECTIVE_037 asks for these to change in the same change. No WP owns these files, so fix them here or at closeout.
