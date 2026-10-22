---
work_package_id: WP02
title: Directive domain + DSO
dependencies:
- WP01
requirement_refs:
- FR-001
- FR-002
- FR-014
- FR-019
- NFR-002
- C-001
planning_base_branch: feat/pack-doctrine-artefacts
merge_target_branch: feat/pack-doctrine-artefacts
subtasks:
- T004
- T005
- T006
phase: Phase 2 - Kinds
agent: claude
history:
- at: '2026-10-23T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/DirectiveIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/DirectiveReference.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Directive.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DirectiveDso.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/DirectiveEntityTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/DirectiveIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/DirectiveReference.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Directive.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DirectiveDso.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/DirectiveEntityTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP02 — Directive domain + DSO

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objectives & Success Criteria

Model the **directive** — a binding governance rule — as a first-class pack child
entity mirroring `Tactic`, plus its flat persistence DSO. Canonical shape:
`contracts/packs/schemas/directive.schema.json`; entity table in
[data-model.md](../data-model.md) ("Directive"). Study the template
`service/packs/src/main/java/ai/speckitty/charter/packs/domain/Tactic.java` and
`.../infra/storage/TacticDso.java` and follow their idioms exactly.

## Context & Constraints

- House style (memory, non-negotiable): typed identifier **value object** (record),
  **package-private all-args constructor** + **public fluent chainable builder**,
  `ModularRuleset` invariants guarded in the constructor, **identity equality** (equal
  iff identifier equal). See `Tactic` for the exact shape.
- `Directive implements CharterArtifact` (WP01): `kind()`=`ArtifactKind.DIRECTIVE`,
  `label()`=`title`, `description()`=`Optional.of(intent)`, `edges()` via the
  `ArtifactEdges` helper (typed `references` + bare `procedures`).
- Reuse `Enforcement` (WP01). **No field beyond the canonical schema** (C-001).

## Branch Strategy

- **Planning base / merge target**: `feat/pack-doctrine-artefacts`. Worktree per lane from `lanes.json`.

```bash
spec-kitty agent action implement WP02 --agent claude --mission pack-doctrine-artefacts-01M5JHD0
```

Build: `./gradlew :packs:test --tests '*DirectiveEntityTest'` locally; CI runs `verifyAll`.

## Subtasks & Detailed Guidance

### Subtask T004: identity + reference VOs `[P]`
- `DirectiveIdentifier(String value)` record. Invariant: `value` matches `^[A-Z][A-Z0-9_-]*$`
  (directive ids are uppercase) — throw `IllegalArgumentException` otherwise.
- `DirectiveReference(String type, String id, Optional<String> when, Optional<String> reason)`
  record; `type`/`id` required non-null, `when`/`reason` non-null Optionals (empty when absent).

### Subtask T005: `Directive` entity + ruleset
Fields: `DirectiveIdentifier id`, `PackIdentifier packId`, `String schemaVersion`,
`String title`, `String intent`, `Enforcement enforcement`, `Optional<String> scope`,
`List<String> procedures`, `List<String> integrityRules`, `List<String> validationCriteria`,
`List<String> explicitAllowances`, `List<DirectiveReference> references`. Package-private
all-args ctor copying lists immutably + `RULES.guard(this)`; public `builder()` with
chainable setters/adders. `ModularRuleset<Directive> RULES`:
- non-blank `schemaVersion` matching `^1\.0$`, non-blank `title`, non-blank `intent`;
- **conditional**: `enforcement == LENIENT_ADHERENCE` ⇒ `explicitAllowances` non-empty (FR-002, M3);
- **every `procedures` item non-blank** (R4/m-3 — Urn rejects empty ids; this is an
  *additional* invariant beyond schema parity).
Implement `CharterArtifact` accessors. Identity equals/hashCode on `id` only.

### Subtask T006: `DirectiveDso`
A flat record in `infra.storage` mirroring the YAML (record equality = fidelity):
`id, schemaVersion, title, intent, enforcement` (the **token string**, not the enum —
the DSO is the persistence mirror), `Optional<String> scope`, `List<String> procedures,
integrityRules, validationCriteria, explicitAllowances`, `List<Reference> references`
with nested `record Reference(String type, String id, Optional<String> when, Optional<String> reason)`.
Copy lists immutably in the compact ctor; require non-null required fields. Do NOT reuse
`TacticDso.Reference` (different shape).

## Red-first evidence (required)

`DirectiveEntityTest` seen failing first (entity/VO absent), then green; both hashes in history.

## Definition of Done

- [ ] T004–T006 done. `DirectiveEntityTest` proves: a valid directive builds; the
  lenient-adherence conditional rejects an empty `explicitAllowances` **and** a paired
  positive control (lenient + allowance) passes on the same fixture; a blank `procedures`
  item is rejected; identity equality holds; `edges()` yields a procedure edge
  (`REQUIRES`) and a typed-ref edge (`SUGGESTS`).
- [ ] No field outside the canonical schema. `:packs:test` targeted run green.
- [ ] Did not touch any shared adapter/aggregate file (those are WP05/06/07).

## Risks

- Storing the `Enforcement` enum in the DSO instead of its token — breaks the flat-mirror
  contract; store the token string, convert at the domain boundary.
- Forgetting the non-blank-procedures invariant — `edges()` would throw on `[""]`.

## Reviewer guidance

Check the conditional invariant has a same-fixture positive control (non-vacuity), the id
pattern is enforced, and the DSO is a faithful flat mirror with its own `Reference` shape.
