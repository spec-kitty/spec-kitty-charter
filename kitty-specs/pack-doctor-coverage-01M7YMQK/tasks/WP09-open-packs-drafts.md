---
work_package_id: WP09
title: Open-packs drafts and closure evidence
dependencies:
- WP08
requirement_refs:
- C-006
- SC-002
planning_base_branch: feat/pack-doctor-coverage
merge_target_branch: feat/pack-doctor-coverage
branch_strategy: Planning artifacts for this mission were generated on feat/pack-doctor-coverage. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-doctor-coverage unless the human explicitly redirects the landing branch.
subtasks:
- T035
- T036
phase: Phase 4 - Records
history:
- at: '2026-11-21T13:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: planner-priti
authoritative_surface: kitty-specs/pack-doctor-coverage-01M7YMQK/research/
create_intent:
- kitty-specs/pack-doctor-coverage-01M7YMQK/research/open-packs-drafts.md
- kitty-specs/pack-doctor-coverage-01M7YMQK/research/closure-evidence.md
execution_mode: planning_artifact
owned_files:
- kitty-specs/pack-doctor-coverage-01M7YMQK/research/open-packs-drafts.md
- kitty-specs/pack-doctor-coverage-01M7YMQK/research/closure-evidence.md
role: planner
tags: []
tracker_refs: []
---

# Work Package Prompt: WP09 - Open-packs drafts and closure evidence

## ⚡ Do This First: Load Agent Profile

Load the `planner-priti` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`AGENTS.md`, `.kittify/charter/charter.md`, ADR-0082, the mission `spec.md` and `plan.md` (the decisions this WP
names), and `research/squad-dispositions.md`.

## Objective

Write what the orchestrator files elsewhere, and what the PR body cites (C-006; the issue rules).

## Branch Strategy

- Planning branch and merge target: `feat/pack-doctor-coverage` (PR to `main`). Mission topic branch:
  `kitty/mission-pack-doctor-coverage-01M7YMQK`.
- The workspace comes from `lanes.json`. Implement with
  `spec-kitty agent action implement WP09 --agent claude --mission pack-doctor-coverage-01M7YMQK`.
- Commits: no model names and no Co-Authored-By trailer (repository rule). Never push to `main`.

## Subtasks

### T035 — open-packs#TBD drafts (`research/open-packs-drafts.md`)
One draft per issue, each with a title, why, what changes, a "done when", and the `open-packs#TBD-<slug>`
placeholder:

- (a) `TBD-pack-doctor-repin` (widened 2026-11-22, dispatcher relay; ruling R-w: no `service-v*` release until this
  mission lands, so open-packs aligns once, against that release). The complete alignment specification, verified
  read-only in open-packs, each item with its open-packs file:line and the exact new value:
  - **Jar name and path**: `scripts/ci/charter_pack_check_jar.sh:35-36` downloads
    `releases/download/<tag>/pack-check-<v>.jar` -> `pack-doctor-<v>.jar`; `:55-59` builds
    `./gradlew :pack-check:packCheckJar` and globs `service/pack-check/build/pack-check/pack-check-*.jar` for a `main`
    pin -> `:pack-doctor:packDoctorJar` and `service/pack-doctor/build/pack-doctor/pack-doctor-*.jar`.
  - **Lock field**: `charter.lock.yaml` (`pack_check_sha256`, comments at 9/23/27, value at 34), `charter_pin.py`
    (`write_lock` 171-188, `PACK_CHECK_JAR` 333) and the workflows' `charter_pack_check_sha256` input -> name the new
    field (state that the name is open-packs' call, recommend `pack_doctor_sha256`) and every file that carries it.
  - **CLI**: the command word comes first (`validate`, `remediate`); the old flat form exits 2 with the usage (no
    alias). Flags: `--pack`, `--config-root`, `--round-trip`, `--full` (delegations, references, enhancement targets;
    check value `REFERENCES`, R-s/R-u), `--report <file> --format yaml|xml` (R-i).
  - **Exit codes and `--round-trip`**: copy the contract from the shipped `Arguments`/`PackDoctorMain` usage text and
    tests (0 clean, 1 findings, 2 usage), and what `--round-trip` adds (one problem per cause; `manifest_stale` from
    the gate, written-stale from the round trip; `EXCLUDED_WRITTEN` retired for `NOT_HELD_WRITTEN`).
  - **Report keys**: the stdout problem JSON keys and the report file's top-level keys, from the shipped
    `PackDoctorReport.yaml` schema (cross-reference (e)).
  - **New refusals open-packs content may hit**: `manifest_stale` (c), `preset_item_not_held` /
    `preset_item_undeclared` (b), the anti-pattern refusals (f).
  - **open-packs #343 (charter:main-red)**: two invalid-fixture expectations already lag charter main (PR #626):
    `case-variant-readme` now answers `case_variant`; `malformed-pack-version` now exits 1 with
    `pack_version_invalid`. The alignment PR fixes them too.
  - **Timing**: open-packs' nightly against charter `main` goes red when this mission merges, before any release;
    say so, and that the alignment PR should land against the first release after this mission (R-w).
- (b) `TBD-preset-item-refusals`: presets whose items the own closure lacks are now refused
  (`preset_item_not_held`, `preset_item_undeclared`). List the open-packs presets that break, if you can check them
  read-only (`/home/user/spec-kitty/spec-kitty-open-packs`).
- (c) `TBD-manifest-stale`: stale hashes are now `manifest_stale`. Run `pack-doctor remediate`, and replace
  `charter_pin.py rederive --derive-manifest` with it (#632 item 2).
- (d) `TBD-retire-python-rules`: which `validate_pack_schemas.py` rules (`cross_pack_violations` around lines
  1460-1518, `derived_manifest`) retire with this release, and which stay as house rules (re-declaration across
  levels, unheld bare endpoints, slots outside an activation, and the `--full` remainder of ruling R-s: the
  `specializes_from` cycle stays an open-packs rule until #636 lands it).
- (e) `TBD-report-format`: the report file and the new stdout keys, for any parser.
- (f) `TBD-anti-pattern-files`: carry the draft already written in `research/open-packs-TBD-anti-pattern-files.md`
  (plan D12-D14, ruling R-r) into this file, and update it with what WP10, WP11 and WP12 shipped: the 60 files with
  inline blocks and the 13 file-less nodes it lists are refused at the repin until curated.

### T036 — Closure evidence (`research/closure-evidence.md`)
- Per issue, list the PR file or scenario that shows the behavior:
  - #614, #629, #630 and #154: `Closes` (#154 per acceptance criterion: the closed schema, the dropped fields, the
    store binding, the four refusal tests with their codes, the canonical example packs);
  - #632: `Refs` (R-s), naming the remainder: item 3's `specializes_from` cycle, relation, edge-index and
    own-closure-walk checks, for #636 slices 2-4 and 7;
  - #52 item 3 and #535: closed with evidence (R-g);
  - #549 and #142: the epics tick (#142 for #154);
  - #158: kept in view; say what `manifest_stale` delivers toward it.
- This is the source for the PR body's issue section.

## Definition of Done
- [ ] Six drafts, each with a placeholder.
- [ ] An evidence row for every issue the mission touched.
