# Tasks: Slice C, the effective step and action context

**Mission**: `effective-step-and-action-context-01M774HB` · **Planning base / merge target**: `slice-c/effective-step-context`
**Inputs**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md) (R1–R10),
[research/plan-squad-2026-11-12.md](research/plan-squad-2026-11-12.md), [data-model.md](data-model.md),
[contracts/README.md](contracts/README.md).

Every code work package is test first (C-004). For each behaviour the contract changes, the BDD scenario under
`contracts/charter/scenarios/` and a failing unit test come before the code. Every refusal has a failing fixture and a
passing twin. Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`, and never
`verifyAll` locally (CI runs it).

## Subtask Index

| ID | Description | WP | Parallel |
|---|---|---|---|
| T001 | Red: effective-walk tests (three passes, owners, kinds, first reach, pass of a suggestion) | WP01 | |
| T002 | Scope owners in `EdgeIndex`, memoized | WP01 | |
| T003 | The effective walk's three passes in `ActionContextWalk` | WP01 | |
| T004 | `ClosureView.actionContext` takes the effective walk; offering read unchanged | WP01 | |
| T005 | NFR-003 and NFR-004 gates at the packs level | WP01 | |
| T006 | Scenarios: scoping feature with the 16-directive table, type-scope cases, rewrites of US5 AS4 and #484 | WP02 | |
| T007 | Step vocabulary prints `appliesThrough`; binding and README | WP02 | |
| T008 | Contract: `EffectiveContextArtifact`, `EffectiveActionContext`, examples, Breaking fragment | WP02 | |
| T009 | Activations carries the pass to `appliesThrough`; mapper | WP02 | |
| T010 | E17 to `v3`; parity pairs; audit of effective-read tests on example packs | WP02 | |
| T011 | `PromptFacts` record and the `prompts` member of `PackCompositionFacts` | WP03 | [P] |
| T012 | `WriteFacts` keeps the member through write transforms | WP03 | |
| T013 | `DomainRepositoryService.compositionFacts` fills it (after the rebase onto #501) | WP03 | |
| T014 | Equality with the load rule's parse; cache test | WP03 | |
| T015 | Scenarios: target-held and inherited-lock twins on reads and at registration | WP04 | [P] |
| T016 | Pure rules: target held (C37), inherited lock (C22/C25) | WP04 | |
| T017 | C37 in `OfferingReadService.composed` | WP04 | |
| T018 | Inherited lock: `ImpactRelations` at registration, `MergeInputs.merged()` at read | WP04 | |
| T019 | `override_not_permitted` description; E6 pins; Breaking fragment | WP04 | |
| T020 | Scenarios: slot-declared twins on every surface; `errors.feature` rows and pins | WP05 | |
| T021 | Pure rule C38 over prompt facts and in-force states | WP05 | |
| T022 | `ClosureView.requireSlots`; the read session calls it | WP05 | |
| T023 | `ActivationImpact` gate on every `RegistrationImpact` path; the adapter implements it | WP05 | |
| T024 | Activation writes call `requireSlots` before storing | WP05 | |
| T025 | `prompt_slot_missing` raised: contract map, `RefusalHandler`, reserved-codes test, fragment | WP05 | |
| T026 | Red: `EffectivePromptQuery` tests (order, gate, empty slot, legacy, no prompt, winner place) | WP06 | |
| T027 | `EffectivePromptQuery` and its order | WP06 | |
| T028 | `ClosureView.effectivePrompt` reads the companion under the fingerprint | WP06 | |
| T029 | NFR-002 operation-count gate | WP06 | |
| T030 | Scenarios: `effective-step-prompt.feature` (example packs, three levels, 409, 404 before 304, ETag, link) | WP07 | |
| T031 | Contract: path, four schemas, examples, ruleset `prompt`, relation table, header lists, fragments | WP07 | |
| T032 | `mission_type_not_in_force`: kernel code, contract enum, `RefusalHandler`, parity | WP07 | |
| T033 | Served: session, service, tag, controller, mapper; context `_links.prompt` | WP07 | |
| T034 | NFR-001 determinism gate | WP07 | |
| T035 | ADR-0018 amendments (D2, D3, D5) | WP08 | [P] |
| T036 | ADR-0066 annotations (d4, d7, d8, d9; built) | WP08 | [P] |
| T037 | Rules page (E6, E12, E16, E17, E18, C22, C25, C37, C38, F6, G8, section 7 and 8) | WP08 | |
| T038 | Divergence register and `divergences.yaml` (DIV-244 note, DIV-245) | WP08 | [P] |
| T039 | Service contract, status and resource docs, glossary, conceptual model, changelog, roadmap | WP08 | |
| T040 | Red: the step read of a step only a replaced version keeps | WP09 | |
| T041 | The step's place through the activations port | WP09 | |
| T042 | `getEffectiveMissionStep` reads at the step's place | WP09 | |
| T043 | Red: every overrider listed, level then URN | WP10 | |
| T044 | Packs: the overrider list | WP10 | |
| T045 | Activations and profiles carry lists | WP10 | |
| T046 | Contract arrays and Breaking fragment | WP10 | |
| T047 | Red: overrides from a non-activatable source refused | WP11 | |
| T048 | The pure rule and the relationship write | WP11 | |
| T049 | Load seam, contract violation code, fragment | WP11 | |

## Work packages

### WP01 — The ownership rule in the packs walk

**Goal**: The effective form of the action walk lists what the operator's ownership rule says applies, each entry with
its pass. **Priority**: P1. **Prompt**: [tasks/WP01-ownership-rule-walk.md](tasks/WP01-ownership-rule-walk.md)
**Independent test**: `ActionContextWalkTest` cases over hand-built indexes.

T001 Red: effective-walk tests (WP01)
T002 Scope owners in `EdgeIndex`, memoized (WP01)
T003 The effective walk's three passes (WP01)
T004 `ClosureView.actionContext` takes the effective walk (WP01)
T005 NFR-003 and NFR-004 gates (WP01)

Dependencies: none. Risks: the offering walk's tests must not change.

### WP02 — The context served with `appliesThrough`

**Goal**: `getEffectiveActionContext` answers the ownership rule's listing on the wire, scenario first.
**Priority**: P1. **Prompt**: [tasks/WP02-context-served-applies-through.md](tasks/WP02-context-served-applies-through.md)

T006 Scenarios (WP02)
T007 Step vocabulary (WP02)
T008 Contract (WP02)
T009 Activations mapping (WP02)
T010 E17 `v3`, parity, audit (WP02)

Dependencies: WP01. Risks: the contract change is `### Breaking`; existing scenarios change meaning.

