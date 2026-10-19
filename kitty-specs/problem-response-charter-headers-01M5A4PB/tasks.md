# Tasks: Charter headers on the default Problem response

**Mission**: `problem-response-charter-headers-01M5A4PB` · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)
**Planning branch**: `fix/problem-response-charter-headers` · **Merge target**: `fix/problem-response-charter-headers`

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Add lint rule `charter-headers-on-every-response` and show it red on the current contract | WP01 | |
| T002 | Planted fixture for the rule; add the headers to `clean.yaml` and every other fixture so each still fails only its own rule | WP01 | |
| T003 | Declare the three headers on `contracts/_shared/responses/Problem.yaml` by reference to `contracts/charter/headers/` | WP01 | |
| T004 | Contract scenario: the 400 `Problem` for `depth=2` carries the headers | WP01 | [P] |
| T005 | Update `contracts/_shared/VENDORED.md` for the canonical-ownership ruling; list this file's change | WP01 | [P] |
| T006 | Contract CHANGELOG entry, lint README convention, and the full gates | WP01 | |

## Phase 1 — Contract fidelity

### WP01 — The default Problem response declares the Charter headers, guarded red-first

**Prompt**: [tasks/WP01-default-problem-charter-headers.md](tasks/WP01-default-problem-charter-headers.md)
**Priority**: P1 · **Dependencies**: none · **Requirements**: FR-001, FR-002, FR-003, FR-004, FR-005, NFR-001, NFR-002, C-001, C-002, C-003, C-004, SC-001, SC-002, SC-003

**Goal**: every response of every charter operation in the bundled contract declares
`Charter-Contract-Version`, `Charter-Worktree` and `Charter-Source-Fingerprint`, and a lint rule
keeps it that way.

**Independent test**: `./gradlew :contracts:lintContract` fails with the new rule before T003 and
passes after it; `./gradlew verifyAll` is green at the end.

Included subtasks:

T001 Add lint rule `charter-headers-on-every-response` and show it red on the current contract (WP01)
T002 Planted fixture; headers on `clean.yaml` and every other fixture (WP01)
T003 Declare the three headers on the shared `Problem` response (WP01)
T004 Contract scenario for the 400 `Problem` (WP01)
T005 `VENDORED.md` for the canonical-ownership ruling (WP01)
T006 CHANGELOG, lint README, full gates (WP01)

**Implementation sketch**: rule first (red), fixtures so `lintFixtures` holds, then the shared
response (green), then scenario, records and gates.

**Risks**: a fixture left without headers fails two rules; the `$ref` from `_shared/` into
`charter/headers/` must resolve in the bundle; the rule must reach `default` responses.
