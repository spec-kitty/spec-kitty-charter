---
okf_version: "0.2"
title: The service ships as a native executable on four targets, released on the service-v* line
description: The charter service builds as a GraalVM native executable on four targets plus a jar fallback, published by a service-v* tag workflow with per-asset checksums and provenance.
doc_status: active
updated: 2026-11-01T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0046-service-release-line-and-native-packaging
  - adr/0023-ci-gate-required-check
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for how the service is now built, packaged and released, and what the release workflow refuses.
---

# 2026-11-01 — The service ships as a native executable on four targets, released on the service-v* line

Closes [#241](https://github.com/spec-kitty/spec-kitty-charter/issues/241), the build-and-check half of
[#71](https://github.com/spec-kitty/spec-kitty-charter/issues/71), under
[ADR-0046](/adr/0046-service-release-line-and-native-packaging/). No release is published yet.

**Before:** the service was built only as a Java 25 jar, and had no release line of its own.

**Now:** CI builds the service as a GraalVM native executable on linux-x64, linux-arm64, macos-arm64 and
windows-x64, plus the jar as a fallback, and starts every one of them before it is ever published.

- **The container profile is read at run time, not frozen at the native build,** so a native binary
  started under the container profile still applies its start-up rules and `charter.container.*`
  validation.
- **A `service-v*` tag publishes a release** of the five assets, each with a SHA-256 and a GitHub build
  provenance attestation.
- **The release workflow refuses rather than guesses:** it publishes nothing for a malformed, placeholder
  or `-SNAPSHOT` tag, a tag that is not the build version, a final release whose contract is unreleased, a
  commit that is not on `main`, a target that failed, a tag that moved after the build, or a release that
  already exists. A manual run is a rehearsal that publishes nothing.

The procedure is in [Building the service](/guides/service-build/).
