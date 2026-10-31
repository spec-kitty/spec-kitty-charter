# Mission Specification: Service release line and native packaging

**Mission Branch**: `issue-241-71-service-release-native`
**Created**: 2026-10-31
**Status**: Draft
**Input**: Issues #241 (ADR: service versioning and release tags, separate from the contract) and #71 (cross-OS
packaging of the charter service). The operator's rulings of 2026-10-31 apply: spec-kitty/spec-kitty#6117,
comment 6544221340, and decisions D1 to D7 recorded on this mission. Research: [research.md](research.md).

## Intent Summary

- **Primary actor:** the release operator, who cuts a release of the charter service by pushing a tag.
- **Secondary actor:** Spec Kitty core, which pins one release and fetches the right asset for its host on
  first use.
- **Trigger:** a `service-v<version>` tag pushed at a reviewed commit on `main`. A rehearsal is a manual run
  that publishes nothing.
- **Outcome:** a release with these assets:
  - a native executable for each of linux-x64, linux-arm64, macos-arm64 and windows-x64;
  - the JVM jar, as the fallback on other hosts;
  - a checksum file core can verify;
  - a provenance attestation.

  Every asset is named as core's pin model expects. Each native executable has been shown to start and
  answer the service's info operation before it is released.
- **Invariants:**
  - Nothing is published unless the tag names one version, matches the build's version, and sits on `main`.
  - A partial asset set is never published.
  - The service release line never moves the contract release line, and the contract line never moves it.
- **Boundaries:** core's pin and fetch code, the launcher (#70), the container image (#234) and the companion
  wheel are out of scope. No tag is pushed by this mission.

## User Scenarios & Testing *(mandatory)*

### User Story 1: The operator reads one record of how the service is versioned and released (Priority: P1)

The release operator and core's maintainers need one accepted decision record. It says:

- what a service tag looks like;
- what a major, minor or patch release of the service means;
- how the service version relates to the contract version it implements;
- which assets a release carries and how they are checked.

**Why this priority:** core codes against the asset names, and every later step builds on the record (D5:
it lands first).

**Independent Test:** read the record. It answers each of these with no reference to an unrecorded decision:

- the tag form;
- the version rules;
- the contract relation;
- the asset table;
- the checksum format;
- the attestation;
- the native-plus-jar rule.

**Acceptance Scenarios:**

1. **Given** the ADR, **When** a maintainer looks up the asset for windows-x64 of release `0.2.0`, **Then**
   they find exactly `spec-kitty-charter-service-0.2.0-windows-x64.exe`.
2. **Given** the ADR, **When** a maintainer asks whether `service-v0.2.0-rc-1` is a valid tag, **Then** the
   record says no (pre-release identifiers use only letters, digits and dots), consistent with core's tag
   rule.
3. **Given** the plan documents, **When** a reader looks up open question 5, **Then** it is marked decided
   (native per OS, with the jar as fallback) and links the ADR.

### User Story 2: Every change shows that each native executable starts and answers (Priority: P1)

A contributor changes the service or the build. CI builds the jar and checks it. For changes that can affect
the native build, CI also builds a native executable for each of the four targets and checks it. Each check
starts the executable with a worktree token and an empty configuration root, and asks it for its info. The
check passes only when the answer names the expected service and contract versions.

**Why this priority:** a missing native reachability hint shows up only in a native build, so a release must
never be the first native build.

**Independent Test:** run the native workflow on a pull request. Each of the four target jobs shows the
executable starting and answering the info operation, and the job fails when it does not.

**Acceptance Scenarios:**

1. **Given** a pull request that touches the service, **When** CI runs, **Then** four native jobs each
   build an executable and pass a start-and-answer check.
2. **Given** an executable that fails to start (for example, a missing resource), **When** the check runs,
   **Then** the job fails and shows the service's own error output.
3. **Given** a native executable started under the `container` profile without its required settings,
   **When** it starts, **Then** it refuses to start, exactly as the JVM jar does.
4. **Given** a native executable with a guide-fetch allowlist set, **When** it starts, **Then** the
   DNS-rebinding guard is installed; if the guard is not installed, it refuses to start rather than run
   unguarded.
5. **Given** a pack in the configuration root, **When** the check reads it through the native executable,
   **Then** the pack is validated against its schema and served.
6. **Given** a request with an invalid body, **When** it reaches the native executable, **Then** the answer
   is the same 422 `validation_failed` problem the jar gives.

