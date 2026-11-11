# Tasks: Pack item ETags and resolver retirement

Mission: `pack-item-etags-and-resolver-retirement-01M75WRS` · lands on `claude/feat-487-488-pack-item-etags`.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | getPackArtifact texts: every item carries the revision | WP01 | |
| T002 | Mission type, step, skeleton declare the ETag | WP01 | [P] |
| T003 | Scenarios assert the ETag (red first) | WP01 | |
| T004 | Change note | WP01 | [P] |
| T005 | Unit tests first (red) | WP02 | |
| T006 | Tactic read carries the revision | WP02 | |
| T007 | Step contract revision; Item.revision() required | WP02 | |
| T008 | Controller sets the ETag; verify | WP02 | |
| T009 | Missions-edge tests first (red) | WP03 | |
| T010 | Holding read returns the pack revision | WP03 | |
| T011 | Read service and controller | WP03 | |
| T012 | Verify | WP03 | |
| T013 | Prove the holder ratchet | WP04 | |
| T014 | Remove the dead path | WP04 | |
| T015 | Tests | WP04 | |
| T016 | Verify | WP04 | |

## WP01 — Contract and scenarios (prompt: tasks/WP01-contract-and-scenarios.md)

Goal: the contract states, and the scenarios assert, the revision ETag on the five reads; the run is recorded red.
Priority P1. Dependencies: none. Requirements: FR-005, FR-006, NFR-001, C-001, C-002.

T001 getPackArtifact texts (WP01)
T002 Mission-type family declares the ETag (WP01)
T003 Scenarios flipped, red run recorded (WP01)
T004 Change note (WP01)

## WP02 — Templated item revisions (prompt: tasks/WP02-templated-item-revisions.md)

Goal: tactic and step-contract items answer the revision. Priority P1. Depends on WP01 and WP04 (WP04 removes the
resolver's `new TacticReadService(` sites first). Requirements: FR-001, FR-002, FR-004, NFR-002.

T005 Unit tests red (WP02)
T006 Tactic read revision (WP02)
T007 Step contract revision, required Item.revision() (WP02)
T008 Controller ETag, verify (WP02)

## WP03 — Mission type, step and skeleton revisions (prompt: tasks/WP03-mission-type-revisions.md)

Goal: the three missions-edge pack reads answer the revision; activation reads unchanged. Priority P1. Depends on WP01.
Requirements: FR-003, FR-004, C-002.

T009 Tests red (WP03)
T010 Holding read revision (WP03)
T011 Read service and controller (WP03)
T012 Verify (WP03)

## WP04 — Retire the resolver walk path (prompt: tasks/WP04-retire-resolver-walk-path.md)

Goal: the unused per-kind walk path is gone; holder checks answer as before. Priority P2. Depends on nothing; WP02
follows it because both edit the resolver's test wiring (`CompositeComponentResolverTest`, and constructor lines in
`ArtifactReadServiceTest`/`ArtifactControllerTest`, which WP04 edits out of map). Requirements: FR-007, FR-008.

T013 Holder ratchet green first (WP04)
T014 Remove the dead path (WP04)
T015 Tests (WP04)
T016 Verify (WP04)

## Parallelism

WP01 and WP04 have no dependencies. WP02 follows WP01 and WP04; WP03 follows WP01. WP02 and WP03 share no files.
