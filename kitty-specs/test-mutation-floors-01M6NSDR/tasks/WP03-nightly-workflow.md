---
work_package_id: WP03
title: The nightly reports every module and alerts on red
dependencies: [WP01]
requirement_refs: [FR-004, NFR-001, NFR-002, SC-002]
planning_base_branch: test-mutation-floors
merge_target_branch: test-mutation-floors
branch_strategy: Planning artifacts for this mission were generated on test-mutation-floors. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into test-mutation-floors unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-test-mutation-floors-01M6NSDR
base_commit: 94e65c04c321350557bcc744d3321aba4b1aaef4
created_at: '2026-11-05T12:47:51.661137+00:00'
subtasks: [T010, T011, T012, T013]
phase: Phase 2 - Nightly
agent: claude
history:
- at: '2026-11-05T12:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: .github/workflows/
create_intent:
- scripts/ci/mutation_summary.py
- scripts/ci/test_mutation_summary.py
execution_mode: code_change
owned_files:
- .github/workflows/mutation-nightly.yml
- scripts/ci/mutation_summary.py
- scripts/ci/test_mutation_summary.py
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP03 — The nightly reports every module and alerts on red

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Implement #239 design item 5 (FR-004):
- `./gradlew pitest --continue`, after syncing packs at the pin;
- an `if: always()` job summary per module or group, built from each `build/reports/pitest/floors.json` (WP01);
- every module's report uploaded;
- on a failed scheduled run, one `mutation-nightly` issue opened or commented.

It stays scheduled plus `workflow_dispatch` only (NFR-001).

## Branch Strategy

Planning base and merge target `test-mutation-floors`; depends on WP01, for the `floors.json` shape.

```bash
spec-kitty agent action implement WP03 --agent claude --mission test-mutation-floors-01M6NSDR
```

## Environment

`export JAVA_HOME=/usr/lib/jvm/temurin-25 SPEC_KITTY_NO_NAG=1 SPEC_KITTY_NO_UPGRADE_CHECK=1`; `./gradlew` only; run
`./gradlew syncPacks -PopenPacksDir=/home/user/spec-kitty-open-packs` once per worktree and pass
`-PopenPacksDir=/home/user/spec-kitty-open-packs` to every run. Never `./gradlew --stop`. Never a model-bearing identity.

## Subtasks

- **T010 Workflow.** In `.github/workflows/mutation-nightly.yml`:
  - Keep the pinned action SHAs; reuse the exact SHAs other workflows in this repo already pin, and add no new
    action.
  - Check out `spec-kitty-open-packs` at `openPacksRef` the way `service.yml` does, then `syncPacks`.
  - Run `./gradlew pitest --continue -PopenPacksDir=…`.
  - Upload `service/*/build/reports/pitest` (`if-no-files-found: error`).
  - Keep `timeout-minutes` with ≥ 25 % headroom over the WP02 measurement; ask the orchestrator for the number.
  - Update the header comment.
  - Triggers stay `schedule` plus `workflow_dispatch`, with a `force_issue` boolean input for the alert proof.
- **T011 Summary script (`scripts/ci/mutation_summary.py`).**
  - Reads every `service/*/build/reports/pitest/floors.json`.
  - Writes a Markdown table to `$GITHUB_STEP_SUMMARY`: module/group, mutants, score, strength, no coverage,
    floor, verdict, top surviving classes.
  - Tolerates a missing file (the module did not run) by showing "no report".
  - Standard library only. Python 3 tests in `scripts/ci/test_mutation_summary.py` (`python3 -m unittest`):
    check how the repo runs `scripts/docs/tests` and mirror it; if CI runs those tests from a workflow, add this
    one in the same place only if that workflow is not owned by another WP, otherwise report.
- **T012 Alert job.**
  - Runs when `needs.pitest.result == 'failure'` and (`github.event_name == 'schedule'` or
    `inputs.force_issue`).
  - Has `permissions: issues: write` only.
  - With `actions/github-script` (already pinned elsewhere in this repo; if not, `gh` CLI on the runner),
    finds the open issue labelled `mutation-nightly`: comments with the run URL and summary if there is one, else
    opens one.
  - The label must exist or be created by the step.
  - No secrets beyond `GITHUB_TOKEN`.
- **T013 Proof.**
  - `actionlint`, if available, or a YAML parse.
  - Unit tests for the summary script.
  - A dry run of the summary on real `floors.json` files from a local `pitest` run.
  - Note in the WP: the live `workflow_dispatch` with `force_issue` is the operator's after merge (#239
    acceptance).

## Definition of Done

- [ ] The workflow, script, tests and alert job are in place; no PR or `merge_group` trigger.
- [ ] The summary is rendered from real reports.
- [ ] One commit, ending with the two required lines.
