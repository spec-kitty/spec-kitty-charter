---
work_package_id: WP05
title: Serialization seam + round-trip
dependencies:
- WP02
- WP03
- WP04
requirement_refs:
- FR-003
- FR-007
- FR-011
- FR-016
- FR-018
- NFR-001
planning_base_branch: feat/pack-doctrine-artefacts
merge_target_branch: feat/pack-doctrine-artefacts
subtasks:
- T014
- T015
- T016
- T017
phase: Phase 3 - Seams
agent: claude
history:
- at: '2026-10-23T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DoctrineRoundTripTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/SerializationStrategy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlSerializationStrategy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlDumpEmitter.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DoctrineRoundTripTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP05 — Serialization seam + round-trip

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objectives & Success Criteria

Extend the shared YAML seam so directive/styleguide/paradigm DSOs read (closed-world)
and write with field-wise fidelity (NFR-001), including the raw `ConfigValue` tree.
Study the existing tactic methods in `YamlSerializationStrategy` / `YamlDumpEmitter` and
mirror them exactly. Fidelity contract: `read(write(dso)).equals(dso)` at the DSO-record level.

## Context & Constraints

- `SerializationStrategy` is the format seam (interface). Add `write<Kind>`/`read<Kind>`
  per kind, mirroring `writeTactic`/`readTactic`.
- Closed-world comes **free**: `file-kinds.yaml` already maps
  `**/*.{directive,styleguide,paradigm}.yaml` and `PackSchemas.bundled()` already loads
  all three with `additionalProperties:false`. Each `read<Kind>` must call
  `PackSchemas.bundled().validate("<kind>", file, document)` and return
  `FileRead.Unreadable` on violations — exactly like `readTactic` (FR-016).
- Absence-vs-empty (edge case): non-nullable lists normalize absent→empty on read; the
  nullable scalars (`directive.scope`, `styleguide.quality_test`/`tooling`/`structural_lint_config`)
  keep the `Optional` present/absent distinction.

## Branch Strategy

- **Planning base / merge target**: `feat/pack-doctrine-artefacts`. Worktree per lane from `lanes.json`.

```bash
spec-kitty agent action implement WP05 --agent claude --mission pack-doctrine-artefacts-01M5JHD0
```

Build: `./gradlew :packs:test --tests '*DoctrineRoundTripTest'`.

## Subtasks & Detailed Guidance

### Subtask T014: extend `SerializationStrategy`
Add `writeDirective/readDirective`, `writeStyleguide/readStyleguide`,
`writeParadigm/readParadigm` to the interface, each `String write<Kind>(<Kind>Dso)` /
`FileRead<<Kind>Dso> read<Kind>(String text, String file)`, with javadoc matching the
tactic methods.

### Subtask T015: `YamlSerializationStrategy` per-kind read/write
Implement the six methods. Reuse the private helpers (`required`, `optional`, `strings`,
`list`, `references`-style builders — add per-kind reference builders since shapes
differ). For styleguide's `tooling` read a `Map<String,String>` (preserve insertion
order via `LinkedHashMap`); for `structural_lint_config` build `ConfigValue.of(parsed)`
from the raw parsed subtree. Write path: emit the token strings (`enforcement`, `scope`),
nested patterns/references, and the config tree.

### Subtask T016: raw-tree emit primitive
Add a `YamlDumpEmitter` primitive that writes a **present** value verbatim, **bypassing
the omit-empty/null rule** (m-5): a present-but-empty mapping (`{}`) and nested explicit
nulls must be emitted, not dropped. Route `structural_lint_config` and `tooling` through
it when the Optional is present; omit the key only when the Optional is absent. Use
`ConfigValue.toParsed()` to feed the dumper the raw nested structure.

### Subtask T017: round-trip tests
`DoctrineRoundTripTest`: for each kind, build a representative DSO (directive incl.
procedures + typed refs + scope; styleguide incl. patterns/anti-patterns, a `tooling`
map, and a `structural_lint_config` tree with mixed scalar types, nested seq/map, a
present-empty map, and an explicit null; paradigm incl. directive_refs + typed refs),
then assert `read(write(dso)).equals(dso)`. Add a negative: an unknown top-level key is
rejected (`FileRead.Unreadable`) with a same-fixture known-key control that reads clean (FR-016).

## Red-first evidence (required)

`DoctrineRoundTripTest` seen failing first (methods unimplemented), then green; hashes in history.

## Definition of Done

- [ ] T014–T017 done. Round-trip is field-equivalent for all three kinds, including the
  nested config tree with present-empty + explicit-null preserved and scalar types intact.
- [ ] Closed-world rejection proven per kind with a same-fixture positive control.
- [ ] `:packs:test` targeted run green. No change to domain or adapter-wiring files.

## Risks

- Routing `structural_lint_config`/`tooling` through the ordinary `scalar`/`mappings`
  helpers — they omit empty/null and flatten; use the T016 raw-tree primitive.
- Reusing the tactic `references` builder for the other kinds — reference shapes differ
  per kind (directive has when/reason, paradigm has only type/id).

## Reviewer guidance

Confirm each `read<Kind>` validates against the bundled schema (closed-world), the raw-tree
emission preserves present-empty/null and types, and every absent-vs-empty case matches
the DSO's Optional/list contract.