### WP03 — Prompt facts in the composition facts

**Goal**: The compose's one verified input carries declared slots and fragment fills (research R9).
**Priority**: P1. **Prompt**: [tasks/WP03-prompt-facts.md](tasks/WP03-prompt-facts.md)

T011 `PromptFacts` record (WP03)
T012 `WriteFacts` (WP03)
T013 `DomainRepositoryService` hunk (WP03)
T014 Equality and cache tests (WP03)

Dependencies: none inside the mission; T013 waits for slice B (#501) to merge and the rebase.

### WP04 — Compose checks: target held and inherited lock

**Goal**: A step enhancement lands only on a step held in its closure whose mission type is not locked.
**Priority**: P1. **Prompt**: [tasks/WP04-compose-target-and-lock.md](tasks/WP04-compose-target-and-lock.md)

T015 Scenarios (WP04)
T016 Pure rules (WP04)
T017 C37 in the compose (WP04)
T018 Inherited lock at registration and read (WP04)
T019 Description, E6 pins, fragment (WP04)

Dependencies: none.

### WP05 — Compose check: slots declared (`prompt_slot_missing`)

**Goal**: No in-force fragment is silently dropped: reads answer 500, registration and the writes 422.
**Priority**: P1. **Prompt**: [tasks/WP05-compose-slots-declared.md](tasks/WP05-compose-slots-declared.md)

T020 Scenarios and `errors.feature` (WP05)
T021 Pure rule C38 (WP05)
T022 `ClosureView.requireSlots` on reads (WP05)
T023 The registration gate (WP05)
T024 Activation writes (WP05)
T025 Raised in the contract and the transport (WP05)

Dependencies: WP03, WP04.

### WP06 — The effective-prompt query in packs

**Goal**: The packs-side answer of the prompt read: the prompt in force, its slots, ordered in-force fragments.
**Priority**: P1. **Prompt**: [tasks/WP06-effective-prompt-query.md](tasks/WP06-effective-prompt-query.md)

T026 Red tests (WP06)
T027 The query and its order (WP06)
T028 `ClosureView.effectivePrompt` (WP06)
T029 NFR-002 gate (WP06)

Dependencies: WP03, WP05.

### WP07 — The effective-prompt read served

**Goal**: `GET /activations/{id}/mission-types/{type}/actions/{action}/prompt`, with `409 mission_type_not_in_force`
and the context's `prompt` link. **Priority**: P1.
**Prompt**: [tasks/WP07-effective-prompt-read-served.md](tasks/WP07-effective-prompt-read-served.md)

T030 Scenarios (WP07)
T031 Contract (WP07)
T032 The 409 code (WP07)
T033 Served (WP07)
T034 NFR-001 gate (WP07)

Dependencies: WP02, WP05, WP06.

### WP08 — Records and docs

**Goal**: The ADRs, rules page, divergence register, contract docs, glossary, changelog and plans say what the service
now does. **Priority**: P2. **Prompt**: [tasks/WP08-records-and-docs.md](tasks/WP08-records-and-docs.md)

T035 ADR-0018 (WP08)
T036 ADR-0066 (WP08)
T037 Rules page (WP08)
T038 Divergence register (WP08)
T039 Contract docs, glossary, changelog, plans (WP08)

Dependencies: WP02, WP04, WP05, WP07.

### WP09 — The effective step read follows the step's place

**Goal**: `getEffectiveMissionStep`, the context and the prompt read agree for a step only a replaced version keeps
(operator ruling 2026-11-12, folds squad A8/R13). **Priority**: P1.
**Prompt**: [tasks/WP09-effective-step-follows-its-place.md](tasks/WP09-effective-step-follows-its-place.md)

T040 Red (WP09)
T041 The step's place (WP09)
T042 The read uses it (WP09)

Dependencies: WP07.

### WP10 — Every overrider in force is named (#439)

**Goal**: `satisfiedBy` and `overriddenBy` list every overrider in force, level then URN (operator ruling, option b).
**Priority**: P2. **Prompt**: [tasks/WP10-every-overrider-named.md](tasks/WP10-every-overrider-named.md)

T043 Red (WP10)
T044 Packs (WP10)
T045 Activations and profiles (WP10)
T046 Contract (WP10)

Dependencies: WP02.

### WP11 — An override comes from an activatable artifact (#447)

**Goal**: refuse an `overrides` edge from a template, asset, guard or lifecycle (operator ruling, option 1).
**Priority**: P2. **Prompt**: [tasks/WP11-override-source-activatable.md](tasks/WP11-override-source-activatable.md)

T047 Red (WP11)
T048 Rule and write (WP11)
T049 Load, contract, fragment (WP11)

Dependencies: WP04.

## Twin matrix (squad R12)

| Rule | Read (500) | Registration (422) | Impact-checked write (422) | Activation write (422) |
|---|---|---|---|---|
| C37 target held | WP04 | WP04 | WP04 | composes as reads (WP04) |
| C22/C25 inherited lock | WP04 (`pack_unreadable`) | WP04 (`override_not_permitted`) | WP04 | composes as reads (WP04) |
| C38 slot declared | WP05 | WP05 | WP05 | WP05 |

Each cell is one failing fixture and one passing twin that differ only in the faulty part.

## MVP

WP01 + WP02 close the #450 scoping half on their own. WP03 to WP07 close #438.
