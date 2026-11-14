# open-packs#TBD-fold-step-contracts: fold the 28 step contracts into their steps' substeps

> Draft for `spec-kitty/spec-kitty-open-packs`, written by charter mission `slice-f-kind-taxonomy-01M7B19K` (WP11).
> The orchestrator files it and replaces `TBD-fold-step-contracts` with the issue number.
> Suggested labels: none beyond the repository's defaults (this is planned content work, not a `follow-up`).

## Title

Fold the 28 step contracts into the substeps of their mission-type steps, and remove the step-contract files

## Why

Charter slice F (spec-kitty-charter#537, ADR-0077 decisions 2 and 3) makes the mission-type step the one authoring
surface. A step now carries `substeps`, and the `mission_step_contract` kind is retired outright:

- `contracts/packs/schemas/mission-step-contract.schema.json` is gone, and `mission-type.schema.json` gains
  `Step.substeps` with `Substep`, `SubstepInput` and `SubstepDelegation`.
- `contracts/packs/file-kinds.yaml` lists `**/*.step-contract.yaml` and `**/mission_step_contract.graph.yaml` under
  `retired:`. The charter refuses such a file by name at load and registration, and the open-packs validator reports it
  as a `retired layout` violation.

Open-packs ships 28 `*.step-contract.yaml` files (foundation 14, writing 7, qa 7) and 2
`mission_step_contract.graph.yaml` fragments (21 nodes, 0 edges). Against the charter after slice F, every one of them
is refused. The charter's own example pack (`service/packs/src/testFixtures/resources/example-packs/built-in/`) is
the reference shape of a folded step.

## What

For each step contract, take its `mission` and `action`: they name a step (or an on-demand step) of a mission type
in the same pack. All 28 do (checked against `1692a3b`). Append the contract's `steps[]` to that step as
`substeps`, in order, then delete the contract.

1. **Substep fields.** Each substep keeps every field: `id`, `description`, `command`, `inputs`, `delegates_to` and
   `guidance`. The contract-level keys (`schema_version`, `id`, `action`, `mission`) are not carried; the step is the
   identity.
2. **Ids follow the kernel identifier grammar** `^[a-z][a-z0-9]*(-[a-z0-9]+)*$` (at most 64 characters, unique within
   the step). 115 of the 149 substep ids hold an underscore; each `_` becomes `-` (full map below). No rename collides
   within its step.
3. **Folded scalars.** A `>` folded `description` or `guidance` is written as one line (the folded value without its
   trailing newline), as the example pack does.
4. **Inputs.** No open-packs substep has `inputs` today. If one is added, an input is `{flag, source}` and its
   `optional` may only be written `true` (`const: true`); absent means required.
5. **Delegation candidates are written `[<pack>::]<id>`**, with the kind given once in `delegates_to.kind`.
   - A candidate held by the step's own pack stays bare (foundation's 92, qa's 18).
   - A candidate held by another pack of the closure is qualified with that pack's name. Writing's 34 candidates are
     all foundation artifacts, so they become `foundation::<id>` (for example `foundation::DIRECTIVE_029`). This
     reverses the "candidates stay bare" note of open-packs ADR `2026-11-11-1` (draft TBD-validator-and-docs amends it).
   - `delegates_to.kind` must be a `scope` target of an action: a guidance kind or `agent_profile`. All 144 are
     (86 directive, 50 tactic, 6 procedure, 1 toolguide, 1 paradigm).
   - The kinds stay as open-packs holds them: `execution-lanes` is a **toolguide** and `git-flow` a **procedure** in
     foundation at `1692a3b` (the charter's example pack reclassified both as paradigms; open-packs has not, and this
     draft does not).
6. **Every candidate needs a `scope` edge** from the step's `action:<type>/<step>` node. 80 candidate uses lack one;
   draft [TBD-delegation-scope-edges](TBD-delegation-scope-edges.md) adds them, and it needs the action nodes of draft
   [TBD-step-requires-edges](TBD-step-requires-edges.md). **This draft depends on both: they must be in the tree by
   its pull request, which also moves the pin with draft TBD-validator-and-docs.**
7. **The one gate is not a substep.** The `review` contract's `gates[]` entry (`in_progress->for_review`,
   `spec-kitty-pre-review`, `fail_open: true`) becomes the guard of draft
   [TBD-pre-review-guard](TBD-pre-review-guard.md). Fold the review contract's `steps[]` only.
