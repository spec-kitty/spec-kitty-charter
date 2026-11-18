---
title: "ADR-0079: Dependencies resolve through a Maven Central mirror first, with bounded retries"
description: "Google's Maven Central mirror is asked first, plugins then from the portal, with five tries on a transient error, so a Central HTTP 429 fails no build."
doc_status: active
updated: 2026-11-18T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-18'
type: ADR
kind: ADR
related:
  - plans/engineering-notes/ci-dependency-resolution-research-2026-11-18
  - adr/0046-service-release-line-and-native-packaging
  - adr/0060-native-build-runs-nightly-and-per-release
  - guides/service-build
  - development/cloud-session-setup
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you add or reorder a repository in the build, or change the dependency retry settings.
  - profile: governance-lead
    guidance_text: Read this for why the build trusts a third-party mirror of Maven Central and what keeps that safe.
---

# ADR-0079: Dependencies resolve through a Maven Central mirror first, with bounded retries

## Status

**Accepted**, 2026-11-18. Decider: Stijn Dejongh (operator). The operator asked for the CI
hardening on 2026-11-18 and accepted the decision the same day, through the orchestrator: Google's
Maven Central mirror is asked first, with strict dependency verification kept on. No CI-visible proof
of the mirror is needed beyond the local `--info` run recorded below. PR #595 (branch
`ci-central-429-hardening`) lands it.

## Context

Maven Central answers HTTP 429 (Too Many Requests) per address, and GitHub's hosted runners share
addresses. On 2026-11-18 the nightly `service-native` run on `main` (run 44229565459) failed its
`linux-x64` leg in ten seconds: `build-logic`'s `kotlin-dsl` plugin resolved
`org.jetbrains.kotlin:kotlin-gradle-plugins-bom:2.4.10` from the plugin portal, the portal redirected
the request to `repo.maven.apache.org`, and Central answered 429. The same failure hit the first
attempt of PR #594's native build (run 44232304892). Cloud sessions met the same 429s on 2026-11-14
and moved to Google's Central mirror through an init script outside the repository
([cloud session setup](/development/cloud-session-setup/)).

What the evidence shows:

- **The Gradle cache did not prevent it.** The failed leg restored its dependency cache by exact key
  and its Gradle User Home by partial key, and still made the request. The release workflows build
  with the cache off (`cache-disabled: true`, for reproducibility), so every release job resolves
  everything from the network.
- **The plugin portal is a second road to Central.** `build-logic/settings.gradle.kts` declared no
  plugin repositories, so the `kotlin-dsl` plugin's whole classpath (the Kotlin Gradle plugin, the
  Kotlin standard library and coroutines) came through the portal, which redirects each Central artifact
  to Central.
- **Gradle already retries a 429, briefly.** Gradle 9.8.1 treats 408, 429, 5xx and network errors as
  transient and tries three times, 1 s and 2 s apart
  (`org.gradle.internal.repository.max.tentatives` and `.initial.backoff`). A 429 window outlasts that.
- **Gradle moves to the next repository only on a 404.** An error on any repository fails the
  resolution ("Repository … is disabled due to earlier error"), so a fallback repository adds no
  resilience against a 429; only the repository asked first matters.
- **The `ReserveCacheError` in the same log is benign.** `setup-gradle` saves content-addressed
  entries; when two matrix legs save the same key at once, the second cannot reserve it and skips
  it ("entry with key already exists"). It is unrelated to the failure.

## Decision

1. **Google's Maven Central mirror comes first in every repository list** of the root build and of
   `build-logic`:
   `https://maven-central.storage-download.googleapis.com/maven2/`, named `MavenCentralMirror`.
2. **Plugins resolve from the mirror, then the plugin portal**, in both builds' `pluginManagement`
   and in `build-logic`'s dependency repositories (which hold the plugins its convention plugins
   apply). The portal serves the plugin markers and the plugins only it hosts. Central is not listed
   there: the portal already hands it anything it does not host.
3. **Libraries resolve from the mirror, then Maven Central**, for an artifact the mirror does not
   hold yet (a release minutes old).
4. **A transient download error is tried five times, 2 s, 4 s, 8 s and 16 s apart**
   (`systemProp.org.gradle.internal.repository.max.tentatives=5` and `.initial.backoff=2000` in
   `gradle.properties`). A 404, 401 or 403 is never retried. The Gradle wrapper retries its
   distribution download three times, 2 s apart (`gradle/wrapper/gradle-wrapper.properties`).
5. **Dependency verification is unchanged:** strict SHA-256 for every artifact and its metadata,
   from whichever repository it came.
6. **The release's cold path can be rehearsed on any branch:** a manual run of `service.yml` or
   `service-native.yml` takes `cache-disabled`, as the release passes it.
7. **A native build has 60 minutes, not 45.** With the cache off, `macos-arm64` took 35 minutes
   (run 44249066154), against 26.5 minutes warm, too close to 45 for a release's tag push.

A cold build (`-Pcharter.native=true help` with an empty Gradle User Home) made 472 requests to the
mirror, 14 to the plugin portal and none to Maven Central.

## Consequences

### Positive

- A Central 429 can no longer fail a pull request, a nightly or a release: no build step asks Central
  for an artifact the mirror holds.
- The release workflows stay reproducible: they still build without the cache, every artifact is
  checked against `gradle/verification-metadata.xml`, and the release workflows do not change.
- Contributors and cloud sessions get the same resolution as CI; the cloud init script is no longer
  needed for this repository.

### Negative

- The build depends on a third party's mirror for its first request. Verification makes a wrong
  byte a red build, not a silent substitution; an outage of the mirror is a red build, as an outage
  of Central was.
- The retry properties are Gradle internals. A Gradle upgrade can rename or drop them without
  notice; the build then falls back to Gradle's own three tries.
- A failing download waits up to 30 s before the build reports it.

### Risks

- The mirror may lag Central by hours. A dependency bump made within that window resolves from
  Central (decision 3), or for a plugin, from the portal.

## Alternatives considered

### A warm, shared read-only dependency cache

`setup-gradle` already restores a shared cache, and the failed leg hit it. A cache misses after an
upgrade or an eviction (seven days unused, 10 GB per repository), and the release workflows turn it
off on purpose.

### Only reorder: Central before the plugin portal

It removes the portal's redirect hop, but every request still goes to Central, which is the host
that answers 429.

### Only raise the retries

Retries wait out a short window and cost a slower red build when the window is long. They complement
the mirror; alone, a sustained 429 still fails the build.

### A dependency download step with backoff ahead of the build

It duplicates Gradle's own retry loop in every job of every workflow, and the build then re-resolves
anyway.

### A pre-seeded dependency archive

A second artifact to build, store, keep in step with every bump and prove the provenance of, for
what the mirror gives with no new moving part.

### A blanket job retry

It would also re-run real failures until they pass. Not considered further.

## References

- [CI dependency resolution research, 2026-11-18](/plans/engineering-notes/ci-dependency-resolution-research-2026-11-18/):
  the two weeks of failures and each remedy's cost; the prebuilt build image is #596.
- Run 44229565459 (nightly `service-native`, 2026-11-18) and run 44232304892 (PR #594, first attempt).
- [Cloud session setup: resolve Maven Central through a mirror](/development/cloud-session-setup/).
- [Service build: change a dependency](/guides/service-build/#change-a-dependency).
