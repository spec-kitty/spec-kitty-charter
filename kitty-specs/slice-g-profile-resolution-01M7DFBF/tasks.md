# Tasks: Slice G: agent profile resolution, selection by role, and the human performer

Mission `slice-g-profile-resolution-01M7DFBF`. Spec: [spec.md](spec.md). Plan: [plan.md](plan.md). Rulings:
[research.md](research.md) §6a.

**Delivery.** The slice lands as one PR, #573, held until slice F lands. WP01 to WP07 are **F-independent**
and are implemented now. WP08 is **F-dependent**: it is blocked until slice F (#537, PR #567) is on `main` and
the branch is rebased onto it.

Completion is event-sourced: `spec-kitty agent tasks mark-status Txxx --status done`. The rows below are references,
not checkboxes.

## Subtask Index

| ID | Description | WP | Parallel |
| --- | --- | --- | --- |
| T001 | Pack schema: sentinel out, performer in | WP01 | [P] |
| T002 | Domain: Performer, AgentProfile, operator rule, RoleBearer.humanPerformed | WP01 |  |
| T003 | Storage: DSO, binding, YAML codec | WP01 |  |
| T004 | Contract AgentProfile and DTOs, examples | WP01 |  |
| T005 | Example: org-base human-operator; java-jenny without sentinel; manifests | WP01 |  |
| T006 | Tests, scenario row, diagram | WP01 |  |
| T007 | policy.yaml role_pins: schema, reader, writer, round trip | WP02 |  |
| T008 | ClosureRoleFacts: fallback and pin in force, conflicts | WP02 |  |
| T009 | RolePinRule and three refusal codes | WP02 |  |
| T010 | fallback_profile_not_agent | WP02 |  |
| T011 | SpecializationCycleRule and specialization_cycle | WP02 |  |
| T012 | Example: project policy.yaml pin | WP02 |  |
| T013 | Selection value types | WP03 | [P] |
| T014 | Order steps 1 to 8 | WP03 |  |
| T015 | Tie and fallback | WP03 |  |
| T016 | Unit tests per step | WP03 |  |
| T017 | Ordered lineage chain from the GovernanceWalk lineage step | WP04 | [P] |
| T018 | ProfileEnhancementMerge with the drop refusal | WP04 | [P] |
| T019 | ProfileInheritance per-field fold | WP04 | [P] |
| T020 | Registration rule and profile_enhancement_drops_field | WP04 |  |
| T021 | Example: org-base enhancer of reviewer-renata | WP04 |  |
| T022 | Contract selectAgentProfile, raised codes, Added fragment | WP05 |  |
| T023 | Activation wiring: ProfileCandidates, EffectiveTag.SelectionQuery | WP05 |  |
| T024 | Edge: service, controller, DTOs | WP05 |  |
| T025 | Example: org-base reviewer-rita | WP05 |  |
| T026 | @contract scenarios per rule | WP05 |  |
| T027 | Contract: effective body, lineage, enhances link, Breaking fragment | WP06 |  |
| T028 | Wiring: merge, fold, 500 at read | WP06 |  |
| T029 | profiles.feature out of @WIP; effective scenarios | WP06 |  |
| T030 | PROMISES.md | WP06 |  |
| T031 | ADR-0078 | WP07 | [P] |
| T032 | service-contract, logical and conceptual model, glossary | WP07 | [P] |
| T033 | Open-packs drafts | WP07 | [P] |
| T034 | Contract: step-keyed path and links | WP08 |  |
| T035 | Service: step to role to selection | WP08 |  |
| T036 | Step scenarios | WP08 |  |

## WP01: performer replaces sentinel

- **Prompt:** [tasks/WP01-performer-replaces-sentinel.md](tasks/WP01-performer-replaces-sentinel.md)
- **Phase:** Phase 1 - Profile model. F-independent.
- **Dependencies:** none.
- **Requirements:** FR-007, FR-008, C-001, NFR-001.

Subtasks:

T001 Pack schema: sentinel out, performer in (WP01)
T002 Domain: Performer, AgentProfile, operator rule, RoleBearer.humanPerformed (WP01)
T003 Storage: DSO, binding, YAML codec (WP01)
T004 Contract AgentProfile and DTOs, examples (WP01)
T005 Example: org-base human-operator; java-jenny without sentinel; manifests (WP01)
T006 Tests, scenario row, diagram (WP01)

## WP02: Role pins, closure facts and registration rules

- **Prompt:** [tasks/WP02-role-pins-and-closure-rules.md](tasks/WP02-role-pins-and-closure-rules.md)
- **Phase:** Phase 1 - Profile model. F-independent.
- **Dependencies:** Depends on WP01.
- **Requirements:** FR-004, FR-006, FR-008, C-001, NFR-001, NFR-002.

Subtasks:

T007 policy.yaml role_pins: schema, reader, writer, round trip (WP02)
T008 ClosureRoleFacts: fallback and pin in force, conflicts (WP02)
T009 RolePinRule and three refusal codes (WP02)
T010 fallback_profile_not_agent (WP02)
T011 SpecializationCycleRule and specialization_cycle (WP02)
T012 Example: project policy.yaml pin (WP02)

## WP03: Profile selection domain service

- **Prompt:** [tasks/WP03-selection-domain.md](tasks/WP03-selection-domain.md)
- **Phase:** Phase 2 - Selection. F-independent.
- **Dependencies:** Depends on WP02.
- **Requirements:** FR-001, FR-002, FR-003, FR-004, FR-008.

Subtasks:

T013 Selection value types (WP03)
T014 Order steps 1 to 8 (WP03)
T015 Tie and fallback (WP03)
T016 Unit tests per step (WP03)

## WP04: Lineage chain, enhancement merge, inheritance and the drop refusal

- **Prompt:** [tasks/WP04-lineage-merge-and-drop-refusal.md](tasks/WP04-lineage-merge-and-drop-refusal.md)
- **Phase:** Phase 2 - Resolution. F-independent.
- **Dependencies:** Depends on WP02.
- **Requirements:** FR-009, FR-010, C-002, NFR-001.

Subtasks:

T017 Ordered lineage chain from the GovernanceWalk lineage step (WP04)
T018 ProfileEnhancementMerge with the drop refusal (WP04)
T019 ProfileInheritance per-field fold (WP04)
T020 Registration rule and profile_enhancement_drops_field (WP04)
T021 Example: org-base enhancer of reviewer-renata (WP04)

## WP05: Selection read: contract, activation wiring and scenarios

- **Prompt:** [tasks/WP05-selection-read.md](tasks/WP05-selection-read.md)
- **Phase:** Phase 3 - Reads. F-independent.
- **Dependencies:** Depends on WP03, WP04.
- **Requirements:** FR-001, FR-002, FR-004, FR-005, NFR-001, NFR-003.

Subtasks:

T022 Contract selectAgentProfile, raised codes, Added fragment (WP05)
T023 Activation wiring: ProfileCandidates, EffectiveTag.SelectionQuery (WP05)
T024 Edge: service, controller, DTOs (WP05)
T025 Example: org-base reviewer-rita (WP05)
T026 @contract scenarios per rule (WP05)

## WP06: Effective profile read (R8) and the #380 scenario

- **Prompt:** [tasks/WP06-effective-profile-read.md](tasks/WP06-effective-profile-read.md)
- **Phase:** Phase 3 - Reads. F-independent.
- **Dependencies:** Depends on WP05.
- **Requirements:** FR-009, FR-010, SC-003, SC-004.

Subtasks:

T027 Contract: effective body, lineage, enhances link, Breaking fragment (WP06)
T028 Wiring: merge, fold, 500 at read (WP06)
T029 profiles.feature out of @WIP; effective scenarios (WP06)
T030 PROMISES.md (WP06)

## WP07: Records: ADR-0078, service contract, models and open-packs drafts

- **Prompt:** [tasks/WP07-records.md](tasks/WP07-records.md)
- **Phase:** Phase 4 - Records. F-independent.
- **Dependencies:** Depends on WP06.
- **Requirements:** FR-011, C-005.

Subtasks:

T031 ADR-0078 (WP07)
T032 service-contract, logical and conceptual model, glossary (WP07)
T033 Open-packs drafts (WP07)

## WP08: Step-keyed selection read and links (after slice F)

- **Prompt:** [tasks/WP08-step-keyed-selection.md](tasks/WP08-step-keyed-selection.md)
- **Phase:** Phase 5 - After slice F. **F-dependent: blocked until slice F lands**.
- **Dependencies:** Depends on WP06.
- **Requirements:** FR-012.

Subtasks:

T034 Contract: step-keyed path and links (WP08)
T035 Service: step to role to selection (WP08)
T036 Step scenarios (WP08)

## Sequencing

One code lane: WP01 → WP02 → (WP03, WP04) → WP05 → WP06. WP03 and WP04 own disjoint files and may run in
parallel after WP02. WP07 (records) follows WP06. WP08 follows WP06 and slice F.

MVP: WP01 to WP05. They give the selection read the usability run needed.
