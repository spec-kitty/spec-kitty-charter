# Mission Specification: Slice C: the effective step and action context

**Mission Branch**: `kitty/mission-effective-step-and-action-context-01M774HB`, merging into `slice-c/effective-step-context`
(the pull-request branch off `main` at `e62d690`)
**Created**: 2026-11-12
**Status**: Draft
**Input**: Slice C, [#467](https://github.com/spec-kitty/spec-kitty-charter/issues/467): #438 (ADR-0066 phase 2),
the scoping half of #450, and #97 (R3). The draft spec and the parked work packages of the former phase-2 mission
`adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271` (branch `issue-424-adr-0066-phase-2-effective-prompt`)
are plan input. They are re-validated here against `main`.

## Intent Summary

- **Primary actors.**
  - A **prompt renderer**: core's skill renderer, or `spec-kitty next`'s prompt builder. It asks the charter for the
    prompt a step really gets.
  - An **agent harness**: core's `charter context`. It asks which governance applies to one action of a mission.
  - A **pack author** ships enhancing packs and prompt overrides.
  - An **activation owner** selects what is in force.
- **Trigger.** A renderer reads a step's prompt under an activation. A harness reads an action's context. Or a compose,
  a registration or a write that rebuilds the merge meets an enhancement that would misplace its fragment.
- **Desired outcome.**
  - The charter serves the effective step prompt: the prompt in force and, per slot, the active fragments in a fixed
    order, each with its provenance.
  - It refuses a compose in which an active fragment would be silently dropped, or would land on a step that does not
    accept it.
  - The effective action context explains every directive the project holds in force for an action. Each one is either
    listed, by a stated rule, or left out by a recorded rule. Each listed item links to its body.
  - What remains of R3 (#97) is decided and recorded.
- **Rules that always hold.**
  - A fragment only ever fills a slot that the prompt in force declares. It never replaces base text, never adds a slot,
    and renders only while its own enhancement is in force (ADR-0066 decisions 4 and 7).
  - The context's rules are judged on the merged graph and the effective set, never on a pack's provenance and never on
    core's current output (charter companion: core's behaviour is evidence, not the oracle).
- **Boundaries.**
  - The charter does not render prompts and does not judge what a fragment says.
  - Pack registration, `file-kinds.yaml`, `PackCommitStore` and the pack document are slice B's (#466, PR #501). This
    mission stays out of them, except where the 422 at registration must be raised.
  - The real `spdd` pack waits on slice B and open-packs #205/#206. The effective prompt is proven on charter's own
    example packs (ADR-0070).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A renderer reads a step's effective prompt (Priority: P1)

A renderer asks the charter for the effective prompt of action `<action>` of mission type `<type>` under activation
`<id>`. The charter answers with the prompt in force and, for every slot that prompt declares, the active fragments in
the order of ADR-0066 decision 5, each naming the pack and enhancement it came from. The renderer places them.

**Why this priority**: This is ADR-0066 decision 9's consumer-facing outcome, and what keeps #438 open.

**Independent Test**: On charter's example packs (built-in, org-base, project), activate built-in's `plan-risk-notes`
and org-base's `spdd-plan`, read the prompt of `software-dev/plan`, and check the order and the provenance.

**Acceptance Scenarios**:

1. **Given** an activation with active enhancements from a built-in and an org pack, each filling `spdd:reasons-block`,
   **When** the prompt is read, **Then** the slot lists the fragments ordered built-in, then org, then project. Within
   a level they are ordered by pack name, then by enhancement id.
2. **Given** an enhancement that is held but not selected, **When** the prompt is read, **Then** its fragment is
   absent. Its target being in force does not pull it in.
3. **Given** a slot that no active enhancement fills, **When** the prompt is read, **Then** the slot is listed with no
   fragments.
4. **Given** an unknown activation, mission type or action, **When** the prompt is read, **Then** the answer is the
   contract's not-found refusal (`activation_not_found`, `mission_type_not_found`, `action_not_found`), judged before a
   `304`, never an empty prompt.
5. **Given** a mission type the closure holds but the activation does not put in force, **When** the prompt is read,
   **Then** the answer is `409 mission_type_not_in_force`, judged after the not-found checks and before a `304`
   (OD-5, which stands: operator ruling of 2026-11-12, Decision Moment `01M774HSKK0SSA4147QEG9ZCKR`). The action
   context keeps its `200` with an empty context for the same case; the reason the two reads differ is recorded on
   ADR-0066.
6. **Given** a slot that still holds legacy base text and an active fragment for it, **When** the prompt is read,
   **Then** the slot is listed with its fragment and no flag, and the read's `diagnostics` carry
   `prompt_slot_not_empty` for that slot (OD-7, OD-8).
7. **Given** an action context of a mission type in force, **When** it is read, **Then** its `_links` carry `prompt`,
   the effective-prompt read of the same action.

---

### User Story 2 - A compose that would misplace a fragment is refused (Priority: P1)

An org or project overrides a step's prompt, and the overriding prompt drops a slot that an active enhancement fills.
Or an enhancement targets a step it may not enhance. The charter refuses the compose instead of dropping the fragment.

**Why this priority**: ADR-0066 decision 7 guarantees that nothing is silently dropped.

**Independent Test**: Compose a fixture closure with a project prompt override that omits the slot.

**Acceptance Scenarios**:

1. **Given** a project prompt override that does not declare `spdd:reasons-block`, and an active enhancement filling it,
   **When** any composing read runs, **Then** it refuses with `500 prompt_slot_missing`. **When** the pack that causes
   it is registered, or a write rebuilds the merge, **Then** it refuses with `422 prompt_slot_missing` (OD-4). An
   inactive enhancement does not refuse (OD-1).
2. **Given** an enhancement whose target step no pack of the closure holds, **When** composed, **Then** the compose
   refuses with `pack_reference_pack_missing` or `pack_reference_artifact_missing`, judged by `StepHolding` (OD-9).
3. **Given** a target step whose mission type is locked, **When** the enhancing pack is registered, **Then** it is
   refused with `422 override_not_permitted`. **When** a composing read runs, **Then** it answers
   `500 pack_unreadable`. A step inherits its mission type's mutability level (OD-2).

---

### User Story 3 - A harness reads every directive in force that governs an action (Priority: P1)

A harness reads the effective action context of `software-dev/implement` for this repository's own charter. Core's
`charter context` lists 16 directives. The service lists 7, the ones with a `scope` edge from
`action:software-dev/implement`. The context states which rule brings each listed artifact in. Each of the 16 directives
in force is either listed or left out by a recorded rule.

**Why this priority**: It is slice C's done-when and #450's open half. Without it, core cannot tell "not governing this
action" apart from "dropped by mistake".

**Independent Test**: A fixture shaped like the evidence below. It has directives scoped to the action, scoped to
another action, scoped to the mission type, scoped to another mission type, and unscoped; some are selected and one is
org-required. Read the context, and compare it with the ownership rule (Decision Moment
`01M774HQ8N5EVYDRTP16MBJCN3`, option B).

**Acceptance Scenarios** (option B, the ownership rule, ruled by the operator on 2026-11-12):

1. **Given** an in-force directive with a `scope` edge from the action, **When** the context is read, **Then** it is
   listed, brought in through the action.
2. **Given** an in-force directive that no action and no mission type scopes, selected or required, **When** the
   context of any action of a mission type in force is read, **Then** it is listed, brought in project-wide.
3. **Given** an in-force directive scoped by `mission_type:M` alone, **When** the context of an action of `M` is read,
   **Then** it is listed, brought in through the mission type. **When** the context of an action of another mission type
   is read, **Then** it is absent.
4. **Given** an in-force directive scoped only by other actions, **When** the context is read, **Then** it is absent,
   whether or not it is required.
5. **Given** an artifact brought in project-wide or through the mission type, **When** the context is read, **Then**
   what it `requires` (transitively) and what it `suggests` are listed as ADR-0018's steps 2 to 5 list them for a scoped
   artifact.
6. **Given** the evidence of 2026-11-12, **When** the 16 directives core lists for `software-dev/implement` are checked,
   **Then** a recorded table explains each one: listed with its rule, or left out with its rule.

### Edge Cases

- A step whose prompt declares no slots answers the prompt text and an empty slot list.
- A same-id enhancement at a higher level replaces the lower one. Only the winner's fragments are served, and only the
  winner is judged by the compose checks.
- A prompt override that keeps every slot an active enhancement fills composes. It may drop a slot no enhancement fills.
- An enhancement of a mission type that is not in force is inactive, so it neither renders nor refuses (OD-6).
- Editing only a fragment's bytes changes the prompt read's ETag. Editing a pack outside the closure does not.
- An artifact both scoped to the action and project-wide-eligible is listed once, brought in through the action (the
  first rule that reaches it wins, as ADR-0018 D3 deduplicates).
- An in-force directive both unscoped and reached from a scoped artifact through `requires` is listed once, through the
  action's walk.

## Domain Language

| Term | Meaning | Avoid |
|------|---------|-------|
| step enhancement | The artifact in an enhancing pack that adds to one step, joined to it by an `enhances` edge (ADR-0053 decision 22). | "step extension", "patch" |
| prompt fragment | Markdown body text in a `*.prompt-fragment.md` companion, filling one slot. | "snippet", "insert" |
| slot | A `<!-- <ns>:<name>:start -->` … `<!-- <ns>:<name>:end -->` marker pair in a step prompt, empty in a conforming base prompt. | "placeholder", "block" (except in the legacy id `reasons-block`) |
| prompt in force | The step prompt that wins the layer merge for the activation; it may be an override. | "base prompt" when an override is in force |
| effective step prompt | The prompt in force plus, per slot, the active fragments in order. The charter serves it and does not render it. | "rendered prompt" |
| scope owner | A node with a `scope` edge to an artifact: an action, or a mission type. | "claimed by" in prose |
| project-wide artifact | An artifact in force that no action and no mission type scopes. | "floating", "unscoped root" |

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Compose refuses a missing slot | As a renderer, I want any compose in which an **active** enhancement names a slot that the prompt in force does not declare, including after an override drops it, refused with `prompt_slot_missing`: `500` on composing reads, and `422` at registration and on writes that rebuild the merge (OD-1 (B), OD-4 (i)). An inactive enhancement does not refuse. So no fragment is silently lost. | High | Open | [build] | no |
| FR-002 | Compose resolves the target and its integrity | As an activation owner, I want a compose refused when an enhancement's target step is held by no pack of its `depends_on` closure (judged by `StepHolding`, OD-9), or when the target step's mission type is locked. A step inherits its mission type's mutability level (OD-2 (A)): `422 override_not_permitted` at registration, `500 pack_unreadable` on reads, the `override_not_permitted` description broadened, no new enum value. So fragments land only on steps that accept them. | High | Open | [build] | no |
| FR-003 | Effective-prompt read | As a renderer, I want `GET /activations/{activationId}/mission-types/{missionType}/actions/{action}/prompt` to return the prompt in force as stored (ADR-0066 decision 9: unchanged), and, for every slot it declares, the fragments of the enhancements that are themselves in force (ADR-0066 decision 4: no pull either way), ordered by pack level, then pack name, then enhancement id (decision 5), each with its pack and enhancement. It is a new built operation (`### Added`), so core's renderers place the same text on every path (decision 9). | High | Open | [build] | no |
| FR-004 | A mission type that is not in force | As a renderer, I want the prompt read of a mission type the closure holds but the activation does not put in force to answer `409 mission_type_not_in_force`, a new refusal code (OD-5, which stands: operator ruling of 2026-11-12, Decision Moment `01M774HSKK0SSA4147QEG9ZCKR`; `### Breaking`, an enum value added), with the reason it differs from the action context's `200`-empty answer recorded on ADR-0066 decision 9, so that I can tell it apart from a mission type that does not exist and never render a prompt for a mission that cannot run. | Medium | Open | [build] | no |
| FR-005 | Legacy slot text kept, with diagnostics | As a renderer, I want a slot that still holds legacy text listed with its fragments and no flag (OD-8: the renderer keeps anything between the markers and adds the fragments after it), and the prompt read to carry a `diagnostics` member, reusing `Diagnostic.yaml`, that lists `prompt_slot_not_empty` for such a slot (OD-7). So no base text is lost and the legacy state is visible. The legacy state is tested as the warning path of the load rule, an error-handling test (C-005). | Medium | Open | [build] | no |
| FR-006 | `prompt_slot_missing` raised | As a client, I want `prompt_slot_missing` raised, with an `operations` entry `getPackRegistration: 422` (OD-4), and its contract clause narrowed from "an active or inactive enhancement" to an active one (OD-1, `### Changed`), so that none of ADR-0066's four codes is still reserved. | High | Open | [build] | no |
| FR-007 | The context links the prompt | As a harness, I want the effective action context's `_links` to carry `prompt`, the effective-prompt read of the same action, with the relation listed in the hypermedia relation table, so a client goes from the context to the prompt without building a path. | Medium | Open | [build] | no |
| FR-008 | Scoping rule for in-force artifacts the action's walk does not reach | As a harness, I want the effective action context to apply the ownership rule (option B, operator ruling of 2026-11-12, Decision Moment `01M774HQ8N5EVYDRTP16MBJCN3`): an in-force directive, tactic or paradigm is listed when the action's walk reaches it, when no action and no mission type scopes it, or when the action's own mission type scopes it, together with what ADR-0018's steps 2 to 5 reach from it; one scoped only by other actions or other mission types is not listed. So every directive in force is listed or left out by a stated rule. | High | Open | [build] | no |
| FR-009 | Each listed item says why it is listed | As a harness, I want each context item to carry the rule that brought it in, `appliesThrough`: `action`, `mission_type` or `project`, as an additive member (operator ruling of 2026-11-12), so a client can tell action-scoped governance from type-wide and project-wide governance. | Medium | Open | [build] | no |
| FR-010 | The ruling is recorded | As a maintainer, I want the scoping ruling recorded as a dated amendment to ADR-0018 (D5) and rule E16, a divergence-register entry for honouring mission-type scope (core ignores it), and the 16 directives of the 2026-11-12 evidence explained in a recorded table. So the next usability run can check them. | High | Open | [build] | yes: positive control is FR-008's fixture test, which fails without the rule |
| FR-011 | R3 decided | As a maintainer, I want R3's decision recorded in `docs/api/service-contract.md`: the merged graph under an activation (`getEffectiveGraph`), the per-artifact graph queries (`listArtifactReferences`) and the reference checks at registration answer core's graph reads; the unfiltered `/offering/graph` waits for the minor that brings the offering merge, as R5's `/offering/mission-types` does. #97 is then closed with that evidence. | Medium | Open | [build] | yes: positive control is the docs checks and the issue state |
| FR-012 | Phase-2 rulings recorded | As a maintainer, I want these recorded: dated annotations on ADR-0066 decision 8 (OD-1: active only; the moments widened to registration, the impact-checked writes and the activation writes, OD-4), decision 7's integrity bullet (OD-2), decision 9 (OD-5: the 409, why it differs from the context, and that it is `### Breaking`, not additive; the text served as stored), decision 4 cited for the 409, a "now built" annotation, and the Follow-ups' built marker; the conceptual-model integrity note; the rules page's compose rules, the effective step prompt and the amended E16; the operation and status lists; the `prompt` relation; the glossary terms "effective step prompt" and "project-wide artifact"; and a dated changelog page. So docs and contract say what the service does. | Medium | Open | [build] | yes: positive control is the docs checks, `RelationTableParityTest` and the contract tests that pin the text |
| FR-013 | Every overrider in force is named (#439) | As a client, I want `satisfiedBy` and `overriddenBy` to list every overrider in force that satisfies or hides an artifact, sorted by level (built-in, org, project), then URN, in `EffectiveArtifact`, `EffectiveContextArtifact`, the effective artifact and profile reads that copy those members, and the skipped report's `overridden`, instead of the one with the smallest URN (operator ruling of 2026-11-12, option (b); `### Breaking`), so that no order picks one silently. | Medium | Open | [build] | no |
| FR-014 | An override comes from an activatable artifact (#447) | As an activation owner, I want an `overrides` edge whose source is not of an activatable kind (template, asset, guard, lifecycle) refused: at registration and on `putPackRelationship` as a violation `overrides_source_not_activatable` inside `422 validation_failed`, and on a composing read that meets one in a stored pack with `500 pack_unreadable` (operator ruling of 2026-11-12, option 1; `### Breaking`), so that the read and the write never disagree about what satisfies a required artifact. | Medium | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Deterministic prompt read | For the same activation state, the effective-prompt read returns an identical body and ETag across 100 repeated reads, and across every permutation of the order in which the closure's packs are configured and loaded (the activation's revision held fixed), tested with at least 3 packs. | Reliability | High | Open |
| NFR-002 | Bounded prompt read cost | The effective-prompt read's work grows at most linearly with the number of step enhancements in the closure, pinned by an operation-count test at 1,000 and 2,000 enhancements: the counted operation is a visit of an enhancement or a slot, and the ratio of counts is at most 2.5. | Performance | Medium | Open |
| NFR-003 | Bounded context cost | The scoping rule adds at most linear work in the number of artifacts in force: an operation-count test at 1,000 and 2,000 in-force directives keeps the ratio of counts at most 2.5, the counted operation being a visit of an edge or a node, the build of the memoized scope-owner index included. | Performance | Medium | Open |
| NFR-004 | Deterministic context | The effective action context is byte-identical across 100 repeated reads and every permutation of the order in which the closure's packs are configured and loaded (3 packs, the activation's revision held fixed), with the scoping rule applied. | Reliability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Contract versioning | Every contract change lands with a dated fragment under `contracts/charter/changes/`: `### Added` for the new read, its schemas, the `prompt` link and the context's new member; `### Changed` for `prompt_slot_missing` raised and the broadened `override_not_permitted`; `### Breaking` for a compose or registration accepted today and now refused (OD-4), and for any change in meaning of the context's listing (ADR-0022, 0.x line). | Contract | High | Open |
| C-002 | Round trip unaffected | The compose checks and the reads do not change how enhancements and fragments are held or written back (ADR-0036). The whole-pack round trip stays green over the example packs. | Integrity | High | Open |
| C-003 | Graph-only relationships | An enhancement's target and an artifact's scope owners are read only from graph-document edges, never from the directory or a field (ADR-0064). | Architecture | High | Open |
| C-004 | Test first | BDD scenarios under `contracts/charter/scenarios/` come before the code for every behaviour the contract changes. Every refusal has a test that fails without its check, and a passing twin. | Process | High | Open |
| C-005 | Canonical shape only | Tests, fixtures and examples carry only the canonical shape, except tests of error handling. No field is excluded from a parity or round-trip check (charter companion, 2026-11-10). The proof runs on charter's example packs (ADR-0070), not open-packs content, which this mission does not edit; other cases use scenario fixtures. The legacy non-empty slot of an unversioned descriptor is tested only as the load rule's warning path. | Process | High | Open |
| C-006 | Stay out of slice B | Pack registration internals, `file-kinds.yaml`, `PackCommitStore`, `PackLocator`, the Loader, `CharterRefusalCode` additions and the pack document belong to slice B (PR #501). This mission adds one refusal code, `mission_type_not_in_force` (OD-5), in the shared `CharterRefusalCode.yaml`, routed through the orchestrator. It touches the registration path only to raise the 422 compose checks. One additive hunk in `DomainRepositoryService.compositionFacts` fills the slot facts (research R9), written after the rebase onto #501. Any overlap is routed through the orchestrator. It rebases onto `main` once #501 merges, before the pre-merge squad. | Coordination | High | Open |
| C-007 | Core is evidence | Core's current output is evidence of intent, never the oracle (charter companion, epic #186). A difference from core is recorded in the divergence register, not copied. | Governance | High | Open |
| C-008 | Out of scope | Core's renderers; guards and emits on enhancements (#434); the open-packs pin bump (#435); the real `spdd` pack (open-packs #205, #206); the offering-wide merged reads; and the tension annotation (#16). | Scope | High | Open |
| C-009 | New ADR number | A new ADR, if one is written, takes 0074 or the next free number after it, checked against `main` and the open PRs when it is written (0072 is on `main`, 0073 is reserved for slice B; orchestrator, 2026-11-12). | Governance | Medium | Open |

## Success Criteria *(mandatory)*

- **SC-001** On charter's example packs, the effective prompt of `software-dev/plan`, with built-in's `plan-risk-notes`
  and org-base's `spdd-plan` selected, lists both fragments in the `spdd:reasons-block` slot, each with its provenance.
  A scenario fixture with three levels, two org packs whose ids sort opposite to their names, a built-in enhancement id
  that sorts after the org one, and a project fragment, lists the fragments in the order of ADR-0066 decision 5 and in
  no other. — [build] · no-op passable: no
- **SC-002** Every refusal rule in FR-001 and FR-002 is demonstrated by one failing fixture and one passing fixture that
  differ only in the faulty part. — [build] · no-op passable: no
- **SC-003** `prompt_slot_missing` is no longer reserved, and at least one scenario raises it on a composing read and
  one at registration. — [build] · no-op passable: no
- **SC-004** For a fixture shaped like the 2026-11-12 evidence, each of the 16 directives is listed by the context or
  left out by a recorded rule, and the recorded table explains each one by id. — [build] · no-op passable: no
- **SC-006** A fixture with two overriders in force of one required artifact, at two levels, lists both in level-then-URN
  order wherever the carriers appear; a fixture with a template overriding a directive is refused at registration and on
  the relationship write, and its passing twin (a directive overriding it) is accepted. — [build] · no-op passable: no
- **SC-005** #438, #450, #97, #439 and #447 are closed or narrowed with evidence, and #467's checklist is ticked. — [build] · no-op
  passable: no

## Key Entities

- **Effective step prompt**: the prompt text in force; the slots it declares, each with its ordered active fragments
  and their provenance (pack id, pack name, level, enhancement id); `diagnostics`; and `_links`.
- **Slot facts**: per pack, the slots each step prompt declares and the slots each step enhancement fills, as
  composition facts.
- **Scope owners**: per artifact in force, the actions and mission types with a `scope` edge to it on the merged graph.

## Evidence (2026-11-12): the 16 directives of `software-dev/implement`

Taken from open-packs `a6cfeab`, this repository's `.kittify/charter/charter.yaml` (16 `activated_directives`), and
core 4.0.0rc9's `spec-kitty charter context --action implement --json`. Core's 16 is its `all_directives` list: the
whole project directive set, rendered as "Directive IDs" on every action. It is not core's action-scoped `directives`
array. Core's own action rule (`delivery_table.py`, `_classify_artifact_urns`) adds selected and required
directive/tactic/paradigm roots to an action, unless another action scopes them, and ignores mission-type scope.

| Directive | Scope owners in open-packs | Service today | Under the ownership rule (ruled) |
|---|---|---|---|
| DIRECTIVE_001, 010, 025, 030, 034, 037, 051 | include `action:software-dev/implement` | listed | listed, through the action |
| DIRECTIVE_003 | 14 other actions, not implement | absent | absent: scoped only by other actions |
| DIRECTIVE_032, DIRECTIVE_041 | none | absent | listed, project-wide |
| NO_FULL_HEAVY_SUITES_IN_MISSION (org-required) | none | absent | listed, project-wide |
| QA_003_NO_FAKE_GREEN | none | absent | listed, project-wide |
| RECONCILE_CHANGE_SCOPE_TENSIONS, USE_C4_MODEL_TECHNIQUES | none | absent | listed, project-wide |
| PROJECT_001, PROJECT_002 (project pack) | none | absent | listed, project-wide |

`QA_003` (scoped by `mission_type:qa`) is a different id from `QA_003_NO_FAKE_GREEN`.

## Assumptions

- "The base prompt text unchanged" (ADR-0066 decision 9) is met by the prompt in force rendered from the held document
  with equal meaning (ADR-0036, ruling D5 of the phase-1 mission).
- The prompt read lists every declared slot, with an empty fragment list where nothing fills it (D5).
- Phase 1's slot grammar (`PromptSlots`, `SlotId`), the held step enhancement (`StepEnhancementFacts`) and PR C's
  `StepHolding`, `ActionContextWalk` and `NodeRef` are on `main` (checked 2026-11-12).
- The body-links half of #450 shipped in PR #486: each effective-artifact item carries `effective-artifact` and
  `artifact` links.

## Dependencies

- The operator's rulings of 2026-11-12 adding #439 (option b) and #447 (option 1) to the slice, relayed by the
  orchestrator (research `research/in-force-rulings-439-447.md`).
- The operator's rulings of 2026-11-12, relayed by the orchestrator: Decision Moment `01M774HQ8N5EVYDRTP16MBJCN3`
  (#450 scoping: option B, the ownership rule) and `01M774HSKK0SSA4147QEG9ZCKR` (OD-5 stands: `409`).
- Slice B (PR #501): this mission rebases onto it once it merges, before the pre-merge squad.
- Parked plan input: the former phase-2 mission's spec, its WP07–WP11 and its contract draft (branch
  `issue-424-adr-0066-phase-2-effective-prompt`), and PR C's symbol list (comment on #438).
