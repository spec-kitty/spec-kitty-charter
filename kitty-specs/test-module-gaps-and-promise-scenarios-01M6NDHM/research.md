# Research: Module-local test gaps and promise-first scenario verdicts (Phase 0)

## R-01 Findings re-verified on `origin/main`

- #370: no test in its own module names `Constrained`, `Rationale` (kernel), `SelfReviewStep`, `ModeDefault`
  (profiles) or `CappedBodySubscriber` (packs). PIT inventory: see plan.md "PIT inventory". Profiles' 29 uncovered
  mutants are all `AgentProfile` accessors and `AgentProfile.Builder`; `SelfReviewStep` and `ModeDefault` are records
  whose only behaviour is refusing a null field.
- #374: 27 scenarios (18 + 9) confirmed; no runner executes either folder (`CharterContractSuite` runs only
  `@contract` from the copied `contracts/charter/scenarios/**`).

## R-02 Overlap

- The parallel session's activation and effective-read work (#359, #360, #291) touches `packs` composition and a
  new activations slice; it may touch `profiles`. Check its open branches before the PR.
- `HttpsGuideFetcherTest` is touched by PR #379 and #367's list; this mission adds a separate `CappedBodySubscriberTest`.

## R-03 Decision: test design for #370

- Strengthen `AgentProfileEntityTest` before adding classes (C-001). A profile built with every field reads every
  field back; that is the round trip the profile store relies on, not a test for the mutant's sake.
- `CappedBodySubscriber` is driven by a hand-written `Flow.Subscription`, consistent with the suite's no-mock style.

## R-04 Promise-first scenario verdicts (#374)

The gap analysis (`research/374-gap-analysis.md`, by `researcher-robbie`, applying the QA pack's
`qa-coverage-gap-analysis` steps 1-3 and 5) gives 5 MAP-PARTIAL, 2 WIRE, 18 RETIRE, 2 GAP. Operator rulings
(2026-11-05):

- **ADR-0005/0019 cluster (15):** move under `contracts/charter/scenarios/<cluster>/`, tagged `@WIP`, so they keep
  a home next to the contract without running; the copy rule is unchanged; the README says "not built, by
  decision"; no issue. Decision `01M6NE5YW0Y4G60B62YTX08WXT`.
- **REFUSAL-001 (gap, product):** follow-up issue plus a `@WIP` contract placeholder pointing to it. Decision
  `01M6NE62FJ43XT0VC78YJ128NP`.
- **SHARING-003 (gap, product, service-owned):** follow-up issue. Decision `01M6NE65TB47HSWMTMJTY5KXGT`.
- **WIRE (SHARING-001, SHARING-004):** one `@contract` scenario each in `offering.feature`, red-first to pin the
  `detail`; existing steps.
- **Remaining RETIRE (GOVERNANCE-001, GOVERNANCE-002, REFUSAL-003, DISTRIBUTE-006) and MAP-PARTIAL rows:** the
  `tests/scenarios/charter` files reduce to a pointer per promise (contract scenario, `@WIP` or reserved placeholder
  plus ADR, or follow-up issue), as the #31 rescope asks; `docs/architecture/behavior-and-scenarios.md` "Where they
  live" is updated.
- **Noted, not ruled:** ADR-0048 lines 195-197 (core fetches built-in) versus ADR-0005 d2/d7; affects TRUST-003 and
  DISTRIBUTE-002 wording only. Recorded in the README, not resolved here.
