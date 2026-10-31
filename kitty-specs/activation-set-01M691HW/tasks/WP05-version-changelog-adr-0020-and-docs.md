---
work_package_id: WP05
title: Version, CHANGELOG, ADR-0020 and docs
dependencies:
- WP04
requirement_refs:
- FR-017
- FR-018
- FR-019
- NFR-005
- SC-005
planning_base_branch: issue-125-activation-set
merge_target_branch: issue-125-activation-set
branch_strategy: Planning artifacts for this mission were generated on issue-125-activation-set. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-125-activation-set unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-activation-set-01M691HW
base_commit: b8ac3efcbd5f2b3a708d08bf527bf48b3af2ec71
created_at: '2026-10-31T14:31:07.424674+00:00'
subtasks:
- T027
- T028
- T029
- T030
- T031
- T032
phase: Phase 3 - Record
agent: claude
history:
- at: '2026-10-31T13:45:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent: []
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/CHANGELOG.md
- docs/adr/0020-offering-and-activations.md
- docs/adr/README.md
- docs/architecture/offering-and-activation.md
- docs/api/service-contract.md
- docs/development/http-status-codes.md
- docs/development/rest-resource-conventions.md
- .github/workflows/contract-release.yml
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP05 – Version, CHANGELOG, ADR-0020 and docs

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave
according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Record the activation set and the rulings (FR-017, FR-018, FR-019, NFR-005, SC-005). Charter
style: short sentences, no em dashes in new prose, cite ADRs by number.

## Branch Strategy

Planning base and merge target: `issue-125-activation-set`. Lanes from `lanes.json`.

## Subtasks

### T027 — CHANGELOG

Under `## 0.1.0-SNAPSHOT` (no new version heading, `info.version` unchanged; operator ruling
2026-10-31): in `### Breaking` an entry "The activation set is declared and reserved (issue #125,
ADR-0020)" listing each break and what core's seam has to change (new enum values in
`CharterRefusalCode`; `Problem` gains `requiredBy`/`violations` on every generated `Problem`;
`error-responses-are-problems` admits 204; `containerDefaultToNull` for generated clients if
relevant). Under `### Added`: the schemas, the 13 operations, the JSON Schema file (the charter
CHANGELOG versions it), the lint rules, the scenarios (every scenario change gets a line). State
that the header of the CHANGELOG now also covers `contracts/offering/schemas/`.

### T028 — ADR-0020 amendment

In place, an "Amendment 2026-10-31" section plus inline notes at D4, D6, D7, D8, D10: every
ruling of `research.md` (1 to 14, with 12 as changed), D-3/D-6/D-11/D-14/D-15, the
`governanceFingerprint` on `Activation`, "references" becomes "mirrors" in D4, D10's "vendored
`Problem` is unchanged" corrected, the controller placement and the future activation slice
(#140), kind/enum growth rule (S19). Answer open questions 1 and 2. Update `updated:`. ADR index
row in `docs/adr/README.md` notes the amendment.

### T029 — Docs

`docs/architecture/offering-and-activation.md`: the document example matches the schema (no
`appliedPresets`, `packs` as objects, `governanceFingerprint`, `_links`); the reserved status
and the 501 pins; functional scenarios still proposed. `docs/api/service-contract.md`,
`docs/development/http-status-codes.md` (201/204/412/428/501, "retry after re-read"),
`rest-resource-conventions.md` where it mentions the activation set. Keep `related:` slugs real.

### T030 — Release workflow

`.github/workflows/contract-release.yml` adds `contracts/offering/schemas/ActivationConfig.schema.json`
to the release assets (S27). Keep the workflow's style and checks.

### T031 — Draft build issue

`kitty-specs/activation-set-01M691HW/research/draft-issue-build-activation-writes.md`: the issue
that builds the nine write operations (storage port, use cases, D-15 build notes); the
orchestrator files it. (This file is under `kitty-specs/`; it is a planning note, written by the
orchestrating agent outside this WP's owned files if the finalizer refuses it here.)

### T032 — Docs checks

`python3 scripts/docs/check_docs.py all`, `npx markdownlint-cli2` and `npx cspell` on the
changed files; zero errors.

## Definition of Done

Release gate green inside `:contracts:check`; docs checks green; ADR amendment complete.

`spec-kitty agent action implement WP05 --agent claude --mission activation-set-01M691HW`
