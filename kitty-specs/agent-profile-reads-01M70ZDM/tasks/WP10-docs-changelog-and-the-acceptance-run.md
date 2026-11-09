---
work_package_id: WP10
title: Docs, changelog and the acceptance run
dependencies:
- WP07
- WP08
- WP09
requirement_refs:
- FR-011
- SC-001
- SC-002
- SC-005
- NFR-003
planning_base_branch: issue-407-agent-profile-reads
merge_target_branch: issue-407-agent-profile-reads
branch_strategy: Planning artifacts for this mission were generated on issue-407-agent-profile-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-407-agent-profile-reads unless the human explicitly redirects the landing branch.
subtasks:
- T049
- T050
- T051
- T052
history:
- timestamp: '2026-11-09T21:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: curator-carla
authoritative_surface: docs/
create_intent:
- docs/changelog/CHANGELOG.md
- kitty-specs/agent-profile-reads-01M70ZDM/research/usability-baseline.md
- kitty-specs/agent-profile-reads-01M70ZDM/research/usability-green.md
execution_mode: planning_artifact
model: ''
owned_files:
- docs/api/service-contract.md
- docs/changelog/CHANGELOG.md
- docs/guides/compose-packs.md
- docs/plans/engineering-notes/**
- kitty-specs/agent-profile-reads-01M70ZDM/research/usability-baseline.md
- kitty-specs/agent-profile-reads-01M70ZDM/research/usability-green.md
role: implementer
tags: []
tracker_refs: []
---
# WP10: Docs, changelog and the acceptance run

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `curator-carla`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Keep the docs living, write the changelog, and record the acceptance proof: the red baseline and the green run.

## Subtasks

### T049 — `docs/api/service-contract.md`

1. Update the R2 row (`agent_profile` served) and the R8/#102 row:
   - the governance read is lineage-walked;
   - field-merge resolution stays with #102.
2. Record the two divergences from core (research D4):
   - `suggests` entries are listed;
   - an ancestor not in force is dropped under an activation (operator ruling, which diverges from the cascade
     pass-through).
3. Mention `effective-artifact`.

### T050 — The changelog

1. Add an `[Unreleased]` entry in `docs/changelog/CHANGELOG.md`. The root file is a symlink.
2. Lead with the impact in bold: "**Agent profiles are readable through the API** (#407, #96)". Then before → after:
   - the pack-scoped read with governance;
   - the activation-scoped read of any served kind;
   - effective items linked;
   - the root links `activations`;
   - the open-packs pin `a2a9379`.
3. Union-resolve on rebase.

### T051 — The engineering note

1. If the note has merged to `main` by now, add a one-line follow-up under its gaps 1 and 3: profiles served by this
   PR, mission types by the parallel PR.
2. If it has not merged, do not add the file. Cite it at `c1c2ab56` in the changelog instead.

### T052 — The acceptance run

1. Record WP09's red baseline in `research/usability-baseline.md`: output, commit, pin.
2. Run `scripts/usability/service-usability-run.sh` on the integrated branch.
3. Record the green output in `research/usability-green.md`: hops, workarounds, p95, commit and pin.
4. Then run the full gate once:
   ```
   ./gradlew --max-workers=1 verifyAll
   ./gradlew --max-workers=1 :packs:wholePackRoundTrip -PopenPacksDir=/home/user/spec-kitty-open-packs
   ```
   Also run the docs checks: `check_docs.py` (all eight), markdownlint-cli2 and cspell.
5. Report any red honestly, with its output.

## Definition of Done

- Docs and changelog are updated.
- The baseline and green runs are recorded.
- `verifyAll`, the whole-pack round trip and the docs checks are green, or each red is reported with its output.

## Branch Strategy

- Planning branch: `kitty/mission-agent-profile-reads-01M70ZDM`, which lands on `issue-407-agent-profile-reads`. The final merge target of the PR is `main`.
- Execution worktrees are allocated per computed lane from `lanes.json`. Never pick a base branch by hand.
- Implementation command: `spec-kitty agent action implement WP10 --agent claude --mission agent-profile-reads-01M70ZDM`

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
