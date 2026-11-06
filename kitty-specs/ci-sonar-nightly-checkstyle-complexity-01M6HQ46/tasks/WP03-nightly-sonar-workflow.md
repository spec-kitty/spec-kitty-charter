---
work_package_id: WP03
title: Nightly reported-only SonarCloud workflow
dependencies: []
requirement_refs:
- FR-003
- FR-004
- FR-005
- NFR-002
- NFR-003
- NFR-004
- C-001
- C-002
- C-004
- SC-003
- SC-004
planning_base_branch: ci-sonar-nightly-checkstyle-complexity
merge_target_branch: ci-sonar-nightly-checkstyle-complexity
branch_strategy: Planning artifacts for this mission were generated on ci-sonar-nightly-checkstyle-complexity. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into ci-sonar-nightly-checkstyle-complexity unless the human explicitly redirects the landing branch.
subtasks:
- T010
- T011
- T012
- T013
agent: claude
history: []
agent_profile: implementer-ivan
authoritative_surface: .github/workflows/
create_intent:
- .github/workflows/sonar.yml
- build-logic/src/test/kotlin/SonarWorkflowGuardTest.kt
execution_mode: code_change
model: ''
owned_files:
- .github/workflows/sonar.yml
- build-logic/src/test/kotlin/SonarWorkflowGuardTest.kt
role: implementer
tags: []
tracker_refs: []
---

# WP03: Nightly reported-only SonarCloud workflow

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `implementer-ivan`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Add `.github/workflows/sonar.yml`: a nightly and manually dispatchable SonarCloud analysis of trusted
`main`. It is reported only, never required, never run on a pull request or in the merge queue, and it
skips green until the operator creates the project and the `SONAR_TOKEN` secret. A build-logic test
guards those invariants.

Implementation command:
`spec-kitty agent action implement WP03 --agent claude --mission ci-sonar-nightly-checkstyle-complexity-01M6HQ46`

## Branch Strategy

- Planning branch: `ci-sonar-nightly-checkstyle-complexity`.
- Final merge target: `ci-sonar-nightly-checkstyle-complexity`, which reaches `main` by pull request.
- This WP is independent of WP01 and WP02.

## Context

- **Spec:** FR-003, FR-004, FR-005, NFR-002, NFR-003, NFR-004, C-001, C-002, C-004, SC-003, SC-004.
- **Research:** R4, which includes the supply-chain disposition.
- **ADRs:**
  - ADR-0023: `CI gate / gate` is the one required check, and advisory work is `continue-on-error`.
  - ADR-0047: a workflow that produces no required check does not run on `merge_group`.
- **Model to follow:** core's `.github/workflows/sonar.yml` (spec-kitty/spec-kitty), read-only. Copy its
  shape, not its Python specifics.
- **Steps to reuse:** this repository's `service.yml`, job `verify`. Use its checkout, open-packs pin
  read, open-packs checkout, setup-java and setup-gradle steps, with their exact SHA pins.

### Subtask T010: Triggers, guard, token probe

```yaml
name: Sonar (nightly, reported)
# Header comment: ADR-0056; reported only; never required (ADR-0023); never on pull_request or
# merge_group (ADR-0047); skips green without SONAR_TOKEN; the operator creates the project.
on:
  schedule:
    - cron: "<pick a minute that is not 0 or 30, outside 06:00-09:00 PT>"
  workflow_dispatch:
permissions:
  contents: read
concurrency:
  group: sonar-nightly
  cancel-in-progress: true
jobs:
  scan:
    if: github.repository == 'spec-kitty/spec-kitty-charter' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-24.04
    timeout-minutes: 30
```

The first step is the token probe. It sets an `enabled` output and, when the token is absent, writes
`::notice::` plus a job-summary line saying the analysis was skipped and why, then exits 0. Every later
step is gated on `steps.sonar_token.outputs.enabled == 'true'`.

### Subtask T011: Build, scan, quality gate

1. Copy the checkout steps from `service.yml`:
   - `fetch-depth: 0`, which SonarCloud needs for new-code detection and blame;
   - the open-packs pin read and checkout;
   - Java 25 and Gradle.
