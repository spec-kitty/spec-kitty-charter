---
work_package_id: WP03
title: Styleguide domain + DSO + ConfigValue
dependencies:
- WP01
requirement_refs:
- FR-005
- FR-006
- FR-014
- FR-018
- NFR-002
- C-001
planning_base_branch: feat/pack-doctrine-artefacts
merge_target_branch: feat/pack-doctrine-artefacts
subtasks:
- T007
- T008
- T009
- T010
phase: Phase 2 - Kinds
agent: claude
history:
- at: '2026-10-23T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StyleguideIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StyleguideScope.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StyleguidePattern.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StyleguideAntiPattern.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Styleguide.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StyleguideDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ConfigValue.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/StyleguideEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ConfigValueTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StyleguideIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StyleguideScope.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StyleguidePattern.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StyleguideAntiPattern.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Styleguide.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StyleguideDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ConfigValue.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/StyleguideEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ConfigValueTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP03 — Styleguide domain + DSO + ConfigValue

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objectives & Success Criteria

Model the **styleguide** — conventions for the shape of a work product — as a
first-class entity, plus the sealed `ConfigValue` raw-tree that makes
`structural_lint_config` round-trip losslessly (the mission's riskiest surface, FR-018).
Canonical shape: `contracts/packs/schemas/styleguide.schema.json`; entity table in
[data-model.md](../data-model.md) ("Styleguide"); representation decision in
[research.md](../research.md) R1. Template: `Tactic.java` / `TacticDso.java`.

## Context & Constraints

- House style as WP02 (identifier VO, package-private ctor + fluent builder, ruleset,
  identity equality).
- `Styleguide implements CharterArtifact`: `kind()`=`ArtifactKind.STYLEGUIDE`,
  `label()`=`title`, `description()`=`Optional.empty()`, `edges()`=`List.of()` — a
  styleguide's `references` are bare citation strings (body, not edges, M4).
- `ConfigValue` lives in `infra.storage` and must preserve raw scalar types and nesting
  (R1). Do not coerce scalars to text.

## Branch Strategy

- **Planning base / merge target**: `feat/pack-doctrine-artefacts`. Worktree per lane from `lanes.json`.

```bash
spec-kitty agent action implement WP03 --agent claude --mission pack-doctrine-artefacts-01M5JHD0
```

Build: `./gradlew :packs:test --tests '*StyleguideEntityTest' --tests '*ConfigValueTest'`.

## Subtasks & Detailed Guidance

### Subtask T007: identity, scope, pattern VOs `[P]`
- `StyleguideIdentifier(String value)` — invariant `^[a-z][a-z0-9-]*$`.
- `enum StyleguideScope { CODE, DOCS, ARCHITECTURE, TESTING, OPERATIONS, GLOSSARY }` with
  stored lowercase tokens + `token()`/`fromToken()` (all tokens here are single words, but
  keep the explicit-token pattern for consistency).
- `StyleguidePattern(String name, String description, Optional<String> badExample, Optional<String> goodExample)`.
- `StyleguideAntiPattern(String name, String description, String badExample, String goodExample)`
  — note **all four required** (differs from `StyleguidePattern`, per schema; m6).

### Subtask T008: `Styleguide` entity + ruleset
Fields: `id`, `packId`, `schemaVersion`, `title`, `StyleguideScope scope`,
`List<String> principles`, `List<StyleguidePattern> patterns`,
`List<StyleguideAntiPattern> antiPatterns`, `Optional<Map<String,String>> tooling`,
`Optional<String> qualityTest`, `Optional<ConfigValue> structuralLintConfig`,
`List<String> appliesToLanguages`, `List<String> references`. Ruleset: non-blank
`schemaVersion` (`^1\.0$`)/`title`; `principles` non-empty; `patterns`/`antiPatterns`
non-empty **when present**. Implement `CharterArtifact` (description/edges empty).

### Subtask T009: `ConfigValue` sealed tree
Per [research.md](../research.md) R1:
`sealed interface ConfigValue permits Scalar, Seq, Mapping`.
- `record Scalar(@Nullable Object raw)` — holds the raw parsed scalar from the shared
  `Yaml.schema()` loader (Integer/BigInteger/Double/Boolean/String/null); equality on `raw`.
- `record Seq(List<ConfigValue> items)` — order + duplicates significant; immutable copy.
- `record Mapping(LinkedHashMap<String,ConfigValue> entries)` — insertion-order preserved;
  structural equality by key→value (a `LinkedHashMap` equals is order-insensitive — fine
  for value equality; keep insertion order only for deterministic emit).
Provide a `static ConfigValue of(Object parsed)` factory that recursively wraps a
SnakeYAML parse result, and an `Object toParsed()` inverse for the emitter (WP05 uses it).
Do NOT resolve/convert types — preserve them.

### Subtask T010: `StyleguideDso`
Flat record mirroring the YAML: `id, schemaVersion, title, scope` (token string),
`List<String> principles`, `List<Pattern> patterns`, `List<AntiPattern> antiPatterns`,
`Optional<Map<String,String>> tooling`, `Optional<String> qualityTest`,
`Optional<ConfigValue> structuralLintConfig`, `List<String> appliesToLanguages`,
`List<String> references`, with nested `Pattern`/`AntiPattern` records matching T007's
optionality. `tooling`/`structuralLintConfig` are `Optional` (absent ≠ present-empty).

## Red-first evidence (required)

`StyleguideEntityTest` and `ConfigValueTest` seen failing first, then green; hashes in history.

## Definition of Done

- [ ] T007–T010 done. `StyleguideEntityTest`: scope-enum membership enforced (reject an
  invalid token with a same-fixture valid control); `principles` non-empty enforced;
  pattern/anti-pattern cardinality-when-present; `description()`/`edges()` empty.
- [ ] `ConfigValueTest`: a nested tree with an int, a bigint, a float, a boolean, a null,
  a nested seq and a nested map survives `of(parsed)` → `toParsed()` → `of(...)` with
  **structural equality preserved and types unchanged** (no string coercion).
- [ ] No field outside the canonical schema. `:packs:test` targeted run green.
- [ ] Did not touch shared adapter/aggregate files.

## Risks

- Coercing ConfigValue scalars to `String` (the Tactic reader's `text()` habit) — the
  whole point of FR-018 is to NOT do that. Keep the raw parsed object.
- Hard-coding the scalar type set to `Long|Double` — SnakeYAML `JsonSchema` boxes small
  ints as `Integer`, large as `BigInteger`. Hold the raw `Object`.
- Unifying `scope` with a directive's free-text scope — they are different types (n13).

## Reviewer guidance

Confirm `ConfigValue` preserves types and nesting with structural equality; the
anti-pattern VO requires all four fields while pattern makes examples optional; and the
styleguide genuinely mints no edges and has no description.
