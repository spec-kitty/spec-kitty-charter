---
work_package_id: WP01
title: 'Doctrine docs: ADR-0006 revision + architecture docs + changelog'
dependencies: []
requirement_refs:
- FR-012
- SC-004
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-application-edge-read-path-01M5FQXC
base_commit: ea27e722d263c865840728c450e100af5ea44897
created_at: '2026-10-21T17:40:00.000000+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: architect-alphonso
authoritative_surface: docs/
create_intent:
- docs/changelog/2026-10-21-application-edge-read-path.md
execution_mode: planning_artifact
owned_files:
- docs/adr/0006-onion-vertical-slices-archunit.md
- docs/architecture/hexagonal-service.md
- docs/architecture/architectural-patterns-applied.md
- docs/architecture/diagrams/c4-components.puml
- docs/architecture/diagrams/c4-components.svg
- docs/architecture/diagrams/class-diagram.puml
- docs/architecture/diagrams/class-diagram.svg
- docs/glossary/ddd-patterns.md
- docs/changelog/2026-10-21-application-edge-read-path.md
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP01 – Doctrine docs: ADR-0006 revision + architecture docs + changelog

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `architect-alphonso` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Record the architecture decision this mission makes: **the application layer is an outer edge adapter, not a pure inner ring.** Only `domain` + `kernel` are pure; `application`, `infra.storage`, `infra.api` are peer edges. Update ADR-0006, the hexagonal and patterns docs, the C4/class diagrams, the DDD-patterns glossary, and add the changelog entry. This is IC-01 (docs half). The ArchUnit gate code that enforces it is WP02.

## Context

- Decision + rationale: [research.md](../research.md) (DM `gate.application-edge-rule`), [plan.md](../plan.md) Charter Check + IC-01, [spec.md](../spec.md) C-003, FR-011, FR-012.
- The current ADR-0006 is `docs/adr/0006-onion-vertical-slices-archunit.md` (Accepted; realized rule names noted). The current rule set is in `HexagonalArchitectureTest` (WP02 revises it).

### T001 — Revise ADR-0006
- State the revision: application reclassified from pure inner ring to outer edge adapter (peer to persistence). The new rule is `APPLICATION_IS_EDGE` (replaces `APPLICATION_IS_PLAIN_JAVA`): application may depend on domain, kernel and framework, but not on another slice's infra, cross-slice domain, or `deployable`. Domain/kernel purity rules are unchanged. Keep status Accepted; add a dated revision note (do not rewrite history — append the decision).
- Reference the superseded rule name so a reader tracing the old name finds the change.

### T002 — Update `hexagonal-service.md`
- The dependency-rule section must present application as an edge ring. Keep the "only the domain is pure" framing explicit.

### T003 — Update `architectural-patterns-applied.md` + regenerate diagrams
- Reflect: edge rings (application/storage/api) around pure domain/kernel; the `deployable` composition root (renamed from bootstrap) with an aggregate config `@ComponentScan`ning the layer config packages; the tactic read path (API → application `@Service` → ports → storage).
- Regenerate `c4-components.puml`/`.svg` and `class-diagram.puml`/`.svg` to match. The SVGs must be regenerated from the PlantUML sources (CI gate "PlantUML diagrams are current" compares them). Describe the regeneration command in your report.
- The 50–180 char frontmatter `description` SEO gate applies to any page you add/retitle.

### T004 — Changelog entry
- Add `docs/changelog/2026-10-21-application-edge-read-path.md` with the per-entry frontmatter style (see `docs/changelog/2026-10-21-pack-repositories-and-validation.md` as the shape). BLUF: the service gains its first real read path (a pack's tactics by identity) on one canonical model; the application layer becomes an edge ring; the pack-listing + action-context endpoints are removed (contract change). Keep under 1,200 chars of entry body (the changelog-style test gate).

### T005 — Reconcile `ddd-patterns` glossary
- If `docs/glossary/ddd-patterns.md` characterizes the application ring as pure, update it to the edge-ring framing. Minimal touch; keep the Domain-Entity-vs-Persistence-Entity headline.

## Definition of Done
- ADR-0006 + hexagonal + patterns docs read application-as-edge; diagrams regenerated and consistent with their PlantUML sources; changelog entry present; glossary consistent.
- Docs-freshness gate expectations: if you add/rename a page, note that the retrieval index + ADR inventory may need regeneration (flag it in your report for the cutover WP, or run it if tooling is present).

## Reviewer guidance (test economy — DIRECTIVE_041)
- This WP is docs-only; there are no tests to add. Flag any doc claim that contradicts the realized WP02 gate. Verify diagrams actually regenerated (not hand-edited SVGs).
