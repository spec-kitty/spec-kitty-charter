---
affected_files: []
cycle_number: 1
mission_slug: activation-register-and-closure-01M6NXBD
reproduction_command:
reviewed_at: '2026-11-05T23:55:18Z'
reviewer_agent: claude:claude-opus-5-5:reviewer-renata
wp_id: WP12
---

# WP12 review (reviewer-renata): changes requested, one blocking issue

Gates are green: `:activations:test --tests '*Cascade*'` ran 100 tests (Clearing 60, Select 17, Deactivate 10, Lineage 7, Scope 6), with 0 failures and 0 skipped. `:activations:check` passed. `HexagonalArchitectureTest` ran 59 tests and passed. The domain is pure: it imports only the kernel and java.util, and the purity grep matches only `packs` field names. It has no traversal. `ActivationChange.of` is the single definition. The enhancer property is non-vacuous: I let `pulled` into `selected` and 51 of the 60 Clearing tests failed. Complexity holds by hand: CC ≤ 4, methods ≤ 25 lines, nesting ≤ 1. There are no suppressions and no disabled tests.

## Issue 1 (blocking, FR-015): a candidate kept only by an active enhancer does not keep what it reaches

Fixture: D requires C, and C requires C2. D, C, C2 and E2 are all selected. E2 enhances C. Deactivate D with cascade ["all"].
Today C is kept, because active E2 references it (correct). C2 is removed, because the shared walk starts only from (selected ∪ floor) − target − candidates, so C2 is never reached.
After the write, C is active and requires C2, which is not selected. The read does not re-cascade `requires`; only `enhances` is pulled at read (ADR-0059 D2.2). So the effective set holds C without its requirement. That breaks FR-015: "keeping … anything another active artifact reaches". C is active after the write, and it reaches C2.

Fix it now, because WP13 will bind to the plan API shape. Suggested approach (it keeps the domain traversal-free):
- Ask `enhancersOf(candidates)` before the shared walk, and pass it to `planShared(document, target, scope, ownReach, enhancers, floor)`.
- Add each candidate with an active enhancer (one in the other active set) to the shared walk's starts. Report it as `SharedSkip(candidate, enhancer)`, as today.
- Keep `applyDeactivate` consistent with this. A candidate in the starts is kept, and so is everything the walk reaches from it.
- Add a red-first test with the fixture above. Expected: C and C2 stay; C2 is in `skippedShared` with `referencedBy` C; `applied` = [D] plus whatever else changes.
- Pick one way to handle the remaining fixed-point case (a kept candidate that is itself an active enhancer of another candidate): iterate in the application until stable, or document it as a limitation in the Javadoc and in a test name. Either is acceptable. Saying nothing is not.

## Notes (non-blocking, for WP13)

- (e) `requireDeniable` throws IllegalArgumentException for mission_type. On the exclusions path the `{kind}` parameter is ActivatableKind, which has no mission_type, and the path declares no 400. WP13 must refuse the kind before it reaches the domain, as 404 kind_not_found (following the artifacts.feature convention) or as 422. It must never be a 500.
- With includeDeactivated, a denied artifact the walk reached that is outside the scope keeps its deny entry, because the clearing uses `walkSelected`, not every reached artifact. That reading of FR-013 is defensible. Pin it with a test.
- Deactivate reports in `skippedByScope` only the out-of-scope reached artifacts that are selected. That is fine; mention it in the Javadoc.
