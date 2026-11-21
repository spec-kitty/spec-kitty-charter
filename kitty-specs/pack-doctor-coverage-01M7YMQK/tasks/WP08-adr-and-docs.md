---
work_package_id: WP08
title: ADR-0083, amendments and docs
dependencies:
- WP03
- WP04
- WP06
- WP07
requirement_refs:
- FR-012
- C-005
planning_base_branch: feat/pack-doctor-coverage
merge_target_branch: feat/pack-doctor-coverage
branch_strategy: Planning artifacts for this mission were generated on feat/pack-doctor-coverage (Mission topic branch kitty/mission-pack-doctor-coverage-01M7YMQK). During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-doctor-coverage, which is PR'd to main.
subtasks:
- T032
- T033
- T034
phase: Phase 4 - Records
agent: claude
history:
- at: '2026-11-21T13:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: architect-alphonso
authoritative_surface: docs/
create_intent:
- docs/adr/0083-one-answer-on-every-surface-preset-items-stale-manifests-and-pack-doctor.md
execution_mode: code_change
owned_files:
- docs/adr/0083-*.md
- docs/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check.md
- docs/adr/0042-*.md
- docs/architecture/composition-and-activation-rules.md
- docs/architecture/architectural-patterns-applied.md
- docs/adr/README.md
- docs/changelog/**
- docs/_meta/spelling-words.txt
role: architect
tags: []
tracker_refs: []
---

# Work Package Prompt: WP08 - ADR-0083, amendments and docs

## ⚡ Do This First: Load Agent Profile

Load the `architect-alphonso` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`AGENTS.md`, `.kittify/charter/charter.md`, ADR-0082, the mission `spec.md` and `plan.md` (the decisions this WP
names), and `research/squad-dispositions.md`.

## Objective

Record the mission's decisions and keep living docs true (spec FR-012; plan D10; DIRECTIVE_003 and 037).

## Branch Strategy

- Planning branch and merge target: `feat/pack-doctor-coverage` (PR to `main`). Mission topic branch:
  `kitty/mission-pack-doctor-coverage-01M7YMQK`.
- The workspace comes from `lanes.json`. Implement with
  `spec-kitty agent action implement WP08 --agent claude --mission pack-doctor-coverage-01M7YMQK`.
- Commits: no model names and no Co-Authored-By trailer (repository rule). Never push to `main`.

## Subtasks

### T032 — ADR-0083
- Check the number against `main` and the open PRs when you write it (charter: an ADR number is chosen when it is
  written). If 0083 is taken, use the next free one and update every reference.
- Title: what the mission decides. One answer on every surface: preset items, stale manifests and the pack-doctor
  CLI.
- Decisions:
  - Quote each operator ruling R-a to R-n verbatim from `kitty-specs/pack-doctor-coverage-01M7YMQK/decisions/`, and
    say what it decides.
  - The own closure (R-k); declared vs held (R-l); the codes (R-m); `manifest_stale` on incoming bytes (R-b).
  - pack-doctor's commands, options and report (R-c, R-e, R-h, R-i, R-n); `remediate` (R-j).
  - `--full`: the operator's ruling on plan D5, quoted.
  - #535 and #52 item 3 are not the charter's to judge (R-g), with the evidence (identity is kind and id, never the
    file name; edge endpoints are URNs).
  - The XUnit form as a later option (R-n).
  - The own-closure rules that join `PackRulesets` in #636 slice 3.
- Relation to ADR-0082: this follows it (rules in the factory, no new walk), and supersedes nothing in it.
- Alternatives considered: the options the operator declined in each batch.

### T033 — Amendments
- ADR-0075:
  - decision 6: what the CLI composes (`--full` per the ruling);
  - decision 7: CLOSURE's preset phase gains the case-only check and the preset items;
  - decision 9: pack-doctor, `validate` and `remediate`, `--report`/`--format`.

  Write each as a dated amendment section, in the form the ADR already uses.
- ADR-0042: an amendment to its 2026-11-12 amendment. Uploaded hashes are verified (`manifest_stale`), and writes
  still stamp.
- `composition-and-activation-rules.md`: A10 and C31. The floor is still reported on reads, but a write is refused
  when a preset item fails the own-closure rule. Name the codes. G9, if it cites A10.
- `architectural-patterns-applied.md`: the scope rows "pack-check set check" and "Doctor (planned)" become pack-doctor
  `validate` (a determined selection; default and `--full`) and the active-set doctor (still planned). Add the
  preset-item phase to the "Own closure" row. Then remove this file and the ADR bodies from WP02's
  `NoPackCheckSurfaceTest` allowlist where they now pass. That test is owned by WP02; a justified edit.

### T034 — Guides and changelog
- `docs/guides/check-packs.md` (text renamed by WP02): document `validate`, `--full` (per the ruling), `--report`,
  `--format` and `remediate`, with examples.
- `docs/changelog`: the entry in the repo's changelog convention (read `docs/changelog/` for the current file and
  shape).
- Run `check_docs.py` (all checks), markdownlint-cli2 and cspell. Add any new words to
  `docs/_meta/spelling-words.txt`.

## Definition of Done
- [ ] The ADR carries every ruling quoted, and the amendments are dated.
- [ ] The docs checks are green.
- [ ] No live doc says pack-check.
