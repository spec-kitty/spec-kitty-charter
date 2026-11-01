# Research: service release line and native packaging (#241, #71)

Research squad of three read-only lenses, run 2026-10-31 before the spec:

- **R1:** core's pin and asset-naming model (`../spec-kitty`);
- **R2:** the events reference release pipeline (`../spec-kitty-events`);
- **R3:** native-image readiness of this service.

Each finding cites the file it comes from. Nothing was built natively: `native-image` is not installed in
the session, so the native builds run in CI.

## Operator rulings this research works under

Recorded 2026-10-31 at spec-kitty/spec-kitty#6117, comment 6544221340:

- The service releases on its own `service-v*` tag line in this repository, separate from
  `contract-charter-v*`. Core's tag rule stays unchanged, and each pin names its repository.
- At 4.0.0 GA the service ships native for `linux-x64`, `linux-arm64`, `macos-arm64` and `windows-x64`,
  with the jar as fallback. Java 25 is needed only on other hosts. This closes open question 5 toward
  native.
- Core's release pins the service at GA and fetches it on first use. Nothing in core calls it yet.
- A companion wheel carrying binaries is core's, and out of scope here.

## R1: core's pin and asset model (read-only, `../spec-kitty`)

### Tag

- The rule is `src/kernel/service_pin.py:92`:
  `_TAG_RE = re.compile(r"service-v([0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?)")`, applied with
  `fullmatch` (`:185-188`).
- These tags match: `service-v0.1.0` and `service-v0.1.0-rc.1`.
- These tags do not match:
  - `service-v0.1.0-rc-1`: the pre-release character class has no `-`;
  - any `+build` metadata;
  - any other prefix.
- `service-v0.0.0` is refused as a placeholder (`:93, :194`).
- The prefix cannot change per repository. **This repository must tag `service-vX.Y.Z[-pre]`, with a
  pre-release made only of `[0-9A-Za-z.]`.**

### Pin

The pin is `pins/<service>.source.yaml`.

- Its keys (`service_pin.py:76-85`): `service`, `repository`, `tag`, `commit`, `protocol_versions`,
  `requires_core`, `assets` (target → `{name, sha256}`) and optional `extras`.
- `service: charter-service` is already one of the known services (`:60`).
- `repository` is per pin (`check_repository`, `:169-182`).
- The vendor command defaults to spec-kitty-events (`vendor.py:70, 333`). The first charter bump must pass
  `--repository https://github.com/spec-kitty/spec-kitty-charter`.

### Targets and names

- The targets are in `ServiceTarget` (`service_pin.py:113-127`): `linux-x64`, `linux-arm64`, `macos-arm64`,
  `windows-x64` and `jvm`. No `macos-x64` target exists, so Intel Macs take the jar.
- Host mapping is in `service_hosts.py:25-35`.
- `select_build` takes the native build, else the jar (`service_hosts.py:52-58`). Java (25,
  `host.py:23`) is looked for only for the jar (`resolution.py:405-420`).
- Names are built in `vendor.py:229-233` as `f"{prefix}-{version}{target.asset_suffix}"`. The version is
  the tag without `service-v`.
- The prefix for `charter-service` is `spec-kitty-charter-service` (`service_pin.py:70-71`), marked
  *Provisional* "until spec-kitty-charter … names its release asset".
- Native assets must be **raw executables**, not archives: core runs them directly and sets mode 0755
  (`service_artifacts.py:192-205`). The jar runs with `java -jar` (`resolution.py:93, 420`).
- The shared extras `contract-bundles-<ver>.tar.gz`, `contract-tools-<ver>.tar.gz` and
  `conformance-fixtures-<ver>.tar.gz` (`vendor.py:78-82`) are vendored into the pin if present.
  **The service line should not ship them**: the contract has its own `contract-charter-v*` line.

### Checksums and attestation

- Core downloads exactly `SHA256SUMS` (`vendor.py:71`). Each line must match
  `([0-9a-f]{64}) [ *](\S.*)` (`vendor.py:75`): plain `sha256sum` output, lowercase hex, bare names.
- A repeated or malformed line is refused.
- Each pinned asset is re-hashed against the pin on every resolve (`resolution.py:465-481`).
- Per-asset `.sha256` files are ignored by core.
- Attestation is not verified by core yet. The vendor docstring lists it as a follow-up (`vendor.py:23`).
  The release still attests, so it is ready when core checks.

### Launchability

- `policies.py:58` marks `charter-service` `launchable=False`, so `service fetch --all` skips it
  (`service.py:156`).
