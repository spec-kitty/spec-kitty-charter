---
work_package_id: WP01
title: Family spine — CharterArtifact, Enforcement, edges helper
dependencies: []
requirement_refs:
- FR-013
- FR-017
- FR-019
- C-002
- C-003
planning_base_branch: feat/pack-doctrine-artefacts
merge_target_branch: feat/pack-doctrine-artefacts
subtasks:
- T001
- T002
- T003
phase: Phase 1 - Foundational
agent: claude
history:
- at: '2026-10-23T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/CharterArtifact.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Enforcement.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ArtifactEdges.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/CharterArtifactSpineTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/CharterArtifact.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Enforcement.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ArtifactEdges.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/CharterArtifactSpineTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP01 — Family spine

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objectives & Success Criteria

Build the `packs.domain` family supertype and the shared edge helper that the three
new kinds (and the retrofitted `Tactic`) bind to. Read [data-model.md](../data-model.md)
("Family interface", "Enforcement", "DRG edge contract") and [research.md](../research.md)
R2 before coding — they are authoritative.

- `CharterArtifact` exposes only the genuine universals and lives in `packs.domain`
  (it returns `PackIdentifier`, a packs type — it must NOT go in `kernel`, which
  `HexagonalArchitectureTest.KERNEL_IS_PURE` keeps pure).
- `Enforcement` round-trips its hyphenated tokens losslessly.
- `ArtifactEdges` turns a kind's references into kernel `DrgEdge`s with the correct
  target kind and the interim relations, reused by every kind.

## Context & Constraints

- Reuse kernel types unchanged: `ArtifactKind`, `Urn`, `NodeKind`, `DrgEdge`,
  `Relation` (all in `ai.speckitty.charter.kernel.domain`). Do not modify the kernel.
- `NodeKind.of(ArtifactKind)` and `ArtifactKind.fromToken(String)` already exist.
- No new dependency. JSpecify `@Nullable` for nullability, matching the module style.

## Branch Strategy

- **Planning base / merge target**: `feat/pack-doctrine-artefacts`.
- Execution worktrees are allocated per computed lane from `lanes.json`; never pick a base by hand.

```bash
spec-kitty agent action implement WP01 --agent claude --mission pack-doctrine-artefacts-01M5JHD0
```

Build: CI runs `verifyAll`; locally run only targeted checks, e.g.
`./gradlew :packs:test --tests '*CharterArtifactSpineTest'`.

## Subtasks & Detailed Guidance

### Subtask T001: `CharterArtifact` interface
Create `CharterArtifact` (interface) with:
`ArtifactKind kind()`, `PackIdentifier packId()`, `String localId()`, `String schemaVersion()`,
`String label()`, `Optional<String> description()`, `List<DrgEdge> edges()`, and a
`default Urn urn() { return new Urn(NodeKind.of(kind()), localId()); }`. Javadoc each
method stating the normalization it represents (label = title|name; description =
intent|summary|purpose, empty for styleguide; edges per the edge contract).

### Subtask T002: `Enforcement` value `[P]`
Create `enum Enforcement { REQUIRED, LENIENT_ADHERENCE, ADVISORY }` with an **explicit
stored token** field (`"required"`, `"lenient-adherence"`, `"advisory"`) — the token is
NOT a derived `name().toLowerCase()` (that yields `lenient_adherence`, wrong). Provide
`String token()` and `static Optional<Enforcement> fromToken(String)`, mirroring
`ArtifactKind.token()`/`fromToken()`.

### Subtask T003: `ArtifactEdges` helper
Create a package-private helper that builds `List<DrgEdge>` for a kind, given the owner's
`Urn` and its reference collections. Per [research.md](../research.md) R2:
- typed references (`type`,`id`,optional `when`/`reason`) → `DrgEdge(owner, Urn(NodeKind.of(ArtifactKind.fromToken(type).orElseThrow()), id), Relation.SUGGESTS, Optional.empty(), when, reason)`;
- bare procedure refs → target kind `procedure`, `Relation.REQUIRES`;
- bare directive refs → target kind `directive`, `Relation.REQUIRES`.
`impacts` is always `Optional.empty()` (no IMPACTS edges). Callers guarantee non-blank
ids (the kinds' rulesets enforce it), so `Urn` never throws; still, fail loudly
(`orElseThrow`) rather than silently skipping, so a bad token surfaces.

## Red-first evidence (required)

History must record the `CharterArtifactSpineTest` first failing (types absent / wrong
token), then green, with both commit hashes.

## Definition of Done

- [ ] T001–T003 done; `CharterArtifactSpineTest` covers `urn()` default, `Enforcement`
  token round-trip for all three (incl. the hyphen case), and `ArtifactEdges` for a
  typed ref, a bare procedure, and a bare directive_ref.
- [ ] No kernel file modified. `:packs:test` targeted run green on Java 25.
- [ ] `HexagonalArchitectureTest` not broken (no kernel dependency added).

## Risks

- Deriving the `Enforcement` token from the enum name — WRONG for `lenient-adherence`. Store it.
- Putting `CharterArtifact` in `kernel` — breaks `KERNEL_IS_PURE`. It is a `packs.domain` type.

## Reviewer guidance

Confirm the spine has only genuine universals (nothing kind-specific leaked in), the
`Enforcement` token is stored not derived, and `ArtifactEdges` sets `impacts` empty and
uses the interim relations from research R2 verbatim.
