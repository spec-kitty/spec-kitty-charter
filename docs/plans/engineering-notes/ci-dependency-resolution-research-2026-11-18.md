---
title: "CI dependency resolution research, 2026-11-18"
description: Why CI builds failed on Maven Central HTTP 429, what the Gradle cache does and does not cover, and the cost of each remedy, a prebuilt build image included (#596).
doc_status: draft
updated: 2026-11-18T00:00:00Z
type: Plan
kind: Planning
related:
  - adr/0079-dependencies-resolve-through-a-maven-central-mirror
  - guides/service-build
  - development/issue-tracker
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for which CI downloads failed in the two weeks to 2026-11-18, why the cache did not prevent them, and what each remedy costs.
  - profile: governance-lead
    guidance_text: Read this for why the build resolves through a Central mirror and why a prebuilt build image is tracked separately.
---

# CI dependency resolution research, 2026-11-18

**Status: research, not a decision.** It feeds
[ADR-0079](/adr/0079-dependencies-resolve-through-a-maven-central-mirror/) (PR #595) and #596.
`main` was at `bbf7f43` when this note was written. Every claim comes from a run's job log or from a
local build with an empty Gradle user home.

## Short answer

Every Maven or Gradle repository failure in the two weeks went through the **plugin portal**, which
hands any artifact it does not host to Maven Central. Central rate-limits by address (HTTP 429), and
GitHub's hosted runners share addresses. The Gradle cache did not prevent any of them. ADR-0079
resolves through Google's Central mirror first, so a cold build asks Central for nothing. A prebuilt
build image would cut download time, not failures, so it is tracked on its own (#596).

## What failed, 2026-11-04 to 2026-11-18

Of 1,198 completed runs, 91 failed. Four failures came from the Maven or Gradle repositories:

| Run | Workflow / job | Date | Cause | Outcome |
| --- | --- | --- | --- | --- |
| 44229565459 | service-native / native (linux-x64), nightly | 11-18 | portal to Central, 429, `kotlin-gradle-plugins-bom` 2.4.10 | failed |
| 44232304892 | service-native / native (linux-x64), dispatch | 11-18 | the same | failed, passed on re-run |
| 43742337201 | whole-pack, push to `main` | 11-15 | portal to Central, 429, `openapi-generator` 7.26.0 POM | failed |
| 43162506596 | CI gate / Service / verify, pull request | 11-12 | portal "Could not find" for the Kotlin 2.4.10 artifacts | failed, passed on re-run |

The other download failures in the window are already fixed on `main`:

- **github.com release downloads (11-08).** `installOasdiff` and `installVacuum` failed with HTTP
  502, 503 and 504 errors. They now keep a cache of verified archives and try four times (#420).
- **An apt mirror DNS failure (11-11).** Graphviz now installs from cached packages, with a fallback
  mirror.

The rest of the 91 failures were ordinary: tests, lint, release gates and timeouts.

The count is not complete in three places:

- Three runs are marked failed with no failed job.
- Seven Docs site failures on 11-11 stop at "Set up Python", and the cause is not in the logs read.
- A few first attempts were not read.

## Why the cache did not prevent it

- **A hit can still miss.** The nightly native leg restored its dependency entry by exact key and its
  Gradle user home by partial key, then still fetched the Kotlin BOM over the network. Each of the
  four failures had a cache restore that did not hold the artifact.
- **Pull requests do not add to it.** `setup-gradle` saves only from the default branch, so a pull
  request that bumps a dependency always downloads it.
- **Releases turn it off on purpose.** `service-release.yml` passes `cache-disabled: true` to every
  job, so a release resolves everything over the network.
- **Entries are evicted.** GitHub evicts a cache entry after seven days without use, or once the
  repository's caches exceed 10 GB.

The `ReserveCacheError` in the nightly log is benign. Two matrix legs saved the same
content-addressed key at once, and the second skipped it ("entry with key already exists").

Two facts about Gradle 9.8.1 shaped the remedy, both read from its classes:

- **It already retries a 429,** along with a 408, a 5xx or a network error. It tries three times,
  1 s and 2 s apart (`org.gradle.internal.repository.max.tentatives` and `.initial.backoff`).
- **It moves to the next repository only on a 404.** An error disables the repository and fails
  the resolution, so a fallback repository adds nothing against a 429.

## Remedies and their cost

| Remedy | Covers releases | Covers macOS and Windows | Cost | Outcome |
| --- | --- | --- | --- | --- |
| Central mirror first, plugin portal after it | yes | yes | a third party serves the first request; SHA-256 verification still checks every byte | adopted (ADR-0079) |
| More Gradle retries (5 tries, 2 s to 16 s apart) | yes | yes | Gradle-internal properties; a failing build takes up to 30 s longer to fail | adopted (ADR-0079) |
| A warm, shared read-only cache | no, releases run cold by policy | yes | already in place; misses after a bump or an eviction | already in use |
| Central before the plugin portal, without a mirror | yes | yes | every request still goes to Central | rejected |
| A download step with backoff ahead of the build | yes | yes | repeats Gradle's retry loop in every job, then the build resolves again | rejected |
| A pre-seeded dependency archive | yes | yes | a second artifact to build, store, keep current with each bump and prove the provenance of | rejected |
| A prebuilt Linux build image (GHCR) | only if the release policy changes | no: GitHub runs container jobs only on Linux | an image rebuild on each dependency, wrapper or JDK bump, plus digest pinning and an attestation | tracked in #596 |
| A blanket job retry | yes | yes | re-runs real failures until they pass | not considered |

A cold local build with ADR-0079 in place made 472 requests to the mirror, 14 to the plugin portal
(the plugins only it hosts) and none to Maven Central. The cold-path CI runs on PR #595 are
`service` 44249070069 and `service-native` 44249066154.

## The prebuilt build image (#596)

The operator asked on 2026-11-18 why CI does not use a container image to avoid downloading
dependencies again on every run. Such an image helps the Linux JVM jobs:

- `service`
- `whole-pack`
- the Javadoc build in `docs-site`
- `sonar`
- `mutation-testing`
- `timing-nightly`
- `open-packs-drift`

It would carry the pinned JDK, the Gradle distribution and a warmed dependency cache. It does not
help the native matrix, whose macOS and Windows legs cannot run container jobs. Nor would it help a
release unless the operator rules that a release may build from the image. It is a speed and volume
question, not a failure question, so it is tracked as a `priority:P3` follow-up.