### User Story 3: The operator cuts or rehearses a release (Priority: P1)

The operator pushes `service-v<version>` at a commit on `main` whose build version is `<version>`. The
release workflow:

1. checks the tag;
2. builds the jar and the four native executables;
3. checks each one;
4. writes `SHA256SUMS` and one `.sha256` file per asset;
5. attests the assets;
6. creates a GitHub release that is never marked latest. A tag with a pre-release suffix creates a GitHub
   pre-release.

A manual run with a tag name is a rehearsal. It assembles the same assets as a workflow artifact and creates
nothing.

**Why this priority:** core's pin needs a real, checksummed release to point at (D1, D2).

**Independent Test:** run the rehearsal on a branch. It produces exactly the expected asset set and a
`SHA256SUMS` that verifies, and it publishes nothing.

**Acceptance Scenarios:**

1. **Given** a tag at a commit that is not on `main`, **When** the workflow runs, **Then** nothing is
   built or published, and the error names the cause.
2. **Given** tag `service-v0.2.0` and a build version `0.2.0-SNAPSHOT` (or `0.3.0`), **When** the workflow
   runs, **Then** it refuses: the tag and the build version must agree.
3. **Given** tag `service-v0.2.0` (a final release) whose bundled contract version has no released contract
   tag merged into the commit, **When** the workflow runs, **Then** it refuses.
4. **Given** tag `service-v0.2.0-rc.1` with an unreleased contract, **When** the workflow runs, **Then** it
   proceeds. The release is a pre-release whose notes say the contract is unreleased.
5. **Given** one native target fails, **When** the workflow runs, **Then** nothing is published.
6. **Given** the tag was moved after the build, **When** the publish step runs, **Then** it refuses.
7. **Given** a release for the tag already exists, **When** the workflow runs, **Then** it fails and does
   not replace the release.

### Edge Cases

- The tag is malformed: `service-v1.0`, `service-v1.0.0-rc-1`, `service-v1.0.0+meta` or
  `service-v0.0.0`. The workflow refuses it, consistent with core's tag rule and placeholder refusal.
- The service is started with a different profile at run time than the one active at build time. The
  container check still runs.
- Windows: the executable name ends in `.exe`, and paths and atomic moves behave on NTFS.
- The checksum file must hold bare file names (no directory) and lowercase hex, as core parses it.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status | Delivery | No-op passable? |
|---|---|---|---|---|
| FR-001 | An accepted ADR records the service tag line `service-vX.Y.Z[-pre]`, with pre-release identifiers limited to letters, digits and dots, kept separate from `contract-charter-v*`. | Proposed | [build] | no |
| FR-002 | The ADR records the service's own SemVer rules (major, minor, patch), the 0.x policy until contract 1.0.0, and that each release implements exactly one bundled contract version, reported by the info operation. | Proposed | [build] | no |
| FR-003 | The ADR records the asset table agreed with core: four raw native executables, the jar, one `.sha256` per asset and `SHA256SUMS`. The prefix is `spec-kitty-charter-service`, no longer provisional. No contract archives are included. | Proposed | [build] | no |
| FR-004 | The ADR records the checksum format and the build-provenance attestation, with the command that verifies it. | Proposed | [build] | no |
| FR-005 | Open question 5 is marked decided (native per OS, jar as fallback), and the milestone and roadmap entries for #71 and #241 are updated. | Proposed | [build] | no |
| FR-006 | The build produces a native executable of the service for the host it runs on, from the same sources and version as the jar. | Proposed | [build] | no |
| FR-007 | A check starts a built artifact (native or jar) with a worktree token and a temporary configuration root holding one fixture pack. It passes only if all of these hold: the info operation answers with the build's service version and contract version; the fixture pack is served; an invalid request body gets a 422 `validation_failed` problem. | Proposed | [build] | no |
| FR-008 | The `container` profile's settings check runs when the profile is active at run time, whether the service runs as a jar or a native executable. | Proposed | [build] | no |
| FR-009 | The DNS-rebinding guard of the guide fetch is installed in the native executable, or the executable refuses to start with an allowlist set. A check proves which. | Proposed | [build] | no |
| FR-010 | CI builds and checks the native executable for linux-x64, linux-arm64, macos-arm64 and windows-x64 on a release, on a push to `main`, and on pull requests that touch the service, the build or the native workflow. It checks the jar on every pull request. | Proposed | [build] | no |
| FR-011 | A release workflow on `service-v*` refuses, and publishes nothing, when any of these holds: the tag is malformed; the commit is not on `main`; the tag disagrees with the build version; a final release lacks its released contract tag; any target fails; the tag moved. | Proposed | [build] | no |
| FR-012 | The release workflow publishes exactly the ADR's asset set. It verifies `SHA256SUMS`, attests every asset, and creates a release that is not marked latest. A tag with a pre-release suffix creates a pre-release. | Proposed | [build] | no |
| FR-013 | A manual run of the release workflow is a rehearsal. It assembles the assets as a workflow artifact and creates no release. CI exercises the tag and version gate on every change, publishing nothing. | Proposed | [build] | no |
| FR-014 | The service release procedure is documented next to the contract release procedure. | Proposed | [build] | no |

