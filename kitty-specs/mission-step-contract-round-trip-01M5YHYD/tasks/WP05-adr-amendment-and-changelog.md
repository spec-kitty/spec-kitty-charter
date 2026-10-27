---
work_package_id: WP05
title: ADR amendment and changelog
dependencies:
- WP04
requirement_refs:
- C-001
planning_base_branch: feat/mission-step-contract-round-trip
merge_target_branch: feat/mission-step-contract-round-trip
branch_strategy: Planning artifacts for this mission were generated on feat/mission-step-contract-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/mission-step-contract-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T017
- T018
- T019
phase: Phase 4 - Polish
agent: claude
history:
- at: '2026-10-27T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: docs/
create_intent:
- docs/changelog/2026-10-27-mission-step-contract-kind.md
execution_mode: planning_artifact
owned_files:
- docs/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam.md
- docs/changelog/2026-10-27-mission-step-contract-kind.md
- docs/changelog/README.md
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP05 — ADR amendment and changelog

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Record the decision this mission took (research D1) and the change it made (NFR-003).

## Branch Strategy

Planning base and merge target: `feat/mission-step-contract-round-trip`. Implement with
`spec-kitty agent action implement WP05 --agent claude --mission mission-step-contract-round-trip-01M5YHYD`.

## Subtask T017 — ADR-0037 amendment [P]

ADR-0037 is **Proposed**. Add a dated amendment, append-only, right after decision 4's paragraph. Follow the house
style (look at how ADR-0024 records its amendment of 2026-10-26).

- Head it "*Amendment of 2026-10-27 (#191)*".
- Body: a binding names its schema as it names its file token (`schema()`, default the token). The store and the
  strategy resolve a schema through the registry. A test ties every binding's file name and schema to
  `file-kinds.yaml`. `mission_step_contract` uses `mission-step-contract` and `step-contract`.
- Say that this relaxes "a schema whose name is the kind's token", and why: renaming schemas would change their
  `$id`, the gate's kind names and their kebab convention.
- Say that #190 (`agent-profile`, `agent`) uses the same hook.
- Update `updated:` in the frontmatter. Do not touch other decisions; siblings may amend the same ADR.

## Subtask T018 — Changelog [P]

Mirror `docs/changelog/2026-10-27-whole-pack-seam.md`: its frontmatter, and its impact-first style.

**What changed:**

- Mission step contracts are read into the domain and written back.
- 21 built-in step contracts are now green in the whole-pack gate; all 28 pinned files round-trip.
- `fail_open` and `optional` stay booleans, and written defaults are kept.

**Impact:**

- The source fingerprint and ETag of packs holding `*.step-contract.yaml` (built-in, qa) change once. Packs without
  step contracts keep theirs.
- No endpoint changes. The contract's fingerprint prose is not updated here (question on #191).

Add one line to `docs/changelog/README.md`, in its order.

## Subtask T019 — Consistency and docs checks

- Read `docs/glossary/charter/README.md#step-contract` and `docs/architecture/logical-model.md` ("Artifact kinds",
  "Mission types, actions and step contracts"). Edit only if something contradicts the change. Today nothing does,
  so expect no edit, and say so in the commit message.
- Run `python3 scripts/docs/check_docs.py all`, `npx markdownlint-cli2` on the changed files, and `npx cspell` on
  them. Fix findings.

## Definition of Done

- The docs checks are green.
- One commit: `docs(adr): a binding names its schema (ADR-0037 amendment, #191)`, plus the changelog. One commit or
  two, each scoped.
