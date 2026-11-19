---
work_package_id: WP01
title: 'Kernel: PackUlid and the ULID grammar'
dependencies: []
requirement_refs:
- FR-002
planning_base_branch: feat/583-pack-id-split
merge_target_branch: feat/583-pack-id-split
branch_strategy: Planning artifacts for this mission were generated on feat/583-pack-id-split. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/583-pack-id-split unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-id-split-01M7SS5G
base_commit: 469761ed79d5550db88ea6886f8b9ceab85c50f4
created_at: '2026-11-19T11:43:47.352962+00:00'
subtasks:
- T001
- T002
- T003
history:
- at: '2026-11-19T12:00:00Z'
  actor: java-jenny
  action: Prompt generated from plan.md and research.md
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/
create_intent:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackUlid.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/PackUlidTest.java
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackUlid.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/IdentifierGrammar.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/PackUlidTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/IdentifierGrammarTest.java
role: implementer
tags: []
tracker_refs:
- '#583'
---
# Work Package Prompt: WP01 - Kernel: PackUlid and the ULID grammar

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else. Then read, in
`kitty-specs/pack-id-split-01M7SS5G/`: `spec.md`, `plan.md` (the Implementation Concern Map), `research.md`, `data-model.md`,
`research/decisions.md` (D1 to D9, ruled) and `research/census.md` (the file:line of each site).

## Branch Strategy

Planning base: `feat/583-pack-id-split`. Final merge target: `feat/583-pack-id-split`, then `main` by PR #610. The execution worktree is allocated per
computed lane from `lanes.json`.

```bash
spec-kitty agent action implement WP01 --agent claude --mission pack-id-split-01M7SS5G
```

Commits are signed. They carry no model name and no Co-Authored-By trailer.

## Objective

Add the descriptor identity type. This is additive: nothing uses it yet, and the build stays green.

## Subtasks

### T001 `IdentifierGrammar.ulid()`

Add a `Constraint<String>` for the ULID grammar: exactly 26 characters from Crockford base32 upper case,
`[0-9A-HJKMNP-TV-Z]`. Follow the style of `slug(int)`. Test it in `IdentifierGrammarTest`, creating that test if it does not
exist.

### T002 `PackUlid`

Write `record PackUlid(String value)` in `kernel.domain`. Its compact constructor guards with
`ModularRuleset.must(IdentifierGrammar.ulid().described(GRAMMAR_DETAIL))`, as `PackIdentifier` does. Give it a static
`isUlid(@Nullable String)`. Javadoc: "the descriptor's `pack_id`: a pack's immutable identity across registrations (the
`Ulid` of `pack-descriptor.schema.json`); never the name a pack is registered under (`PackIdentifier`)."

### T003 Tests

`PackUlidTest`: a valid ULID is accepted. These are refused: `01PACK`, a 25-character and a 27-character text, a text
holding `I`, `L`, `O` or `U`, a lower-case ULID, blank, null. Assert the refusal detail. Use real ULIDs (e.g.
`01J6XW9KQT7M0YB3N4R5CQZ2EX`).

## Definition of Done

`./gradlew :kernel:check` is green.