- `spec-kitty service fetch --service charter-service` works explicitly.
- This matches the ruling that nothing in core calls the service yet. Flipping it belongs to the seam work
  (#70), not here.

## R2: the reference pipeline (read-only, `../spec-kitty-events`)

The workflow is `.github/workflows/service-release.yml`, with its rules in
`docs/adr/0003-gradle-build-and-service-release-line.md`. That ADR says its gate was copied from this
repository's `contract-release.yml`.

### Jobs

The jobs are gate → build (`workflow_call` of `service.yml`) → assemble → publish.

- Triggers: `push: tags: ["service-v*"]`, and `workflow_dispatch` with a `tag` input, which is a rehearsal
  that publishes nothing.
- Concurrency: `service-release-${{ github.ref }}`, never cancelled.
- Permissions are read-only by default. Only `publish` gets `contents: write`, `id-token: write` and
  `attestations: write`.

### Gates

- Tag format: `^service-v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$`. A `-` suffix makes a pre-release.
- Main ancestry: `git merge-base --is-ancestor "$GITHUB_SHA" origin/main`.
- The tag-moved recheck runs before publishing. It peels an annotated tag and compares it with the built
  commit.
- An existing release fails `gh release create`.
- The asset set is compared with the ADR table using `diff -u`.
- Events does **not** check that the tag equals its build version. This repository's `contract-release.yml`
  is stricter, and the service gate here should be too: tag = `gradle.properties` `version`, with no
  `-SNAPSHOT`.

### Build

- `fail-fast: false`, with these runners: `ubuntu-24.04`, `ubuntu-24.04-arm`, `macos-15` and
  `windows-2025`.
- GraalVM is set up with `graalvm/setup-graalvm@27b360dbb857524eaf0b4c4cef364d38e5b4f173 # v1.6.7`,
  `distribution: graalvm-community`, `java-version: "25.0.2"`.
- The build runs `nativeCompile` and a smoke task against the built binary.
- The Gradle cache is disabled on release runs. No outside code is checked out on release runs.

### Assets and publishing

- Asset names are `<name>-<ver>-<target>[.exe]` and `<name>-<ver>.jar`.
- There is one `<asset>.sha256` per asset, plus `SHA256SUMS`, sorted with `LC_ALL=C`.
- Publishing runs `sha256sum --check --strict SHA256SUMS`, then
  `actions/attest-build-provenance@4d101475d8b20a2381f78447822ac1eab6504dd8 # v4.2.2` with
  `subject-checksums: dist/SHA256SUMS`.
- The release is created with `gh release create "$TAG" --verify-tag --latest=false [--prerelease]` and only
  the listed files.
- The notes give the verify commands:
  - `sha256sum --check --ignore-missing SHA256SUMS`;
  - `gh attestation verify <asset> --repo <repo> --signer-workflow <repo>/.github/workflows/service-release.yml --source-ref refs/tags/<tag>`.
- Every action is SHA-pinned with a version comment, and every checkout uses `persist-credentials: false`.

## R3: native-image readiness of this service

- **Versions:** Spring Boot 4.1.1, Java 25, Jackson 3 (`tools.jackson`), snakeyaml-engine 3.2 and networknt
  json-schema-validator 3.0.8.
- **Setup:** no native plugin, no `RuntimeHints` and no `@RegisterReflectionForBinding` exist yet.
- **Dependency verification:** `gradle/verification-metadata.xml` verifies every artifact, so the
  `org.graalvm.buildtools.native` plugin and the reachability-metadata artifact it resolves need
  verification entries.
- **The endpoint the smoke test hits:** `GET /api/v1/info`.
  - `InfoController` sits under `${charter.api.base-path:/api/v1}`.
  - Every request needs the worktree token. Startup refuses unless `CHARTER_WORKTREE_TOKEN` (at least 32
    characters) is set in the environment (`GuardWiring.java:73-103`).
  - The default bind is `127.0.0.1` on port 0, so the smoke test passes `--server.port`.
  - Packs are read from `--configRootLocation`; none are inside the jar.

### Incompatibilities found

Each is named, with a fix or the options.

| # | Component | Where | Why native breaks it | Fix | Severity |
|---|---|---|---|---|---|
| N1 | `@Profile("container")` wiring | `deployable/ContainerWiring.java:12`, `ContainerStartupCheck.java:30` | AOT decides profile-gated beans when the image is built. A native binary started with `SPRING_PROFILES_ACTIVE=container` would silently skip `ContainerStartupCheck` and the validation of `ContainerProperties`. That fails **open** on a security check (ADR-0041), although `application-container.yaml` still loads. | (a) Evaluate the profile at run time: one always-present bean that runs the check when `Environment.matchesProfiles("container")`. (b) Keep the container on the JVM (#234 builds on the jar), and make the native binary refuse the `container` profile at start. | Blocker for native under `container`; none for the desktop binaries. **Decision D4a.** |
| N2 | DNS-rebinding guard, an `InetAddressResolverProvider` SPI | `packs/src/main/resources/META-INF/services/java.net.spi.InetAddressResolverProvider`, `GlobalAddressResolverProvider.java:60` | The JDK loads the provider with `ServiceLoader` on the first lookup. Native-image support for this JEP 418 hook is unverified, and a lookup at image build time could fix the built-in resolver first. The code fails **closed**: with `charter.guide-fetch.allowed-hosts` set and no guard, startup is refused. | Register the service-loader entry in `RuntimeHints`, make sure no lookup runs at build time, and add a smoke run with allowed-hosts set. If it still fails: (a) the native binary refuses allowed-hosts and points to the jar, or (b) the guard moves from DNS to a connect-time address check. | Needs a runtime test. **Decision D4b if red.** |
| N3 | Classpath resources the service loads itself | `packs/schemas/*.schema.json` and `packs/file-kinds.yaml` (`PackSchemas.java:373`, `PackFileKind.java:457`); `ai/speckitty/charter/packs/infra/api/{contract,service}-version.properties`; `ai/speckitty/charter/deployable/charter-openapi.yaml` (`ContractDocumentWiring.java:44`) | AOT does not include these resources automatically. | A `RuntimeHintsRegistrar` with resource patterns. A start without them fails loudly. | Fixes easily |
| N4 | networknt 3.0.8 | `PackSchemas.java:79-88` | It loads the 2020-12 metaschemas from its own jar (`draft/2020-12/**`), uses the `jsv-messages` `ResourceBundle`, and may use `ServiceLoader`. | Resource and bundle hints; a native smoke run that loads a pack, so validation runs. | Needs a runtime test |
| N5 | Jackson bodies not visible in controller signatures | `ProblemHandler.java:34` (`ResponseEntity<Object>`), `WorktreeGuard.java:142`, `ProblemErrorReportValve.java:49`; the mixins in `ProblemJson.java:34-35` | AOT registers only the types it sees, so these error bodies come out empty or broken. | `@RegisterReflectionForBinding` for `Problem`, `CharterRefusal` and the mixins. | Fixes easily |
| N6 | Generated DTOs and Bean Validation | `charter-api` (`interfaceOnly`, `useBeanValidation`) | AOT sees these through the generated interfaces. The constraint metadata is the riskier part. | Smoke-test a 400 on an invalid body; add binding hints if anything is missing. | Needs a runtime test |
| N7 | `@ConditionalOnProperty("charter.config-root")` | `StorageConfiguration.java:64` | The condition is decided at image build time. `application.yaml` always sets the property, so behavior does not change, but the condition no longer switches the bean at run time. | Document it. | Fixes easily |
| N8 | Tomcat error valve set by class name | `ConnectorProblems.java:54` | `setErrorReportValveClass` takes a class name. The instance is added first, so the reflective path should not run. | A defensive type hint. | Fixes easily |
| N9 | HTTPS guide fetch, virtual threads | `HttpsGuideFetcher` | TLS and virtual threads are supported in native, but the certificate store is per OS. | Runtime check on each target (Windows especially). | Needs a runtime test |
| N10 | Test-only libraries (ArchUnit, Mockito, Cucumber, the JaCoCo and PIT agents) | test classpath | They break `nativeTest`. | Do not run `nativeTest`; prove the binary with a black-box smoke test instead. | Design choice |

**Found safe:**
- snakeyaml-engine is used only with `Map`, `List` and `String` plus a custom `Schema`, so there is no bean
  reflection.
- The `@ConfigurationProperties` records are handled by Boot's AOT.
- All configuration classes use `proxyBeanMethods=false`.
- No `Class.forName`, dynamic proxies, JNI or `sun.misc` were found in main code.
- NIO `ATOMIC_MOVE` and `toRealPath` are supported, with a Windows check in the smoke test.

**No hard blocker is known before a native build has run.** N1 needs a decision. N2 and N4 are confirmed
or cleared only by the CI smoke test on all four targets.

## Consequences for the mission

### PR slicing

The ADR (#241), the native build (#71) and the release workflow can be reviewed apart:

- **PR 1:** the ADR, the release procedure doc, and the plan updates (open question 5, milestones).
  Documentation only.
- **PR 2:** stacked on PR 1. The native build and hints, the smoke test, the CI native matrix, and
  `service-release.yml` with its rehearsal.

  The release workflow reuses the native build, so splitting them further would leave a workflow with
  nothing to attach.

### What #71 can close

#71's criteria include "the launcher (#70) starts it on each OS in CI" and "core's install verifies its
checksum". Both are outside this repository's scope here: #70 is out of scope, and the install is core's.
**This mission references #71 rather than closing it**, unless the operator splits those criteria off.

### Native CI cost

The four-target matrix takes minutes per target. Candidate triggers:

- release runs;
- pushes to `main`;
- pull requests that touch the service, the build or the native workflow (path-filtered and called by the
  `ci.yml` orchestrator, ADR-0023).

## Draft core issue

The draft is in [`drafts/core-issue-charter-service-release-line.md`](drafts/core-issue-charter-service-release-line.md),
for the orchestrator to file.

## Pre-PR adversarial squad (2026-10-31): findings and dispositions

Three lenses reviewed the branch, read-only: release and supply chain, native-image and Spring runtime, and CI
cross-OS and gating.

### Release and supply chain

| Finding | Disposition |
|---|---|
| S1: GraalVM `java-version: "25"` floats between the four jobs | **Fixed:** pinned to `25.0.2` as events pins it; each job records `native-image --version` |
| S2: the trust model rests on a `service-v*` tag ruleset nothing shows exists | **Fixed in docs, operator action:** ADR-0046 decision 7 makes the ruleset a prerequisite of the first tag, and the publish job runs in the `service-release` environment, where reviewers can be required. Listed in the hand-over |
| S3: a 5 s start limit is a hard release gate on hosted runners | **Fixed:** the check fails past 30 s. NFR-001's 5 s is measured per target and a later answer is a warning (plan R-3) |
| N1: the SNAPSHOT check is case-sensitive | **Fixed** (red first) |
| N2: two contract-version parsers (awk in the workflows, a regex in the gate) | **Accepted:** if they disagree the run fails, so nothing wrong is published |
| N3: concurrency drops a third pending run for the same ref | **Accepted:** a release run is per tag |
| N4, N5: artifacts expire after 7 days; a failed upload can leave a partial release | **Documented** in the release procedure |
| N6: no explicit wrapper validation | **Accepted:** `setup-gradle` v6 validates wrappers by default |

### Native-image and Spring runtime

| Finding | Disposition |
|---|---|
| 1: Hibernate Validator needs the record's fields and the two validators reflectively. Without them the container check fails open or crashes natively | **Fixed** (red first): field and method hints for `ContainerProperties` and constructor hints for the two validators. The smoke check gains start 3, a valid container start that must answer |
| 2: the DNS guard may not install natively | **Open until CI:** start 1 runs with an allowlist, so the CI run proves it on four targets. If it is red, D4b goes back to the operator |
| 3: the HTTPS guide fetch (TLS, trust store) is not exercised by the smoke check | **Accepted gap, in the hand-over:** a smoke step needs an external host, so it is left to the launcher's tests (#70) |
| 4: native-image memory on `macos-15` | **Fixed:** `-J-Xmx5g` |
| 6: the valve hint defends nothing; duplicate `--no-fallback`; locale of the networknt messages | **Fixed:** the valve hint and its test are dropped, and so is the duplicate flag. The locale is accepted |
| 7: `processTestAot` is not in `check` | No change |

### CI cross-OS and gating

| Finding | Disposition |
|---|---|
| 1: on Windows a refused connect takes about 2 s, so the time limit can fail a service that started in time | **Fixed:** each poll is timed from when it was sent |
| 2: stopping the service on Windows can hang or fail the cleanup | **Fixed:** the stop is bounded (kill, then `taskkill` or `kill -9`), and the cleanup never fails a passing run |
| 3: memory on the macOS runner; MSVC on windows-2025 | **Partly fixed:** `-J-Xmx5g`. MSVC is watched in CI |
| 5: start 2 matched any log naming the property | **Fixed:** it matches the refusal's own sentence |
| 7: `service-release.yml` never runs on a PR | **In the hand-over:** run `workflow_dispatch` once after merge, with a tag that is not the build version, to see the refusal |
| 8: ADR-0023 does not mention the `native` group | **Fixed:** an extension note |
| 9: line endings on developers' Windows clones | **Fixed:** `.gitattributes` keeps `*.sh` and `gradlew` LF |
| 4, 6: port probing; the rehearsal's run time | **Accepted** |