8. **Delete** the 28 files, the now-empty directories `packs/foundation/missions/built_in_step_contracts/`,
   `packs/writing/missions/built_in_step_contracts/` and `packs/qa/mission_step_contracts/`, and the 2 graph
   fragments.
9. **Restamp** what the content change moves: `python3 scripts/ci/pack_digest.py --update foundation writing qa`,
   and regenerate `docs/packs/{foundation,writing,qa}.md` with `scripts/docs/gen_pack_reference.py` (draft
   TBD-validator-and-docs). The manifests do not list mission types or step contracts, so no constituent changes.
10. **Optional prose fixes, same PR.** The review step's `supply-chain-security-review` description says "the existing
    `in_progress->for_review` gate above is unchanged"; after the fold that gate is the lifecycle guard, so "above"
    should read "the `spec-kitty-pre-review` guard on the work-package lifecycle's `in_progress -> for_review`
    transition". `packs/foundation/missions/research/steps/output.guidelines.md` says the publication gate is
    "enforced by `packs/built-in/missions/built_in_step_contracts/research-output.step-contract.yaml`"; it now lives
    in the `gate-publication-approved` substep of the research type's `output` step.

`apply-drafts.py` in this directory does steps 1 to 8 on a copy of the repository (`--drafts 1`), editing the files
textually so their formatting is kept. It is a content sketch: the PR author may run it on a branch, then review the
diff.

### Example: the folded `review` step of software-dev

```yaml
- id: review
  # ... title, description, role, prompt_path, guidelines_path, expected_input_artifacts as today
  substeps:
  - id: bootstrap
    description: Load charter context for this action
    command: spec-kitty charter context --action review --json
  - id: verify-spec-fidelity
    description: Verify implementation matches specification intent
    delegates_to:
      kind: directive
      candidates:
      - DIRECTIVE_010
      - DIRECTIVE_037
  # ... verify-example-sync, assess-intent-and-risk, supply-chain-security-review, quality-gate,
  #     verify-stopping-conditions
  - id: status-transition
    description: Move WP to approved or request changes
    command: spec-kitty agent tasks move-task {wp_id} --to approved --mission {mission_slug}
    guidance: If changes are needed, move to in_progress with a reason. If review passes, move to approved with evidence. Consolidate later records done.
```

### Example: a writing substep, qualified

```yaml
  - id: commit-acceptance
    description: Commit acceptance evidence or blocker notes to main branch
    delegates_to:
      kind: directive
      candidates:
      - foundation::DIRECTIVE_029
      - foundation::DIRECTIVE_033
```

## Acceptance

- No `*.step-contract.yaml` and no `mission_step_contract.graph.yaml` remains under `packs/`.
- The five mission types that had contracts (software-dev, plan, research in foundation; documentation in writing;
  qa in qa) carry 149 substeps on 28 steps; every substep id matches the grammar; writing's 34 candidates are
  `foundation::`-qualified.
- `scripts/ci/validate-packs.sh` passes against a charter contract that includes slice F (see the ordering note in
  [README.md](README.md)).
- The charter service job (`scripts/ci/charter_service_check.py`) registers every pack with 0 refusals, which also
  proves `substep_delegation_unresolved` and `substep_delegation_unscoped` (needs drafts TBD-step-requires-edges and
  TBD-delegation-scope-edges in the same pin).
- `census-check.py` in this directory reports 0 unresolved and 0 unscoped candidates and 0 retired files.

## Files

Removed (30):

- `packs/foundation/missions/built_in_step_contracts/{implement,plan-plan,plan-research,plan-review,plan-specify,plan,research-gathering,research-methodology,research-output,research-scoping,research-synthesis,review,specify,tasks}.step-contract.yaml`
- `packs/writing/missions/built_in_step_contracts/documentation-{accept,audit,design,discover,generate,publish,validate}.step-contract.yaml`
- `packs/qa/mission_step_contracts/qa-{accept,discovery,implement,plan,review,specify,tasks}.step-contract.yaml`
- `packs/foundation/mission_step_contract.graph.yaml`, `packs/writing/mission_step_contract.graph.yaml`

Changed:

