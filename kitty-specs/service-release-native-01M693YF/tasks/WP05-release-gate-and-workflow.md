---
work_package_id: WP05
title: Release gate, assets, rehearsal, release workflow and procedure
dependencies:
- WP04
requirement_refs:
- FR-011
- FR-012
- FR-013
- FR-014
- NFR-004
- C-001
- C-005
- SC-003
- SC-004
planning_base_branch: issue-241-71-service-release-native
merge_target_branch: issue-241-71-service-release-native
branch_strategy: Planning artifacts for this mission were generated on issue-241-71-service-release-native. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-241-71-service-release-native unless the human explicitly redirects the landing branch.
subtasks:
- T020
- T021
- T022
- T023
- T024
- T025
history: []
agent_profile: implementer-ivan
authoritative_surface: scripts/release/
create_intent:
- build-logic/src/main/kotlin/ServiceReleaseGate.kt
- build-logic/src/test/kotlin/ServiceReleaseGateTest.kt
- scripts/release/service-release-assets.sh
- scripts/release/rehearse-service-release.sh
- .github/workflows/service-release.yml
execution_mode: code_change
model: ''
owned_files:
- build-logic/src/main/kotlin/ServiceReleaseGate.kt
- build-logic/src/test/kotlin/ServiceReleaseGateTest.kt
- build.gradle.kts
- scripts/release/service-release-assets.sh
- scripts/release/rehearse-service-release.sh
- .github/workflows/service-release.yml
- docs/guides/service-build.md
role: implementer
tags: []
tracker_refs: []
---

# WP05: Release gate, assets, rehearsal, release workflow and procedure

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `implementer-ivan`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

- Publish a `service-v*` release exactly as ADR-0046 says, or refuse before anything is published (FR-011,
  FR-012).
- Rehearse every refusal on every change, without publishing (FR-013).
- Document the procedure (FR-014).
- No tag is pushed (C-001).

Implementation command:
`spec-kitty agent action implement WP05 --agent claude --mission service-release-native-01M693YF`

## Context

- The reference designs are `contract-release.yml` (this repository) and events' `service-release.yml`
  (`research.md` R2).
- The asset contract is `contracts/service-release-assets.md`.
- The CI artifacts come from WP04: `native-<target>` from `service-native.yml`, and `service-jar` from
  `service.yml`. Both are called with `cache-disabled: true` on a release.

### Subtask T020: Red: `ServiceReleaseGateTest`

These are JUnit tests in build-logic, Kotlin or Java, matching the existing build-logic test style. The
function is
`ServiceReleaseGate.check(tag, buildVersion, contractVersion, contractTagMerged): Verdict`.

| Case | Expected |
|---|---|
| `service-v0.2.0`, build `0.2.0`, contract `0.1.0`, merged | final release, `0.2.0` |
| `service-v0.2.0-rc.1`, build `0.2.0-rc.1`, contract `0.2.0-SNAPSHOT`, not merged | pre-release; the notes flag "contract unreleased" |
| `service-v1.0`, `service-v1.0.0-rc-1`, `service-v1.0.0+m`, `v1.0.0`, `service-v0.0.0` | refused: format or placeholder |
| tag `0.2.0` against build `0.2.0-SNAPSHOT` or `0.3.0` | refused: the tag does not match the build version |
| final release whose contract is `-SNAPSHOT` or not merged | refused: the contract is unreleased |

Each refusal message names the cause.

### Subtask T021: `ServiceReleaseGate` and the root task

- `build-logic/src/main/kotlin/ServiceReleaseGate.kt`: pure logic, with the regex quoted from core's
  `_TAG_RE`.
- Root `build.gradle.kts`: `tasks.register("checkServiceReleaseTag")`.
  - It reads `-PreleaseTag`, `version`, and the contract `info.version` (the same regex as
    `service/packs/build.gradle.kts`).
  - It finds out whether the contract tag is merged with `providers.exec { git merge-base --is-ancestor
    contract-charter-v<cv> HEAD }`, ignoring the exit value and reading it.
  - It runs the gate. It fails with the refusal, or writes
    `build/service-release/gate.properties` (`version`, `prerelease`, `contract`, `contractReleased`).
  - It must be configuration-cache compatible.

