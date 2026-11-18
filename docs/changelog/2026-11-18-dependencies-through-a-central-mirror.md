---
okf_version: "0.2"
title: A Maven Central rate limit no longer fails a build or a release
description: The build resolves Central artifacts from Google's Maven Central mirror, plugins from the mirror then the plugin portal, and retries a transient download error five times.
doc_status: active
updated: 2026-11-18T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0079-dependencies-resolve-through-a-maven-central-mirror
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if a build failed because Maven Central answered HTTP 429 (Too Many Requests).
---

# 2026-11-18 — A Maven Central rate limit no longer fails a build or a release

**Maven Central's HTTP 429 no longer turns a build red.** On 2026-11-18 the nightly native build's
`linux-x64` leg failed in ten seconds: the plugin portal redirected a Kotlin BOM request to Maven
Central, which answered 429 Too Many Requests. The first native build of PR #594 failed the same way.
The release workflows build without the Gradle cache, so a tag push faced the same risk on every job.

- **Mirror first.** Both the root build and `build-logic` resolve from Google's Maven Central mirror
  first. Plugins then come from the plugin portal (markers and plugins only it hosts); libraries then
  come from Maven Central, for an artifact the mirror does not hold yet. A cold build asks Maven
  Central for nothing.
- **`build-logic` declares its plugin repositories.** Its `kotlin-dsl` plugin's classpath came from
  the plugin portal alone, which is the request that failed.
- **Longer retries.** A transient download error (429, 408, 5xx or a network error) is tried five
  times, 2 s to 16 s apart, instead of three times within 3 s. A 404, 401 or 403 is never retried.
  The wrapper retries its distribution download three times.
- **Unchanged:** strict SHA-256 dependency verification checks every artifact from any repository,
  and the release workflows still build without the cache.
- **Rehearse the cold path.** A manual run of `service` or `service-native` takes `cache-disabled`,
  so a branch can build the way a release does.

The decision and the alternatives weighed are in
[ADR-0079](/adr/0079-dependencies-resolve-through-a-maven-central-mirror/).
