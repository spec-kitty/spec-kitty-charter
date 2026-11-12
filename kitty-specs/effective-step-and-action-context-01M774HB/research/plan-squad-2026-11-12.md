# Plan-point squad, 2026-11-12: findings and dispositions

The squad had three read-only delegates, each profile-loaded:

- architect-alphonso: model and design soundness;
- reviewer-renata: testability and contract discipline;
- doctrine-daphne: consistency of the records.

Each returned APPROVE-WITH-CHANGES. The architect required A1 and A2 to be redesigned before tasks. Both were checked
against the code and confirmed:

- `PackContents` already carries `promptSlots` and `stepEnhancements` from the full read.
- `RegistrationImpact.check(..., namingActivations)` reaches `activationsAffected` only on the full registration.
- `ActivationImpactAdapter` swallows every `CharterRefusal`.

Disposition key: **accepted** (artifact changed, location given), **changed** (resolved another way, location given),
**deferred** (with rationale and tracking).

## Architect (A)

| # | Sev | Finding | Disposition |
|---|---|---|---|
| A1 | HIGH | A lazily called storage port reads outside the verified read, and at registration it reads the committed pack, not the staged one. | **changed**: slot facts become part of `PackCompositionFacts` (research R9, revised). That is the one verified, fingerprint-cached and staged-aware input of the compose. Fragment canonical text travels with them. The prompt text is read through the existing `PackCompanionFiles.stepFiles` under the closure fingerprint check. This needs one additive hunk in `DomainRepositoryService.compositionFacts`, which is slice B's file. It is announced to the orchestrator and lands after the rebase onto #501 (plan, Structure Decision). |
| A2 | HIGH | The activation gate is reached only on full registration, and the adapter swallows refusals. | **accepted**: research R7 (revised). The `ActivationImpact` port gains a gate method, `requireComposable(ClosureChange)`, separate from the notices. Every check path of `RegistrationImpact` calls it. The no-impact fallback stays a no-op (no activations stored means nothing to judge). An activation already broken before the change (G21) is not blamed on it. |
| A3 | MED | `ClosureView.inForce` is a shared pure resolution, so activation writes reach it only by accident. | **accepted**: plan Design. `inForce` stays pure. An explicit `requireSlots(states)` on `ClosureView` is called by the read session, `ActivationWrites.commit` and `ActivationService`. |
| A4 | MED | The read-side seat of the inherited lock is not stated. | **accepted**: plan Design. One pure domain rule, called from `ImpactRelations` at registration and from `MergeInputs.merged()` at read, with OD-2 (A) as ruled: a closure-level refusal like C21, whatever the activation. No new operator question: OD-2 already ruled "500 pack_unreadable on reads". |
| A5 | MED | The target-held refusal makes E6's unheld branch unreachable for step enhancers. | **accepted**: IC-06 amends E6 (the unheld-target branch now covers only the enhancer shapes the new check does not refuse) and its pinning tests. |
| A6 | MED | An edge from an unheld source would make a directive project-wide and leak it. | **accepted**: research R2 (revised). Owners are every authored `scope` edge whose source kind is `action` or `mission_type`, held or not. |
| A7 | MED | Pass roots break `via`/`hops`, `Engine.hopsOf` throws for `mission_type:mt`, the suggestion-label rule conflicts, and vocabulary first-reach is unclear. | **accepted**: research R3 (revised). Each entry carries its pass and root. Pass roots are seeded with explicit hops. A suggestion and its candidate carry the pass that lists them. First reach wins, vocabulary included, and is recorded. ADR-0018 D3 is annotated for the effective form. |
| A8 | MED | "Highest-level version" is undefined at one level. The step-read mismatch is a correctness risk. | **accepted**: research R8 (revised). The prompt in force is the companion at the merged winner's place of `action:M/s` (`StepHolding`). **deferred** for the `getEffectiveMissionStep` mismatch: a pre-existing correctness defect of slice A's read, filed as a `follow-up` issue (Rule 1) at closeout and listed under "Not done". |
| A9 | MED | `PromptSlotFacts` escapes `isPortOf`, collides with `PackContents.PromptSlotFacts`, and has no wiring home. | **changed**: there is no new port (A1). |
| A10 | LOW | Judging the 409 before the 304 costs a resolution on every conditional read. | **accepted**: research R10 records the cost and the order, with `500 prompt_slot_missing` before the 304. |
| A11 | LOW | `promptPackId` is required but undefined when there is no prompt. | **accepted**: `data-model.md`, now optional. |

## Reviewer (R)

