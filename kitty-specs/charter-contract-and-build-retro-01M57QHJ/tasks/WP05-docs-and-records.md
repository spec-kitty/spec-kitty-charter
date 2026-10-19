---
work_package_id: WP05
title: Docs, plans and records
dependencies:
- WP01
- WP02
- WP03
- WP04
requirement_refs:
- FR-001
- FR-002
- FR-013
- FR-014
- FR-015
- NFR-004
- C-006
- SC-005
planning_base_branch: ccr-9654c444-x7y94q
merge_target_branch: ccr-9654c444-x7y94q
branch_strategy: Planning artifacts for this mission were generated on ccr-9654c444-x7y94q. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into ccr-9654c444-x7y94q unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-charter-contract-and-build-retro-01M57QHJ
base_commit: d7b13a2bba6f8b9d7d48a8328bdd289ec019df86
created_at: '2026-10-18T21:16:57.321669+00:00'
subtasks:
- T027
- T028
- T029
- T030
- T031
phase: Phase 5 - Docs
history:
- at: '2026-10-18T15:42:56Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: curator-carla
authoritative_surface: docs/
create_intent:
- docs/changelog/2026-10-18-retroactive-contract-and-build-mission.md
execution_mode: code_change
model: ''
owned_files:
- README.md
- docs/guides/**
- docs/plans/**
- docs/architecture/**
- docs/api/service-contract.md
- docs/changelog/**
- docs/_meta/spelling-words.txt
- .kittify/charter/charter.md
role: curator
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP05 – Docs, plans and records

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `curator-carla`
- **Role**: `curator`
- **Agent/tool**: `claude`

---

## ⚠️ IMPORTANT: Review Feedback

- Check `review_ref` in the event log (`spec-kitty agent tasks status`); address every item and record it with `spec-kitty agent tasks add-history WP05 --note "..."`.

## Review Feedback

*[Filled from the status event log when this WP returns from review.]*

---

## Objectives & Success Criteria

Every doc tells the truth about the build, the rulings and the scenario homes after WP01–WP04,
and the mission is recorded. Docs follow doc-kitty (ADR-0003): US spelling, the terms guard
(no "feature"), frontmatter rules, linked rather than restated rules.

Done when all eight `check_docs.py` checks, markdownlint-cli2 0.18.1 and cspell 9.2.0 report
0 errors, and each item below is in place.

## Context & Constraints

- Read first: `spec.md` FR-001, FR-002, FR-013–FR-015, NFR-004; `research.md` §8 (the drift
  list with exact sections), §10 (rulings, dispositions, D5 terminology), §11, §12.
- **Roadmap honesty.** Steps 1 and 2 read "done" only once the charter's own contract checks (FR-004) and the
  first Java 25 CI run (FR-005) are green. This WP writes them as "done pending the first CI
  run on the pull request"; the closeout flips the wording after CI is green.
- Canonical terms (D5, S6): "charter contract" (all of `contracts/charter/`), "contract
  scenarios" (`contracts/charter/scenarios/`), "acceptance scenarios" (`tests/scenarios/charter/`);
  `PACK_LEVEL_MISSING`, now spelled `pack_level_missing`, is a non-blocking **diagnostic**.
- Upstream records to cite: spec-kitty-open-packs #46 (jqwik advisory) and #47 (strict pack
  rule), core #6402 (enum-case `built-in`) and #6403 (glossary wording), the comment on
  charter #12.
- Do not edit the imported glossary pages (`docs/glossary/**`): core owns them (#6403).

## Branch Strategy

- **Strategy**: set by finalize-tasks
- **Planning base branch**: `ccr-9654c444-x7y94q`
- **Merge target branch**: `ccr-9654c444-x7y94q`

```bash
spec-kitty agent action implement WP05 --agent claude --mission charter-contract-and-build-retro-01M57QHJ
```

## Subtasks & Detailed Guidance

### Subtask T027 – Guides and README

- **Steps**: `docs/guides/service-build.md` (prerequisites; the task table with the new gates, Javadoc, the aggregate reports and `pitest` as a nightly task; "Change the contract" gains the contract checks (`./gradlew :contracts:check`) and the rule-plus-fixture convention for the ruleset; "Change a dependency" covers tool plugins; the suppressions register); `docs/guides/contributing.md` "Quality gates" (replace the "when the Gradle build lands" placeholder with the real commands); `docs/guides/docsite.md` "What CI runs" (the Javadoc step); `README.md` Status and Building sections (contract `1.0.0-SNAPSHOT`, the gates).

### Subtask T028 – Open questions and roadmap

- **Steps**: `docs/plans/open-questions.md`: add dated rows for the three rulings (pack-level strictness ratified, action-context rules provisional to #9, scenario directory), the BDD layout with **all three** scenario homes and when each moves or binds (FR-013), the vendored-pack defects with owners (#47, #36, #37), the OpenRewrite and jqwik decisions. `docs/plans/roadmap.md`: Progress for steps 1 and 2 per the honesty rule above; the action-context paragraph points at the provisional marker and #9.

### Subtask T029 – Architecture and API docs

- **Steps**: `docs/architecture/behavior-and-scenarios.md` "Where they live" (the three homes, the Cucumber suite in `charter-service`, the `@contract`/`@provisional` tags); `contract-and-dtos.md` (version `1.0.0-SNAPSHOT`, core's checks, the charter's own contract checks (`contracts/lint/` ruleset and the `:contracts` tasks; core's tools do not run here), snake_case codes); the four docs that call `PACK_LEVEL_MISSING` a refusal (`resolution-model.md` ~53–56, `descriptors.md` ~63, `pack-registration-distribution.md` ~148, `api/service-contract.md` ~50–52): replace the restated rule with a link to `DiagnosticCode` (S6); `docs/api/service-contract.md`: point at the real contract files (closes the last open criterion of #7). Remove the stale "no code exists yet" claims (found in WP03's review): `docs/README.md` (~61), `docs/_meta/sections.yaml` (~42), and the banners on `docs/api/service-contract.md` and `docs/api/compatibility.md`; scope each to what is still only designed.

### Subtask T030 – Charter companion, changelog, spelling

- **Steps**: `.kittify/charter/charter.md` "Gates before merge" (the style check is now wired) and "Known gaps" (link #47 for the glossary stem); `docs/changelog/2026-10-18-retroactive-contract-and-build-mission.md` (what changed, the rulings, the upstream issues; changelog frontmatter rules); add only the words cspell needs to `docs/_meta/spelling-words.txt`.

### Subtask T031 – Docs checks green

- **Steps**: `for c in frontmatter related freshness links terms adr changelog orphans; do python scripts/docs/check_docs.py $c; done`, `npx --yes markdownlint-cli2@0.18.1`, `npx --yes cspell@9.2.0 --no-progress`; fix every finding; record counts with `add-history`.

## Risks & Mitigations

- Restating rules again: link to the contract or the ADR instead.
- Claiming CI results before they exist: use the "pending first CI run" wording.

## Review Guidance

- Every surface in research §8 marked in-scope-now is updated; nothing claims a CI result that has not happened.
- No edit under `docs/glossary/`.

## Progress & Status

`spec-kitty agent tasks add-history WP05 --note "..."`, `spec-kitty agent tasks mark-status T027 --status done`, `spec-kitty agent tasks status`.