2. Run `./gradlew syncPacks -PopenPacksDir=…` and then
   `./gradlew verifyAll -PopenPacksDir=… -PrequireSymlinks=true`, both `continue-on-error: true`, with
   an `id`.
3. Scan, gated on the build having produced the coverage XML:
   `uses: SonarSource/sonarqube-scan-action@22918119ff8e1ca75a623e15c8296b6ea4fbe28f # v8.2.1`,
   `continue-on-error: true`, `env: SONAR_TOKEN`. Pass the arguments with `args:`:
   - `-Dsonar.organization=spec-kitty`
   - `-Dsonar.projectKey=spec-kitty_spec-kitty-charter` (an assumption the operator confirms; say so
     in a comment)
   - `-Dsonar.sources=service/kernel/src/main/java,service/packs/src/main/java,service/profiles/src/main/java,service/missions/src/main/java,service/deployable/src/main/java`
   - `-Dsonar.tests=` the matching `src/test/java` directories
   - `-Dsonar.java.binaries=` the matching `build/classes/java/main`
   - `-Dsonar.java.test.binaries=` the matching `build/classes/java/test`
   - `-Dsonar.java.libraries=$HOME/.gradle/caches/modules-2/files-2.1/**/*.jar`
   - `-Dsonar.coverage.jacoco.xmlReportPaths=service/charter-reports/build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml`
   - `-Dsonar.java.source=25`
   - `-Dsonar.scm.revision=${{ github.sha }}`

   `charter-api` is generated and has no `src/main/java`, so listing only the five hand-written modules
   excludes it. `contracts` is the contract's own checker, not the service; leave it out, as the
   coverage aggregation does.
4. Quality gate:
   `uses: SonarSource/sonarqube-quality-gate-action@7a5fffe8e523c40e0c740b6bc2712ab503e52efa # v1.2.1`,
   `continue-on-error: true`, `timeout-minutes: 5`. Then an `if: always()` step writes the build, scan
   and gate outcomes to `$GITHUB_STEP_SUMMARY`.

### Subtask T012: Guard test

`build-logic/src/test/kotlin/SonarWorkflowGuardTest.kt`. Locate the repository root from the test's
working directory: build-logic's parent, as `OpenPacksCheckoutTest` does if applicable. Read the YAML as
text; build-logic has no YAML library, and none may be added (C-001). The test asserts:
- the `on:` block of `sonar.yml` has `schedule` and `workflow_dispatch`, and no `pull_request`,
  `pull_request_target`, `merge_group` or `push`;
- `ci.yml` never references `sonar.yml`, and the gate job's `needs:` list does not name a sonar job;
- every `uses:` in `sonar.yml` is pinned to a 40-hex SHA followed by a `# vX` comment (NFR-004);
- `SONAR_TOKEN` appears only in the probe's and the scan's `env`;
- **non-vacuity:** the same checks, applied to a planted text that adds `pull_request:` or an unpinned
  `uses: foo/bar@v1`, fail.

### Subtask T013: Validate

1. `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/sonar.yml'))"` parses.
   actionlint is not installed; do not install it.
2. `./gradlew -p build-logic test` passes.
3. In the review notes, record the skip-green path, step by step: no secret, the probe outputs
   `enabled=false`, every later step is skipped, and the job succeeds. Note that the analysis path is
   verified by a manual dispatch after the operator adds the token, and that this is a follow-up, not
   part of this WP.

## Definition of Done

- `sonar.yml` exists, matches T010 and T011, has every action SHA-pinned, and has no PR or merge-queue
  trigger.
- The guard test passes and fails on the planted variants.
- No change to `ci.yml`, `service.yml` or any required check.

## Risks and reviewer guidance

- **The reviewer checks** that no step builds or runs code from a pull request; the triggers make this
  impossible.
- `continue-on-error` must sit on every step after the probe, so a failure never turns the night red.
- **The project key is an assumption.** The PR body asks the operator to confirm it.
