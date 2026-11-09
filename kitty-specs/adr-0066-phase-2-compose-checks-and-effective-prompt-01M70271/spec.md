# Mission Specification: Compose checks and the effective step prompt (ADR-0066, phase 2)

> **Draft — re-validate against main after PR C merges.** Every requirement below was carried over from the phase-1
> mission's plan on 2026-11-09, before PR C of `layer-merge-and-effective-reads-01M6R0J6` merged. The names this spec
> assumes from PR C (the action walk, `NodeReference`, the OD-6 E6 amendment, the OD-9 shared predicate,
> `action_not_found`) are checked against `main` before `/spec-kitty.plan` runs.

**Mission Branch**: created on its own branch; slug recorded on creation
**Created**: draft 2026-11-09 (not yet created as a mission)
**Status**: Draft — re-validate against main after PR C merges
**Input**: #424; the operator's split ruling of 2026-11-09; the phase-1 mission
`adr-0066-step-enhancement-service-alignment-01M6ZRYK` (its spec's former User Stories 4 and 5, former adr-0066-step-enhancement-service-alignment-01M6ZRYK#FR-008 to
adr-0066-step-enhancement-service-alignment-01M6ZRYK#FR-010, the phase-2 parts of former adr-0066-step-enhancement-service-alignment-01M6ZRYK#FR-011 and adr-0066-step-enhancement-service-alignment-01M6ZRYK#FR-012, former adr-0066-step-enhancement-service-alignment-01M6ZRYK#NFR-003 and adr-0066-step-enhancement-service-alignment-01M6ZRYK#NFR-004, former adr-0066-step-enhancement-service-alignment-01M6ZRYK#SC-001); its `plan.md`
sections IC-05, IC-06, IC-07, the phase-2 parts of IC-08 and the "ruled" table, on branch
`issue-424-428-adr-0066-service-alignment`.

## Intent Summary

This is **phase 2 of 2**. Phase 1 (`adr-0066-step-enhancement-service-alignment-01M6ZRYK`) holds step enhancements
and their fragments, refuses malformed ones at load, and closes #428. This mission closes #424.

- **Primary actors.**
  - A **prompt renderer**, core's skill renderer or `spec-kitty next`, asks the charter for the prompt a step really
    gets.
  - An **activation owner** selects step enhancements and overrides step prompts.
  - A **pack author** ships enhancing packs and prompt overrides.
- **Trigger.** A renderer reads a step's prompt under an activation; or a compose, a registration or a write that
  rebuilds the merge meets an enhancement that would misplace its fragment.
- **Desired outcome.**
  - The charter serves the effective step prompt: the prompt in force, and for each slot the active fragments in a
    fixed order, each with its provenance.
  - It refuses a compose in which an active fragment would be silently dropped or would land on a step that does not
    accept it.
- **Rule that always holds.** A fragment only ever fills a slot that the prompt in force declares. It never replaces
  base text, never adds a slot, and renders only while its own enhancement is active (ADR-0066 decisions 4 and 7).
- **Boundaries.**
  - The charter does not render prompts and does not judge what a fragment says.
  - The E6 amendment (OD-6) and the shared "pack holds step" predicate (OD-9) are PR C's; this mission consumes them.
  - Guards and emits stay recorded as a shape only (#434).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A renderer reads a step's effective prompt (Priority: P1)

(Was phase-1 User Story 4.)

A renderer asks the charter for the effective prompt of step `<action>` of mission type `<type>` under activation
`<id>`. The charter answers with the prompt in force and, for every slot the prompt declares, the active fragments in
the order of ADR-0066 decision 5, each naming the pack and enhancement it came from. The renderer places them.

**Why this priority**: This is the consumer-facing outcome of ADR-0066 decision 9 and what keeps #424 open.

**Independent Test**: Activate a fixture closure with two enhancing packs at different levels, read the prompt, and
check the order and provenance.

**Acceptance Scenarios**:

1. **Given** an activation with active enhancements from a built-in, an org and a project pack, each filling
   `spdd:reasons-block`, **When** the prompt is read, **Then** the slot lists the three fragments ordered built-in,
   org, project; within a level by pack name, then by enhancement id.
2. **Given** an enhancement that is held but not selected, **When** the prompt is read, **Then** its fragment is
   absent. Its target being active does not pull it in.
3. **Given** a slot that no active enhancement fills, **When** the prompt is read, **Then** the slot is listed with no
   fragments.
4. **Given** an unknown activation, mission type or step, **When** the prompt is read, **Then** the answer is the
   contract's not-found refusal, never an empty prompt. **Given** a mission type the closure holds but the activation
   does not put in force, **Then** the answer is `409 mission_type_not_in_force` (operator ruling OD-5).
5. **Given** a slot that still holds legacy base text and an active fragment for it, **When** the prompt is read,
   **Then** the slot is listed with its fragment. The renderer contract says to keep anything between the markers and
   add the fragments after it, so no base text is lost (operator ruling OD-8). The response's `diagnostics` carry
   `prompt_slot_not_empty` for that slot (operator ruling OD-7).

---

### User Story 2 - A compose that would misplace a fragment is refused (Priority: P1)

(Was phase-1 User Story 5.)

An org or project overrides a step's prompt, and the overriding prompt drops a slot that an active enhancement fills.
Or an enhancement targets a step it is not allowed to enhance. The charter refuses the compose instead of silently
dropping the fragment.

**Why this priority**: This is ADR-0066 decision 7's guarantee that nothing is silently dropped. It needs PR C's
merged-step view (operator ruling D2).

**Independent Test**: Compose a fixture closure with a prompt override that omits the slot.

**Acceptance Scenarios**:

1. **Given** a project prompt override that does not declare `spdd:reasons-block`, and an active enhancement filling
   it, **When** any composing read runs, **Then** it refuses with `500 prompt_slot_missing`. **When** the pack that
   causes it is registered, or a write rebuilds the merge, **Then** it refuses with `422 prompt_slot_missing`
   (operator ruling OD-4). An inactive enhancement in the closure does not refuse: only an active one does (operator
   ruling OD-1, decision `01M6ZS10GDRY7NAA4PA5BK5EB9`).
2. **Given** an enhancement whose target step does not resolve in the `depends_on` closure, **When** composed,
   **Then** the compose refuses with `pack_reference_pack_missing`, `pack_reference_artifact_missing` or, for a bare
   target that resolves only in another pack, `pack_reference_unqualified`.
3. **Given** a target step whose mission type is locked, **When** the enhancing pack is registered, **Then** it is
   refused with `422 override_not_permitted`; **When** a composing read runs, **Then** it answers
   `500 pack_unreadable`, as for a forbidden override under rule C21. A step inherits its mission type's mutability
   level (operator ruling OD-2, decision `01M6ZS12SJAZYET6A3E06R0T74`).

### Edge Cases

- A renderer asks for the prompt of a step whose prompt declares no slots. It gets the prompt text and an empty slot
  list.
- A step with no prompt at all answers an empty prompt and no slots (planning reading; confirm at plan).
- A same-id enhancement at a higher level replaces the lower one; only the winning copy's fragments are served, and
  only the winning copy is judged by the compose checks.
- A prompt override that keeps every slot an active enhancement fills composes; a slot no enhancement fills may be
  dropped.
- An enhancement of a mission type that is not in force is inactive, so it neither renders nor refuses (OD-6 meaning:
  a selected step enhancer is in force exactly when the mission type of its single target is in force).
- Editing only a fragment's bytes changes the read's ETag; editing a pack outside the closure does not.

## Domain Language

| Term | Meaning | Avoid |
|------|---------|-------|
| step enhancement | The artifact in an enhancing pack that adds to one step, joined to it by an `enhances` edge (ADR-0053 decision 22). | "step extension", "patch" |
| prompt fragment | Markdown body text in a `*.prompt-fragment.md` companion, filling one slot. | "snippet", "insert" |
| slot | A `<!-- <ns>:<name>:start -->` … `<!-- <ns>:<name>:end -->` marker pair in a step prompt, empty in a conforming base prompt. | "placeholder", "block" (except for the legacy `reasons-block` id) |
| prompt in force | The step prompt that wins the layer merge for the activation, which may be an override. | "base prompt" when an override is in force |
| effective prompt | The prompt in force plus, per slot, the active fragments in order. The charter serves it and does not render it. | "rendered prompt" |

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Compose refuses a missing slot | As a renderer, I want any compose in which an **active** enhancement names a slot that the prompt in force does not declare, including after an override drops it, refused with `prompt_slot_missing`: `500` on composing reads, and `422` at registration and on the writes that rebuild the merge (operator rulings OD-1 (B) and OD-4 (i)); an inactive enhancement does not refuse; so that no fragment is silently lost. (Was phase-1 adr-0066-step-enhancement-service-alignment-01M6ZRYK#FR-008.) | High | Open | [build] | no |
| FR-002 | Compose resolves the target and its integrity | As an activation owner, I want a compose refused when an enhancement's target step does not resolve in the `depends_on` closure, judged by PR C's one shared predicate "P defines `mission_type:M` and declares `action:M/s`" (OD-9), or when the target step's mission type is locked, since a step inherits its mission type's mutability level (OD-2 (A): `422 override_not_permitted` at registration, `500 pack_unreadable` on reads, the `override_not_permitted` description broadened with no new enum value), so that fragments land only on steps that accept them. (Was phase-1 adr-0066-step-enhancement-service-alignment-01M6ZRYK#FR-009.) | High | Open | [build] | no |
| FR-003 | Effective-prompt read | As a renderer, I want `GET /activations/{id}/mission-types/{type}/actions/{action}/prompt` to return the prompt in force unchanged in meaning (its canonical render, D5) and, for every slot it declares, the fragments of the enhancements that are themselves in force (ADR-0066 decision 4: no pull either way), ordered by pack level, then pack name, then enhancement id (decision 5), each with its pack and enhancement, as a new built operation (`### Added`), so that core's renderers place the same text on every path (decision 9). (Was phase-1 adr-0066-step-enhancement-service-alignment-01M6ZRYK#FR-010.) | High | Open | [build] | no |
| FR-004 | Mission type not in force | As a renderer, I want the prompt read for a mission type the closure holds but the activation does not put in force to answer `409 mission_type_not_in_force`, a new refusal code (OD-5, `### Breaking`), so that I can tell it apart from a mission type that does not exist. (Was part of phase-1 adr-0066-step-enhancement-service-alignment-01M6ZRYK#FR-010.) | Medium | Open | [build] | no |
| FR-005 | Legacy slot text kept, with diagnostics | As a renderer, I want a slot that still holds legacy text listed with its fragments and no flag, the renderer contract saying "keep anything between the markers, then add the fragments after it" (OD-8), and the prompt read to carry a `diagnostics` member, reusing `Diagnostic.yaml`, that lists `prompt_slot_not_empty` for such a slot (OD-7), so that no base text is lost and the legacy state is visible. (Was part of phase-1 adr-0066-step-enhancement-service-alignment-01M6ZRYK#FR-010 and adr-0066-step-enhancement-service-alignment-01M6ZRYK#FR-011.) | Medium | Open | [build] | no |
| FR-006 | `prompt_slot_missing` raised | As a client, I want `prompt_slot_missing` raised as a top-level code, with an `operations` map entry `getPackRegistration: 422` (OD-4), and its contract clause narrowed from "an active or inactive enhancement" to an active one (OD-1, `### Changed`), so that none of ADR-0066's four codes is still reserved. (Was the phase-2 part of phase-1 adr-0066-step-enhancement-service-alignment-01M6ZRYK#FR-011.) | High | Open | [build] | no |
| FR-007 | Phase-2 rulings recorded | As a maintainer, I want these recorded: a dated annotation on ADR-0066 decision 8 (OD-1: only an active enhancement refuses) and on decision 7's integrity bullet (OD-2: a step inherits its mission type's mutability level); the conceptual-model integrity note; the rules page's compose rules and effective step prompt; the operation and status lists (C-11); the `prompt` relation on the action context (C-05); the glossary term "effective step prompt"; and a dated changelog page; so that docs and contract say what the service does. (Was the phase-2 part of phase-1 adr-0066-step-enhancement-service-alignment-01M6ZRYK#FR-012.) | Medium | Open | [build] | yes — positive control: the docs checks, `RelationTableParityTest` and the contract tests pin the text |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Deterministic prompt read | For the same activation state, the effective-prompt read returns an identical body and ETag across 100 repeated reads and across every permutation of the closure's pack order, tested with at least 3 packs. (Was phase-1 adr-0066-step-enhancement-service-alignment-01M6ZRYK#NFR-003.) | Reliability | High | Open |
| NFR-002 | Bounded read cost | The effective-prompt read's work grows at most linearly with the number of step enhancements in the closure. It is pinned by an operation-count test at 1,000 and 2,000 enhancements, as the effective reads' adr-0066-step-enhancement-service-alignment-01M6ZRYK#NFR-003 gate is. (Was phase-1 adr-0066-step-enhancement-service-alignment-01M6ZRYK#NFR-004.) | Performance | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Contract versioning | Every contract change lands with a dated fragment under `contracts/charter/changes/`: `### Added` for the new read, its schemas and the `prompt` link; `### Changed` for `prompt_slot_missing` raised and the broadened `override_not_permitted` description; `### Breaking` for a compose or registration accepted today and now refused (OD-4) and for the new enum value `mission_type_not_in_force` (OD-5) (ADR-0022, 0.x line). | Contract | High | Open |
| C-002 | Round trip unaffected | The compose checks and the read do not change how enhancements and fragments are held or written back (phase 1's round trip by meaning, ADR-0036). | Integrity | High | Open |
| C-003 | Graph-only relationships | An enhancement's target is read only from its `enhances` edge in the graph documents, never from the directory or a field (ADR-0064). | Architecture | High | Open |
| C-004 | Test first | BDD scenarios under `contracts/charter/scenarios/` come before code for every behaviour the contract changes. Every refusal has a test that fails without its check, and a passing twin. | Process | High | Open |
| C-005 | Out of scope | #429 is PR C's (including the OD-6 E6 amendment and the OD-9 predicate, consumed here, not built); #430 is done; #431 `org-charter.yaml`, #432, #433, #434 guard and emit fields, #435 pin bump, and core's renderers are out of scope. If org-level requirements or presets are touched, cite #431 and escalate. | Scope | High | Open |
| C-006 | Blocked until | Work starts only after (1) PR C of `layer-merge-and-effective-reads-01M6R0J6` (WP27–WP29, #429) has merged into `main`, carrying the OD-6 E6 amendment, the OD-9 shared predicate, `NodeReference` and the action walk; and (2) the phase-1 mission `adr-0066-step-enhancement-service-alignment-01M6ZRYK` has merged. The plan and the tasks are then redone against `main`. | Sequencing | High | Open |

## Success Criteria *(mandatory)*

- **SC-001** A fixture pack shaped like open-packs#175's `spdd` pack registers, and its five step enhancements are
  selectable. Each one's fragment then appears in the effective prompt of its step. (Was phase-1 adr-0066-step-enhancement-service-alignment-01M6ZRYK#SC-001.) —
  [build] · no-op passable: no
- **SC-002** Every refusal rule in FR-001 and FR-002 is demonstrated by one failing fixture and one passing fixture
  that differ only in the faulty part. (Was the compose part of phase-1 adr-0066-step-enhancement-service-alignment-01M6ZRYK#SC-002.) — [build] · no-op passable: no
- **SC-003** `prompt_slot_missing` is no longer listed as reserved, and has at least one scenario that raises it on a
  composing read and one at registration. (Was the fourth code of phase-1 adr-0066-step-enhancement-service-alignment-01M6ZRYK#SC-004.) — [build] · no-op passable: no
- **SC-004** #424 can be closed: every checklist item is delivered or ruled and recorded. — [build] · no-op passable:
  no

## Key Entities

- **Effective step prompt**: the prompt text in force, a list of slots, each with its ordered active fragments and
  their provenance (pack id, pack name, level, enhancement), and the read's `diagnostics`.
- **Slot facts**: per pack, the slots each step prompt declares and the slots each step enhancement fills, as
  composition facts.

## Assumptions

- "The base prompt text unchanged" (ADR-0066 decision 9) is met by the prompt in force rendered from the held
  document with equal meaning (ADR-0036). The service holds a parsed step document, not raw bytes (operator ruling
  D5).
- The prompt read lists every declared slot, with empty fragment lists where nothing fills it (operator ruling D5).
- Phase 1's slot grammar (`PromptSlots`, `SlotId`), the held `StepEnhancement` and the registration `diagnostics`
  member are on `main` when this mission starts.
- The pack fixtures stand in for the real `spdd` pack until #435 bumps the pin (operator ruling D21).

## Dependencies

- PR C of `layer-merge-and-effective-reads-01M6R0J6` (WP27–WP29, with #429) must merge first. It owns the E6
  amendment (OD-6): without it no step enhancement can be in force, so SC-001, User Story 1 and FR-001's in-force
  filter cannot pass. It also owns the shared "pack holds step" predicate (OD-9) and `action_not_found`.
- The phase-1 mission `adr-0066-step-enhancement-service-alignment-01M6ZRYK` (closes #428) must merge first.
- The parked material (former WP07–WP11 prompts, the effective-prompt contract draft) is input to this mission's plan,
  not a commitment; see `README.md` beside this spec.
