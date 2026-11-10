---
work_package_id: WP09
title: Usability script and red baseline
dependencies:
- WP01
requirement_refs:
- FR-011
- SC-002
- NFR-003
planning_base_branch: issue-407-agent-profile-reads
merge_target_branch: issue-407-agent-profile-reads
branch_strategy: Planning artifacts for this mission were generated on issue-407-agent-profile-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-407-agent-profile-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-agent-profile-reads-01M70ZDM
base_commit: befc60442c1c2609e77f05c4a9d7ca68dd847744
created_at: '2026-11-10T08:38:56.219831+00:00'
subtasks:
- T045
- T046
- T047
- T048
history:
- timestamp: '2026-11-09T21:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: scripts/usability/
create_intent:
- scripts/usability/README.md
- scripts/usability/charter-to-activation.py
- scripts/usability/service-usability-run.sh
execution_mode: code_change
model: ''
owned_files:
- scripts/usability/**
role: implementer
tags: []
tracker_refs: []
---
# WP09: Usability script and red baseline

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

A committed, rerunnable client that proves the acceptance. It follows links from `GET /api/v1/` to `java-jenny`'s
body and governance, pack-scoped and under an activation built from this repo's `charter.yaml`. It exits 0 only if
every hop resolves.

Run it red on the WP01 base: that is the SC-002 baseline. The green run is WP10's.

## Context

- Plan: IC-07, fold U1 to U3. `quickstart.md`.
- The engineering note is at `c1c2ab56`, on branch `origin/docs/service-usability-run-2026-11-09`:
  `git show c1c2ab56:docs/plans/engineering-notes/service-usability-run-2026-11-09.md`.
- Service config (`docs/guides/service-build.md`, `service/deployable/src/main/resources/application.yaml`):
  - `--configRootLocation`;
  - `charter.registration.enabled`;
  - `charter.pack-sources.filesystem.enabled` and `.root`;
  - `server.port` (0 = free);
  - the worktree token (find how `WorktreeGuard` and `WorktreeToken` read it, and how a client sends it).
- Tell the orchestrator the script path when it is pushed. The mission-types session extends it.

## Subtasks

### T045 — `scripts/usability/service-usability-run.sh` (bash, `curl`, `jq`)

1. Options: `--open-packs <dir>`, `--profile <id>` (default `java-jenny`), `--keep`, `--jar <path>` (default: build
   `:deployable:bootJar`).
2. Start the service:
   - a temporary config root, and a token;
   - registration and the filesystem source on, with the source root at a temp dir;
   - port 0. Read the port from the log, or pass a fixed free port.
3. Provision packs **unmodified** (fold U2):
   - Copy `packs/built-in` from the open-packs checkout into `<config-root>/packs/`, then verify the copy against
     `git -C <open-packs> rev-parse HEAD:packs/built-in`, computed the way `scripts/packs/sync_packs.py` does.
   - Register `qa` and `internal` through `POST /packs` (filesystem source).
   - For each refusal (#451 README/PROVENANCE, #452 project pack), print the refusal and the named workaround, if
     any. The summary lists every workaround applied. Workarounds never change built-in.
4. Register the project pack (`.kittify/charter-packs`) if that is possible without edits. Otherwise, report it as a
   named workaround or skip it, and say so.

### T046 — The activation document

`scripts/usability/charter-to-activation.py` (run with `python3 -I`):
- Translate `.kittify/charter/charter.yaml` into an activation document, plus `agent_profile: java-jenny`.
- Map the slug spellings to service ids. Gap 6 of the note: `001-project-intent-directive` → `PROJECT_001`. Keep the
  table explicit.

### T047 — The link walk

Follow only `_links`, filling templated variables a link declares (`packId`, `kind`).

1. **Pack-scoped:** root `artifacts` (`packId=built-in`, `kind=agent_profile`) → the `java-jenny` item's `self` →
   her body and `governance` → each entry's `artifact`.
2. **Under the activation:** root `activations` (`POST` the document) → the answer's `self` → the activation's
   effective `artifacts` → the profile item's `effective-artifact` → body and governance → each entry's
   `effective-artifact` and `artifact`.
3. Assert that `java-conventions` is a `requires` entry (SC-003).
4. Time 20 profile reads on each path, and print p95. Fail if p95 ≥ 500 ms (NFR-003).
5. The summary has:
   - hops followed and failed;
   - workarounds;
   - p95;
   - the main commit and the open-packs pin.

   Exit non-zero on any failure.

### T048 — Red baseline (SC-002)

1. Run it on the WP01 base (no reads yet). Expect it to stop at the first profile hop with 404 `kind_not_found`.
2. Hand the output (last 30 lines) and the commit plus pin it ran at to WP10 through the review notes. WP10 records
   them under the mission's `research/`.
3. Add `scripts/usability/README.md`: purpose, options, prerequisites, and what each exit code means.

## Definition of Done

- The script runs end to end, and exits non-zero today for the right reason.
- It is shellcheck-clean if `shellcheck` is available. Otherwise use `bash -n` and say so.
- The README is written.
- The path is reported.

## Branch Strategy

- Planning branch: `kitty/mission-agent-profile-reads-01M70ZDM`, which lands on `issue-407-agent-profile-reads`. The final merge target of the PR is `main`.
- Execution worktrees are allocated per computed lane from `lanes.json`. Never pick a base branch by hand.
- Implementation command: `spec-kitty agent action implement WP09 --agent claude --mission agent-profile-reads-01M70ZDM`

## Rules that hold for every WP of this mission

- **Charter wins.** Read `.kittify/charter/charter.md`. Both are gates:
  - no divergence from the charter's own model, contract and packs;
  - no fake green. Never skip, disable or weaken a test to get green.
- **Test first.** Write the failing test or scenario first, see it red, then make it green. Record the red run in the
  WP's commit message body (one line: the test and its failure).
- **Gradle:**
  - JDK: `JAVA_HOME=/usr/lib/jvm/temurin-25`.
  - Always `--max-workers=1`, in the foreground, one run at a time, never `--stop`.
  - On a Maven Central 429, wait and rerun.
- **Before you hand off:**
  - Run the module's tests, plus `./gradlew --max-workers=1 :deployable:test --tests '*HexagonalArchitectureTest*'`
    when you touch a ring.
  - Run `./gradlew --max-workers=1 spotlessApply` and the module's `check`.
  - The final WP runs the full `verifyAll`.
- **Commits:**
  - Each commit is green on its own: the repo lands commits individually.
  - Commits are SSH-signed, which is automatic here.
  - End each message with:
    ```
    Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
    Claude-Session: https://claude.ai/code/session_01E5wJUBWJn7yEXdCeUEJGmP
    ```
  - No model identifiers in code, comments or commit text.
- **Contract changes** follow ADR-0022 (0.x line) and ADR-0047:
  - one fragment per `###` block in `contracts/charter/changes/`;
  - never edit `contracts/charter/CHANGELOG.md`;
  - every 2xx response gets an example with `_links`;
  - every new 2xx is exercised by an `@contract` scenario (`OperationCoverageTest`).
- **Javadoc:** every new package has a `package-info.java` annotated `@NullMarked`. Javadoc runs with `-Werror`.
- **Design references:** `plan.md` (the IC sections, then the "Fold of the plan-point squad", which wins where they
  differ), `data-model.md` and `research.md` in `kitty-specs/agent-profile-reads-01M70ZDM/`.