- `packs/foundation/missions/software-dev/software-dev.mission-type.yaml` (specify, plan, tasks, implement, review)
- `packs/foundation/missions/plan/plan.mission-type.yaml` (specify, research, plan, review)
- `packs/foundation/missions/research/research.mission-type.yaml` (scoping, methodology, gathering, synthesis, output)
- `packs/writing/missions/documentation/documentation.mission-type.yaml` (discover, audit, design, generate, validate,
  publish, accept)
- `packs/qa/missions/qa/qa.mission-type.yaml` (discovery, specify, plan, tasks, implement, review, accept)
- `packs/foundation.source.yaml`, `packs/writing.source.yaml`, `packs/qa.source.yaml` (digests)
- Optional prose: `packs/foundation/missions/research/steps/output.guidelines.md`

## The folds (generated)

| Step contract (removed) | Folds into | Step | Substeps | Candidates | Qualified |
|---|---|---|---|---|---|
| `packs/foundation/missions/built_in_step_contracts/implement.step-contract.yaml` | `packs/foundation/missions/software-dev/software-dev.mission-type.yaml` | `implement` | 9 | 10 | 0 |
| `packs/foundation/missions/built_in_step_contracts/plan-plan.step-contract.yaml` | `packs/foundation/missions/plan/plan.mission-type.yaml` | `plan` | 7 | 7 | 0 |
| `packs/foundation/missions/built_in_step_contracts/plan-research.step-contract.yaml` | `packs/foundation/missions/plan/plan.mission-type.yaml` | `research` | 6 | 6 | 0 |
| `packs/foundation/missions/built_in_step_contracts/plan-review.step-contract.yaml` | `packs/foundation/missions/plan/plan.mission-type.yaml` | `review` | 5 | 5 | 0 |
| `packs/foundation/missions/built_in_step_contracts/plan-specify.step-contract.yaml` | `packs/foundation/missions/plan/plan.mission-type.yaml` | `specify` | 6 | 6 | 0 |
| `packs/foundation/missions/built_in_step_contracts/plan.step-contract.yaml` | `packs/foundation/missions/software-dev/software-dev.mission-type.yaml` | `plan` | 7 | 7 | 0 |
| `packs/foundation/missions/built_in_step_contracts/research-gathering.step-contract.yaml` | `packs/foundation/missions/research/research.mission-type.yaml` | `gathering` | 5 | 5 | 0 |
| `packs/foundation/missions/built_in_step_contracts/research-methodology.step-contract.yaml` | `packs/foundation/missions/research/research.mission-type.yaml` | `methodology` | 6 | 6 | 0 |
| `packs/foundation/missions/built_in_step_contracts/research-output.step-contract.yaml` | `packs/foundation/missions/research/research.mission-type.yaml` | `output` | 7 | 8 | 0 |
| `packs/foundation/missions/built_in_step_contracts/research-scoping.step-contract.yaml` | `packs/foundation/missions/research/research.mission-type.yaml` | `scoping` | 6 | 6 | 0 |
| `packs/foundation/missions/built_in_step_contracts/research-synthesis.step-contract.yaml` | `packs/foundation/missions/research/research.mission-type.yaml` | `synthesis` | 6 | 6 | 0 |
| `packs/foundation/missions/built_in_step_contracts/review.step-contract.yaml` | `packs/foundation/missions/software-dev/software-dev.mission-type.yaml` | `review` | 8 | 8 | 0 |
| `packs/foundation/missions/built_in_step_contracts/specify.step-contract.yaml` | `packs/foundation/missions/software-dev/software-dev.mission-type.yaml` | `specify` | 7 | 7 | 0 |
| `packs/foundation/missions/built_in_step_contracts/tasks.step-contract.yaml` | `packs/foundation/missions/software-dev/software-dev.mission-type.yaml` | `tasks` | 4 | 5 | 0 |
| `packs/qa/mission_step_contracts/qa-accept.step-contract.yaml` | `packs/qa/missions/qa/qa.mission-type.yaml` | `accept` | 3 | 2 | 0 |
| `packs/qa/mission_step_contracts/qa-discovery.step-contract.yaml` | `packs/qa/missions/qa/qa.mission-type.yaml` | `discovery` | 3 | 2 | 0 |
| `packs/qa/mission_step_contracts/qa-implement.step-contract.yaml` | `packs/qa/missions/qa/qa.mission-type.yaml` | `implement` | 3 | 4 | 0 |
| `packs/qa/mission_step_contracts/qa-plan.step-contract.yaml` | `packs/qa/missions/qa/qa.mission-type.yaml` | `plan` | 4 | 3 | 0 |
| `packs/qa/mission_step_contracts/qa-review.step-contract.yaml` | `packs/qa/missions/qa/qa.mission-type.yaml` | `review` | 3 | 2 | 0 |
| `packs/qa/mission_step_contracts/qa-specify.step-contract.yaml` | `packs/qa/missions/qa/qa.mission-type.yaml` | `specify` | 2 | 2 | 0 |
| `packs/qa/mission_step_contracts/qa-tasks.step-contract.yaml` | `packs/qa/missions/qa/qa.mission-type.yaml` | `tasks` | 3 | 3 | 0 |
| `packs/writing/missions/built_in_step_contracts/documentation-accept.step-contract.yaml` | `packs/writing/missions/documentation/documentation.mission-type.yaml` | `accept` | 5 | 5 | 5 |
| `packs/writing/missions/built_in_step_contracts/documentation-audit.step-contract.yaml` | `packs/writing/missions/documentation/documentation.mission-type.yaml` | `audit` | 5 | 4 | 4 |
| `packs/writing/missions/built_in_step_contracts/documentation-design.step-contract.yaml` | `packs/writing/missions/documentation/documentation.mission-type.yaml` | `design` | 6 | 5 | 5 |
| `packs/writing/missions/built_in_step_contracts/documentation-discover.step-contract.yaml` | `packs/writing/missions/documentation/documentation.mission-type.yaml` | `discover` | 5 | 5 | 5 |
| `packs/writing/missions/built_in_step_contracts/documentation-generate.step-contract.yaml` | `packs/writing/missions/documentation/documentation.mission-type.yaml` | `generate` | 6 | 5 | 5 |
| `packs/writing/missions/built_in_step_contracts/documentation-publish.step-contract.yaml` | `packs/writing/missions/documentation/documentation.mission-type.yaml` | `publish` | 6 | 5 | 5 |
| `packs/writing/missions/built_in_step_contracts/documentation-validate.step-contract.yaml` | `packs/writing/missions/documentation/documentation.mission-type.yaml` | `validate` | 6 | 5 | 5 |

