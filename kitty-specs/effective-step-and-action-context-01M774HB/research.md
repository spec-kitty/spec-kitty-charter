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
- **Revised after the plan-point squad (A7).** Each entry carries its pass (`action`, `mission_type`, `project`) and,
  for passes 2 and 3, its root. Pass roots are seeded with explicit hops (1, from the pass's own origin), so the engine
  never asks the hops of a node it did not reach. A suggestion and its candidate carry the pass that lists them. First
  reach wins in every step, vocabulary included: an eligible root reached first by vocabulary in pass 1 is listed
  `action` and still seeds pass 2 or 3. ADR-0018 D3's invariant holds for the offering read and within a pass; it is
  annotated for the effective form, which does not serialize `via` or `hops`.
- **Alternatives.** One breadth-first walk with all roots at hop 1: rejected, because `hops` would tie action roots
  with project roots and the step that lists an artifact would depend on the tie order rather than on the rule.

## R4. `appliesThrough` on the wire (planning decision)

- **Decision.** The context's `artifacts` items gain a required `appliesThrough` (`action`, `mission_type`, `project`).
  The listing (`listEffectiveArtifacts`) does not carry it. A suggestion carries the `appliesThrough` of the entry
  whose `suggests` edge reached it. The item schema becomes a context-only schema that copies `EffectiveArtifact`'s
  members and adds the one member, as `EffectiveArtifactRead` already copies them.
- **Rationale.** The member is about an action, so it has no meaning on the activation-wide listing.
- **Contract class** (revised after the squad, R5). `### Breaking`: the item schema is swapped and gains a required
  member under the closed-schema rule (ADR-0022 D4). Also `### Breaking`: the meaning of `artifacts` (an answer that was
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
  `/offering/mission-types` and R18's `/offering/kinds` do. No new issue (issue-tracker Rules 1 and 5).
- **Recorded in** `docs/api/service-contract.md`, the R3 row.

## R7. What "active" means where there is no activation (planning decision, from OD-1 and OD-4)

- **Decision.** On a composing read, an enhancement is active when it is in force under the read's activation
  (`InForceStates`). At registration and on the impact-checked writes, a pack has no activation of its own, so the
  check runs once per stored activation whose closure the change joins, through the existing impact port
  (`ActivationImpactAdapter`, which already resolves `inForce` per stored activation for the notices). An enhancement
  active under any of them refuses with `422 prompt_slot_missing`, the detail naming the activation. A change that joins
  no activation's closure passes. An activation write (create, replace, select, exclusion, preset) that would make such
  an enhancement active is refused with `422 prompt_slot_missing` before it is stored.
- **Rationale.** OD-1 (B) ruled active-only, and ADR-0066 decision 8's "whatever the activation" is annotated with
  that ruling (FR-012). Judging per stored activation is the only reading of "active" that a registration can apply,
  and it is the reading the impact notices already use.
- **Revised after the plan-point squad (A2, A3).** The gate is not the notices path. The `ActivationImpact` port gains
  a gate method, called on every check path of `RegistrationImpact` (full registration, per-artifact registration,
  relationship writes, deletes), which resolves in-force per stored activation the change joins and raises the first
  `prompt_slot_missing`, never swallowed. An activation that already failed before the change (G21) is not blamed on
  it. Activation writes call an explicit `ClosureView.requireSlots(states)` before storing; `inForce` stays pure.
- **Alternatives.** Refuse at registration when any enhancement, active or not, names a missing slot: that is the
  "whatever the activation" reading OD-1 overruled. Judge only at read: drops OD-4.

## R8. The prompt in force follows the closure's step holding (planning decision)

- **Decision** (revised after the squad, A8). The prompt read judges the action with `StepHolding` on the merged
  closure (as the context read does, OD-9). The prompt in force is the stored prompt companion at the place of the
  merged winner of `action:M/s`, the one place `StepHolding` gives a step (the winner's pack when the winning type
  declares it, else the pack of the version that keeps it, A7). Its text is
  served as stored (ADR-0066 decision 9: "unchanged"); its slots are parsed from its canonical render, as the load rule
  parses them (A5). A step with no prompt companion answers an empty prompt and no slots.
- **Rationale.** The compose check and the read must judge one prompt per step, and the closure's step predicate is
  the model's (DIV-243).
- **Known mismatch, recorded, not fixed here.** `getEffectiveMissionStep` reads only the winner's steps, so it answers
  `action_not_found` for a step only a replaced version keeps, where the context and prompt reads answer. This is
  pre-existing (slice A). Operator ruling of 2026-11-12: fold the fix into this mission (WP09), so the step, context
  and prompt reads agree.

## R9. Slot facts are composition facts (planning decision, revised after the squad, A1)

- **Decision.** `PackCompositionFacts` gains `prompts`: per held step `action:M/s`, the slot ids its prompt companion
  declares (from the canonical render, as the load rule parses it, A5) and whether each holds text; and, per held step
  enhancement, its entries `(slot id, canonical fragment text)`. They are filled where the facts are built
  (`DomainRepositoryService.compositionFacts`, which also serves the staged view) from the same read that fills
  `PackContents.promptSlots` and `stepEnhancements`.
- **Rationale.** The facts are the compose's one verified, fingerprint-cached, staged-aware input: the check judges the
  staged pack at registration and the verified state on reads (E19, G15), and nothing is read again. A storage port
  called lazily would read outside the verified read and, at registration, would judge the committed pack.
- **Cost.** One additive hunk in `DomainRepositoryService`, slice B's file: announced to the orchestrator, written after
  the rebase onto #501.
- **Alternatives.** A new storage port (first plan): rejected for the reasons above.

## R10. ETag of the prompt read (planning decision)

- **Decision.** `EffectiveTag.Read.PROMPT`, with the query `(missionType, action)`; the recipe is E17's, so the tag
  changes with the activation's revision and the closure fingerprint (which digests every loaded file, fragments and
  prompt companions included, ADR-0018 D8 as amended). The order is: `activation_not_found`, `mission_type_not_found`,
  `action_not_found`, `mission_type_not_in_force`, `prompt_slot_missing` (500), then the tag and `304`.
- **Revised after the squad (D1, A10).** E17's domain separator moves to `v3` for every effective read, because the
  context changes meaning under an unchanged revision and fingerprint; a client's old tag then never matches. Judging
  the 409 before the 304 costs a merge and an in-force resolution on each conditional prompt read; the session caches
  them per request, and the cost is recorded here.
