---
work_package_id: WP04
title: CI workflows
dependencies:
- WP01
- WP02
- WP03
requirement_refs:
- FR-004
- FR-005
- FR-009
- FR-012
- NFR-002
- C-003
- C-007
- SC-003
- SC-006
planning_base_branch: ccr-9654c444-x7y94q
merge_target_branch: ccr-9654c444-x7y94q
branch_strategy: Planning artifacts for this mission were generated on ccr-9654c444-x7y94q. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into ccr-9654c444-x7y94q unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-charter-contract-and-build-retro-01M57QHJ
base_commit: 312e3c4f8fcdb30fe35eb0b3588f4d4e232a337e
created_at: '2026-10-18T21:01:03.492792+00:00'
subtasks:
- T022
- T023
- T024
- T025
- T026
phase: Phase 4 - CI
history:
- at: '2026-10-18T15:42:56Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: implementer-ivan
authoritative_surface: .github/workflows/
create_intent:
- .github/workflows/mutation-nightly.yml
execution_mode: code_change
model: ''
owned_files:
- .github/workflows/service.yml
- .github/workflows/docs-site.yml
- .github/workflows/mutation-nightly.yml
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP04 – CI workflows

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `implementer-ivan`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ IMPORTANT: Review Feedback

- Check `review_ref` in the event log (`spec-kitty agent tasks status`); address every item and record it with `spec-kitty agent tasks add-history WP04 --note "..."`.

## Review Feedback

*[Filled from the status event log when this WP returns from review.]*

---

## Objectives & Success Criteria

- `service.yml` (the main build workflow) runs `verifyAll` on Temurin 25 (the charter's own
  contract checks included), and uploads three artifacts: the aggregated Javadoc,
  the aggregated coverage report and the contract bundle. It is also callable
  (`workflow_call`).
- `docs-site.yml` publishes the Javadoc produced by `service.yml` inside the docsite.
- `mutation-nightly.yml` runs PIT on a schedule and on manual dispatch, never on pull requests.
- Every action is pinned to a real release commit SHA with a version comment; actionlint passes.

The first green run (FR-005) happens on the pull request at closeout; this WP makes it possible.

## Context & Constraints

- Read first: `spec.md` FR-004, FR-005, FR-009, FR-012, NFR-002, C-003, C-007; `plan.md` IC-04, IC-05; `research.md` §3–§5 (workflow facts, pins, the three options for getting Javadoc into Pages and why `workflow_call` won).
- Existing pins to reuse: `actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09` (v5.1.0), `actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961` (v5.7.0), `gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6` (v6.4.0), `actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` (v7.0.1), `actions/setup-node`, `actions/upload-pages-artifact`, `actions/deploy-pages` as in `docs-site.yml`. **No `download-artifact` pin exists yet**: look up the real release commit SHA from the action's GitHub releases (never invent one) and record where you read it.
- CI never builds with `-Pcharter.javaVersion=21` (C-003).
- Never commit a mirror init script (C-007).
- Least privilege: `contents: read` by default; `pages`/`id-token` only on the deploy job.

## Branch Strategy

- **Strategy**: set by finalize-tasks
- **Planning base branch**: `ccr-9654c444-x7y94q`
- **Merge target branch**: `ccr-9654c444-x7y94q`

```bash
spec-kitty agent action implement WP04 --agent claude --mission charter-contract-and-build-retro-01M57QHJ
```

## Subtasks & Detailed Guidance

### Subtask T022 – service.yml

- **Steps**: `runs-on: ubuntu-24.04` (like the other workflows); add `workflow_call:` to `on:`; keep the pull-request and push path filters (add `scripts/contracts/**`, `config/**`); after `verifyAll`, upload `service/charter-reports/build/docs/javadoc` as artifact `javadoc` and the aggregated JaCoCo report as `coverage` (`if: always()` for coverage so a red run still shows it). Note the workflow's `concurrency` also applies when called; keep a group that does not cancel the docs caller unexpectedly (for example include `github.workflow`).

### Subtask T023 – Contract bundle artifact

- **Steps**: the charter's contract checks run inside `verifyAll` (WP01's `:contracts` project), so no separate job and no core checkout are needed (operator, 2026-10-18: core's contract tools do not run here). After `verifyAll`, upload `contracts/build/contract/charter-openapi.yaml` and its `.sha256` as artifact `contract-bundle`. Check out with `fetch-depth: 0` so `checkContractCompatibility` can see `contract-charter-v*` tags. Cache the downloaded tool binaries (`contracts/build/tools/`) keyed on the pins.

### Subtask T024 – docs-site.yml

- **Steps**: add a job `service` that calls `./.github/workflows/service.yml` (`uses:` with `secrets: inherit` only if needed; it needs none); the `build` job `needs: service`, downloads artifact `javadoc` into `site/public/javadoc` **before** `npm run build`, then runs the existing validate, build and `check:built-links` steps. Widen the triggers to the service build paths so a Javadoc change republishes the site. Keep `deploy` on `main` only. Check the reusable-workflow permission model: the caller's `permissions` bound the callee.

### Subtask T025 – mutation-nightly.yml [P]

- **Steps**: `on: schedule` (one nightly cron, off the hour) and `workflow_dispatch`; no `pull_request`; checkout plus the open-packs checkout at `OPEN_PACKS_REF` if `pitest` needs the test inputs (it runs unit tests; the round-trip test lives in adapter-out-yaml, which PIT does not target, so it may not); Temurin 25; `./gradlew pitest`; upload the PIT HTML/XML reports as artifact `mutation-report`. Timeout generous but bounded (for example 60 minutes).

### Subtask T026 – Validate the workflows

- **Steps**: run `actionlint` (download a pinned release binary with its checksum, or `go run` a pinned version; do not commit it) on all three workflows; fix every finding. Grep that every `uses:` line is `owner/repo@<40-hex>  # vX.Y.Z`. Confirm no workflow passes `-Pcharter.javaVersion=21`. Record the results with `add-history`.

## Test Strategy

actionlint plus a dry reading of each job's commands against the scripts and tasks they
call (`:contracts:*`, `:charter-reports:*`, `pitest`). The live proof
is the pull request's first run at closeout.

## Risks & Mitigations

- Reusable-workflow artifacts: the caller can download artifacts uploaded by a called workflow in the same run; confirm with the `download-artifact` version's docs.
- Rate limits on Maven Central in CI: `setup-gradle` caching reduces downloads; never add a mirror.

## Review Guidance

- All pins real and commented; least-privilege permissions; no Java 21 for the service build.
- `mutation-nightly.yml` has no pull-request trigger.

## Progress & Status

`spec-kitty agent tasks add-history WP04 --note "..."`, `spec-kitty agent tasks mark-status T022 --status done`, `spec-kitty agent tasks status`.
