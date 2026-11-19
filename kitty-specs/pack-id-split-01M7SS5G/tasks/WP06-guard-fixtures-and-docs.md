---
work_package_id: WP06
title: The slug(63) guard, the remaining fixtures, and the docs
dependencies:
- WP05
requirement_refs:
- FR-001
- FR-010
- FR-012
- NFR-001
- NFR-003
- SC-002
- SC-003
planning_base_branch: feat/583-pack-id-split
merge_target_branch: feat/583-pack-id-split
branch_strategy: Planning artifacts for this mission were generated on feat/583-pack-id-split. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/583-pack-id-split unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-id-split-01M7SS5G
base_commit: aaf5abe736ef334332b1d87c094cb6302dd9636a
created_at: '2026-11-19T17:18:50.847202+00:00'
subtasks:
- T026
- T027
- T028
- T029
- T030
history:
- at: '2026-11-19T12:00:00Z'
  actor: java-jenny
  action: Prompt generated from plan.md and research.md
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/
create_intent: []
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackIdentifier.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/PackIdentifierTest.java
- service/profiles/src/test/**
- service/profiles-edge/src/test/**
- service/missions/src/test/**
- service/missions-edge/src/test/**
- service/routing/src/test/**
- service/routing-edge/src/test/**
- service/deployable/src/test/**
- service/artifacts-edge/src/test/**
- contracts/charter/parameters/PackId.yaml
- docs/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check.md
role: implementer
tags: []
tracker_refs:
- '#583'
---
# Work Package Prompt: WP06 - The slug(63) guard, the remaining fixtures, and the docs

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else. Then read, in
`kitty-specs/pack-id-split-01M7SS5G/`: `spec.md`, `plan.md` (the Implementation Concern Map), `research.md`, `data-model.md`,
`research/decisions.md` (D1 to D9, ruled) and `research/census.md` (the file:line of each site).

## Branch Strategy

Planning base: `feat/583-pack-id-split`. Final merge target: `feat/583-pack-id-split`, then `main` by PR #610. The execution worktree is allocated per
computed lane from `lanes.json`.

```bash
spec-kitty agent action implement WP06 --agent claude --mission pack-id-split-01M7SS5G
```

Commits are signed. They carry no model name and no Co-Authored-By trailer.

## Objective

Close the type: the constructor admits only a pack id, every remaining fixture carries the canonical shape, and the
docs say so.

## Subtasks

### T026 Guard

`PackIdentifier`'s `RULES` gains `.must(RULE)` (the `slug(63)` constraint, `GRAMMAR_DETAIL`). Rewrite the Javadoc that
cites "#552 Q3, pending": the descriptor's `pack_id` is `PackUlid`. `PackIdentifierTest`: these are refused: `a--b`,
`-a`, `a-`, `A`, `my_pack`, a 64-character slug, a ULID, blank. These are accepted: `a`, `1a`, `my-pack`, a 63-character
slug.

### T027 Fixtures

Run each module's tests and fix what the guard refuses. A ULID-shaped literal becomes a `PackUlid` or the registered id
that fits. A name such as `q/a` in artifacts-edge tests becomes a valid id, unless the test is an error-path test. No
assertion's expected wire value changes except where WP04 or WP05 listed one.

### T028 `PackId.yaml`

The description becomes: "The id the Charter Pack is registered under: the name the service's pack registry gives it,
a kebab slug of at most 63 characters (ADR-0075 decision 2). It is not the pack.yaml's pack_id." Run the contract
checks.

### T029 Docs

ADR-0075 decision 2: add a dated status note (2026-11-19, #583) recording the split, `PackUlid` and the storage code
`pack_id_invalid`. Update the glossary term for pack id if `docs/context/` has one. Update the pack-ecosystem work
breakdown if it lists #583.

### T030 Evidence

Run the quickstart's grep (0 hits), `./gradlew verifyAll` and the whole-pack round trip, and record the output in the
activity log.

## Definition of Done

`./gradlew verifyAll` is green on Java 25.