| # | Sev | Finding | Disposition |
|---|---|---|---|
| R1 | HIGH | SC-001 cannot catch a wrong sort order. | **accepted**: spec SC-001 is amended, and IC-01 adds a three-levels scenario fixture: two org packs whose ids sort opposite to their names, a built-in enhancement id that sorts after the org one, and a project fragment. |
| R2 | HIGH | The mission-type pass and R2 have no positive control. | **accepted**: IC-01 scenarios cover a type-scoped directive (listed), another type's directive (absent), another type not in force (still absent), and a required directive scoped only to other actions (absent). |
| R3 | HIGH | Existing scenarios change meaning (US5 AS4; #484 `DX`, `t3`). | **accepted**: IC-01 rewrites them first, keeps `px`, `s3`, `g3` as negative twins, and audits every `/context` scenario and effective-read test. The meaning change is `### Breaking`. |
| R4 | HIGH | `prompt_slot_missing`'s operations map is incomplete. | **accepted**: `contracts/README.md`. The map lists every 422 operation (registration, impact-checked writes, activation writes). Declaration pins go in `errors.feature`, and the contract-marks test is extended. |
| R5 | MED | `EffectiveContextArtifact`, the broadened `override_not_permitted` and the raised `prompt_slot_missing` are breaking. | **accepted**: `contracts/README.md`. One `### Breaking` fragment names every newly refusing operation and the item swap. `### Added` is kept for the `prompt` link and the read. `### Changed` is wording only. |
| R6 | MED | Parity tests and links not named. | **accepted**: IC-01 names WireTokenParityTest (new pairs), `x-derived` on every member, the `effective-context` relation (not `context`), and the ruleset enum and `RelationTableParityTest` in one commit. |
| R7 | MED | `appliesThrough` is not pinned by any scenario. | **accepted**: IC-01 extends the step vocabulary and its binding to print `appliesThrough`, and owns the README vocabulary. |
| R8 | MED | The 16-directive table can drift from the test. | **changed**: the table lives once, as the example table of the scoping scenario (living documentation). The docs link to it. |
| R9 | MED | Example-pack extension is unowned and collides with slice B. | **changed**: the example packs are not edited. SC-001 uses them as they are; SC-004 and the pass-2 cases use scenario fixtures. |
| R10 | MED | The legacy-text test conflicts with C-005. | **changed**: it is the warning path of the load rule, an error-handling test, recorded in spec FR-005 and C-005. |
| R11 | LOW | NFR details. | **accepted**: spec NFR-001 to NFR-004 name the counted operation (memo build included), permute the closure's pack order under one canonical revision, and use a ratio of at most 2.5. |
| R12 | LOW | List the twins. | **accepted**: tasks get one row per rule × surface. |
| R13 | LOW | The prompt read's `effective-artifact` link can 404. | **deferred**: the same defect as A8, on its follow-up issue. |

## Doctrine (D)

| # | Sev | Finding | Disposition |
|---|---|---|---|
| D1 | HIGH | E17: the context changes meaning under an unchanged tag. | **accepted**: research R10. The domain separator moves to `v3` for every effective read (E17 is amended), and `PROMPT` is added. E18 gains the `409`. |
| D2 | HIGH | `divergences.yaml` is omitted. | **accepted**: IC-06. |
| D3 | HIGH | DIV-244 becomes stale. | **accepted**: IC-06. A dated note goes on DIV-244. One new entry, **DIV-245**, covers both parts of the ownership rule (roots added; mission-type scope honoured). The block prose is updated. |
| D4 | MED | Label collision with "Built (PR C)". | **accepted**: IC-06 defines "Built (slice C)" and adds the section 7 status rows. |
| D5 | MED | ADR-0018: annotate, don't edit. | **accepted**: IC-06. Amendment entries go on D5 (the ruling), D2 (the mission type is walked by the effective read) and D3 (the invariant holds for the offering read and within a pass), with `decision_status` and `updated` bumped. D5's corpus clause is noted as superseded for the activated cases by the ruling. |
| D6 | MED | FR-012 is incomplete for ADR-0066. | **accepted**: spec FR-012. Annotations: d8 (active-only; the moments widened to registration, impact-checked writes and activation writes); d9 (409 is Breaking, not additive; text served as stored, which is unchanged); a "now built" annotation; the Follow-ups built marker; d4 cited for the 409. |
| D7 | MED | The rules page needs more changes. | **accepted**: IC-06. E16 is rewritten (the ⚠ note removed); E12 is extended; section 8 gets its intro and the "reserved" paragraph fixed. New ids are **C37** (target held), **C38** (slot declared), and C22/C25 are amended for the inherited lock. F6 precedence is defined. G8 is extended. The R-REL `relate` path is stated. |
| D8 | MED | Other records missed. | **accepted**: IC-06 covers the R9 row, the glossary (effective step prompt, project-wide artifact, scope owner; the "effective action context" definition amended), the changelog page and index, and the roadmap and next-slices ("match core" reworded). |
| D9 | LOW | Issue hygiene. | **accepted**: closeout. Close #438, #450 (both boxes) and #97 with evidence, then tick #467 and #94. Do not cite #424, which is closed. Research R6 now cites Rules 1 and 5. |
