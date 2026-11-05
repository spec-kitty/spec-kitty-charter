# Work Packages: Deadline checks become hang caps

**Mission**: `test-deadline-hang-caps-01M6MGHX` · **Branch**: `test-deadline-hang-caps` (merges to `main` by draft PR)
**Inputs**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [quickstart.md](quickstart.md)

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Measure the storage guards' own work time | WP01 | |
| T002 | Widen six storage bounds to a 30 s `HANG_CAP` | WP01 | |
| T003 | Planted break per storage guard | WP01 | |
| T004 | Storage gates | WP01 | |
| T005 | Measure the fetcher tests and server stop | WP02 | [P] |
| T006 | 30 s caps over 60 s slow work | WP02 | [P] |
| T007 | Planted break for lookup and body deadlines | WP02 | [P] |
| T008 | Fetcher gates | WP02 | [P] |

## Phase 1 — Hang caps

### WP01 — Storage guards get 30 s hang caps

Prompt: [tasks/WP01-storage-guard-hang-caps.md](tasks/WP01-storage-guard-hang-caps.md)

- **Goal**: six storage bounds become 30 s caps; cause assertions kept (FR-001, FR-003).
- **Priority**: P1 · **Independent test**: planted breaks red; `:packs:test` green, count unchanged.
- **Subtasks**:

T001 Measure the storage guards' own work time (WP01)
T002 Widen six storage bounds to a 30 s hang cap (WP01)
T003 Planted break per storage guard (WP01)
T004 Storage gates (WP01)

- **Dependencies**: none. **Risks**: runaway thread after a planted preemptive cap; PR #377 hunks in the same file.

### WP02 — Fetcher deadline checks get 30 s hang caps over 60 s slow work

Prompt: [tasks/WP02-fetcher-deadline-hang-caps.md](tasks/WP02-fetcher-deadline-hang-caps.md)

- **Goal**: two fetcher bounds become 30 s caps; slow work 60 s; `TIMEOUT` kept (FR-002, FR-003, NFR-001).
- **Priority**: P1 · **Independent test**: planted breaks red; green-run time grows ≤ 5 s.
- **Subtasks**:

T005 Measure the fetcher tests and server stop (WP02)
T006 30 s caps over 60 s slow work (WP02)
T007 Planted break for lookup and body deadlines (WP02)
T008 Fetcher gates (WP02)

- **Dependencies**: none (parallel with WP01). **Risks**: the drip delaying tear-down; #367's `send` class.

## MVP

Both WPs, in parallel; together they close #320.
