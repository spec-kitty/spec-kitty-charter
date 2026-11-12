# Research: Slice C, the effective step and action context

Decisions for `plan.md`, each as decision, rationale and alternatives. The rulings are the operator's (2026-11-12,
relayed by the orchestrator); the rest are this mission's planning decisions, put to the plan-point squad.

## R1. The scoping rule of the effective action context (#450) — operator ruling

- **Decision.** Option B, the ownership rule (Decision Moment `01M774HQ8N5EVYDRTP16MBJCN3`). An in-force directive,
  tactic or paradigm applies to action `mt/a` when any of these holds:
  - the action's walk reaches it (ADR-0018 D2, as built);
  - no action node and no mission-type node scopes it (project-wide);
  - `mission_type:mt` scopes it (type-wide).
  What such an artifact reaches by ADR-0018 steps 2 to 5 comes with it. One scoped only by other actions or other
  mission types stays out. Each entry carries `appliesThrough`. Recorded as an amendment to ADR-0018 D5 and rule E16,
  with a divergence entry for honouring mission-type scope.
- **Rationale.** ADR-0018 D5 already gives the effective context "selection roots and their closure", and D2 says
  type-wide guidance reaches an action through R9; E16 as built (PR C) is the walk alone, and FR-019's "what the step
  contract names" was flagged unclear. "Required" is the wrong criterion: required means "must be in force" (E5), not
  "applies everywhere". Ownership by `scope` edges is a fact of the graph, independent of pack provenance.
- **Alternatives.** A: keep the drop and link the listing (rejected by the operator). C: add the required floor only
  (adds one of nine; leaks action-scoped required artifacts).
- **Evidence.** `spec.md`, section "Evidence (2026-11-12)": 15 of 16 listed; DIRECTIVE_003 left out, scoped only by
  other actions.

## R2. Ownership is judged on authored scope edges, whatever is in force (planning decision)

- **Decision.** "Scoped by an action" and "scoped by a mission type" are judged on the merged closure's held `scope`
  edges whose source is a held step `action:M/s` (OD-9, `StepHolding`) or a `mission_type:M` the closure holds,
  whether or not `M` is in force.
- **Rationale.** Ownership is where the author placed the artifact. If it were judged on what is in force, turning a
  mission type off would make its scoped directives project-wide and leak them into every other action. A stable answer
  needs a fact the activation does not move.
- **Alternatives.** Judge on the effective graph (E15) only: rejected for the leak above.

## R3. The rule's place in the walk (planning decision)

- **Decision.** The walk runs in three passes over one listing, in this order: the action's five steps as built
  (`appliesThrough: action`); then the five steps seeded by the mission type's scoped roots
  (`appliesThrough: mission_type`); then the five steps seeded by the project-wide roots
  (`appliesThrough: project`). An artifact an earlier pass listed is never listed again and still seeds later steps,
  as ADR-0018 D3 deduplicates within a walk. Roots of the second and third pass are in-force artifacts of kind
  `directive`, `tactic` or `paradigm` only; what their steps 2 to 5 reach is of any kind, as in the first pass.
- **Rationale.** "The first rule that reaches it wins" makes the answer one total order (action, then type, then
  project), deterministic, and keeps the action-scoped walk byte-identical where no root adds anything. The offering
  read (`getActionContext`, ADR-0018 D1) is unchanged: the passes run only for the effective read.
- **Alternatives.** One breadth-first walk with all roots at hop 1: rejected, because `hops` would tie action roots
  with project roots and the step that lists an artifact would depend on the tie order rather than on the rule.

## R4. `appliesThrough` on the wire (planning decision)

- **Decision.** The context's `artifacts` items gain a required `appliesThrough` (`action`, `mission_type`, `project`).
  The listing (`listEffectiveArtifacts`) does not carry it. A suggestion carries the `appliesThrough` of the entry
  whose `suggests` edge reached it. The item schema becomes a context-only schema that copies `EffectiveArtifact`'s
  members and adds the one member, as `EffectiveArtifactRead` already copies them.
- **Rationale.** The member is about an action, so it has no meaning on the activation-wide listing.
- **Contract class.** `### Added` for the member; `### Breaking` for the meaning of `artifacts` (an answer that was
  the walk alone now lists more), ADR-0022 0.x line.

## R5. OD-5 stands: the prompt read answers `409 mission_type_not_in_force` — operator ruling

- **Decision.** Decision Moment `01M774HSKK0SSA4147QEG9ZCKR`. A new refusal code `mission_type_not_in_force`, status
  `409`, raised by `getEffectiveStepPrompt` only, judged after `activation_not_found`, `mission_type_not_found` and
  `action_not_found` and before a `304`.
- **Why the two reads differ** (recorded on ADR-0066 decision 9). The action context describes what governs an
  action; for a mission type that is not in force the truthful description is "nothing", with the reason in its
  skipped report, so `200` with an empty context is an answer. The prompt read hands a renderer the text it places in
  a running step; a mission type that is not in force has no step to run, and an empty prompt would be rendered as if
  it were one. A conflict with the activation's state is the honest answer, and the client fixes it by changing the
  activation.
- **Contract class.** `### Breaking`: an enum value added to `CharterRefusalCode` (a closed enum). The file is shared
  with slice B (PR #501); the one added value is routed through the orchestrator.

## R6. R3, the merged graph and graph queries (#97)

- **Decision.** Close #97 as delivered. Core's graph reads (`load_built_in_graph`, `merge_three_layers`,
  `load_org_drg`, `validate_dangling_references`, `scan_project_artifacts`, `drg_activation`) are answered by:
  - the merged graph under an activation, `getEffectiveGraph` (PR #448);
  - the per-artifact graph query, `listArtifactReferences`, each reference resolved through the compose;
  - the reference checks at registration (`pack_reference_pack_missing`, `pack_reference_artifact_missing`), which
    replace a dangling-reference lint.
  The unfiltered `/offering/graph`, with no activation, waits for the minor that brings the offering merge, as R5's
  `/offering/mission-types` and R18's `/offering/kinds` do. No new issue (issue-tracker Rule 6).
- **Recorded in** `docs/api/service-contract.md`, the R3 row.
