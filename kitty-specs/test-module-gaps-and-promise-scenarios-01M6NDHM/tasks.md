# Work Packages: Module-local test gaps and promise-first scenario verdicts

**Mission**: `test-module-gaps-and-promise-scenarios-01M6NDHM` · **Branch**: `test-module-gaps-and-promise-scenarios` (merges to `main` by draft PR)
**Inputs**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md)

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Read kernel.validation API and usages | WP01 | [P] |
| T002 | Tests of the validation invariants | WP01 | [P] |
| T003 | Planted breaks and kernel PIT | WP01 | [P] |
| T004 | kernel gates | WP01 | [P] |
| T005 | Read AgentProfile and its entry points | WP02 | [P] |
| T006 | Strengthen AgentProfileEntityTest (round trip, null refusal, FIX) | WP02 | [P] |
| T007 | Planted breaks and profiles PIT | WP02 | [P] |
| T008 | profiles gates | WP02 | [P] |
| T009 | CappedBodySubscriber tests | WP03 | [P] |
| T010 | Planted breaks | WP03 | [P] |
| T011 | packs gates | WP03 | [P] |
| T012 | Move ADR-0005/0019 cluster under the contract as @WIP | WP04 | [P] |
| T013 | Wire SHARING-001 and SHARING-004 red-first | WP04 | [P] |
| T014 | #380 placeholder and pointers | WP04 | [P] |
| T015 | READMEs and architecture page | WP04 | [P] |
| T016 | Verdict record and gates | WP04 | [P] |

## Phase 1 — Module gaps (#370)

### WP01 — kernel.validation tested in kernel
Prompt: [tasks/WP01-kernel-validation-tests.md](tasks/WP01-kernel-validation-tests.md)

T001 Read kernel.validation API and usages (WP01)
T002 Tests of the validation invariants (WP01)
T003 Planted breaks and kernel PIT (WP01)
T004 kernel gates (WP01)

### WP02 — profiles tested in profiles
Prompt: [tasks/WP02-profiles-tests.md](tasks/WP02-profiles-tests.md)

T005 Read AgentProfile and its entry points (WP02)
T006 Strengthen AgentProfileEntityTest (WP02)
T007 Planted breaks and profiles PIT (WP02)
T008 profiles gates (WP02)

### WP03 — CappedBodySubscriber tested in packs
Prompt: [tasks/WP03-capped-body-subscriber-tests.md](tasks/WP03-capped-body-subscriber-tests.md)

T009 CappedBodySubscriber tests (WP03)
T010 Planted breaks (WP03)
T011 packs gates (WP03)

## Phase 2 — Scenario verdicts (#374)

### WP04 — Apply the promise-first scenario verdicts
Prompt: [tasks/WP04-promise-first-scenario-verdicts.md](tasks/WP04-promise-first-scenario-verdicts.md)

T012 Move the ADR-0005/0019 cluster under the contract as @WIP (WP04)
T013 Wire SHARING-001 and SHARING-004 red-first (WP04)
T014 #380 placeholder and pointers (WP04)
T015 READMEs and architecture page (WP04)
T016 Verdict record and gates (WP04)

All four WPs are independent (separate lanes). Risks: the parallel session's profiles/packs work (C-005); WP04 changes the contract suite (C-004).
