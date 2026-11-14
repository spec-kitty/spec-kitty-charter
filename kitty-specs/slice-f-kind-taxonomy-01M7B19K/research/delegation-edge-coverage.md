# Delegation candidates without a scope edge

> **Correction (post-plan squad, 2026-11-13).** The table below counts a candidate as covered by any edge from the
> action or from the mission type. The ruled rule (FR-005) needs a `scope` edge from the step's action itself. Under
> that exact predicate, 64 of the 144 candidates are covered and **80 are missing** (79 distinct). The 8 extra
> cases are qa's QA_001 to QA_006 delegations and plan/research `adr-drafting-workflow`, which have a scope edge from
> the mission type only. The open-packs draft adds 80 edges.

Open-packs at `1692a3b`. Of 144 delegation candidates in `*.step-contract.yaml`, 72 already have an edge from the step's `action:<type>/<step>` node or its mission type; the 72 below have none. Under research R3 (Q2) the open-packs migration adds a `scope` edge for each. Method: every `delegates_to.candidates` entry, as `<kind>:<candidate>`, matched against the edge pairs of every `*.graph.yaml` and `drg/fragment.yaml` in open-packs, with pack qualifiers stripped.

| File | Substep | Source node | Candidate |
|---|---|---|---|
| `packs/foundation/missions/built_in_step_contracts/implement.step-contract.yaml` | `commit` | `action:software-dev/implement` | `directive:DIRECTIVE_033` |
| `packs/foundation/missions/built_in_step_contracts/plan-plan.step-contract.yaml` | `read_inputs` | `action:plan/plan` | `directive:DIRECTIVE_010` |
| `packs/foundation/missions/built_in_step_contracts/plan-plan.step-contract.yaml` | `decompose_and_decide` | `action:plan/plan` | `tactic:requirements-validation-workflow` |
| `packs/foundation/missions/built_in_step_contracts/plan-plan.step-contract.yaml` | `document_decisions` | `action:plan/plan` | `directive:DIRECTIVE_003` |
| `packs/foundation/missions/built_in_step_contracts/plan-plan.step-contract.yaml` | `supply_chain_check` | `action:plan/plan` | `tactic:supply-chain-install-safety` |
| `packs/foundation/missions/built_in_step_contracts/plan-plan.step-contract.yaml` | `commit_plan` | `action:plan/plan` | `directive:DIRECTIVE_029` |
| `packs/foundation/missions/built_in_step_contracts/plan-plan.step-contract.yaml` | `commit_plan` | `action:plan/plan` | `directive:DIRECTIVE_033` |
| `packs/foundation/missions/built_in_step_contracts/plan-research.step-contract.yaml` | `read_open_questions` | `action:plan/research` | `directive:DIRECTIVE_010` |
| `packs/foundation/missions/built_in_step_contracts/plan-research.step-contract.yaml` | `gather_and_validate` | `action:plan/research` | `tactic:requirements-validation-workflow` |
| `packs/foundation/missions/built_in_step_contracts/plan-research.step-contract.yaml` | `document_decisions` | `action:plan/research` | `directive:DIRECTIVE_003` |
| `packs/foundation/missions/built_in_step_contracts/plan-research.step-contract.yaml` | `commit_research` | `action:plan/research` | `directive:DIRECTIVE_029` |
| `packs/foundation/missions/built_in_step_contracts/plan-research.step-contract.yaml` | `commit_research` | `action:plan/research` | `directive:DIRECTIVE_033` |
| `packs/foundation/missions/built_in_step_contracts/plan-review.step-contract.yaml` | `verify_plan_fidelity` | `action:plan/review` | `directive:DIRECTIVE_010` |
| `packs/foundation/missions/built_in_step_contracts/plan-review.step-contract.yaml` | `verify_plan_fidelity` | `action:plan/review` | `directive:DIRECTIVE_037` |
| `packs/foundation/missions/built_in_step_contracts/plan-review.step-contract.yaml` | `assess_intent_and_risk` | `action:plan/review` | `tactic:review-intent-and-risk-first` |
| `packs/foundation/missions/built_in_step_contracts/plan-review.step-contract.yaml` | `verify_decisions_complete` | `action:plan/review` | `tactic:quality-gate-verification` |
| `packs/foundation/missions/built_in_step_contracts/plan-review.step-contract.yaml` | `verify_decisions_complete` | `action:plan/review` | `tactic:stopping-conditions` |
| `packs/foundation/missions/built_in_step_contracts/plan-specify.step-contract.yaml` | `frame_problem` | `action:plan/specify` | `directive:DIRECTIVE_010` |
| `packs/foundation/missions/built_in_step_contracts/plan-specify.step-contract.yaml` | `frame_problem` | `action:plan/specify` | `directive:DIRECTIVE_037` |
| `packs/foundation/missions/built_in_step_contracts/plan-specify.step-contract.yaml` | `validate_boundaries` | `action:plan/specify` | `tactic:requirements-validation-workflow` |
| `packs/foundation/missions/built_in_step_contracts/plan-specify.step-contract.yaml` | `document_decisions` | `action:plan/specify` | `directive:DIRECTIVE_003` |
| `packs/foundation/missions/built_in_step_contracts/plan-specify.step-contract.yaml` | `commit_spec` | `action:plan/specify` | `directive:DIRECTIVE_029` |
| `packs/foundation/missions/built_in_step_contracts/plan-specify.step-contract.yaml` | `commit_spec` | `action:plan/specify` | `directive:DIRECTIVE_033` |
| `packs/foundation/missions/built_in_step_contracts/plan.step-contract.yaml` | `commit_plan` | `action:software-dev/plan` | `directive:DIRECTIVE_029` |
| `packs/foundation/missions/built_in_step_contracts/plan.step-contract.yaml` | `commit_plan` | `action:software-dev/plan` | `directive:DIRECTIVE_033` |
| `packs/foundation/missions/built_in_step_contracts/research-gathering.step-contract.yaml` | `read_methodology` | `action:research/gathering` | `directive:DIRECTIVE_010` |
| `packs/foundation/missions/built_in_step_contracts/research-gathering.step-contract.yaml` | `validate_sources` | `action:research/gathering` | `directive:DIRECTIVE_010` |
| `packs/foundation/missions/built_in_step_contracts/research-gathering.step-contract.yaml` | `commit_register` | `action:research/gathering` | `directive:DIRECTIVE_029` |
| `packs/foundation/missions/built_in_step_contracts/research-gathering.step-contract.yaml` | `commit_register` | `action:research/gathering` | `directive:DIRECTIVE_033` |
| `packs/foundation/missions/built_in_step_contracts/research-methodology.step-contract.yaml` | `commit_plan` | `action:research/methodology` | `directive:DIRECTIVE_029` |
| `packs/foundation/missions/built_in_step_contracts/research-methodology.step-contract.yaml` | `commit_plan` | `action:research/methodology` | `directive:DIRECTIVE_033` |
| `packs/foundation/missions/built_in_step_contracts/research-output.step-contract.yaml` | `verify_citation_completeness` | `action:research/output` | `tactic:usage-examples-sync` |
| `packs/foundation/missions/built_in_step_contracts/research-output.step-contract.yaml` | `assess_publication_readiness` | `action:research/output` | `tactic:review-intent-and-risk-first` |
| `packs/foundation/missions/built_in_step_contracts/research-output.step-contract.yaml` | `gate_publication_approved` | `action:research/output` | `tactic:quality-gate-verification` |
| `packs/foundation/missions/built_in_step_contracts/research-output.step-contract.yaml` | `gate_publication_approved` | `action:research/output` | `tactic:stopping-conditions` |
| `packs/foundation/missions/built_in_step_contracts/research-output.step-contract.yaml` | `commit_report` | `action:research/output` | `directive:DIRECTIVE_029` |
| `packs/foundation/missions/built_in_step_contracts/research-output.step-contract.yaml` | `commit_report` | `action:research/output` | `directive:DIRECTIVE_033` |
| `packs/foundation/missions/built_in_step_contracts/research-scoping.step-contract.yaml` | `capture_question` | `action:research/scoping` | `directive:DIRECTIVE_037` |
| `packs/foundation/missions/built_in_step_contracts/research-scoping.step-contract.yaml` | `commit_spec` | `action:research/scoping` | `directive:DIRECTIVE_029` |
| `packs/foundation/missions/built_in_step_contracts/research-scoping.step-contract.yaml` | `commit_spec` | `action:research/scoping` | `directive:DIRECTIVE_033` |
| `packs/foundation/missions/built_in_step_contracts/research-synthesis.step-contract.yaml` | `synthesize_findings` | `action:research/synthesis` | `tactic:adr-drafting-workflow` |
| `packs/foundation/missions/built_in_step_contracts/research-synthesis.step-contract.yaml` | `commit_findings` | `action:research/synthesis` | `directive:DIRECTIVE_029` |
| `packs/foundation/missions/built_in_step_contracts/research-synthesis.step-contract.yaml` | `commit_findings` | `action:research/synthesis` | `directive:DIRECTIVE_033` |
| `packs/foundation/missions/built_in_step_contracts/specify.step-contract.yaml` | `capture_intent` | `action:software-dev/specify` | `directive:DIRECTIVE_037` |
| `packs/foundation/missions/built_in_step_contracts/specify.step-contract.yaml` | `map_examples` | `action:software-dev/specify` | `procedure:example-mapping-workshop` |
| `packs/foundation/missions/built_in_step_contracts/specify.step-contract.yaml` | `commit_spec` | `action:software-dev/specify` | `directive:DIRECTIVE_029` |
| `packs/foundation/missions/built_in_step_contracts/specify.step-contract.yaml` | `commit_spec` | `action:software-dev/specify` | `directive:DIRECTIVE_033` |
| `packs/qa/mission_step_contracts/qa-accept.step-contract.yaml` | `run_go_no_go_review` | `action:qa/accept` | `procedure:qa-go-no-go-readiness-review` |
| `packs/qa/mission_step_contracts/qa-discovery.step-contract.yaml` | `apply_exploratory_tactics` | `action:qa/discovery` | `tactic:qa-exploratory-charter-writing` |
| `packs/qa/mission_step_contracts/qa-implement.step-contract.yaml` | `apply_execution_tactics` | `action:qa/implement` | `tactic:qa-flaky-test-quarantine` |
| `packs/qa/mission_step_contracts/qa-implement.step-contract.yaml` | `apply_execution_tactics` | `action:qa/implement` | `tactic:qa-test-data-minimization` |
| `packs/qa/mission_step_contracts/qa-implement.step-contract.yaml` | `apply_execution_tactics` | `action:qa/implement` | `tactic:qa-regression-suite-selection` |
| `packs/qa/mission_step_contracts/qa-plan.step-contract.yaml` | `apply_prioritization_tactic` | `action:qa/plan` | `tactic:qa-risk-based-test-prioritization` |
| `packs/qa/mission_step_contracts/qa-plan.step-contract.yaml` | `review_test_plan` | `action:qa/plan` | `procedure:qa-test-plan-review-workshop` |
| `packs/qa/mission_step_contracts/qa-review.step-contract.yaml` | `triage_defects` | `action:qa/review` | `procedure:qa-defect-triage-ritual` |
| `packs/qa/mission_step_contracts/qa-tasks.step-contract.yaml` | `reduce_test_surface` | `action:qa/tasks` | `tactic:qa-pairwise-test-reduction` |
| `packs/qa/mission_step_contracts/qa-tasks.step-contract.yaml` | `reduce_test_surface` | `action:qa/tasks` | `tactic:qa-boundary-equivalence-partitioning` |
| `packs/qa/mission_step_contracts/qa-tasks.step-contract.yaml` | `close_coverage_gaps` | `action:qa/tasks` | `procedure:qa-coverage-gap-analysis` |
| `packs/writing/missions/built_in_step_contracts/documentation-accept.step-contract.yaml` | `commit_acceptance` | `action:documentation/accept` | `directive:DIRECTIVE_029` |
| `packs/writing/missions/built_in_step_contracts/documentation-accept.step-contract.yaml` | `commit_acceptance` | `action:documentation/accept` | `directive:DIRECTIVE_033` |
| `packs/writing/missions/built_in_step_contracts/documentation-audit.step-contract.yaml` | `commit_gap_analysis` | `action:documentation/audit` | `directive:DIRECTIVE_029` |
| `packs/writing/missions/built_in_step_contracts/documentation-audit.step-contract.yaml` | `commit_gap_analysis` | `action:documentation/audit` | `directive:DIRECTIVE_033` |
| `packs/writing/missions/built_in_step_contracts/documentation-design.step-contract.yaml` | `commit_plan` | `action:documentation/design` | `directive:DIRECTIVE_029` |
| `packs/writing/missions/built_in_step_contracts/documentation-design.step-contract.yaml` | `commit_plan` | `action:documentation/design` | `directive:DIRECTIVE_033` |
| `packs/writing/missions/built_in_step_contracts/documentation-discover.step-contract.yaml` | `commit_spec` | `action:documentation/discover` | `directive:DIRECTIVE_029` |
| `packs/writing/missions/built_in_step_contracts/documentation-discover.step-contract.yaml` | `commit_spec` | `action:documentation/discover` | `directive:DIRECTIVE_033` |
| `packs/writing/missions/built_in_step_contracts/documentation-generate.step-contract.yaml` | `commit_docs` | `action:documentation/generate` | `directive:DIRECTIVE_029` |
| `packs/writing/missions/built_in_step_contracts/documentation-generate.step-contract.yaml` | `commit_docs` | `action:documentation/generate` | `directive:DIRECTIVE_033` |
| `packs/writing/missions/built_in_step_contracts/documentation-publish.step-contract.yaml` | `commit_release` | `action:documentation/publish` | `directive:DIRECTIVE_029` |
| `packs/writing/missions/built_in_step_contracts/documentation-publish.step-contract.yaml` | `commit_release` | `action:documentation/publish` | `directive:DIRECTIVE_033` |
| `packs/writing/missions/built_in_step_contracts/documentation-validate.step-contract.yaml` | `commit_audit_report` | `action:documentation/validate` | `directive:DIRECTIVE_029` |
| `packs/writing/missions/built_in_step_contracts/documentation-validate.step-contract.yaml` | `commit_audit_report` | `action:documentation/validate` | `directive:DIRECTIVE_033` |
