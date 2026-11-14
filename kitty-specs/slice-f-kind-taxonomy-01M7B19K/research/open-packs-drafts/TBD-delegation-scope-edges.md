# open-packs#TBD-delegation-scope-edges: a `scope` edge from each step's action to each delegation candidate

> Draft for `spec-kitty/spec-kitty-open-packs`, written by charter mission `slice-f-kind-taxonomy-01M7B19K` (WP11).
> The orchestrator files it and replaces `TBD-delegation-scope-edges` with the issue number.

## Title

Add the 79 `scope` edges that 80 substep delegation candidates lack

## Why

Charter slice F (ADR-0077 decision 2, operator ruling `DM-01M7B1YH1GF15RD0G4ZPSA8KW9`) keeps a substep's
`delegates_to` as data, and requires that the step's `action:<type>/<step>` node has a `scope` edge to each
candidate. A pack that breaks it is refused with `substep_delegation_unscoped`: a bare candidate at load, a
qualified one at compose and at registration.

Of the 144 candidates in open-packs' step contracts at `1692a3b`, 64 already have that exact edge. **80 candidate uses
have none; they need 79 distinct edges**, since `research/gathering` delegates to `DIRECTIVE_010` from two substeps.
This corrects the earlier count of 72 in the charter mission's `research/delegation-edge-coverage.md`: that note
counted an edge from the mission type as covering a candidate, which the rule does not. The 8 edges it missed are
`plan/research → tactic:adr-drafting-workflow` and qa's seven `QA_00x` directives, which are scoped from
`mission_type:plan` and `mission_type:qa` only.

**A ruled consequence.** `ActionContextWalk` walks `scope` edges, so each new edge puts its candidate in force for the
step's effective context (ADR-0077, Risks; squad finding D-M10). That is intended: the delegation already names it.

## What

Add one edge per row of the table below, in the graph document of the pack that holds the step:

- foundation and writing: `action.graph.yaml`, after the existing edges of the same source;
- qa: `drg/fragment.yaml`.

The target is written as the candidate is written in the folded substep (draft
[TBD-fold-step-contracts](TBD-fold-step-contracts.md)): bare for an artifact of the same pack, and
`<pack>::<kind>:<id>` for another pack's. All 14 of writing's new edges target `foundation::`, the form writing's
`action.graph.yaml` already uses for its 20 existing ones.

```yaml
- source: action:software-dev/implement
  target: directive:DIRECTIVE_033
  relation: scope
- source: action:documentation/accept
  target: foundation::directive:DIRECTIVE_029
  relation: scope
```

`apply-drafts.py --drafts 3` in this directory adds exactly these on a copy of the repository. Then restamp the
digests and regenerate the pack pages.

Each edge is allowed by the endpoint table (`scope`: action → a guidance kind or agent_profile). The edges are valid
under the current charter pin too, so this draft can land before the pin moves; it is independent of the fold.

## Acceptance

- `census-check.py` in this directory, over a tree with the fold applied, reports 0 unscoped candidates.
- The charter service job, under a pin that includes slice F, registers every pack with 0
  `substep_delegation_unscoped` refusals.
- `scripts/ci/validate-packs.sh` passes.

## Files

- `packs/foundation/action.graph.yaml` (47 edges)
- `packs/writing/action.graph.yaml` (14 edges)
- `packs/qa/drg/fragment.yaml` (18 edges)
- `packs/{foundation,writing,qa}.source.yaml` (digests), `docs/packs/{foundation,writing,qa}.md` (regenerated)

## The edges (generated: 79 edges, 80 candidate uses)

