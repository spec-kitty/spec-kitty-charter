---
work_package_id: WP08
title: Family + corpus + wiring verification
dependencies:
- WP05
- WP06
- WP07
requirement_refs:
- SC-001
- SC-003
- SC-004
- NFR-002
- NFR-003
- C-004
planning_base_branch: feat/pack-doctrine-artefacts
merge_target_branch: feat/pack-doctrine-artefacts
subtasks:
- T025
- T026
- T027
phase: Phase 4 - Verification
agent: claude
history:
- at: '2026-10-23T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/CharterArtifactFamilyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDoctrineCorpusRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DoctrineValidationParityTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/DoctrineWiringRoundTripTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/CharterArtifactFamilyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDoctrineCorpusRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DoctrineValidationParityTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/DoctrineWiringRoundTripTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP08 — Family + corpus + wiring verification

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objectives & Success Criteria

Prove the mission's success criteria as a cohesive verification layer: family uniformity
(SC-003), corpus round-trip (SC-001), validation parity (NFR-002), Spring wiring (n-1), and
that the hexagonal gates stay green (SC-004/NFR-003). This WP adds tests only.

## Context & Constraints

- Existing helpers to reuse: `OpenPacksCorpus` and `PackFixtures` (packs storage tests),
  and `WiringRoundTripTest` as the deployable Spring template.
- `HexagonalArchitectureTest` is in `deployable`; this WP does NOT edit it (no new slice —
  everything is in `packs`), it only confirms it still passes.

## Branch Strategy

- **Planning base / merge target**: `feat/pack-doctrine-artefacts`. Worktree per lane from `lanes.json`.

```bash
spec-kitty agent action implement WP08 --agent claude --mission pack-doctrine-artefacts-01M5JHD0
```

Build: `./gradlew :packs:test --tests '*CharterArtifactFamilyTest' --tests '*PackDoctrineCorpusRoundTripTest' --tests '*DoctrineValidationParityTest'` and `./gradlew :deployable:test --tests '*DoctrineWiringRoundTripTest' --tests '*HexagonalArchitectureTest'`.

## Subtasks & Detailed Guidance

### Subtask T025: family test
`CharterArtifactFamilyTest`: build one of each kind (Directive, Styleguide, Paradigm, Tactic),
assert each is assignable to `CharterArtifact`, and assert kind-correct spine values:
`kind()`, `label()` (title|name), `description()` (intent|summary|purpose|empty for styleguide),
`urn()` (`<kind>:<id>`), and `edges()` shape (styleguide empty; others per the edge contract).

### Subtask T026: corpus round-trip
`PackDoctrineCorpusRoundTripTest`: over the open-packs fixture corpus (via `OpenPacksCorpus`),
load every directive/styleguide/paradigm and assert `read(write(dso)).equals(dso)` with zero
field drift (SC-001/NFR-001). If the corpus lacks a kind, fall back to representative fixtures
and note it; do not skip silently.

### Subtask T027: validation parity + wiring + gates
- `DoctrineValidationParityTest`: assert each `ModularRuleset` rejects a fixture violating each
  canonical required field / enum / cardinality / the directive conditional, each paired with a
  passing control on the same fixture (non-vacuity). This is the NFR-002 audit.
- `DoctrineWiringRoundTripTest` (deployable): mirror `WiringRoundTripTest` to prove the Spring
  context wires the new repositories/serializer beans and a save→load round-trips end to end.
- Confirm `HexagonalArchitectureTest` passes (incl. `KERNEL_IS_PURE`, `SLICE_COVERAGE`) — report
  the run in history; no edit expected.

## Red-first evidence (required)

Each new test seen failing first where it can (e.g. before the beans wire), then green; hashes
in history. For the ArchUnit gate, record the passing run.

## Definition of Done

- [ ] T025–T027 done. Family, corpus, parity and wiring tests green; ArchUnit gates confirmed green.
- [ ] `:packs:test` and `:deployable:test` targeted runs green. Scope stayed within the three
  kinds (C-004) — no toolguide/procedure/asset/profile/mission-type code added.

## Risks

- A corpus gap for a kind masking a fidelity bug — fall back to representative fixtures, do not skip.
- Parity test vacuity — every rejection assertion needs a same-fixture positive control.

## Reviewer guidance

Confirm the parity test is non-vacuous (paired controls), the corpus test asserts true field
equivalence (not just "no exception"), and the wiring test exercises the real Spring beans, not a helper.