### Subtask T022: `scripts/release/service-release-assets.sh <ver> <in-dir> <out-dir>`

- It takes `<in-dir>` holding `native-*/spec-kitty-charter-service[.exe]` and `service-jar/*.jar`, as
  downloaded with `merge-multiple: false`.
- It copies them to the ADR names, writes one `.sha256` per asset, and writes `SHA256SUMS`
  (`LC_ALL=C sort`, bare names, made with `cd out && sha256sum`).
- It compares the output listing with the expected set using `diff -u`.
- It refuses a missing or extra file.

### Subtask T023: `scripts/release/rehearse-service-release.sh`

- In a throwaway clone with no remote, it runs `./gradlew -q checkServiceReleaseTag -PreleaseTag=…` for each
  refusal case in SC-004, and expects each one refused.
  - The tag-moved check and the main-ancestry check are workflow-level; they are covered in T024 and named
    in the script's output as not rehearsed here.
- A synthetic `rc.1` commit (version bumped in the clone) is expected to pass.
- The assets script runs on placeholder files and must produce exactly 11 files that verify with
  `sha256sum --check --strict SHA256SUMS`.
- `service.yml`'s rehearsal job runs it, via WP04's `rehearse-*.sh` loop.

### Subtask T024: `.github/workflows/service-release.yml`

- **Triggers:** `push: tags: ["service-v*"]`, and `workflow_dispatch` with a `tag` input.
- **Permissions:** `contents: read` at the top. Concurrency group `service-release-${{ github.ref }}`, never
  cancelled.
- **`gate`:**
  - checkout with `fetch-depth: 0` and `persist-credentials: false`;
  - format check;
  - main ancestry, on push only;
  - Temurin 25 and `checkServiceReleaseTag`;
  - outputs: tag, version, prerelease, contract, contractReleased, commit.
- **`jvm`:** `uses: ./.github/workflows/service.yml` with `cache-disabled: true`. `native`: `uses:
  ./.github/workflows/service-native.yml` with `cache-disabled: true`. Both need `gate`.
- **`assemble`:** read-only. Download the artifacts, run the assets script, and upload `service-release`,
  kept 7 days.
- **`publish`:**
  - Runs on push only, with `contents: write`, `id-token: write` and `attestations: write`.
  - `sha256sum --check --strict SHA256SUMS`.
  - Re-check that the tag still names the built commit (peel annotated tags).
  - `actions/attest-build-provenance@4d101475d8b20a2381f78447822ac1eab6504dd8 # v4.2.2` with
    `subject-checksums`.
  - `gh release create "$TAG" --verify-tag --latest=false [--prerelease]` with the notes file and only the
    listed files.
  - The notes name: the contract version, and whether it is released; the verify commands; and that the
    binaries are unsigned.

### Subtask T025: The procedure in `docs/guides/service-build.md`

Add a section "Release a service version", beside "Release a contract version", covering:

- bump `version` in a PR;
- for a final release, the contract tag first;
- merge, then tag the merge commit on `main` (operator only);
- what the workflow does and refuses;
- the rehearsal;
- how to verify assets (`sha256sum`, `gh attestation verify`).

Link ADR-0046. Run the docs checks.

## Definition of Done

- The gate tests are green under `verifyAll`. `rehearse-service-release.sh` passes locally.
- The release workflow lints clean. A `workflow_dispatch` rehearsal on the PR branch produces the 11 files.
  Record the run.
- The procedure is documented, and the docs checks are green.

## Risks

- **`gh release create` with many files on Windows-built artifacts:** the publish job runs on ubuntu, so
  this is not an issue.
- **`workflow_dispatch` runs only workflows on the default branch** until merged. Rehearse it after merge,
  or rely on the rehearsal script in CI. Say which in the handover.

## Reviewer Guidance

- Only `publish` can write. No tag-triggered step checks out other repositories.
- The asset list is exact. The checksum format matches core's parser (`[0-9a-f]{64} [ *]name`).
