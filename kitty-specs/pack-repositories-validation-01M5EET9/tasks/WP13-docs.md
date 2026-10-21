---
work_package_id: WP13
title: DDD docs, glossary, changelog, ADR amendment
dependencies:
- WP12
requirement_refs:
- FR-015
- FR-016
- FR-017
- SC-005
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
subtasks:
- T054
- T055
- T056
- T057
- T058
- T059
- T060
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: curator-carla
authoritative_surface: docs/
create_intent:
- docs/development/code-style-and-conventions.md
- docs/development/how-to-ddd.md
- docs/architecture/architectural-patterns-applied.md
- docs/architecture/diagrams/c4-components.puml
- docs/architecture/diagrams/class-diagram.puml
- docs/glossary/ddd-patterns.md
- docs/changelog/2026-10-21-pack-repositories-and-validation.md
execution_mode: planning_artifact
owned_files:
- docs/development/code-style-and-conventions.md
- docs/development/how-to-ddd.md
- docs/architecture/architectural-patterns-applied.md
- docs/architecture/diagrams/c4-components.puml
- docs/architecture/diagrams/class-diagram.puml
- docs/glossary/ddd-patterns.md
- docs/changelog/2026-10-21-pack-repositories-and-validation.md
- docs/adr/0006-onion-vertical-slices-archunit.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP13 – DDD docs, glossary, changelog, ADR amendment

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `curator-carla` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Author the pattern-setting documentation for the cornerstone DDD + hexagonal style: a code-style/conventions page, a "How to DDD here" guide, an "Architectural Patterns Applied" page embedding the C4 + class diagrams and the concern↔pattern-role mapping, glossary entries headlined by **Domain Entity vs Persistence Entity**, a changelog entry, and the ADR-0006 amendment to Accepted/realized. This is IC-07. **Docs only — no `service/**` edits.**

## Context

- The doc inventory + what each must document (records-vs-builders, DIP, identity-vs-fidelity, DTO vs DSO): [plan.md](../plan.md) IC-07; [spec.md](../spec.md) FR-015/016/017; [quickstart.md](../quickstart.md) §6.
- The realized shape to describe is the one implemented across WP01..WP12 — read the merged slice layout, do not restate the plan verbatim.
- The ADOPT/AVOID learnings to fold (modular-validators + TraMBU): [research.md](../research.md) findings-disposition table.

### Subtask T054 — code-style-and-conventions.md *(parallel-safe)*
- **Steps**: `docs/development/code-style-and-conventions.md` — the type-nature modelling rule (records for VOs/DSOs/params; final class + public fluent builder + package-private all-args ctor for entities), null-marking, the palantir-java-format convention, and the "no public wide constructor" rule.
- **Files**: `code-style-and-conventions.md`.

### Subtask T055 — How-to-DDD guide *(parallel-safe)*
- **Steps**: `docs/development/how-to-ddd.md` — how to add an aggregate/entity/VO/port/adapter in this codebase, the DIP (ports in domain, `@Service` at the edge), the identity-vs-fidelity rule (entity equals vs DSO round-trip), and the folded AVOID list (no aggregate self-persistence, no anemic domain, no raw-UUID identity, no `@Data` on persistence, stereotype only at the edge).
- **Files**: `how-to-ddd.md`.

### Subtask T056 — architectural-patterns-applied.md
- **Steps**: `docs/architecture/architectural-patterns-applied.md` — the concern (`kernel`/`packs.{domain,application,infra.storage,infra.api}`/`bootstrap`/`validation`) ↔ pattern-role (aggregate root / child entity / VO / port / adapter / strategy / DSO / composition root) mapping, and a prose walk of the onion + slices. Embed the two diagrams from T057.
- **Files**: `architectural-patterns-applied.md`.

### Subtask T057 — Copy + embed the canonical diagrams
- **Steps**: copy the canonical PlantUML **verbatim** (do not redraw) from
  `/tmp/claude-1000/-home-stijn-Documents--code-SDD-fork-spec-kitty-charter/6420b659-1f26-4b34-a19e-dae5fa2dd11d/scratchpad/renders/c4-components.puml`
  and `.../renders/class-diagram.puml`
  into `docs/architecture/diagrams/c4-components.puml` and `docs/architecture/diagrams/class-diagram.puml`; embed/reference them from `architectural-patterns-applied.md` in the style of the other `docs/architecture/diagrams/*.puml` pages.
- **Files**: `docs/architecture/diagrams/c4-components.puml`, `docs/architecture/diagrams/class-diagram.puml`.
- **Edge cases**: if the docsite pipeline renders `.puml`→`.svg`, follow the existing convention (the diagrams dir already pairs `.puml` + `.svg`); generate the `.svg` the same way the pipeline does rather than hand-editing.

### Subtask T058 — Glossary entries *(parallel-safe)*
- **Steps**: `docs/glossary/ddd-patterns.md` headlined **Domain Entity vs Persistence Entity**, plus value object, aggregate root, port/adapter, projection, DTO vs DSO, identifier VO. Link into the existing glossary README/index.
- **Files**: `ddd-patterns.md`.

### Subtask T059 — Changelog entry *(parallel-safe)*
- **Steps**: `docs/changelog/2026-10-21-pack-repositories-and-validation.md` recording the new `validation` kernel sub-package, the repository reshape (ports + `@Service` edge + strategy/DSO), and the screaming-architecture slice migration (`kernel`/`packs`/`bootstrap`).
- **Files**: `2026-10-21-pack-repositories-and-validation.md`.

### Subtask T060 — Amend ADR-0006 → Accepted/realized
- **Steps**: amend `docs/adr/0006-onion-vertical-slices-archunit.md` from Proposed to Accepted, noting it is realized by this migration and that the prior flat role-based modules are superseded; reference the realized rule inventory (WP07).
- **Files**: `0006-onion-vertical-slices-archunit.md`.

## Branch Strategy

- Planning branch `feat/pack-repositories-validation`; final merge target `feat/pack-repositories-validation`.
- Execution worktrees per lane from `lanes.json`. Depends on WP12 (documents the realized, wired shape). `planning_artifact` WP — keep strictly to `docs/**`.

## Definition of Done

- All named docs exist and document the specific rules (records-vs-builders, DIP, identity-vs-fidelity, DTO vs DSO).
- The C4 + class diagrams are copied verbatim and embedded; the glossary headlines Domain vs Persistence Entity.
- The changelog entry exists; ADR-0006 reads Accepted/realized.

## Risks

- **Scope leak** → no `service/**` edits in this WP (code is frozen by WP12).
- **Redrawing** → copy the canonical `.puml`, do not re-author the diagrams.

## Reviewer guidance

- Confirm every FR-015/016/017 artifact exists and documents the rule, not just names it.
- Confirm the diagrams match the canonical renders and the ADR status flipped to Accepted/realized.
- Confirm no file outside `docs/**` was touched.
