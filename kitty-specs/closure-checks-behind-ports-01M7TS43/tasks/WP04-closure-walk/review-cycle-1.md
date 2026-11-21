---
affected_files: []
cycle_number: 1
mission_slug: closure-checks-behind-ports-01M7TS43
reproduction_command:
reviewed_at: '2026-11-20T08:16:44Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP04
---

# WP04 review (claude::reviewer-renata) - changes requested

The production code is sound: `ClosureFacts` names only domain/kernel types, `ClosureView` implements it with no cast,
the three `Breach` records are gone, the walk's order is fallback, pins, enhancements, and the bridge keeps single-breach
refusals as they were (code, "In the closure rooted at ..." detail, packs = involved then roots; `ClosureFacts.named`
reads the same `OfferingEntry` derived from the staged descriptor that `staged.descriptors().find` read). The targeted
tests and the ArchUnit gate are green. The rejection is for test coverage of FR-005 and an undeclared plan deviation.

## MEDIUM - must fix

1. **FR-005 "every breach" is untested for two of the three rules.**
   `service/packs/src/test/java/ai/speckitty/charter/packs/application/OwnClosureWalkTest.java:61-68` stubs
   `pinConflict` to `List.of()` and `profileEnhancements` to `Optional.empty()`, and no impact test registers a closure
   with two conflicting roles or two enhanced profiles that both drop. If `RolePinRule` or `ProfileEnhancementRule`
   regressed to "first breach only", every test would still pass (DIRECTIVE_041).
   Add, in `OwnClosureWalkTest` (cheap, over a stub `ClosureFacts`):
   - two roles whose pins conflict give two `ROLE_PIN_CONFLICT` faults, in `pinnedRoles()` order, each naming its packs
     via `named(...)`, after the pins' own faults;
   - two enhanced profiles whose merges both drop give two `PROFILE_ENHANCEMENT_DROPS_FIELD` faults, in URN order, each
     naming its enhancing pack (stub `ProfileEnhancementGateway.firstDrop`).
   Assert the codes, the order and the packs.

2. **Undeclared deviation from T013 / squad A1.** The plan ("Ports", line ~118) and A1 say `ClosureRoleFacts` and
   `ProfileEnhancers` are reworked to read `ClosureFacts`; instead `ClosureView` implements `ClosureFacts` by delegating
   to them (`ClosureView.java:1165-1198`). The inversion is a reasonable design (the seam is real and no domain type
   names the application ring), but the commit only declares the merge-refusal deviation. Declare this one too
   (commit/WP history note), so the plan and the ADR-0075 amendment in a later WP can be aligned (DIRECTIVE_010).

## LOW - fold in if convenient

3. Merge-refusal deviation: judged correct. The previous `ProfileEnhancementRule` already returned no breach when
   `closure.merged()` refused, with the same comment, so no write that was refused before now passes. Note that a merge
   refusal raised through `winnerPackOf` (fallback and pin rules) still propagates out of the walk as a `CharterRefusal`,
   as before. T016's "a merge refusal gives one fault" and plan line ~119 are now stale; the coordinator should amend
   them. Consider one test that pins the kept behaviour (a profile enhancement over a closure whose layers do not merge
   is no `profile_enhancement_drops_field` fault).
4. `ProfileEnhancementRule.java:45`: the ISE for a missing gateway now no longer fires when the merge fails, and does
   fire when an earlier fallback/pin breach exists (all three rules now run). Production always supplies
   `AgentProfileEnhancements.INSTANCE`; acceptable, defect path only.
5. `ClosureFacts.java:95-102`: the Javadoc says "copies the maps" but `enhancersOf` is not copied (only null-checked).
   Copy it preserving order (e.g. `Collections.unmodifiableMap(new LinkedHashMap<>(...))`) or fix the Javadoc.
6. `ClosureView.java:1165-1177`: `ClosureRoleFacts.of(this)` is rebuilt on every `pinsOf`/`pinnedRoles`/`pinConflict`
   call (once per conflicting pack in `RolePinRule`). Linear and small, but memoizing once per view is cheap.
