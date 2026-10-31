---
work_package_id: WP03
title: 'Records: ADR-0045 and the documents that state the −1 reading'
dependencies:
- WP02
requirement_refs:
- FR-010
- C-001
planning_base_branch: issue-235-236-drg-round-trip
merge_target_branch: issue-235-236-drg-round-trip
branch_strategy: Planning artifacts for this mission were generated on issue-235-236-drg-round-trip. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-235-236-drg-round-trip unless the human explicitly redirects the landing branch.
subtasks:
- T013
- T014
- T015
phase: Phase 3 - Records
history:
- at: '2026-10-31T07:10:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/adr/0045-tension-beside-the-relationships-and-nodes-keep-every-declared-field.md
- docs/changelog/2026-10-31-drg-tension-and-node-fields-round-trip.md
execution_mode: code_change
model: ''
owned_files:
- docs/adr/0045-tension-beside-the-relationships-and-nodes-keep-every-declared-field.md
- docs/adr/README.md
- docs/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship.md
- docs/architecture/logical-model.md
- docs/conformance/divergences.yaml
- docs/conformance/divergence-register.md
- docs/changelog/2026-10-31-drg-tension-and-node-fields-round-trip.md
- docs/changelog/README.md
- contracts/charter/schemas/Relation.yaml
- contracts/charter/CHANGELOG.md
role: documentarian
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP03 – Records: ADR-0045 and the documents that state the −1 reading

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave
according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `documentarian`
- **Agent/tool**: `claude`

---

## Objective

Make the records match the behaviour WP01 and WP02 deliver:

- an ADR decides the domain representation ADR-0028 D5 left open;
- no record still says a tension edge is read as `impacts` with strength −1.

## Branch Strategy

Planning base and merge target are both `issue-235-236-drg-round-trip`. The mission is
`single_branch`, and completed work merges back into `issue-235-236-drg-round-trip`.

Implement with:
`spec-kitty agent action implement WP03 --agent claude --mission drg-tension-and-node-fields-01M68A0T`

## Subtasks

### T013 — ADR-0045

- **Number check**: re-check `main` and the open PRs before writing. 0044 is taken on the #194 PR-B
  branch.
- **Status**: Proposed, pending operator acceptance. It cites decision DM-01M68A2TYC1VMDPCJ8B76602YH.
- **Decisions**, from research.md D1–D4 and squad S1, S3, S5, S6 and S16:
  1. Tension is a kernel value beside the relationships. It is not a `RelationType`, and it is not in
     pair identity.
  2. `in_tension_with` is a storage spelling.
  3. Nodes keep every declared field; each writer refuses the fields its shape cannot hold.
  4. A fragment write exists.
  5. The registered shard refuses a tension.
  6. Deferred: "at most one tension per pair" and canonical orientation are pack consistency, reported
     by the gate and fixed in open-packs.
  7. Merge keeps the first node whole.
- **Follow the house format**: ADR-0043's frontmatter. `related:` entries must be real page slugs
  (`adr/README` is not one).
- **ADR-0028**: add a dated "Representation decided by ADR-0045" note under Status. D5 is answered for
  the domain and the pack files, and the contract is unchanged.
- **Index**: add a row to `docs/adr/README.md`. Update ADR-0028's row status.

### T014 — The documents that state −1

- **`docs/conformance/divergences.yaml` DIV-018** and its row in `divergence-register.md`: the service
  holds `in_tension_with` as tension (ADR-0028, ADR-0045) and writes it back as written. Keep the
  divergence: core has neither relation.
- **`docs/architecture/logical-model.md`**:
  - L304 (`impacts` current state): drop the −1 sentence and say tension is held as tension;
  - L305 (`reconciles_tension`): current state is now "joined by a tension";
  - L315-317: note the domain representation (ADR-0045).
- **`contracts/charter/schemas/Relation.yaml`**:
  - description text only, with no enum value (C-001): the service holds `in_tension_with` as the
    tension of ADR-0028 and does not answer it as a relationship; it never reads it as `impacts`;
  - log this as an open review item in the PR.
- **`contracts/charter/CHANGELOG.md`** under the current unreleased section: a "Changed" line for the
  description correction, saying no wire change. Check that the CHANGELOG's structure allows it; the
  contract checks run in `verifyAll`.
- **Docs changelog**: `docs/changelog/2026-10-31-drg-tension-and-node-fields-round-trip.md`, in the
  house format of the neighbouring entries, plus an index row.

### T015 — Follow-up draft

Write `kitty-specs/drg-tension-and-node-fields-01M68A0T/research/followup-load-fixture-runner.md`: a
draft issue to restore a runner for the 63 orphaned `load-fixtures` cases, whose runner was deleted in
`2bb3f762`. The orchestrator files it.

## Definition of Done

- [ ] `python3 scripts/docs/check_docs.py all` is green; markdownlint and cspell are clean on the
      changed files.
- [ ] `./gradlew :charter-api:check`, or `verifyAll`, is green: the contract checks and the
      breaking-change check.
- [ ] `grep -rn "strength -1\|strength −1\|impacts -1\|-1 (sabotages)" docs contracts service/*/src/main`
      leaves no claim that tension is read as impacts. Dated changelog entries are history and stay.

## Reviewer guidance

- No path, schema, enum value or wire shape changed under `contracts/charter/`.
- The ADR states its deferrals honestly.
