---
work_package_id: WP06
title: Repository ports + storage adapter
dependencies:
- WP02
- WP03
- WP04
- WP05
- WP07
requirement_refs:
- FR-004
- FR-008
- FR-012
- SC-002
planning_base_branch: feat/pack-doctrine-artefacts
merge_target_branch: feat/pack-doctrine-artefacts
subtasks:
- T018
- T019
- T020
- T021
phase: Phase 3 - Seams
agent: claude
history:
- at: '2026-10-23T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/DirectiveRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StyleguideRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ParadigmRepository.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DoctrineRepositoryContractTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/DirectiveRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StyleguideRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ParadigmRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackContents.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DoctrineRepositoryContractTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP06 — Repository ports + storage adapter

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objectives & Success Criteria

Give each kind a per-type repository **port** in the domain and implement all three in the
storage adapter, so a pack's directives/styleguides/paradigms load and save one at a time
through a stable boundary (SC-002) and the `Pack` aggregate (WP07) is assembled with them.
Templates: `TacticRepository.java` (port) and its `FilesystemPackStore`/`DomainRepositoryService`
implementation.

## Context & Constraints

- The adapter is **tactic-hardcoded today**: `PackContents.PackRead` carries only `tactics`,
  and `FilesystemPackStore` has `tacticFiles()/locateTactic()/newTacticFile()/readTactics()/writeTactic()`.
  Each new kind needs the analogous locate / new-file-name (`<id>.<kind>.yaml`) / upsert and a
  `PackRead` field. This is the bulk of the WP (m-2).
- `DomainRepositoryService.assemble()` must read all kinds and build the `Pack` via the
  extended `Pack.Builder` adders (WP07). Uses the WP05 `read<Kind>`/`write<Kind>` methods.
- Each DSO↔entity mapping converts the token strings back to `Enforcement`/`StyleguideScope`
  and rebuilds the `Optional`/list fields. The no-duplicate-id invariant (WP07) matters:
  `locate` returns the first match, so saving a new id must create a new file.

## Branch Strategy

- **Planning base / merge target**: `feat/pack-doctrine-artefacts`. Worktree per lane from `lanes.json`.

```bash
spec-kitty agent action implement WP06 --agent claude --mission pack-doctrine-artefacts-01M5JHD0
```

Build: `./gradlew :packs:test --tests '*DoctrineRepositoryContractTest'`.

## Subtasks & Detailed Guidance

### Subtask T018: repository ports `[P]`
Create `DirectiveRepository`, `StyleguideRepository`, `ParadigmRepository` in `domain`,
each mirroring `TacticRepository`: `void save(<Kind>)` and
`Optional<<Kind>> load(PackIdentifier pack, <Kind>Identifier id)`.

### Subtask T019: `FilesystemPackStore` + `PackRead`
Extend `PackContents.PackRead` to carry `List<Directive>`, `List<Styleguide>`,
`List<Paradigm>` alongside tactics. Add per-kind `locate`/`new-file-name`/upsert/read
methods mirroring the tactic ones, globbing `*.directive.yaml` etc.

### Subtask T020: `DomainRepositoryService`
Implement the three ports and extend `assemble()` to read every kind and populate the
`Pack` via the new builder adders. Map DSO↔entity both ways (token↔enum, Optionals, lists).

### Subtask T021: contract tests
`DoctrineRepositoryContractTest`: for each kind, `save` then `load` returns an equal entity;
saving a second entity with a new id creates a second file (no overwrite); `load` of an
unknown id returns `Optional.empty()`. Use the existing `PackFixtures`/temp-dir style.

## Red-first evidence (required)

`DoctrineRepositoryContractTest` seen failing first, then green; hashes in history.

## Definition of Done

- [ ] T018–T021 done. Save→load round-trips an equal entity per kind; new-id save does
  not overwrite a sibling; unknown-id load is empty.
- [ ] `assemble()` populates the aggregate with all kinds. `:packs:test` targeted run green.
- [ ] No duplicate-id silent overwrite (guarded with the WP07 invariant).

## Risks

- `PackRead` is consumed elsewhere — extend it additively; keep tactics working unchanged.
- Forgetting the token↔enum conversion at the DSO boundary (store token, hold enum in domain).

## Reviewer guidance

Confirm the ports match the `TacticRepository` contract, the adapter globs the right file
suffixes, `assemble()` wires every kind, and the save path respects the no-duplicate-id invariant.
