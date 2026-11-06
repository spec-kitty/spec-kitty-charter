# Work Packages: Mutation testing floors, and PIT on packs

**Mission**: `test-mutation-floors-01M6NSDR` · **Branch**: `test-mutation-floors` (merges to `main` by draft PR)
**Inputs**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md)

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Convention plugin | WP01 | |
| T002 | Floor model and lenient check | WP01 | |
| T003 | Floor check tests | WP01 | |
| T004 | Module floors kernel/missions/profiles | WP01 | |
| T005 | Proof: forced floor, --continue, no pitest in verifyAll | WP01 | |
| T006 | Apply PIT to packs, targets, groups | WP02 | |
| T007 | Additive dependency verification | WP02 | |
| T008 | Measure packs; report; stop for ruling | WP02 | |
| T009 | Write ruled packs floors; proof | WP02 | |
| T010 | Nightly workflow | WP03 | [P] |
| T011 | Summary script and tests | WP03 | [P] |
| T012 | Alert job | WP03 | [P] |
| T013 | Workflow proof | WP03 | [P] |
| T014 | ADR | WP04 | |
| T015 | Guide and changelog | WP04 | |
| T016 | Docs checks | WP04 | |
| T017 | Kernel survivor inventory | WP05 | [P] |
| T018 | Triage | WP05 | [P] |
| T019 | Proof | WP05 | [P] |

## Phase 1

### WP01 — Mutation convention plugin and floor check
Prompt: [tasks/WP01-mutation-conventions-and-floor-check.md](tasks/WP01-mutation-conventions-and-floor-check.md)

T001 Convention plugin (WP01)
T002 Floor model and lenient check (WP01)
T003 Floor check tests (WP01)
T004 Module floors (WP01)
T005 Proof (WP01)

### WP05 — Kernel survivors triaged
Prompt: [tasks/WP05-kernel-survivor-triage.md](tasks/WP05-kernel-survivor-triage.md)

T017 Kernel survivor inventory (WP05)
T018 Triage (WP05)
T019 Proof (WP05)

## Phase 2

### WP02 — PIT on packs, measured and floored (depends on WP01)
Prompt: [tasks/WP02-pit-on-packs.md](tasks/WP02-pit-on-packs.md)

T006 Apply PIT to packs (WP02)
T007 Additive dependency verification (WP02)
T008 Measure packs; stop for ruling (WP02)
T009 Write ruled packs floors (WP02)

### WP03 — The nightly reports every module and alerts on red (depends on WP01)
Prompt: [tasks/WP03-nightly-workflow.md](tasks/WP03-nightly-workflow.md)

T010 Nightly workflow (WP03)
T011 Summary script and tests (WP03)
T012 Alert job (WP03)
T013 Workflow proof (WP03)

## Phase 3

### WP04 — ADR and docs (depends on WP02, WP03)
Prompt: [tasks/WP04-adr-and-docs.md](tasks/WP04-adr-and-docs.md)

T014 ADR (WP04)
T015 Guide and changelog (WP04)
T016 Docs checks (WP04)
