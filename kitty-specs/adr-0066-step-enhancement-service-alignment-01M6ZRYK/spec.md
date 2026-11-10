# Mission Specification: Step enhancements and prompt fragments in the charter service

**Mission Branch**: `issue-424-428-adr-0066-service-alignment`
**Created**: 2026-11-09
**Status**: Draft (narrowed to phase 1 of 2 by operator ruling, 2026-11-09)
**Input**: #424 and #428, from the Phase A research brief (`research/grounding.md`) and the operator's rulings of
2026-11-09 (research brief "Operator rulings"; decision `01M6ZS0Y9AZ7C1PNNMVVS1GRW8`).

## Intent Summary

This is **phase 1 of 2**. It closes #428 and its own tracker #437 (ADR-0066 phase 1), and refers to #424.
Phase 2, the compose checks and the effective-prompt read, is the phase-2 mission (#438, mission `adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271`), which closes #424. See
[Split (operator ruling, 2026-11-09)](#split-operator-ruling-2026-11-09).

- **Primary actors.**
  - A **pack author** ships a pack whose step enhancements add prompt text to a base mission's steps. The open-packs
    `spdd` pack (open-packs#175) is the first.
  - An **activation owner** selects those enhancements for a project.
- **Trigger.** A pack carrying step enhancements and their Markdown prompt fragments is registered or validated.
  Later, an activation selects an enhancement.
- **Desired outcome.**
  - The charter holds the enhancement and its fragments, and writes them back without loss of meaning.
  - It refuses malformed enhancements, fragments and prompt slots at load, with the codes ADR-0066 reserved.
  - It reports a base prompt's legacy non-empty slot as a warning in the registration result.
- **Rule that always holds.** A fragment is body text only: it never carries frontmatter or a slot marker, and a base
  prompt declares well-formed slots (ADR-0066 decisions 2, 3 and 8). Where a fragment lands at compose, and what a
  renderer is served, is the phase-2 mission's.
- **Boundaries.**
  - The charter does not render prompts and does not judge what a fragment says.
  - Guards and emits on an enhancement are recorded as a shape only (#434).
  - The compose checks, the effective-prompt read and the ADR-0066 d7/d8 annotations are the phase-2 mission's.
  - The skipped-report step targets are PR C's (#429).
  - Parity tests are a separate Op (#430).
  - `org-charter.yaml` is out of scope (#431).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A pack with step enhancements registers and round-trips (Priority: P1)

A pack author registers a pack that ships step enhancements under `missions/<type>/enhancements/`, each with one or
more `*.prompt-fragment.md` companions. Today the charter refuses the pack as "unmodelled" and drops the files on
write. After this mission, it holds them and writes them back with the same paths and meaning.

**Why this priority**: Nothing else in ADR-0066 can work until the charter holds the artifact. This story alone
unblocks open-packs#175.

**Independent Test**: Register a fixture pack shaped like the `spdd` pack, read it back, and compare its meaning to
the source files.

**Acceptance Scenarios**:

1. **Given** a pack with a step enhancement and its fragment companion, **When** it is registered, **Then** the
   registration succeeds and both files are held.
2. **Given** that registered pack, **When** it is written back, **Then** every enhancement and fragment returns at its
   original path with equal meaning (ADR-0036).
3. **Given** a pack with no step enhancements, **When** it is loaded before and after this mission, **Then** its source
   fingerprint is unchanged.

---

### User Story 2 - An activation selects a step enhancement (Priority: P1)

An activation owner selects a step enhancement directly or through a preset (`activated_step_enhancements`). Today the
selection answers `404 artifact_not_found`, and preset ids show as stale and are dropped by the next write.

**Why this priority**: Selecting the enhancement is the only gate on whether its fragments render (ADR-0066 decision
4).

**Independent Test**: Select a held step enhancement and read the activation back.

**Acceptance Scenarios**:

1. **Given** a registered pack with step enhancement `spdd-plan`, **When** the owner selects
   `step_enhancement/spdd-plan`, **Then** the write succeeds and the activation lists it as selected.
2. **Given** a preset that lists the enhancement, **When** the preset is applied and the activation is written again,
   **Then** the enhancement id is kept, not reported as stale.
3. **Given** another pack at the same level that defines a step enhancement with the same id, **When** both are
   composed, **Then** the compose refuses with the existing sibling-conflict rule. Sibling enhancers of one step with
   different ids compose (ADR-0053 decision 21).

---

### User Story 3 - Malformed enhancements, fragments and prompt slots are refused at load (Priority: P1)

A pack author makes a mistake: an empty fragment, a fragment with frontmatter or with a slot marker, a slot named
twice, an enhancement whose target is not a step or that overrides anything, a directory that disagrees with the target, a missing or orphaned
companion, or a base prompt with a malformed or non-empty slot. The charter refuses the pack at validation and at
registration, and names the problem.

**Why this priority**: Without load checks, a bad fragment would reach a renderer. ADR-0066 decision 8 makes these
checks the charter's job.

**Independent Test**: Validate one fixture pack per rule and check the refusal code and the named file.

**Acceptance Scenarios**:

1. **Given** a fragment that is blank, has frontmatter, or contains `<!-- x:y:start -->`, **When** the pack is
   validated or registered, **Then** it is refused with `prompt_fragment_invalid`, and the violation's detail names
   the fragment. (Its pointer is the fragment for a slot marker; for a blank or frontmatter fragment it is the
   enhancement's yaml, since the store's companion read refuses that fragment at the yaml; data-model.md, "Where each
   enhancement or fragment fault points"; fourth analysis A12.)
2. **Given** an enhancement whose `enhances` edge targets something other than one `action:<type>/<step>`, or whose
   `<type>` directory differs from the edge's type, or that is the source of any `overrides` edge (operator ruling
   R-OVR: an enhancement only adds), **When** loaded, **Then** it is refused with `prompt_fragment_invalid`. **Given**
   the same enhancement with only its single `enhances` edge, **Then** it loads (the positive control). **Given** a
   registered pack holding a valid enhancement, **When** a relationship write would give it an `overrides` edge or a
   second `enhances` edge, **Then** the write is refused `422 prompt_fragment_invalid` and nothing is written; the same
   `overrides` write from a tactic of the pack succeeds (operator ruling R-REL, 2026-11-09).
3. **Given** a base prompt with a start marker but no end marker, a repeated slot id, or nested slots, **When**
   loaded, **Then** it is refused with `prompt_slot_malformed`.
4. **Given** a base prompt whose slot holds text, **When** loaded from a pack whose descriptor declares
   `schema_version: "1.0"`, **Then** it is refused with `prompt_slot_not_empty`. **When** the descriptor declares no
   version, **Then** it loads, and the registration result's `diagnostics` carry a `prompt_slot_not_empty` warning
   (operator ruling OD-7). This is the state of the built-in pack at the current pin.
5. **Given** an enhancement that names one slot twice or names a missing companion, **When** loaded, **Then** it is
   refused with `prompt_fragment_invalid`. **Given** a companion no entry names (an orphan), **When** the pack is
   validated or registered, **Then** it is refused with `prompt_fragment_invalid`; a composing read does not hold the
   orphan, like every unmodelled file, and does not refuse it (plan P8, reading of D11; fourth analysis A11).
   **Given** an entry whose slot id
   breaks the `<namespace>:<name>` grammar, **When** loaded, **Then** it is refused by the step-enhancement schema (a
   schema violation, not `prompt_fragment_invalid`), as `prompt_fragments: []` is, and so is an enhancement whose id
   differs from its file stem (the store's stem rule, operator ruling A1).

---

The former User Stories 4 (a renderer reads a step's effective prompt) and 5 (a compose that would misplace a fragment
is refused) moved to the phase-2 mission, where they are its User Stories 1 and 2.

### Edge Cases

- An enhancement is the source of an `overrides` edge, alone or beside its `enhances` edge. It is refused with
  `prompt_fragment_invalid` (R-OVR): an enhancement only adds, so it never overrides, not even its own mission type.
  A relationship write that would add such an edge, or a second `enhances` edge, to a held enhancement is refused
  before anything is written (R-REL).
- One enhancement fills two slots, with two companions. Both are held. Each companion is named by exactly one entry,
  and a companion named by no entry is refused.
- Two mission types in one pack have enhancements with the same file stem. The second, in path order (segment by
  segment, the order validate mode already sorts files by), is refused
  with `prompt_fragment_invalid` and withheld from the pack, so the pack's same-id invariant never breaks the load:
  ids are unique per pack (operator rulings D8 and A2).
- An enhancement with no fragments, and no guards or emits since those are not built, is refused as empty (operator
  ruling D12).
- A fragment mentions an artifact in its prose. It is read as text, never as a reference (ADR-0066 decision 3).
- The built-in pack at the current pin still holds text in its five `spdd:reasons-block` slots and declares no
  descriptor version. It keeps loading, with warnings.
- A whole-pack round trip over built-in finds no step enhancement. The gate records the empty kind against #428
  instead of failing (operator ruling D20).
- A base prompt declares a slot that no enhancement fills, or an enhancement names a slot its target's prompt does not
  declare. Phase 1 refuses neither: the compose-time check is the phase-2 mission's.

The renderer edge case (a step whose prompt declares no slots) moved to the phase-2 mission.

## Domain Language

| Term | Meaning | Avoid |
|------|---------|-------|
| step enhancement | The artifact in an enhancing pack that adds to one step, joined to it by an `enhances` edge (ADR-0053 decision 22). | "step extension", "patch" |
| prompt fragment | Markdown body text in a `*.prompt-fragment.md` companion, filling one slot. | "snippet", "insert" |
| slot | A `<!-- <ns>:<name>:start -->` … `<!-- <ns>:<name>:end -->` marker pair in a step prompt, empty in a conforming base prompt. | "placeholder", "block" (except for the legacy `reasons-block` id) |
| prompt in force | The step prompt that wins the layer merge for the activation, which may be an override. Used by the phase-2 mission. | "base prompt" when an override is in force |
| effective prompt | The prompt in force plus, per slot, the active fragments in order. Served by the phase-2 mission, not built here. | "rendered prompt" |

## Requirements *(mandatory)*

### Functional Requirements

This spec's former eighth, ninth and tenth functional requirements moved to the phase-2 mission, where they are
adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#FR-001, adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#FR-002 and
adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#FR-003. Their old numbers are not reused here. The FR-001 to FR-012
rows below are this mission's own, with their IDs unchanged so that the work packages keep their references.

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Hold step enhancements | As a pack author, I want the charter to hold every step enhancement in a pack so that registration no longer refuses it as unmodelled and writing no longer drops it. | High | Open | [build] | no |
| FR-002 | Hold fragment companions | As a pack author, I want each `*.prompt-fragment.md` companion held with its enhancement and written back at the same path with the same meaning (ADR-0036), so that fragments survive a round trip. | High | Open | [build] | no |
| FR-003 | Step enhancements take part in composition | As an activation owner, I want a step enhancement's id to resolve in the compose, win or lose the layer merge, and be selectable directly and through presets, so that selecting it answers success instead of `404` and preset ids are not reported stale. | High | Open | [build] | no |
| FR-004 | Fragment content checks | As a pack author, I want a fragment refused with `prompt_fragment_invalid` when it is blank, has frontmatter or contains any slot marker, so that only valid body text can reach a slot. | High | Open | [build] | no |
| FR-005 | Enhancement structure checks | As a pack author, I want an enhancement refused with `prompt_fragment_invalid` when a slot id repeats within it, an entry's companion is missing, or a companion is named by no entry (orphan), and refused by the step-enhancement schema (a schema violation, as `prompt_fragments: []` is) when a slot id breaks the `<namespace>:<name>` grammar, so that every entry maps to exactly one fragment. | High | Open | [build] | no |
| FR-006 | Enhancement identity and target checks | As a pack author, I want an enhancement refused with `prompt_fragment_invalid` when it does not have exactly one `enhances` edge to an `action:<type>/<step>`, when it is the source of any `overrides` edge (operator ruling R-OVR, 2026-11-09: an enhancement only adds; refused at validate, load and registration, and on the relationship write by operator ruling R-REL, 2026-11-09: a relationship PUT that would give a held step enhancement an `overrides` edge or a second `enhances` edge answers `422 prompt_fragment_invalid` before anything is committed), when its `<type>` directory differs from that edge's type, when its id repeats within the pack across type directories (the second file in path order, segment by segment, is refused and withheld from the pack, operator ruling A2, 2026-11-09), or when it adds nothing, and refused by the store's stem rule (a schema violation, as the slot grammar and `prompt_fragments: []` are, operator ruling A1, 2026-11-09) when its id differs from its file stem, so that the graph edge stays the only statement of its target (ADR-0064) and ids cannot collide. | High | Open | [build] | no |
| FR-007 | Base prompt slot checks | As a pack author, I want a step prompt refused with `prompt_slot_malformed` when its slot markers are unpaired, repeated or nested, and with `prompt_slot_not_empty` when a slot holds text and the pack's descriptor declares `schema_version: "1.0"`, with a warning instead for an unversioned descriptor, so that base prompts declare slots that hold nothing. | High | Open | [build] | no |
| FR-011 | Load codes become raised | As a client, I want the three load codes (`prompt_slot_malformed`, `prompt_slot_not_empty`, `prompt_fragment_invalid`) raised as per-file violation codes inside `422 validation_failed`, with the contract's definition of `raised` amended to include that (operator ruling OD-3 (a)), `drg_schema_error` marked raised by the same amended definition (WP04), and the `prompt_slot_not_empty` warning delivered in a new `diagnostics` member of the registration result (OD-7), so that the contract's promises match the service. Raising `prompt_slot_missing` and the prompt read's `diagnostics` member are the phase-2 mission's. | High | Open | [build] | no |
| FR-012 | Phase-1 rulings recorded | As a maintainer, I want these recorded: the kept layout `missions/<type>/enhancements/` as a dated annotation on ADR-0053 open question 2; the guard/emit shape (fields, not edges, built under #434) on ADR-0066 or ADR-0053 decision 22; the descriptions in the step-enhancement schema; and the rules page (`composition-and-activation-rules.md`) and the two pack-schema pages (`pack-schemas/step-enhancement.md`, `pack-schemas/prompt-fragment.md`) for what phase 1 builds; so that docs and contract say what the service does. The ADR-0066 decision 7 and 8 annotations (OD-1, OD-2) are the phase-2 mission's. | Medium | Open | [build] | yes — positive control: the docs checks and a contract test pin the schema description text |

### Non-Functional Requirements

This spec's former third and fourth non-functional requirements moved to the phase-2 mission, where they are
adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#NFR-001 and adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#NFR-002. Their old
numbers are not reused here. NFR-001 and NFR-002 below are this mission's own.

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Fingerprint stability | Packs without step enhancements keep a byte-identical source fingerprint: 0 digest changes across every pack at the current pin that holds no `enhancements/` directory (at pin `22c14b2`: all five packs, `built-in`, `cave-kitty`, `event-driven`, `internal` and `qa`), against a baseline captured on the base commit of the work package that adds the binding, before the binding exists. | Compatibility | High | Open |
| NFR-002 | Corpus stays loadable | The packs at the current open-packs pin load with 0 new refusals. New prompt diagnostics on them are warnings only. | Compatibility | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Contract versioning | Every contract change lands with a dated fragment under `contracts/charter/changes/`: `### Changed` for raised codes, the selection and the held kind, `### Breaking` for content that loaded before and is now refused and for the registration result's new `diagnostics` member (ADR-0022, 0.x line). | Contract | High | Open |
| C-002 | Round trip by meaning | Enhancements and fragments are held and written back by meaning, never dropped (ADR-0036; charter "What must never go wrong"). | Integrity | High | Open |
| C-003 | Graph-only relationships | An enhancement's target is stated only by its `enhances` edge in the graph documents. No target field is added, and the directory is checked against the edge, never read instead of it (ADR-0064). | Architecture | High | Open |
| C-004 | PR C boundary | This mission edits no file PR C (WP27–WP29) owns. The compose checks and the effective-prompt read, which reuse PR C's action walk and `NodeReference` (operator ruling D2), are the phase-2 mission's. Files both sides edit in separate hunks (`CharterRefusalCode.yaml`, `ReservedRefusalCodesTest`) are reconciled at the rebase. | Sequencing | High | Open |
| C-005 | Out of scope | #429 (PR C), #430 (separate Op), #431 `org-charter.yaml`, #434 guard and emit fields, #435 pin bump, #432, #433, and core's renderers are out of scope. The compose checks, the effective-prompt read and the ADR-0066 d7/d8 annotations are the phase-2 mission, tracked in #438. If org-level requirements or presets are touched, cite #431 and escalate. | Scope | High | Open |
| C-006 | Layout kept | Step enhancements stay at `missions/<type>/enhancements/<id>.step-enhancement.yaml` with companions beside them (operator ruling D7). | Contract | High | Open |
| C-007 | Test first | BDD scenarios under `contracts/charter/scenarios/` come before code for every behaviour the contract changes. Every refusal has a test that fails without its check. | Process | High | Open |

## Success Criteria *(mandatory)*

This spec's former first success criterion moved to the phase-2 mission, where it is adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#SC-001.
Its old number is not reused here. SC-002 to SC-006 below are this mission's own.

- **SC-002** Every refusal rule in FR-004 to FR-007 is demonstrated by one failing fixture and one passing fixture
  that differ only in the faulty part. — [build] · no-op passable: no
- **SC-003** 100% of the enhancements and fragments in the test corpus write back with equal meaning. Packs without
  enhancements show 0 fingerprint changes. — [build] · no-op passable: no
- **SC-004** None of the three load codes (`prompt_slot_malformed`, `prompt_slot_not_empty`,
  `prompt_fragment_invalid`) is still listed as reserved, and each has at least one scenario that raises it as a
  violation `code` inside `422 validation_failed` on `getPackRegistration`. `drg_schema_error` is marked
  `raised: true` by the same amended definition, and the raised-code table of `errors.feature` lists it. — [build] ·
  no-op passable: no
- **SC-005** #428 can be closed: every checklist item is delivered or ruled and recorded, its guard/emit item by the
  recorded shape and #434. #424 stays open (Refs) for the phase-2 mission. — [build] · no-op passable: no
- **SC-006** A held step enhancement is selectable directly and through presets, and its selection survives a
  re-write: selecting it answers success instead of `404`, and a preset's id is kept, not reported stale, when the
  activation is written again. Being in force is not claimed (OD-6, PR C). — [build] · no-op passable: no

## Key Entities

- **Step enhancement**: id equal to its file stem and unique per pack. It has one `enhances` edge to
  `action:<type>/<step>`, no `overrides` edge (R-OVR), and a list of prompt-fragment entries, each a slot id plus a companion path. Guards and emits
  are deferred to #434.
- **Prompt fragment**: the Markdown body of a companion file, with no frontmatter and no slot markers.
- **Slot**: an id `<namespace>:<name>`, declared once by a step prompt.

The effective step prompt entity moved to the phase-2 mission.

## Assumptions

- How a load fault reaches the wire, and what "raised" means for it, is operator ruling OD-3 (a), stated once in
  [contracts/README.md](contracts/README.md#how-a-load-fault-reaches-the-wire-canonical) (FR-011 traces it).
- The pack fixtures stand in for the real `spdd` pack until #435 bumps the pin (operator ruling D21).
- The canonical render of a held prompt (ADR-0036, operator ruling D5) is the text the slot grammar parses at load. The
  phase-2 mission's read serves the same text.

## Dependencies

- This is phase 1 of 2. It does not depend on PR C of `layer-merge-and-effective-reads-01M6R0J6`; it runs while PR C
  is open and edits no PR C-owned file (C-004).
- The phase-2 mission (#438, mission `adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271`) depends on this mission merging and on PR C (WP27–WP29, with #429,
  the OD-6 E6 amendment and the OD-9 shared "pack holds step" predicate) merging.
- open-packs#175 is blocked on this mission. The conformance issue spec-kitty/spec-kitty-open-packs#177
  (filed 2026-11-09 from `research/grounding.md`, appendix A.7) aligns the spdd pack with the rules this mission enforces.

## Split (operator ruling, 2026-11-09)

The operator ruled on 2026-11-09 to split the mission into two, one per phase. `meta.json`'s creation-time purpose
text, which still mentions the prompt read and #424, predates the split and is immutable; this spec.md governs. This mission keeps phase 1 (work
packages WP01–WP06; closes #428 and #437, refers to #424). The items below moved, by their old ID, to the phase-2 mission,
which is blocked until PR C merges and this mission merges, and which closes #424:

- Former User Stories 4 and 5: the effective-prompt read and the compose refusal.
- Former eighth functional requirement, now adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#FR-001: compose refuses a missing slot (`prompt_slot_missing`, operator rulings OD-1 and OD-4).
- Former ninth functional requirement, now adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#FR-002: compose resolves the target and its integrity (OD-2, OD-9).
- Former tenth functional requirement, now adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#FR-003: the effective-prompt read (OD-5, OD-8).
- Former FR-011, phase-2 part: `prompt_slot_missing` raised, and the `diagnostics` member on the prompt read (OD-7).
- Former FR-012, phase-2 part: the ADR-0066 decision 7 and 8 annotations (OD-1, OD-2) and the docs for the compose rules
  and the read.
- Former third and fourth non-functional requirements, now adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#NFR-001 and
  adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#NFR-002: the prompt read's determinism and bounded cost.
- Former first success criterion, now adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#SC-001, and the compose part of SC-002 (now covered by
  adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#FR-001 and adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#FR-002).
- One edge case: a step whose prompt declares no slots, as read by a renderer.
- Work packages WP07–WP11 and plan concerns IC-05, IC-06, IC-07 and the phase-2 parts of IC-08 (see
  [plan.md](plan.md), "Split into two missions").
