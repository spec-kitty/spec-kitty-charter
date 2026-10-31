---
work_package_id: WP04
title: Smoke check, the jar job and the native CI matrix
dependencies:
- WP03
requirement_refs:
- FR-007
- FR-009
- FR-010
- NFR-001
- NFR-002
- C-005
- SC-002
planning_base_branch: issue-241-71-service-release-native
merge_target_branch: issue-241-71-service-release-native
branch_strategy: Planning artifacts for this mission were generated on issue-241-71-service-release-native. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-241-71-service-release-native unless the human explicitly redirects the landing branch.
subtasks:
- T014
- T015
- T016
- T017
- T018
- T019
history: []
agent_profile: implementer-ivan
authoritative_surface: .github/workflows/
create_intent:
- scripts/release/smoke-service.sh
- scripts/release/smoke/**
- .github/workflows/service-native.yml
execution_mode: code_change
model: ''
owned_files:
- scripts/release/smoke-service.sh
- scripts/release/smoke/**
- .github/workflows/service.yml
- .github/workflows/service-native.yml
- .github/workflows/ci.yml
role: implementer
tags: []
tracker_refs: []
---

# WP04: Smoke check, the jar job and the native CI matrix

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `implementer-ivan`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

- Prove that every built artifact starts and answers, using a black-box smoke check that is the same for the
  jar and for the native binaries (FR-007).
- Run the native check on the four targets in CI, and the jar check on every service-group change (FR-010,
  ruling D6, plan R-1).
- Keep to the time budgets in NFR-001 and NFR-002.

Implementation command:
`spec-kitty agent action implement WP04 --agent claude --mission service-release-native-01M693YF`

## Context

- `GET /api/v1/info` needs `Authorization: Bearer <token>`. The token comes from `CHARTER_WORKTREE_TOKEN`,
  at least 32 characters, and must never be passed as an argument.
- The default port is 0, so the smoke check passes `--server.port`. Packs are read from
  `--configRootLocation <dir>`.
- The release workflow (WP05) consumes the artifacts uploaded here: `native-<target>` and `service-jar`.
- **Action pinning:** every action is SHA-pinned, using the SHAs already in this repository's workflows and,
  for `graalvm/setup-graalvm`, events' SHA `27b360dbb857524eaf0b4c4cef364d38e5b4f173 # v1.6.7`. Every
  checkout sets `persist-credentials: false`.

### Subtask T014: The smoke fixture

Create `scripts/release/smoke/config-root/packs/smoke/` with a minimal valid pack: `pack.yaml` (`pack_id`,
`pack_version`, `name: smoke`, `pack_level`) and one tactic. Copy the shape from
`service/deployable/src/test/resources/config-root/packs/built-in/`. Add a README line saying what it is.

### Subtask T015: `scripts/release/smoke-service.sh`

**Usage:** `smoke-service.sh <artifact> <service-version> <contract-version> [max-start-seconds]`

It runs under bash with curl on Linux, macOS and Windows Git Bash, so it uses no `jq`, no `ss` and no GNU-only
flags.

1. Copy the fixture root to a temporary directory. Pick a port (try 18080 to 18099). Generate a 40-character
   token.
2. **Start 1:**
   - Arguments: `--server.port=<p> --configRootLocation <root> --charter.guide-fetch.allowed-hosts=example.org`.
   - The allowed host makes the start prove the DNS guard (N2, FR-009).
   - Start it as `java -jar` for a `.jar`, else run the executable directly.
3. Poll `/api/v1/info` until the time limit. Report the measured seconds and fail past the limit (NFR-001).
4. Assert:
   - 200;
   - the body contains `"serviceVersion":"<sv>"` and `"contractVersion":"<cv>"`;
   - without the token, 401 with a JSON problem body (N5);
   - `GET /api/v1/packs/smoke/tactics` gives 200 with the fixture tactic id (N3, N4);
   - `POST /api/v1/packs/smoke/directives` with an invalid body gives 422 and `validation_failed` (N6).
5. Stop the process: a trap kills it on every exit, Windows included (`kill`; the process is a direct
   child).
6. **Start 2:**
   - `SPRING_PROFILES_ACTIVE=container`, `CHARTER_CONTAINER_SECRETS_DIR=<tmp>/secrets` (created),
     `--server.port=<p>`, `--configRootLocation=<root>`, and no allowed hosts.
   - It must exit non-zero within the time limit, and its output must name `charter.container.allowed-hosts`
     (FR-008).
7. On any failure, print the service log.

### Subtask T016: Run it against the jar locally (red-first evidence)

- Build with `./gradlew :deployable:bootJar`, then run the script.
- Then show that it fails in each of these cases:
  - a wrong expected version;
  - an artifact that exits at once (a `false` script, or a jar given a bad argument);
  - the container case broken by setting allowed hosts, which makes start 2 succeed.
- Record the runs.

### Subtask T017: `service.yml`

1. A `workflow_call` input `cache-disabled` (boolean, default false), passed to `setup-gradle`'s
   `cache-disabled`.
2. A new `jar` job:
   - Temurin 25 and `./gradlew :deployable:bootJar`;
   - read `version` from `gradle.properties` and the contract's `info.version`;
   - run the smoke script with a 60-second limit;
   - upload `service-jar` with the jar.
3. The `release-rehearsal` job runs every `scripts/release/rehearse-*.sh` in turn, so WP05's rehearsal joins
   without another edit.
4. Add `.github/workflows/service-native.yml` and `.github/workflows/service-release.yml` to the push paths.

### Subtask T018: `service-native.yml`

- **Triggers:**
  - `workflow_call`, with input `cache-disabled`;
  - `workflow_dispatch`;
  - `push` to `main` on these paths: `service/**`, `build-logic/**`, `gradle/**`, `*.gradle.kts`,
    `gradle.properties`, `scripts/release/**`, `.github/workflows/service-native.yml`.
- **Permissions:** `contents: read`.
- **Matrix** (`fail-fast: false`, `timeout-minutes: 45`):

  | Runner | Target | Suffix |
  |---|---|---|
  | `ubuntu-24.04` | linux-x64 | |
  | `ubuntu-24.04-arm` | linux-arm64 | |
  | `macos-15` | macos-arm64 | |
  | `windows-2025` | windows-x64 | `.exe` |

- **Steps:**
  1. checkout;
  2. setup-graalvm (`distribution: graalvm-community`, `java-version: "25"`);
  3. setup-gradle;
  4. `./gradlew :deployable:nativeCompile` with `shell: bash`;
  5. the smoke check on `service/deployable/build/native/nativeCompile/spec-kitty-charter-service<exe>`
     with a 5-second limit;
  6. record the binary size and start time in `$GITHUB_STEP_SUMMARY`;
  7. upload `native-<target>`.

### Subtask T019: `ci.yml`

- Add a `native` output to `changes`, using `native_re` for `service/`, `build-logic/`, `gradle/`, the root
  `*.gradle.kts`, `gradle.properties`, `gradlew`, `scripts/release/`, and
  `.github/workflows/(service-native|ci)\.yml`.
- Add a `native` job: `uses: ./.github/workflows/service-native.yml`.
- `gate` adds `NATIVE` to its loop.
- Extend `service_re` with `service-native` and `service-release` in the workflow alternation.

## Definition of Done

- The smoke check passes against the jar locally, and every negative case fails.
- The workflows lint clean: `actionlint` if available, otherwise a YAML parse plus review.
- CI on the PR shows four green native jobs and a green jar job. Record the start times.

## Risks

- **Windows:** process kill, paths given to the JVM (`cygpath -w` when passing a path to a native Windows
  binary), and CRLF. Pass Windows paths for `--configRootLocation`.
- **macOS first-run latency** may break 5 s. Record the actual time; if it is reproducibly above 5 s, raise
  it with evidence rather than loosening silently.
- **The DNS guard is not installed natively:** start 1 fails with the guard's message. Escalate (D4b).

## Reviewer Guidance

- The smoke check proves what it says. No `|| true`, and every assertion is an outcome.
- Pins, permissions and `persist-credentials: false` are on every job.