Totals: 28 contracts, 149 substeps, 144 candidates, 34 of them qualified (all writing → foundation).

## The substep id rename map (generated, 115 renames)

Ids without an underscore keep their name (`bootstrap`, `workspace`, `execute`, `commit`, and all four of
`tasks.step-contract.yaml`'s). Each row is one substep.

| Step contract | Step | Old substep id | New substep id |
|---|---|---|---|
| `packs/foundation/missions/built_in_step_contracts/implement.step-contract.yaml` | `software-dev/implement` | `branching_paradigm` | `branching-paradigm` |
| `packs/foundation/missions/built_in_step_contracts/implement.step-contract.yaml` | `software-dev/implement` | `branching_procedure` | `branching-procedure` |
| `packs/foundation/missions/built_in_step_contracts/implement.step-contract.yaml` | `software-dev/implement` | `supply_chain_security_check` | `supply-chain-security-check` |
| `packs/foundation/missions/built_in_step_contracts/implement.step-contract.yaml` | `software-dev/implement` | `quality_gate` | `quality-gate` |
| `packs/foundation/missions/built_in_step_contracts/implement.step-contract.yaml` | `software-dev/implement` | `status_transition` | `status-transition` |
| `packs/foundation/missions/built_in_step_contracts/plan-plan.step-contract.yaml` | `plan/plan` | `read_inputs` | `read-inputs` |
| `packs/foundation/missions/built_in_step_contracts/plan-plan.step-contract.yaml` | `plan/plan` | `decompose_and_decide` | `decompose-and-decide` |
| `packs/foundation/missions/built_in_step_contracts/plan-plan.step-contract.yaml` | `plan/plan` | `document_decisions` | `document-decisions` |
| `packs/foundation/missions/built_in_step_contracts/plan-plan.step-contract.yaml` | `plan/plan` | `supply_chain_check` | `supply-chain-check` |
| `packs/foundation/missions/built_in_step_contracts/plan-plan.step-contract.yaml` | `plan/plan` | `write_plan` | `write-plan` |
| `packs/foundation/missions/built_in_step_contracts/plan-plan.step-contract.yaml` | `plan/plan` | `commit_plan` | `commit-plan` |
| `packs/foundation/missions/built_in_step_contracts/plan-research.step-contract.yaml` | `plan/research` | `read_open_questions` | `read-open-questions` |
| `packs/foundation/missions/built_in_step_contracts/plan-research.step-contract.yaml` | `plan/research` | `gather_and_validate` | `gather-and-validate` |
| `packs/foundation/missions/built_in_step_contracts/plan-research.step-contract.yaml` | `plan/research` | `document_decisions` | `document-decisions` |
| `packs/foundation/missions/built_in_step_contracts/plan-research.step-contract.yaml` | `plan/research` | `write_research` | `write-research` |
| `packs/foundation/missions/built_in_step_contracts/plan-research.step-contract.yaml` | `plan/research` | `commit_research` | `commit-research` |
| `packs/foundation/missions/built_in_step_contracts/plan-review.step-contract.yaml` | `plan/review` | `verify_plan_fidelity` | `verify-plan-fidelity` |
| `packs/foundation/missions/built_in_step_contracts/plan-review.step-contract.yaml` | `plan/review` | `assess_intent_and_risk` | `assess-intent-and-risk` |
| `packs/foundation/missions/built_in_step_contracts/plan-review.step-contract.yaml` | `plan/review` | `verify_decisions_complete` | `verify-decisions-complete` |
| `packs/foundation/missions/built_in_step_contracts/plan-review.step-contract.yaml` | `plan/review` | `record_outcome` | `record-outcome` |
| `packs/foundation/missions/built_in_step_contracts/plan-specify.step-contract.yaml` | `plan/specify` | `frame_problem` | `frame-problem` |
| `packs/foundation/missions/built_in_step_contracts/plan-specify.step-contract.yaml` | `plan/specify` | `validate_boundaries` | `validate-boundaries` |
| `packs/foundation/missions/built_in_step_contracts/plan-specify.step-contract.yaml` | `plan/specify` | `document_decisions` | `document-decisions` |
| `packs/foundation/missions/built_in_step_contracts/plan-specify.step-contract.yaml` | `plan/specify` | `write_spec` | `write-spec` |
| `packs/foundation/missions/built_in_step_contracts/plan-specify.step-contract.yaml` | `plan/specify` | `commit_spec` | `commit-spec` |
| `packs/foundation/missions/built_in_step_contracts/plan.step-contract.yaml` | `software-dev/plan` | `read_spec` | `read-spec` |
| `packs/foundation/missions/built_in_step_contracts/plan.step-contract.yaml` | `software-dev/plan` | `design_architecture` | `design-architecture` |
| `packs/foundation/missions/built_in_step_contracts/plan.step-contract.yaml` | `software-dev/plan` | `document_decisions` | `document-decisions` |
| `packs/foundation/missions/built_in_step_contracts/plan.step-contract.yaml` | `software-dev/plan` | `supply_chain_security_check` | `supply-chain-security-check` |
| `packs/foundation/missions/built_in_step_contracts/plan.step-contract.yaml` | `software-dev/plan` | `write_plan` | `write-plan` |
| `packs/foundation/missions/built_in_step_contracts/plan.step-contract.yaml` | `software-dev/plan` | `commit_plan` | `commit-plan` |
| `packs/foundation/missions/built_in_step_contracts/research-gathering.step-contract.yaml` | `research/gathering` | `read_methodology` | `read-methodology` |
| `packs/foundation/missions/built_in_step_contracts/research-gathering.step-contract.yaml` | `research/gathering` | `collect_sources` | `collect-sources` |
| `packs/foundation/missions/built_in_step_contracts/research-gathering.step-contract.yaml` | `research/gathering` | `validate_sources` | `validate-sources` |
| `packs/foundation/missions/built_in_step_contracts/research-gathering.step-contract.yaml` | `research/gathering` | `commit_register` | `commit-register` |
| `packs/foundation/missions/built_in_step_contracts/research-methodology.step-contract.yaml` | `research/methodology` | `read_spec` | `read-spec` |
| `packs/foundation/missions/built_in_step_contracts/research-methodology.step-contract.yaml` | `research/methodology` | `design_methodology` | `design-methodology` |
| `packs/foundation/missions/built_in_step_contracts/research-methodology.step-contract.yaml` | `research/methodology` | `document_decisions` | `document-decisions` |
| `packs/foundation/missions/built_in_step_contracts/research-methodology.step-contract.yaml` | `research/methodology` | `write_plan` | `write-plan` |
| `packs/foundation/missions/built_in_step_contracts/research-methodology.step-contract.yaml` | `research/methodology` | `commit_plan` | `commit-plan` |
| `packs/foundation/missions/built_in_step_contracts/research-output.step-contract.yaml` | `research/output` | `verify_findings_fidelity` | `verify-findings-fidelity` |
| `packs/foundation/missions/built_in_step_contracts/research-output.step-contract.yaml` | `research/output` | `verify_citation_completeness` | `verify-citation-completeness` |
| `packs/foundation/missions/built_in_step_contracts/research-output.step-contract.yaml` | `research/output` | `assess_publication_readiness` | `assess-publication-readiness` |
| `packs/foundation/missions/built_in_step_contracts/research-output.step-contract.yaml` | `research/output` | `write_report` | `write-report` |
| `packs/foundation/missions/built_in_step_contracts/research-output.step-contract.yaml` | `research/output` | `gate_publication_approved` | `gate-publication-approved` |
| `packs/foundation/missions/built_in_step_contracts/research-output.step-contract.yaml` | `research/output` | `commit_report` | `commit-report` |
| `packs/foundation/missions/built_in_step_contracts/research-scoping.step-contract.yaml` | `research/scoping` | `capture_question` | `capture-question` |
| `packs/foundation/missions/built_in_step_contracts/research-scoping.step-contract.yaml` | `research/scoping` | `validate_scope` | `validate-scope` |
| `packs/foundation/missions/built_in_step_contracts/research-scoping.step-contract.yaml` | `research/scoping` | `document_decisions` | `document-decisions` |
| `packs/foundation/missions/built_in_step_contracts/research-scoping.step-contract.yaml` | `research/scoping` | `write_spec` | `write-spec` |
| `packs/foundation/missions/built_in_step_contracts/research-scoping.step-contract.yaml` | `research/scoping` | `commit_spec` | `commit-spec` |
| `packs/foundation/missions/built_in_step_contracts/research-synthesis.step-contract.yaml` | `research/synthesis` | `read_sources` | `read-sources` |
| `packs/foundation/missions/built_in_step_contracts/research-synthesis.step-contract.yaml` | `research/synthesis` | `synthesize_findings` | `synthesize-findings` |
| `packs/foundation/missions/built_in_step_contracts/research-synthesis.step-contract.yaml` | `research/synthesis` | `document_decisions` | `document-decisions` |
| `packs/foundation/missions/built_in_step_contracts/research-synthesis.step-contract.yaml` | `research/synthesis` | `write_findings` | `write-findings` |
| `packs/foundation/missions/built_in_step_contracts/research-synthesis.step-contract.yaml` | `research/synthesis` | `commit_findings` | `commit-findings` |
| `packs/foundation/missions/built_in_step_contracts/review.step-contract.yaml` | `software-dev/review` | `verify_spec_fidelity` | `verify-spec-fidelity` |
| `packs/foundation/missions/built_in_step_contracts/review.step-contract.yaml` | `software-dev/review` | `verify_example_sync` | `verify-example-sync` |
| `packs/foundation/missions/built_in_step_contracts/review.step-contract.yaml` | `software-dev/review` | `assess_intent_and_risk` | `assess-intent-and-risk` |
| `packs/foundation/missions/built_in_step_contracts/review.step-contract.yaml` | `software-dev/review` | `supply_chain_security_review` | `supply-chain-security-review` |
| `packs/foundation/missions/built_in_step_contracts/review.step-contract.yaml` | `software-dev/review` | `quality_gate` | `quality-gate` |
| `packs/foundation/missions/built_in_step_contracts/review.step-contract.yaml` | `software-dev/review` | `verify_stopping_conditions` | `verify-stopping-conditions` |
| `packs/foundation/missions/built_in_step_contracts/review.step-contract.yaml` | `software-dev/review` | `status_transition` | `status-transition` |
| `packs/foundation/missions/built_in_step_contracts/specify.step-contract.yaml` | `software-dev/specify` | `capture_intent` | `capture-intent` |
| `packs/foundation/missions/built_in_step_contracts/specify.step-contract.yaml` | `software-dev/specify` | `map_examples` | `map-examples` |
| `packs/foundation/missions/built_in_step_contracts/specify.step-contract.yaml` | `software-dev/specify` | `validate_requirements` | `validate-requirements` |
| `packs/foundation/missions/built_in_step_contracts/specify.step-contract.yaml` | `software-dev/specify` | `document_decisions` | `document-decisions` |
| `packs/foundation/missions/built_in_step_contracts/specify.step-contract.yaml` | `software-dev/specify` | `write_spec` | `write-spec` |
| `packs/foundation/missions/built_in_step_contracts/specify.step-contract.yaml` | `software-dev/specify` | `commit_spec` | `commit-spec` |
| `packs/qa/mission_step_contracts/qa-accept.step-contract.yaml` | `qa/accept` | `confirm_evidence_integrity` | `confirm-evidence-integrity` |
| `packs/qa/mission_step_contracts/qa-accept.step-contract.yaml` | `qa/accept` | `run_go_no_go_review` | `run-go-no-go-review` |
| `packs/qa/mission_step_contracts/qa-discovery.step-contract.yaml` | `qa/discovery` | `scope_qa_charter` | `scope-qa-charter` |
| `packs/qa/mission_step_contracts/qa-discovery.step-contract.yaml` | `qa/discovery` | `apply_exploratory_tactics` | `apply-exploratory-tactics` |
| `packs/qa/mission_step_contracts/qa-implement.step-contract.yaml` | `qa/implement` | `report_defects` | `report-defects` |
| `packs/qa/mission_step_contracts/qa-implement.step-contract.yaml` | `qa/implement` | `apply_execution_tactics` | `apply-execution-tactics` |
| `packs/qa/mission_step_contracts/qa-plan.step-contract.yaml` | `qa/plan` | `prioritize_by_risk` | `prioritize-by-risk` |
| `packs/qa/mission_step_contracts/qa-plan.step-contract.yaml` | `qa/plan` | `apply_prioritization_tactic` | `apply-prioritization-tactic` |
| `packs/qa/mission_step_contracts/qa-plan.step-contract.yaml` | `qa/plan` | `review_test_plan` | `review-test-plan` |
| `packs/qa/mission_step_contracts/qa-review.step-contract.yaml` | `qa/review` | `verify_no_fake_green` | `verify-no-fake-green` |
| `packs/qa/mission_step_contracts/qa-review.step-contract.yaml` | `qa/review` | `triage_defects` | `triage-defects` |
| `packs/qa/mission_step_contracts/qa-specify.step-contract.yaml` | `qa/specify` | `define_traceable_exit_criteria` | `define-traceable-exit-criteria` |
| `packs/qa/mission_step_contracts/qa-tasks.step-contract.yaml` | `qa/tasks` | `reduce_test_surface` | `reduce-test-surface` |
| `packs/qa/mission_step_contracts/qa-tasks.step-contract.yaml` | `qa/tasks` | `close_coverage_gaps` | `close-coverage-gaps` |
| `packs/writing/missions/built_in_step_contracts/documentation-accept.step-contract.yaml` | `documentation/accept` | `confirm_publication_handoff` | `confirm-publication-handoff` |
| `packs/writing/missions/built_in_step_contracts/documentation-accept.step-contract.yaml` | `documentation/accept` | `validate_acceptance_fidelity` | `validate-acceptance-fidelity` |
| `packs/writing/missions/built_in_step_contracts/documentation-accept.step-contract.yaml` | `documentation/accept` | `acceptance_decision` | `acceptance-decision` |
| `packs/writing/missions/built_in_step_contracts/documentation-accept.step-contract.yaml` | `documentation/accept` | `commit_acceptance` | `commit-acceptance` |
| `packs/writing/missions/built_in_step_contracts/documentation-audit.step-contract.yaml` | `documentation/audit` | `inventory_existing_docs` | `inventory-existing-docs` |
| `packs/writing/missions/built_in_step_contracts/documentation-audit.step-contract.yaml` | `documentation/audit` | `identify_gaps` | `identify-gaps` |
| `packs/writing/missions/built_in_step_contracts/documentation-audit.step-contract.yaml` | `documentation/audit` | `write_gap_analysis` | `write-gap-analysis` |
| `packs/writing/missions/built_in_step_contracts/documentation-audit.step-contract.yaml` | `documentation/audit` | `commit_gap_analysis` | `commit-gap-analysis` |
| `packs/writing/missions/built_in_step_contracts/documentation-design.step-contract.yaml` | `documentation/design` | `plan_divio_types` | `plan-divio-types` |
| `packs/writing/missions/built_in_step_contracts/documentation-design.step-contract.yaml` | `documentation/design` | `architecture_decision` | `architecture-decision` |
| `packs/writing/missions/built_in_step_contracts/documentation-design.step-contract.yaml` | `documentation/design` | `validate_design` | `validate-design` |
| `packs/writing/missions/built_in_step_contracts/documentation-design.step-contract.yaml` | `documentation/design` | `write_plan` | `write-plan` |
| `packs/writing/missions/built_in_step_contracts/documentation-design.step-contract.yaml` | `documentation/design` | `commit_plan` | `commit-plan` |
| `packs/writing/missions/built_in_step_contracts/documentation-discover.step-contract.yaml` | `documentation/discover` | `capture_documentation_needs` | `capture-documentation-needs` |
| `packs/writing/missions/built_in_step_contracts/documentation-discover.step-contract.yaml` | `documentation/discover` | `validate_scope` | `validate-scope` |
| `packs/writing/missions/built_in_step_contracts/documentation-discover.step-contract.yaml` | `documentation/discover` | `write_spec` | `write-spec` |
| `packs/writing/missions/built_in_step_contracts/documentation-discover.step-contract.yaml` | `documentation/discover` | `commit_spec` | `commit-spec` |
| `packs/writing/missions/built_in_step_contracts/documentation-generate.step-contract.yaml` | `documentation/generate` | `produce_artifacts` | `produce-artifacts` |
| `packs/writing/missions/built_in_step_contracts/documentation-generate.step-contract.yaml` | `documentation/generate` | `living_doc_sync` | `living-doc-sync` |
| `packs/writing/missions/built_in_step_contracts/documentation-generate.step-contract.yaml` | `documentation/generate` | `validate_output` | `validate-output` |
| `packs/writing/missions/built_in_step_contracts/documentation-generate.step-contract.yaml` | `documentation/generate` | `write_docs` | `write-docs` |
| `packs/writing/missions/built_in_step_contracts/documentation-generate.step-contract.yaml` | `documentation/generate` | `commit_docs` | `commit-docs` |
| `packs/writing/missions/built_in_step_contracts/documentation-publish.step-contract.yaml` | `documentation/publish` | `living_doc_sync` | `living-doc-sync` |
| `packs/writing/missions/built_in_step_contracts/documentation-publish.step-contract.yaml` | `documentation/publish` | `specification_fidelity` | `specification-fidelity` |
| `packs/writing/missions/built_in_step_contracts/documentation-publish.step-contract.yaml` | `documentation/publish` | `final_validation` | `final-validation` |
| `packs/writing/missions/built_in_step_contracts/documentation-publish.step-contract.yaml` | `documentation/publish` | `write_release` | `write-release` |
| `packs/writing/missions/built_in_step_contracts/documentation-publish.step-contract.yaml` | `documentation/publish` | `commit_release` | `commit-release` |
| `packs/writing/missions/built_in_step_contracts/documentation-validate.step-contract.yaml` | `documentation/validate` | `quality_gates` | `quality-gates` |
| `packs/writing/missions/built_in_step_contracts/documentation-validate.step-contract.yaml` | `documentation/validate` | `risk_review` | `risk-review` |
| `packs/writing/missions/built_in_step_contracts/documentation-validate.step-contract.yaml` | `documentation/validate` | `validate_against_spec` | `validate-against-spec` |
| `packs/writing/missions/built_in_step_contracts/documentation-validate.step-contract.yaml` | `documentation/validate` | `write_audit_report` | `write-audit-report` |
| `packs/writing/missions/built_in_step_contracts/documentation-validate.step-contract.yaml` | `documentation/validate` | `commit_audit_report` | `commit-audit-report` |