### Non-Functional Requirements

| ID | Requirement | Threshold | Status |
|---|---|---|---|
| NFR-001 | Each native executable starts and answers the info operation quickly enough to serve as a per-worktree daemon. | First info answer within 5 s of process start on every target in CI (the JVM jar gets 60 s) | Proposed |
| NFR-002 | A native target job, build and check included, fits a CI budget. | At most 45 minutes per target job | Proposed |
| NFR-003 | The build's dependency verification stays strict. | Every new build dependency has a SHA-256 entry; zero new suppressions or verification exemptions | Proposed |
| NFR-004 | Release assets are checkable by anyone. | 100% of published assets are listed in `SHA256SUMS` and covered by an attestation | Proposed |

### Constraints

| ID | Constraint | Status |
|---|---|---|
| C-001 | No tag is pushed and no release is created by this mission; the operator merges and tags. | Accepted |
| C-002 | Core's tag rule and pin code are not changed here; core's requests are spec-kitty/spec-kitty#6909. | Accepted |
| C-003 | The launcher (#70), the container image (#234), and the companion wheel are out of scope. | Accepted |
| C-004 | The contract (`contracts/charter/`) and the packs domain are not changed; sibling missions own them. | Accepted |
| C-005 | Every GitHub Action is pinned to a full commit SHA; checkouts do not persist credentials; only the publish job can write. | Accepted |
| C-006 | Two stacked pull requests: the ADR and plan documents first, then the build, checks and workflows. | Accepted |
| C-007 | The pull request closes #241 and references #71 without closing it (D7). | Accepted |

### Key Entities

- **Service release:** a tag `service-v<version>`, its GitHub release and its assets.
- **Release asset:** a native executable for one target, or the jar. Each asset has a SHA-256 and an
  attestation.
- **Target:** one of linux-x64, linux-arm64, macos-arm64 and windows-x64. The jar serves every other host.
- **Bundled contract version:** the contract version the service build carries and reports.

## Domain Language

| Canonical term | Meaning | Avoid |
|---|---|---|
| service release | A `service-v*` tag and its assets | "charter release" (ambiguous with the contract release) |
| contract release | A `contract-charter-v*` tag and its assets | "API release" |
| target | One of the four native platforms named as core names them | "platform", "arch" alone |
| rehearsal | A release run that publishes nothing | "dry run" |

## Success Criteria *(mandatory)*

- **SC-001:** A reader can answer every one of these questions from the ADR alone: tag form, version rules,
  contract relation, asset names, checksum format, attestation, native and fallback. — [build] · no-op
  passable: no
- **SC-002:** On a pull request that touches the service, 4 of 4 native targets and the jar pass the
  start-and-answer check. — [build] · no-op passable: no
- **SC-003:** A rehearsal produces exactly the 5 assets of the ADR's table plus their 5 `.sha256` files and
  `SHA256SUMS`. Every line of `SHA256SUMS` verifies. — [build] · no-op passable: no
- **SC-004:** Each of the 7 refusal cases of User Story 3 is refused before anything is published. —
  [build] · no-op passable: no

## Assumptions

- GraalVM Community for Java 25 builds Spring Boot 4.1 applications. Boot 4.1.1 manages native-build-tools
  1.1.8.
- GitHub-hosted runners `ubuntu-24.04`, `ubuntu-24.04-arm`, `macos-15` and `windows-2025` are available to
  this repository, as they are to spec-kitty-events.
- Native executables are not code-signed in this mission. The release notes say so, as events' do.
