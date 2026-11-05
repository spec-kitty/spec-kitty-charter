# Work Packages: Test-suite tidy: Mockito at Spring's version, refusal codes pinned

**Mission**: `test-tidy-mockito-refusal-codes-01M6KZ05` · **Branch**: `test-tidy-mockito-refusal-codes` (merges to `main` by draft PR)
**Inputs**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [quickstart.md](quickstart.md)

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Align the catalog's Mockito version to Spring Boot's managed 5.23.0 | WP01 | |
| T002 | Record why Mockito and its agent are kept (comment only) | WP01 | [P] |
| T003 | Regenerate `verification-metadata.xml` from scratch over every task family | WP01 | |
| T004 | Prove strict `verifyAll` and `pitest` from a cold Gradle user home | WP01 | |
| T005 | Find each of the 15 sites' expected refusal code from the production path | WP02 | |
| T006 | Strengthen the 15 checks in place | WP02 | |
| T007 | Planted-break proof, one per distinct code | WP02 | |
| T008 | Scope guard: throwaway rescan reports 0; nothing else touched | WP02 | |

## Phase 1 — Tidy first

### WP01 — Mockito at Spring's version, verified metadata regenerated

Prompt: [tasks/WP01-mockito-at-spring-version-and-metadata-regen.md](tasks/WP01-mockito-at-spring-version-and-metadata-regen.md)

- **Goal**: one Mockito version (Spring's) everywhere, the keep-reason recorded, no stale checksums (FR-001, FR-001a).
- **Priority**: P1 · **Independent test**: dependency reports show one Mockito version; strict `verifyAll` green from a cold cache.
- **Subtasks**:

T001 Align the catalog's Mockito version to Spring Boot's managed 5.23.0 (WP01)
T002 Record why Mockito and its agent are kept (WP01)
T003 Regenerate the verified metadata from scratch (WP01)
T004 Prove it from a cold Gradle user home (WP01)

- **Dependencies**: none.
- **Risks**: a dropped metadata entry for a task outside the regen run (mitigated by the per-family regen and the cold-cache run); overlap with draft PR #368 on `charter.hand-written.gradle.kts` (comment-only change here).

## Phase 2 — Refusal codes

### WP02 — Pin the refusal code on the 15 type-only CharterRefusal checks

Prompt: [tasks/WP02-pin-refusal-codes.md](tasks/WP02-pin-refusal-codes.md)

- **Goal**: each of the 15 checks fails on a wrong refusal code (FR-002, FR-003).
- **Priority**: P1 · **Independent test**: a planted wrong code turns the sampled checks red.
- **Subtasks**:

T005 Find each site's expected code from the production path (WP02)
T006 Strengthen the 15 checks in place (WP02)
T007 Planted-break proof, one per distinct code (WP02)
T008 Scope guard (WP02)

- **Dependencies**: WP01 (commit order only, tidy-first C-002).
- **Risks**: symlink-dependent sites skip without `-PrequireSymlinks=true`; run with it.

## MVP

WP01 then WP02; both are needed to close #373 and #369.