| # | Pack | Source | Target | Substeps that delegate to it |
|---|---|---|---|---|
| 1 | foundation | `action:software-dev/implement` | `directive:DIRECTIVE_033` | `commit` |
| 2 | foundation | `action:plan/plan` | `directive:DIRECTIVE_010` | `read-inputs` |
| 3 | foundation | `action:plan/plan` | `tactic:requirements-validation-workflow` | `decompose-and-decide` |
| 4 | foundation | `action:plan/plan` | `directive:DIRECTIVE_003` | `document-decisions` |
| 5 | foundation | `action:plan/plan` | `tactic:supply-chain-install-safety` | `supply-chain-check` |
| 6 | foundation | `action:plan/plan` | `directive:DIRECTIVE_029` | `commit-plan` |
| 7 | foundation | `action:plan/plan` | `directive:DIRECTIVE_033` | `commit-plan` |
| 8 | foundation | `action:plan/research` | `directive:DIRECTIVE_010` | `read-open-questions` |
| 9 | foundation | `action:plan/research` | `tactic:requirements-validation-workflow` | `gather-and-validate` |
| 10 | foundation | `action:plan/research` | `tactic:adr-drafting-workflow` | `gather-and-validate` |
| 11 | foundation | `action:plan/research` | `directive:DIRECTIVE_003` | `document-decisions` |
| 12 | foundation | `action:plan/research` | `directive:DIRECTIVE_029` | `commit-research` |
| 13 | foundation | `action:plan/research` | `directive:DIRECTIVE_033` | `commit-research` |
| 14 | foundation | `action:plan/review` | `directive:DIRECTIVE_010` | `verify-plan-fidelity` |
| 15 | foundation | `action:plan/review` | `directive:DIRECTIVE_037` | `verify-plan-fidelity` |
| 16 | foundation | `action:plan/review` | `tactic:review-intent-and-risk-first` | `assess-intent-and-risk` |
| 17 | foundation | `action:plan/review` | `tactic:quality-gate-verification` | `verify-decisions-complete` |
| 18 | foundation | `action:plan/review` | `tactic:stopping-conditions` | `verify-decisions-complete` |
| 19 | foundation | `action:plan/specify` | `directive:DIRECTIVE_010` | `frame-problem` |
| 20 | foundation | `action:plan/specify` | `directive:DIRECTIVE_037` | `frame-problem` |
| 21 | foundation | `action:plan/specify` | `tactic:requirements-validation-workflow` | `validate-boundaries` |
| 22 | foundation | `action:plan/specify` | `directive:DIRECTIVE_003` | `document-decisions` |
| 23 | foundation | `action:plan/specify` | `directive:DIRECTIVE_029` | `commit-spec` |
| 24 | foundation | `action:plan/specify` | `directive:DIRECTIVE_033` | `commit-spec` |
| 25 | foundation | `action:software-dev/plan` | `directive:DIRECTIVE_029` | `commit-plan` |
| 26 | foundation | `action:software-dev/plan` | `directive:DIRECTIVE_033` | `commit-plan` |
| 27 | foundation | `action:research/gathering` | `directive:DIRECTIVE_010` | `read-methodology`, `validate-sources` |
| 28 | foundation | `action:research/gathering` | `directive:DIRECTIVE_029` | `commit-register` |
| 29 | foundation | `action:research/gathering` | `directive:DIRECTIVE_033` | `commit-register` |
| 30 | foundation | `action:research/methodology` | `directive:DIRECTIVE_029` | `commit-plan` |
| 31 | foundation | `action:research/methodology` | `directive:DIRECTIVE_033` | `commit-plan` |
| 32 | foundation | `action:research/output` | `tactic:usage-examples-sync` | `verify-citation-completeness` |
| 33 | foundation | `action:research/output` | `tactic:review-intent-and-risk-first` | `assess-publication-readiness` |
| 34 | foundation | `action:research/output` | `tactic:quality-gate-verification` | `gate-publication-approved` |
| 35 | foundation | `action:research/output` | `tactic:stopping-conditions` | `gate-publication-approved` |
| 36 | foundation | `action:research/output` | `directive:DIRECTIVE_029` | `commit-report` |
| 37 | foundation | `action:research/output` | `directive:DIRECTIVE_033` | `commit-report` |
| 38 | foundation | `action:research/scoping` | `directive:DIRECTIVE_037` | `capture-question` |
| 39 | foundation | `action:research/scoping` | `directive:DIRECTIVE_029` | `commit-spec` |
| 40 | foundation | `action:research/scoping` | `directive:DIRECTIVE_033` | `commit-spec` |
| 41 | foundation | `action:research/synthesis` | `tactic:adr-drafting-workflow` | `synthesize-findings` |
| 42 | foundation | `action:research/synthesis` | `directive:DIRECTIVE_029` | `commit-findings` |
| 43 | foundation | `action:research/synthesis` | `directive:DIRECTIVE_033` | `commit-findings` |
| 44 | foundation | `action:software-dev/specify` | `directive:DIRECTIVE_037` | `capture-intent` |
| 45 | foundation | `action:software-dev/specify` | `procedure:example-mapping-workshop` | `map-examples` |
| 46 | foundation | `action:software-dev/specify` | `directive:DIRECTIVE_029` | `commit-spec` |
| 47 | foundation | `action:software-dev/specify` | `directive:DIRECTIVE_033` | `commit-spec` |
| 48 | qa | `action:qa/accept` | `directive:QA_003` | `confirm-evidence-integrity` |
| 49 | qa | `action:qa/accept` | `procedure:qa-go-no-go-readiness-review` | `run-go-no-go-review` |
| 50 | qa | `action:qa/discovery` | `directive:QA_006` | `scope-qa-charter` |
| 51 | qa | `action:qa/discovery` | `tactic:qa-exploratory-charter-writing` | `apply-exploratory-tactics` |
| 52 | qa | `action:qa/implement` | `directive:QA_002` | `report-defects` |
| 53 | qa | `action:qa/implement` | `tactic:qa-flaky-test-quarantine` | `apply-execution-tactics` |
| 54 | qa | `action:qa/implement` | `tactic:qa-test-data-minimization` | `apply-execution-tactics` |
| 55 | qa | `action:qa/implement` | `tactic:qa-regression-suite-selection` | `apply-execution-tactics` |
| 56 | qa | `action:qa/plan` | `directive:QA_005` | `prioritize-by-risk` |
| 57 | qa | `action:qa/plan` | `tactic:qa-risk-based-test-prioritization` | `apply-prioritization-tactic` |
| 58 | qa | `action:qa/plan` | `procedure:qa-test-plan-review-workshop` | `review-test-plan` |
| 59 | qa | `action:qa/review` | `directive:QA_003` | `verify-no-fake-green` |
| 60 | qa | `action:qa/review` | `procedure:qa-defect-triage-ritual` | `triage-defects` |
| 61 | qa | `action:qa/specify` | `directive:QA_001` | `define-traceable-exit-criteria` |
| 62 | qa | `action:qa/specify` | `directive:QA_004` | `define-traceable-exit-criteria` |
| 63 | qa | `action:qa/tasks` | `tactic:qa-pairwise-test-reduction` | `reduce-test-surface` |
| 64 | qa | `action:qa/tasks` | `tactic:qa-boundary-equivalence-partitioning` | `reduce-test-surface` |
| 65 | qa | `action:qa/tasks` | `procedure:qa-coverage-gap-analysis` | `close-coverage-gaps` |
| 66 | writing | `action:documentation/accept` | `foundation::directive:DIRECTIVE_029` | `commit-acceptance` |
| 67 | writing | `action:documentation/accept` | `foundation::directive:DIRECTIVE_033` | `commit-acceptance` |
| 68 | writing | `action:documentation/audit` | `foundation::directive:DIRECTIVE_029` | `commit-gap-analysis` |
| 69 | writing | `action:documentation/audit` | `foundation::directive:DIRECTIVE_033` | `commit-gap-analysis` |
| 70 | writing | `action:documentation/design` | `foundation::directive:DIRECTIVE_029` | `commit-plan` |
| 71 | writing | `action:documentation/design` | `foundation::directive:DIRECTIVE_033` | `commit-plan` |
| 72 | writing | `action:documentation/discover` | `foundation::directive:DIRECTIVE_029` | `commit-spec` |
| 73 | writing | `action:documentation/discover` | `foundation::directive:DIRECTIVE_033` | `commit-spec` |
| 74 | writing | `action:documentation/generate` | `foundation::directive:DIRECTIVE_029` | `commit-docs` |
| 75 | writing | `action:documentation/generate` | `foundation::directive:DIRECTIVE_033` | `commit-docs` |
| 76 | writing | `action:documentation/publish` | `foundation::directive:DIRECTIVE_029` | `commit-release` |
| 77 | writing | `action:documentation/publish` | `foundation::directive:DIRECTIVE_033` | `commit-release` |
| 78 | writing | `action:documentation/validate` | `foundation::directive:DIRECTIVE_029` | `commit-audit-report` |
| 79 | writing | `action:documentation/validate` | `foundation::directive:DIRECTIVE_033` | `commit-audit-report` |
