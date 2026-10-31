---
title: "ADR-0046: The service releases on its own service-v* line, native per OS with the jar as fallback"
description: A service-v tag line apart from the contract's; the service's own SemVer; four native executables and the jar under names core pins, with SHA256SUMS and provenance.
doc_status: active
updated: 2026-10-31T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
  - adr/0022-contract-versions-start-at-0-x
  - adr/0041-containerized-configuration-model
  - architecture/service-lifecycle
  - guides/service-build
  - plans/open-questions
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you change the service version, the native build, or anything a release attaches.
  - profile: core-maintainer
    guidance_text: Read this for the tag, the asset names and the checksum format your service pin fetches and verifies.
  - profile: governance-lead
    guidance_text: Read this for what a service release contains and how to check a download.
---

# ADR-0046: The service releases on its own service-v* line, native per OS with the jar as fallback

## Status

**Accepted**: 2026-10-31, for [#241](https://github.com/spec-kitty/spec-kitty-charter/issues/241) and
[#71](https://github.com/spec-kitty/spec-kitty-charter/issues/71).

- **Decider:** Stijn Dejongh (operator).
- **Sources:**
  - the operator's rulings of 2026-10-31, recorded at
    [spec-kitty#6117](https://github.com/spec-kitty/spec-kitty/issues/6117);
  - the decisions of mission `service-release-native-01M693YF` (D1 to D7).

This record decides [open question 5](/plans/open-questions/) toward native. It answers the native-readiness
risk of [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/) ("a native image is not ready in
time").

Core's requests that follow from it are
[spec-kitty#6909](https://github.com/spec-kitty/spec-kitty/issues/6909). None of them blocks a release here.

## Context

- **Only the contract is released today.** The repository has one release line, `contract-charter-v*`
  ([ADR-0022](/adr/0022-contract-versions-start-at-0-x/)). It publishes the contract bundle and its
  scenarios, and no runnable service.
- **The service has a version but no release.** `version` in `gradle.properties` is reported as
  `serviceVersion` by `GET /info`, but nothing publishes it.
- **The container image needs a trigger.** The container image
  ([#234](https://github.com/spec-kitty/spec-kitty-charter/issues/234)) needs a release trigger and a version.
- **Core already has a pin model** for external services (core ADR 2026-10-29-1, `kernel/service_pin.py`):
  - a pin names a repository, a tag and a SHA-256 per asset;
  - core downloads `SHA256SUMS` and the asset for its host, and re-hashes the file on every use;
  - its tag rule is fixed: `service-v<semver>`;
  - its asset names are `<prefix>-<version>-<target>[.exe]` and `<prefix>-<version>.jar`;
  - its targets are linux-x64, linux-arm64, macos-arm64 and windows-x64, and every other host takes the jar;
  - the charter service's prefix, `spec-kitty-charter-service`, was recorded there as provisional, pending
    this repository's decision.
- **The service runs as a per-worktree daemon** started on first use
  ([ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/)). A native start (about 50 to 200
  ms) suits that better than a JVM start (1 to 2 s), and needs no Java on the user's machine.

## Decision

1. **The service has its own tag line, `service-v<version>`.**
   - A tag matches `^service-v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$`. That is core's rule (`_TAG_RE`):
     - a pre-release identifier uses only letters, digits and dots, so `rc.1` and never `rc-1`;
     - build metadata (`+…`) is not allowed;
     - `service-v0.0.0` is refused as a placeholder.
   - The line is separate from `contract-charter-v*`. Neither moves the other: a contract release does not
     release the service, and a service release does not release a contract.
2. **The service version is the service's own SemVer.**
   - It is `version` in `gradle.properties`, and a tag must equal it.
   - It describes what a launcher or an operator sees:
     - **Major:** a command-line option, configuration key, environment variable or token rule is removed or
       changes meaning, or support for a contract major is dropped.
     - **Minor:** the service implements a new contract minor, or adds a capability or an option.
     - **Patch:** fixes only.
   - The service stays at 0.x, where a minor may break, until contract 1.0.0
     ([#128](https://github.com/spec-kitty/spec-kitty-charter/issues/128)) and the first route switched to the
     service.
3. **Each release implements exactly one contract version: the one it bundles.**
   - That is `info.version` of `contracts/charter/openapi.yaml` at the tagged commit. `GET /info` reports it
     as `contractVersion`, and the release notes name it.
   - A **pre-release** (a `-` suffix) may bundle an unreleased `-SNAPSHOT` contract, and its notes say so.
   - A **final release** requires the tag `contract-charter-v<info.version>` to be merged into the tagged
     commit. So a released service never claims an unreleased contract.
4. **A release carries exactly these assets.** `<ver>` is the tag without `service-v`:

   | Asset | Content |
   |---|---|
   | `spec-kitty-charter-service-<ver>-linux-x64` | GraalVM native executable |
   | `spec-kitty-charter-service-<ver>-linux-arm64` | GraalVM native executable |
   | `spec-kitty-charter-service-<ver>-macos-arm64` | GraalVM native executable |
   | `spec-kitty-charter-service-<ver>-windows-x64.exe` | GraalVM native executable |
   | `spec-kitty-charter-service-<ver>.jar` | Spring Boot executable jar, the fallback (Java 25) |
   | `<asset>.sha256` | One per asset above: `<64 lowercase hex>  <asset>` |
   | `SHA256SUMS` | Every asset above, in `sha256sum` format, bare names, sorted with `LC_ALL=C` |

   - The prefix `spec-kitty-charter-service` is no longer provisional.
   - The native executables are raw files, not archives: core runs them directly.
   - Nothing else is attached. In particular, no `contract-bundles-*`, `contract-tools-*` or
     `conformance-fixtures-*` archive: core would vendor them as extras of the service pin, and the contract
     has its own line.
   - Adding a target is additive. Renaming or removing an asset needs a new ADR.
5. **Native per OS, with the jar as fallback.**
   - The service is built with Spring AOT and GraalVM native-image for linux-x64, linux-arm64, macos-arm64
     and windows-x64.
   - Every other host, an Intel Mac included, runs the jar and needs Java 25. Java is needed only there.
   - Every executable and the jar are started in CI before they are released. The check:
     - asks for the info operation, reads a fixture pack, and refuses an invalid body;
     - runs with the DNS-rebinding guard of the guide fetch;
     - starts under the container profile, which must refuse a missing setting.
   - Behavior that Spring AOT would fix when the image is built is decided at run time instead, for example
     the container profile ([ADR-0041](/adr/0041-containerized-configuration-model/)).
6. **Every asset is attested.**
   - The release attests the build provenance of every asset in `SHA256SUMS`.
   - Anyone can check an asset with:

     ```bash
     sha256sum --check --ignore-missing SHA256SUMS
     gh attestation verify <asset> --repo spec-kitty/spec-kitty-charter \
       --signer-workflow spec-kitty/spec-kitty-charter/.github/workflows/service-release.yml \
       --source-ref refs/tags/<tag>
     ```

     Without the signer and ref flags, any workflow of the repository would pass.
   - Core does not check the attestation yet (spec-kitty#6909).
7. **Publishing refuses rather than guesses.** A tag pushed at a commit publishes only if:
   - the tag has the form above and is not the placeholder;
   - the commit is on `main`;
   - the tag equals the build's version;
   - a final release's contract is released (decision 3);
   - every target and the jar were built and checked;
   - the tag still names the built commit when publishing starts.

   Further rules:
   - Only the publishing job can write.
   - A release is never marked latest, and a `-` suffix makes it a GitHub pre-release.
   - An existing release is never replaced.
   - A manual run of the workflow is a rehearsal: it assembles the assets and publishes nothing.
   - The procedure is in [Building the service](/guides/service-build/).

## Consequences

### Positive

- **Core pins without code changes:** the asset names and the tag are those core's model already expects.
- **No Java on the four supported targets**, and a start fast enough for a per-worktree daemon.
- **Checkable downloads:** every download can be checked by digest and by provenance, independently of this
  repository's CI logs.
- **Independent cadences:** the service and the contract release on their own schedules. A service fix
  needs no contract release.
- **A trigger for the image:** the container image (#234) gets a trigger and a version.

### Negative

- **Each release builds on five runners**, one per target plus the jar. A failed target blocks the release.
- **A missing native reachability hint shows up only in a native build.** CI therefore builds the native
  executables on every change to the service or the build, not only on release.
- **A final service release waits for its contract release** (decision 3).

### Risks

- **Native-only failures.**
  - Reflection, resources or service loaders that AOT did not see break only the native executable.
  - **Mitigation:** the hints are registered in code and tested, and every executable is started in CI.
- **The DNS-rebinding guard is not installed in a native executable.**
  - The service then refuses to start with a guide-fetch allowlist set. It fails closed, so this is not a
    hole, but it is an outage.
  - **Mitigation:** the CI check starts every executable with an allowlist.
- **Unsigned binaries.**
  - macOS and Windows may warn on first run.
  - **Mitigation:** core fetches and runs the file itself, not through a browser. Code signing is a later
    decision.

## Alternatives considered

### One tag line for the contract and the service

spec-kitty-events puts its contracts on its `service-v*` line. Not chosen here:
[ADR-0022](/adr/0022-contract-versions-start-at-0-x/) gives the contract its own line, and core vendors the
contract from it.

### The jar only

It needs Java 25 on every machine and starts more slowly. The operator ruled for native at 4.0.0 GA.

### An archive per target

It would make room for a README or a license file, but core runs the asset directly and expects a raw
executable.

### A different asset prefix

It would cost a core change and gain nothing.

## Revisit when

- Core verifies attestations, or asks for signatures.
- A target is added (for example macos-x64), or a runner stops being available.
- Code signing or notarization is wanted.
- Contract 1.0.0 is minted. The service's own 1.0.0 is then due.

## References

- [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/): the per-worktree daemon and its
  native risk
- [ADR-0022](/adr/0022-contract-versions-start-at-0-x/): the contract's release line
- [ADR-0041](/adr/0041-containerized-configuration-model/): the container profile
- Core: `kernel/service_pin.py` (the tag rule and asset prefix), `kernel/service_hosts.py` (the targets),
  `services/resolution.py` (the jar fallback) and core ADR 2026-10-29-1
- spec-kitty-events ADR-0003: the reference pipeline
- Issues [#241](https://github.com/spec-kitty/spec-kitty-charter/issues/241),
  [#71](https://github.com/spec-kitty/spec-kitty-charter/issues/71),
  [#234](https://github.com/spec-kitty/spec-kitty-charter/issues/234) and
  [spec-kitty#6909](https://github.com/spec-kitty/spec-kitty/issues/6909)
