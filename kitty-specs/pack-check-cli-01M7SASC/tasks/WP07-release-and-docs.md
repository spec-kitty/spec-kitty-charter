---
work_package_id: WP07
title: Release wiring, the ADRs and the guides
dependencies:
- WP06
requirement_refs:
- FR-010
- FR-011
- SC-003
- NFR-002
planning_base_branch: feat/478-pack-check
merge_target_branch: feat/478-pack-check
branch_strategy: Planning artifacts for this mission were generated on feat/478-pack-check. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/478-pack-check unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-check-cli-01M7SASC
base_commit: 00ceb8bff65106f87932430f5422e98a95dc70db
created_at: '2026-11-19T22:16:50.425889+00:00'
subtasks:
- T027
- T028
- T029
- T030
- T031
phase: Phase 4 - Release
history:
- at: '2026-11-19T08:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: implementer-ivan
authoritative_surface: scripts/release/
create_intent:
- docs/guides/check-packs.md
execution_mode: code_change
owned_files:
- .github/workflows/service.yml
- .github/workflows/service-release.yml
- scripts/release/service-release-assets.sh
- scripts/release/rehearse-service-release.sh
- docs/adr/0046-service-release-line-and-native-packaging.md
- docs/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check.md
- docs/guides/service-build.md
- docs/guides/check-packs.md
- docs/guides/README.md
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP07 — Release wiring, the ADRs and the guides

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `implementer-ivan`
- **Role**: `implementer`

## Objective

Put the jar on the `service-v*` release (ADR-0046, as amended) and record the mission in the ADRs and guides.

## Context

- Spec: FR-010, FR-011, SC-003, NFR-002. research.md R7 and squad J2 (core's vendor ignores unmapped `SHA256SUMS`
  names: spec-kitty `src/specify_cli/services/vendor.py` at `4890242`, `map_assets`), J11 and S13.
- The workflows and scripts:
  - `.github/workflows/service.yml`: the `jar` job builds `:deployable:bootJar`, smoke-tests it and uploads
    `service-jar`.
  - `.github/workflows/service-release.yml`:
    - `assemble` downloads `native-*` and `service-jar`, then runs `scripts/release/service-release-assets.sh`;
    - `publish` attests `SHA256SUMS` and attaches every listed file plus its `.sha256`.
  - `scripts/release/service-release-assets.sh` checks the exact ADR-0046 set: 11 files, 5 sums.
  - `scripts/release/rehearse-service-release.sh` rehearses the gate and the asset set in CI.
- `docs/guides/service-build.md`, "Release a service version" (about lines 551 to 640).

## Branch Strategy

Planning base `feat/478-pack-check`, merge target `feat/478-pack-check`; worktrees per lane from `lanes.json`. Run
`spec-kitty agent action implement WP07 --agent <name> --mission pack-check-cli-01M7SASC`.

## Subtasks

### T027 — `service.yml`

In the `jar` job, after the service jar:

1. `./gradlew :pack-check:packCheckJar`.
2. Run it with `java -jar` over `service/packs/src/testFixtures/resources/example-packs` as five `--pack` arguments
   with levels, plus `--round-trip`. Time it (`/usr/bin/time -f %e` or `date` arithmetic) and fail when it exceeds 20 s
   (NFR-002). Write the time to the step summary.
3. Upload `service/pack-check/build/pack-check/pack-check-<version>.jar` as artifact `pack-check-jar`
   (`if-no-files-found: error`, retention 7 days, the same pinned action SHA as the other uploads).

If WP06 left the byte-identical build check to CI, add it here: build twice with `--rerun-tasks` into two copies and
`cmp` them.

### T028 — Release assembly

- **`service-release.yml` `assemble`:** download `pack-check-jar` into `build-outputs/pack-check-jar`.
- **`scripts/release/service-release-assets.sh`:**
  - `take "$in/pack-check-jar/pack-check-$version.jar" "pack-check-$version.jar"`;
  - the expected list adds `pack-check-$version.jar` and its `.sha256`: 13 files;
  - `SHA256SUMS` lists 6 assets;
  - update the header comment and the error message ("the ADR-0046 asset set").
- **Release notes** (`publish`): name both jars. `spec-kitty-charter-service-<ver>.jar` is the service fallback on
  any other host with Java 25; `pack-check-<ver>.jar` is the pack checker (Java 25). Add the `gh attestation verify`
  line for `pack-check-<ver>.jar`.

### T029 — Rehearsal

`scripts/release/rehearse-service-release.sh`:

- plant `pack-check-jar/pack-check-0.2.0-rc.1.jar`;
- expect 13 files and 6 lines in `SHA256SUMS`;
- add a refusal case where the pack-check jar is missing (the assets script must fail and name it);
- update the header comment.

Run the script locally; it needs no remote.

### T030 — ADRs

- **ADR-0046:** an amendment section dated 2026-11-19 (#478). Decision 4's table gains `pack-check-<ver>.jar` (the
  pack-check CLI, ADR-0075 decision 9; Java 25) with its `.sha256` and its `SHA256SUMS` line. It is additive under the
  ADR's own rule.
  - Record the evidence that core's pin is unaffected: core's vendor maps names exactly and ignores the rest
    (`vendor.py` `map_assets` at spec-kitty `4890242`); its extras are only the `*.tar.gz` stems.
  - Update `updated:` and `decision_status`.
- **ADR-0075:** a landing note for #478:
  - the CLI shipped;
  - `PackCheckMain` may name `FileSystemPackInspector` and `PackRoundTrip`, with a control;
  - the facts ride on the one read (no second derivation);
  - `PackRulesets.closure(presetIdsOf)`;
  - the report schema and its standalone exemption;
  - D1 and D6 (links refused on every surface, #597);
  - D5 (the set as given);
  - Spring is off the jar, proven by `jarTest`.

  Update `updated:` and `decision_status`.

### T031 — Guides

- **`docs/guides/service-build.md`:** the release asset list gains the pack-check jar, and "To check a download"
  gains its attestation line.
- **New `docs/guides/check-packs.md`** for pack authors, in the guides' front-matter style (copy the keys of
  `compose-packs.md`, with audience `pack-author`). It covers:
  - download and verify;
  - the commands (`--config-root`, `--pack dir[:level]`, `--round-trip`) and the exit codes;
  - the report and its warnings;
  - D5: check a pack together with the packs it depends on;
  - links are refused, but a pack root may be a link; hard links and Windows junctions are not judged;
  - what only the service checks: the graph walk, impact, mutability, fallback profile and required floor (C-006).

  Link it from `docs/guides/README.md`.
- Run the docs checks: `check_docs.py` (all eight), `markdownlint-cli2` and `cspell`, as `docs-cleanliness.yml` does.

## Definition of Done

- `scripts/release/rehearse-service-release.sh` passes. `service.yml` runs and times the jar.
- The docs checks pass and `verifyAll` is green.
- A manual `workflow_dispatch` of `service-release.yml` with a candidate tag is left to the release runbook (WP08), not
  run here.

## Risks and reviewer guidance

- **The asset set is exact.** Check that the assets script still refuses any extra file.
- **The release notes** must not claim the pack-check jar runs natively.
