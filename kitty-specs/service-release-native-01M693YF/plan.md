# Implementation Plan: Service release line and native packaging

**Branch**: `issue-241-71-service-release-native` (PR 1), stacked `issue-241-71-service-release-native-build` (PR 2)
| **Date**: 2026-10-31 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/service-release-native-01M693YF/spec.md`

## Summary

The charter service gets its own release line, `service-v*`, recorded in ADR-0046.

- **Assets:** a GraalVM native executable for each of four targets, plus the Spring Boot jar, named as core's
  pin model expects (`spec-kitty-charter-service-<ver>-<target>[.exe]`, `…-<ver>.jar`). They are checksummed
  in `SHA256SUMS` and attested.
- **Build:** the native build is Spring AOT plus `org.graalvm.buildtools.native`. A black-box smoke check
  starts each artifact and checks:
  - `GET /api/v1/info`, the 401 problem body without a token, a fixture pack read, a 422 problem on an invalid
    body;
  - the DNS-rebinding guard;
  - the container profile's refusal.
- **Workflows:** the release workflow follows `contract-release.yml` and events' `service-release.yml`: gate →
  build (the `service.yml` and `service-native.yml` calls) → assemble → publish.
- **Container profile:** it moves from `@Profile` to a run-time check (D4a), so a native binary keeps
  ADR-0041's guarantees.

## Planning answers (from the operator rulings, D1 to D7)

The planning questions are answered by the rulings recorded in `decisions/`, so no plan-time question is
open. The mission runs semi-automatically.

Two calls are made in this plan without separate review. They are logged here as open review items, per the
charter.

- **R-1:** "the jar smoke on every PR" (D6) is read as every pull request that runs the service CI group, and
  every push to `main`. A documentation-only pull request builds nothing, so it smokes nothing.
- **R-3** (pre-PR squad, 2026-10-31): NFR-001's 5 s is measured and reported as a warning, and the native check fails only past 30 s, because a hosted runner's first run of a fresh executable can be scanned or slowed, and a flaky red target would block every release.
- **R-2:** the service release gate is a Kotlin class in `build-logic`, unit-tested under `verifyAll`. It is
  not a Java class in `:contracts`, because the service version and the contract version are both build
  facts, and the contract project is the contract's own.

## Technical Context

**Language/Version**: Java 25 (Temurin in the JVM build, GraalVM Community 25 for native); Kotlin DSL for Gradle 9.8.1
**Primary Dependencies**: Spring Boot 4.1.1 (AOT), `org.graalvm.buildtools.native` 1.1.8 (the version Boot 4.1.1 manages), GitHub Actions
**Storage**: N/A (release assets on GitHub Releases)
**Testing**:
- JUnit 6 and AssertJ:
  - RuntimeHints predicates;
  - the container check context runner;
  - an ArchUnit rule that no main bean is gated by `@Profile`;
  - the `ServiceReleaseGate` unit tests in build-logic.
- A bash smoke script run against the jar locally and against every native binary in CI.
- A release rehearsal script, run in `service.yml` on every change.

**Target Platform**: linux-x64 (`ubuntu-24.04`), linux-arm64 (`ubuntu-24.04-arm`), macos-arm64 (`macos-15`), windows-x64 (`windows-2025`); the jar on any Java 25 host
**Project Type**: single (Gradle multi-project service; the build and CI change, plus one wiring change in `service/deployable`)
**Performance Goals**: native first answer within 5 s of process start (NFR-001); the jar within 60 s
**Constraints**:
- At most 45 min per native job (NFR-002).
- Strict SHA-256 dependency verification with no new exemptions (NFR-003).
- SHA-pinned actions, and no persisted checkout credentials (C-005).
- No tag is pushed (C-001).

**Scale/Scope**:
- One ADR and two plan documents.
- About 6 Java and Kotlin files and 4 workflows.
- 3 scripts and one guide section.

### Supply-chain checks (DIRECTIVE_051)

| Control | `org.graalvm.buildtools.native` 1.1.8 (Gradle plugin) and the reachability-metadata artifact it resolves |
|---|---|
| Provenance | Oracle's GraalVM build tools, published to Maven Central and the Gradle plugin portal. It is the version Spring Boot 4.1.1's BOM manages (`native-build-tools-plugin.version`). |
| Pinning | Pinned in `gradle/libs.versions.toml`. Every artifact gets a SHA-256 in `gradle/verification-metadata.xml`, written by `--write-verification-metadata sha256` and reviewed in the diff. No `trust` entries are added. |
| Scope | Build-time only. Nothing is added to the service's runtime classpath. |
| Actions | `graalvm/setup-graalvm` is pinned to the SHA events already uses (`27b360dbb857524eaf0b4c4cef364d38e5b4f173`, v1.6.7). `actions/attest-build-provenance` is pinned to the SHA events uses (`4d101475d8b20a2381f78447822ac1eab6504dd8`, v4.2.2). |
| Adversarial evidence | The pre-PR squad runs a release and supply-chain lens (closeout step 3). Its dispositions are recorded in `research.md`. |

## Charter Check

| Gate | Status |
|---|---|
| `verifyAll` on Java 25 with strict verification | Must stay green. New dependencies are verified (NFR-003). |
| Style gates (palantir, Checkstyle, Error Prone, NullAway, doclint) | They apply to the new Java, including the AOT-generated sources if they are compiled into a checked source set. No gate is turned off for hand-written code. Generated AOT code is treated like the other generated sources. |
| Docs checks (`check_docs.py all`, markdownlint, cspell) | Apply to the ADR, the guide section and the plan updates. |
| Adversarial squad (architecture or contract change) | The ADR is an architecture record. A pre-PR squad (closeout step 3) reviews it with the release lens. |
| No unverified dependency | See the supply-chain table above. |
| Contract and packs domain untouched | Yes. `StorageConfiguration`'s `@ConditionalOnProperty` (N7) is documented, not changed. |

## Design

### D-1: ADR-0046 (PR 1)

The ADR covers these points, with the asset table copied into
[contracts/service-release-assets.md](contracts/service-release-assets.md):

- the tag line and its regex (core's `_TAG_RE`);
- the service's SemVer rules and the 0.x policy;
- the bundled contract version, and pre-release versus final releases;
- the asset table, the `.sha256` files and `SHA256SUMS`;
- the attestation and its verify command;
- native plus jar;
- rehearsal;
- what is not shipped (contract archives).

Plan updates:

- `docs/plans/open-questions.md` Q5 is decided.
- `docs/plans/milestones.md` updates the #71 and #241 rows.
- `docs/plans/roadmap.md` gets a line where native packaging is mentioned.

### D-2: Container profile at run time (D4a)

- `ContainerStartupCheck` loses `@Profile`. It is always present, and it returns at once unless
  `environment.matchesProfiles("container")`.
- Under the profile, it also binds and validates `ContainerProperties` with `Binder` and the context's
  validator. This replaces `ContainerWiring`'s profile-gated `@EnableConfigurationProperties`.
- `ContainerWiring` is removed. Its binding hints are registered through `BindableRuntimeHintsRegistrar`.
- **Red-first:**
  - an ArchUnit rule refuses `@Profile` on any main class (AOT freezes profiles);
  - a context-runner test shows the check fails on a missing `charter.container.allowed-hosts` under the
    profile, and starts without it.

### D-3: Native build and hints (N3, N5, N8, N2)

- `deployable` applies `org.graalvm.buildtools.native` with `imageName = "spec-kitty-charter-service"`. The
  metadata repository is enabled, and `toolchainDetection` is off, so native-image comes from the GraalVM on
  `JAVA_HOME`.
- `CharterRuntimeHints`, in `service/deployable`, is imported by `CharterServiceApplication`. It registers:
  - the resources:
    - `packs/schemas/*.schema.json`
    - `packs/file-kinds.yaml`
    - `ai/speckitty/charter/**/*.properties`
    - `ai/speckitty/charter/deployable/charter-openapi.yaml`
    - networknt's `draft/2020-12/**`
    - the `jsv-messages` bundle;
  - the binding types `Problem` and `CharterRefusal`, plus the private mix-in, by name;
  - the `ProblemErrorReportValve` type;
  - the `InetAddressResolverProvider` service entry and the provider's constructor.
- **Red-first:** `CharterRuntimeHintsTest` asserts each one with `RuntimeHintsPredicates`.

### D-4: Smoke check (FR-007)

`scripts/release/smoke-service.sh <artifact> <service-version> <contract-version> [max-start-seconds]`
works on bash with curl, on Linux, macOS and Windows Git Bash.

- **Setup:** it copies a smoke fixture configuration root (`scripts/release/smoke/config-root/`, one pack
  with one tactic) to a temporary directory. It picks a free port, sets a 40-character token, and starts the
  artifact (`java -jar` for `*.jar`, else the executable).
- **Start 1, assertions:**
  - `/api/v1/info` answers 200 with the expected versions, within the time limit;
  - a request without the token gets 401 with a problem JSON;
  - `GET /api/v1/packs/smoke/tactics` lists the fixture tactic;
  - `POST /api/v1/packs/smoke/directives` with an invalid body gets 422 `validation_failed`;
  - `charter.guide-fetch.allowed-hosts` is set, so this start also proves the DNS guard (N2).
- **Start 2:** the `container` profile with every rule met except `charter.container.allowed-hosts`. It must
  exit non-zero, naming that property.
- **Process handling:** the process is always killed on exit, and its log is printed on failure.

### D-5: CI workflows (FR-010, D6)

- **`service-native.yml`:**
  - Triggers: `workflow_call` (input `cache-disabled`), `workflow_dispatch`, and `push` to main on the
    native paths.
  - A `native` matrix runs over the four targets: setup-graalvm, `./gradlew :deployable:nativeCompile`, the
    smoke check, then an upload of `native-<target>` holding the raw executable.
  - `fail-fast: false`.
- **`service.yml`:**
  - A new `jar` job builds `:deployable:bootJar`, runs the smoke check and uploads `service-jar`.
  - It takes the `cache-disabled` input.
  - It adds `scripts/release/**` and the new workflows to its paths. The `release-rehearsal` job also runs the
    service release rehearsal.
- **`ci.yml`:** a `native` concern group (`service/`, `build-logic/`, `gradle/`, root Gradle files,
  `scripts/release/`, `service-native.yml`, `ci.yml`) calls `service-native.yml`. The `gate` job counts it.

### D-6: Release gate, assets and workflow (FR-011 to FR-013)

- **`ServiceReleaseGate`** (build-logic, Kotlin) is a pure function. It takes the tag, the build version, the
  bundled contract version and whether `contract-charter-v<contract>` is merged into HEAD. It returns the
  version and the pre-release flag, or a refusal that names the reason.
- **The root task `checkServiceReleaseTag -PreleaseTag=<tag>`:**
  - It reads `gradle.properties` `version` and the contract's `info.version`.
  - It asks git (`merge-base --is-ancestor`) whether the contract tag is merged.
  - It writes `build/service-release/gate.properties`.
- **`scripts/release/service-release-assets.sh <ver> <in> <out>`:**
  - It names the four executables and the jar, writes one `.sha256` per asset and `SHA256SUMS`
    (`LC_ALL=C sort`, bare names).
  - It checks the exact asset set with `diff`.
  - It refuses a missing or extra file.
- **`scripts/release/rehearse-service-release.sh`:**
  - In a throwaway clone with no remote, it checks that each refusal case of User Story 3 is refused by the
    gate, and that a synthetic rc release passes.
  - It runs the assets script on placeholder files and verifies `SHA256SUMS`.
- **`service-release.yml`:**
  - `gate`: the tag format, main ancestry and `checkServiceReleaseTag`.
  - Build: `service.yml` and `service-native.yml` are called with `cache-disabled: true`.
  - `assemble`: read-only. It runs the assets script and uploads `service-release`.
  - `publish`: push only. It is the only job with `contents: write`, `id-token: write` and
    `attestations: write`. It runs `sha256sum --check --strict`, re-checks that the tag still names the built
    commit, then attests `subject-checksums`. It creates the release with
    `gh release create --verify-tag --latest=false [--prerelease]` and the listed files only.
  - A manual run is a rehearsal: no publish.

### D-7: Release procedure doc (FR-014)

`docs/guides/service-build.md` gets a "Release a service version" section beside "Release a contract version".

## Project Structure

### Documentation (this mission)

```
kitty-specs/service-release-native-01M693YF/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/service-release-assets.md
├── decisions/            # D1-D7
└── tasks.md, tasks/      # /spec-kitty.tasks
```

### Source Code (repository root)

```
docs/adr/0046-service-release-line-and-native-packaging.md     # PR 1
docs/plans/{open-questions,milestones,roadmap}.md              # PR 1
docs/guides/service-build.md                                   # PR 2 (release procedure)
gradle/libs.versions.toml, gradle/verification-metadata.xml    # PR 2
build-logic/build.gradle.kts
build-logic/src/main/kotlin/ServiceReleaseGate.kt (+ test)
build.gradle.kts                                               # checkServiceReleaseTag
service/deployable/build.gradle.kts                            # native plugin
service/deployable/src/main/java/.../CharterRuntimeHints.java (+ test)
service/deployable/src/main/java/.../ContainerStartupCheck.java, ContainerWiring.java (removed)
service/deployable/src/test/java/.../NoBuildTimeProfilesTest (ArchUnit)
scripts/release/{smoke-service.sh,service-release-assets.sh,rehearse-service-release.sh}
scripts/release/smoke/config-root/packs/smoke/...
.github/workflows/{service.yml,service-native.yml,service-release.yml,ci.yml}
```

## Implementation Concern Map

| IC | Concern | Requirements | PR |
|---|---|---|---|
| IC-01 | ADR-0046 and the plan updates | FR-001 to FR-005 | 1 |
| IC-02 | Container profile decided at run time | FR-008 | 2 |
| IC-03 | Native build and runtime hints | FR-006, FR-009, NFR-003 | 2 |
| IC-04 | Smoke check, and the jar and native CI | FR-007, FR-010, NFR-001, NFR-002 | 2 |
| IC-05 | Release gate, assets, rehearsal and workflow; the procedure doc | FR-011 to FR-014, NFR-004 | 2 |

## Risks

- **N2 is red:** the DNS guard is not installed in native. The binary refuses to start with an allowlist
  set, so the smoke check is red. Escalate to the operator with options (a) and (b) from the research (D4b).
- **N4 or N6 is red:** networknt or Bean Validation is missing metadata. Add hints from the failure; the
  smoke check shows the error.
- **AOT on the JVM build:** applying the native plugin makes Boot add `processAot` to `bootJar`, so AOT
  code-generation errors surface in the jar build. Mitigation: build `bootJar` locally first.
- **Runner availability:** a failed target blocks the release, by design.
