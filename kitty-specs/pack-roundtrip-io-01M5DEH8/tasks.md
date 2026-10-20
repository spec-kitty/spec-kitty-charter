# Tasks: Pack parse/write-back round-trip

**Mission**: pack-roundtrip-io-01M5DEH8 | **Branch**: `feat/pack-roundtrip-io`
**Plan**: [plan.md](./plan.md) · **Spec**: [spec.md](./spec.md) · **Data model**: [data-model.md](./data-model.md)

Three work packages mirror the Implementation Concern Map: domain (IC-01+IC-02) → adapter (IC-03) → verification (IC-04). Tests are explicitly required (the round-trip invariant is the mission). Honour test economy (DIRECTIVE_041): each test guards a distinct failure mode through the production port; no shape-pins, no inert duplicates.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | `TacticReference` record (name/type/id/when, verbatim) | WP01 | [P] |
| T002 | `TacticStep` record (title; optional description/examples/nested references) | WP01 | [P] |
| T003 | `Tactic` record (steps non-empty; optionals; lists; pinned equality) | WP01 | |
| T004 | Extend `PackDescriptor` (activeCharter/packDrg + back-compat constructor) | WP01 | |
| T005 | `PackSnapshot` aggregate + `PackElementViolation` (descriptor + tactics + violations) | WP01 | |
| T006 | `PackLocator` domain value + `PackElementRepository` port | WP01 | |
| T007 | `TacticReader` — parse `*.tactic.yaml` → `Tactic` | WP02 | |
| T008 | New SnakeYAML `Dump` emitter helper (GraphShardWriter pattern) | WP02 | |
| T009 | `TacticWriter` — `Tactic` → YAML via the Dump emitter | WP02 | |
| T010 | `PackDescriptorWriter` — descriptor (incl. active_charter/pack_drg) → `pack.yaml` | WP02 | |
| T011 | Update `PackDescriptorReader` to read active_charter/pack_drg | WP02 | |
| T012 | `FilesystemPackElementRepository` — fetch (partial-tolerant) + write-by-id (atomic, contained) | WP02 | |
| T013 | Proving fixtures under `roundtrip-fixtures/` | WP03 | [P] |
| T014 | Fetch-fidelity literal oracle test | WP03 | |
| T015 | Round-trip equality + mutate→write→refetch control + replace-existing | WP03 | |
| T016 | Presence/absence + reference order/duplicates + collection/descriptor-only | WP03 | |
| T017 | Partial-tolerant invalid-file test (+ same-fixture valid control) | WP03 | |
| T018 | Write-safety test (failure-injection atomicity + path containment) | WP03 | |
| T019 | ArchUnit port-signature rule + committed known-bad sample | WP03 | |

## Work Packages

### WP01 — Domain models, extended descriptor & port (IC-01, IC-02)

- **Goal**: Introduce the graph-free domain vocabulary and the domain-owned port. No infra; no storage coordinate on any model.
- **Priority**: P1 (foundational). **Dependencies**: none.
- **Independent test**: the module compiles and `./gradlew :charter-domain:test` stays green (existing descriptor tests keep compiling via the back-compat constructor).
- **Subtasks**: T001, T002, T003, T004, T005, T006.
- **Prompt**: [tasks/WP01-domain-models-and-port.md](./tasks/WP01-domain-models-and-port.md)
- **Risks**: extending the shared `PackDescriptor` record ripples to 8 constructor callers — mitigated by appending fields + a back-compat old-arity constructor.

### WP02 — Filesystem adapter: reader, Dump writers, repository (IC-03)

- **Goal**: Implement the port against the filesystem — a tactic-content reader, a new Dump-based serializer feeding both writers, and the repository with partial-tolerant fetch and atomic, pack-root-contained write-by-id.
- **Priority**: P1. **Dependencies**: WP01.
- **Independent test**: `./gradlew :adapter-out-yaml:test` green; `PackDescriptorReaderTest` updated for the two new fields.
- **Subtasks**: T007, T008, T009, T010, T011, T012.
- **Prompt**: [tasks/WP02-filesystem-adapter.md](./tasks/WP02-filesystem-adapter.md)
- **Risks**: `YamlDocument`/`ScalarWriter` cannot build a document — the emitter is genuinely new (D6); the failure-injection seam must exist for T018.

### WP03 — Round-trip & layering verification (IC-04)

- **Goal**: Prove the semantic round-trip and the enforced layering, non-vacuously, through the port.
- **Priority**: P1 (the mission's acceptance). **Dependencies**: WP01, WP02.
- **Independent test**: `./gradlew verifyAll` green incl. the new battery and ArchUnit known-bad sample.
- **Subtasks**: T013, T014, T015, T016, T017, T018, T019.
- **Prompt**: [tasks/WP03-roundtrip-and-layering-tests.md](./tasks/WP03-roundtrip-and-layering-tests.md)
- **Risks**: vacuity — the round-trip is blind to reader-dropped fields (hence T014's literal oracle); the known-bad ArchUnit control must be a committed rule-against-sample, not a manual toggle.

## MVP

WP01 + WP02 deliver the working fetch/write path; WP03 proves it. All three are needed for the invariant; there is no partial-value cut.

## Next

`/spec-kitty.analyze` (required readiness gate) → `/spec-kitty.implement`.
