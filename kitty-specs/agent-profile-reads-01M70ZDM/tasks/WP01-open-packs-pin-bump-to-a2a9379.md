---
work_package_id: WP01
title: Open-packs pin bump to a2a9379
dependencies: []
requirement_refs:
- FR-001
- SC-003
planning_base_branch: issue-407-agent-profile-reads
merge_target_branch: issue-407-agent-profile-reads
branch_strategy: Planning artifacts for this mission were generated on issue-407-agent-profile-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-407-agent-profile-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-agent-profile-reads-01M70ZDM
base_commit: 45fe80fc10bd3272781a936a3e1872ceab22d370
created_at: '2026-11-09T20:59:32.943597+00:00'
subtasks:
- T001
- T002
- T003
- T004
history:
- timestamp: '2026-11-09T21:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: gradle.properties
create_intent: []
execution_mode: code_change
model: ''
owned_files:
- gradle.properties
- packs/open-packs.lock.yaml
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackOpenPacksRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackManifestParityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MissionTypeCorpusTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MissionStepContractCorpusTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompanionDigestTest.java
- service/packs/src/test/resources/whole-pack/**
role: implementer
tags: []
tracker_refs: []
---
# WP01: Open-packs pin bump to a2a9379

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Move the open-packs pin from `22c14b2d` to `a2a9379` (open-packs `main`). After this WP, every later WP reads a
corpus in which `java-jenny requires java-conventions` (#405, #435), the duplicate edge pairs are collapsed (#135),
and the qa profiles qualify their references to `built-in::`.

## Context

- Plan: IC-01, fold P1 to P3.
- Operator ruling: the bump stays in this PR, and merge order decides. If #437 (phase 1) merges first, this PR
  re-baselines that WP's new assertions at the bumped pin and brings any change to an asserted expectation to the
  operator.
- The open-packs clone is at `/home/user/spec-kitty-open-packs`. Fetch it, and make sure `a2a9379` is present.
- `git -C /home/user/spec-kitty-open-packs log --oneline 22c14b2d..a2a9379` lists the 50 upstream commits. Every
  count you move must be traced to one of them.

## Subtasks

### T001 — Bump the pin and re-vendor

1. Set `openPacksRef=a2a9379…` (the full SHA) in `gradle.properties`, and update the pin comment beside it.
2. Run `./gradlew --max-workers=1 syncPacks -PupdatePacksLock=true -PopenPacksDir=/home/user/spec-kitty-open-packs`.
   - It runs `git archive` on the local clone, so it needs no network.
   - It rewrites `packs/internal`, `packs/qa` and `packs/open-packs.lock.yaml` (the commit and the two tree digests).
3. `packs/internal` and `packs/qa` are gitignored (`.gitignore` lines 58-62): only `packs/open-packs.lock.yaml`
   changes in git.
   - Check that the synced trees match the lock with `./gradlew syncPacks -PcheckPacks=true`.
   - `spdd` is not vendored, and that is expected.
4. Commit this as the first commit of the PR: `build(packs): pin open-packs at a2a9379 (#435, #405)`.
   - If the tests below go red at this commit, fold T002 and T003 into the same commit, so no commit is red.

### T002 — Move the corpus counts, each traced upstream

1. Run the corpus tests at the new pin:
   ```
   ./gradlew --max-workers=1 :packs:test --tests '*WholePackOpenPacksRoundTripTest*' --tests '*PackManifestParityTest*'      --tests '*MissionTypeCorpusTest*' --tests '*MissionStepContractCorpusTest*' --tests '*CompanionDigestTest*'      -PopenPacksDir=/home/user/spec-kitty-open-packs
   ```
2. Move these expectations:
   - `WholePackOpenPacksRoundTripTest`: `Counts(…)` (around lines 295-299 and 448-450), the 315 constituents
     (expect about 310), and "26 built-in + 5 qa = 31" (around line 528).
   - `PackManifestParityTest`: 315 and 317.
   - `MissionTypeCorpusTest`: 47, 13, 17 and 32.
   - `MissionStepContractCorpusTest`: 28.
   - `CompanionDigestTest`: 10.
3. For each number you change, put a one-line trace in the commit body: the old value, the new value, and the
   upstream commit that explains it. Examples:
   - `7174cc9`: execution-lanes reclassified as a toolguide.
   - `a6e11d9`: git-flow becomes a procedure. The built-in manifest omits both reclassified constituents
     (spec-kitty/spec-kitty-open-packs#182).
   - `9f9bf06`: shared-branch-ci folded into trunk-based.
   - `b73432c`: extract-then-publish folded.
   - the two `__init__.py` deletions;
   - the new css, html and typescript styleguides.
4. **A count that you cannot trace is a finding.** Stop and report it in the WP's review notes, then escalate. Do
   not adjust it to match.

### T003 — expected-red and digests

1. Update `service/packs/src/test/resources/whole-pack/expected-red.yaml` to the new pin. Every added or removed
   entry is traced as in T002.
2. Update any companion or prompt digests that a test pins. After the bump, the software-dev prompts carry empty
   `spdd:reasons-block` slot pairs.

### T004 — Divergences and the round-trip gate

1. Run the whole-pack round trip explicitly. `verifyAll` does not include it (plan fold P3).
   ```
   ./gradlew --max-workers=1 :packs:wholePackRoundTrip -PopenPacksDir=/home/user/spec-kitty-open-packs
   ```
   It must be green, and so must `:packs:openPacksRoundTrip` if it exists.
2. Record the divergence in the commit body.
   - `.kittify/missions/software-dev/actions/implement/index.yaml:33-35` and
     `.kittify/missions/built_in_step_contracts/implement.step-contract.yaml:15-17` name paradigms that the pin
     deletes.
   - These are core's generated project files. `charter.yaml` selects none of them, so nothing in this repo changes.
   - The divergence is reported to the orchestrator (via the WP review notes) for open-packs or core follow-up.
3. Grep this repo for the four deleted paradigm ids. List every hit outside `.kittify` and outside vendored packs,
   and fix only the ones in this repo's own tests and docs.

## Definition of Done

- The pin is at `a2a9379`, and the lock and vendored packs match it.
- The `:packs` tests, `:packs:wholePackRoundTrip` and `:packs:openPacksRoundTrip` are green.
- Every changed count is traced to an upstream commit in the commit body.
- `java-jenny`'s graph at the pin has `requires → styleguide:java-conventions`. Check with
  `grep -n java-conventions /home/user/spec-kitty-open-packs/packs/built-in/agent_profile.graph.yaml`.

## Risks

- It collides with #437 WP01's files (`expected-red.yaml`, the round-trip test). Whoever merges second rebases.
- Never weaken an assertion to make it pass: only move counts you can trace.

## Reviewer guidance

- Check each count change against its upstream trace.
- Check that the lock's tree digests match `git -C <open-packs> rev-parse a2a9379:packs/internal` and the same for
  `packs/qa`.

## Branch Strategy

- Planning branch: `kitty/mission-agent-profile-reads-01M70ZDM`, which lands on `issue-407-agent-profile-reads`. The final merge target of the PR is `main`.
- Execution worktrees are allocated per computed lane from `lanes.json`. Never pick a base branch by hand.
- Implementation command: `spec-kitty agent action implement WP01 --agent claude --mission agent-profile-reads-01M70ZDM`

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
