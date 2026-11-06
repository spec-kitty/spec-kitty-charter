---
affected_files: []
cycle_number: 1
mission_slug: activation-register-and-closure-01M6NXBD
reproduction_command:
reviewed_at: '2026-11-06T00:39:20Z'
reviewer_agent: claude:claude-opus-5-5:reviewer-renata
wp_id: WP13
---

# WP13 review (reviewer-renata) - changes requested

## Blocking

**Issue 1 - The production fixed point of the shared walk is untested (FR-015, NFR-007).**
`ActivationExclusionService.Shared.fixedPoint()` is the only production driver of `CascadeRules.planShared` to its fixed point (WP12 drives it only from a test helper in `CascadeRulesDeactivateTest`). Mutation: insert `if (plan != null) return ActivationWrites.walk(session, plan);` as the first statement after `ReachPlan plan = plan(Reach.empty());` (one shared step, no loop). `ActivationExclusionServiceTest` stays fully green, and so would the feature: every fixture (US4 AS1, US4 AS2 with e2 already selected) reaches its fixed point in one step. `walked() hasSizeGreaterThanOrEqualTo(2)` cannot tell one step from two.

Fix: in `ActivationExclusionServiceTest` (with `FakeAvailablePacks`), add the two-step case through `service.deactivate(...)`. Use WP12's D/C/C2/E2 shape or the C/C3/C4/C5 shape from `aKeptCandidateThatEnhancesAnotherCandidateKeepsItAndWhatItReaches`, where a candidate kept by an active enhancer enhances another candidate. Assert:
- the stored `selected` and `applied`, plus every `skippedShared` entry with its `referencedBy`;
- `packs.calls() == 1` (one read for the whole request);
- the exact number of walks (`1 + 2`), so that a single step, or a loop that never ends, fails.

Confirm the mutation above turns the test red. A matching scenario in activation-selections.feature is welcome but not required.

## Verified (no change needed)
- Gates: `:contracts:check -PcontractBaseline=none :charter-api:compileJava :activations:check` green (377 tests, 0 failed). The deployable gate (CharterContractSuite, ReservedOperationCoverage, ReservedRefusalCodes, OperationCoverage, Hexagonal, ContractDescriptions, ReservedActivationOperation) is green: 415 tests, and activation-selections.feature passes 32/32. The red list from 06d0be2 (32 names, each 501) comes before the green commit 8a53b3f.
- Reserved bookkeeping: 6 `@reserved` scenarios over 5 operations, with the binding pin on `applyActivationPreset`.
- Mutation `pulled = Set.of()` (skip the includeDeactivated clearing) is killed.
- US3 AS1 has e absent from `applied` and `selected`. US3 AS3 has e in `cleared` and `applied` and absent from `selected`. US4 AS2 has e2 in `referencedBy`. Cross-pack `shared::xt` is covered.
- Precondition order 428 -> 404 activation -> 412 -> 422 kind/cascade -> session -> 404 artifact/mission_type -> 409 -> CAS 412 matches FR-010. A refused precondition reads no packs (`packs.calls() == 0`). `mission_type` on the exclusions path gets a 422 at `/kind` and never reaches the domain.
- Decisions accepted:
  - The `up` link without `self` is correct. Links is an open map, the operations never answered 2xx before, and the fragment records it.
  - `ActivationArtifactNotFound` (which names only raised codes) is better than reusing `ActivationNotFound`.
  - 404 `activation_not_found` under If-Match is correct by RFC 9110 §13.2.1, since preconditions are ignored when the unconditional answer would be 404. §13.1.1 only applies once preconditions are evaluated. No alignment is needed now. The register's DELETE, which answers 412 for a missing id with If-Match, is the outlier: a follow-up, not WP13's scope.
  - The out-of-map edits (errors.feature, ContractDescriptionsTest, OperationCoverageTest, ReservedActivationOperationTest, ActivationTestWiring, `ActivationDocument.withoutSelected`) follow from the contract change and the new constructor.
- Complexity: new methods are within CC/NPath/length/nesting limits by hand. No suppressions, no `@Disabled`.
