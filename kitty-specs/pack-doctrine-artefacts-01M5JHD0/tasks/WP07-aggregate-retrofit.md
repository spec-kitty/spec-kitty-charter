---
work_package_id: WP07
title: Pack aggregate + Tactic retrofit
dependencies:
- WP01
- WP02
- WP03
- WP04
requirement_refs:
- FR-015
- SC-002
planning_base_branch: feat/pack-doctrine-artefacts
merge_target_branch: feat/pack-doctrine-artefacts
subtasks:
- T022
- T023
- T024
phase: Phase 3 - Seams
agent: claude
history:
- at: '2026-10-23T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/AggregateRetrofitTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Pack.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Tactic.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/AggregateRetrofitTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP07 — Pack aggregate + Tactic retrofit

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objectives & Success Criteria

Make the new kinds true aggregate members and fold `Tactic` into the family interface.
Decision recorded in [data-model.md](../data-model.md) ("Pack aggregate membership"):
**extend** the aggregate. `Tactic`'s existing behaviour must be preserved exactly
(regression-pinned).

## Context & Constraints

- `Pack` currently holds `List<Tactic>` with two invariants (every tactic belongs to the
  pack; no two tactics share an id) and a fluent builder with `.tactic(...)`.
- No kernel change; reuse `CharterArtifact` (WP01).

## Branch Strategy

- **Planning base / merge target**: `feat/pack-doctrine-artefacts`. Worktree per lane from `lanes.json`.

```bash
spec-kitty agent action implement WP07 --agent claude --mission pack-doctrine-artefacts-01M5JHD0
```

Build: `./gradlew :packs:test --tests '*AggregateRetrofitTest' --tests '*PackAggregateTest' --tests '*TacticEntityTest'`.

## Subtasks & Detailed Guidance

### Subtask T022: extend the `Pack` aggregate
Add `List<Directive>`, `List<Styleguide>`, `List<Paradigm>` fields with accessors, builder
adders (`.directive(...)`, `.styleguide(...)`, `.paradigm(...)`), and — for each new kind —
the same two invariants as tactics: every child's `packId` equals the pack id, and no two
children of a kind share an identifier. Keep tactics unchanged. Immutable list copies.

### Subtask T023: retrofit `Tactic`
Make `Tactic implements CharterArtifact`: `kind()`=`ArtifactKind.TACTIC`, `label()`=`name()`,
`description()`=`purpose()`, `edges()` via the `ArtifactEdges` helper over its typed
`references` (map each `TacticReference`'s always-present `when` string — possibly `""` —
to `Optional`, empty when blank). Add no new stored fields; do not change the ctor, builder,
or any existing accessor/behaviour.

### Subtask T024: tests
`AggregateRetrofitTest`: (a) the aggregate rejects a directive/styleguide/paradigm whose
`packId` differs, and rejects duplicate ids per kind, each with a same-fixture positive
control that a well-formed set is accepted; (b) a `Tactic` is assignable to `CharterArtifact`
and returns kind-correct `label`/`description`/`urn`/`edges`. The existing `PackAggregateTest`
and `TacticEntityTest` must still pass unchanged (behaviour preservation).

## Red-first evidence (required)

`AggregateRetrofitTest` seen failing first, then green; hashes in history. Confirm the
pre-existing `TacticEntityTest`/`PackAggregateTest` pass without edits.

## Definition of Done

- [ ] T022–T024 done. Aggregate invariants hold for all kinds (with positive controls);
  `Tactic` is a `CharterArtifact` with unchanged behaviour.
- [ ] No new `Tactic` field; existing tactic tests pass unmodified. `:packs:test` targeted run green.

## Risks

- Changing `Tactic`'s ctor/builder/accessors — the retrofit is additive only. If an existing
  tactic test needs editing, the retrofit changed behaviour — stop and rethink.
- Copy-paste drift in the per-kind invariants — each kind needs its own belongs/no-dup pair.

## Reviewer guidance

Confirm the retrofit is purely additive (no behaviour change — existing tactic tests
untouched and green), and each new kind has both aggregate invariants with a positive control.
