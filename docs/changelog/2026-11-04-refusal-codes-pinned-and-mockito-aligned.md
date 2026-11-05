---
okf_version: "0.2"
title: "Refusal checks pin their code; Mockito follows Spring Boot's version"
description: "Fifteen storage-layer refusal checks now fail on a wrong refusal code, and the test build resolves one Mockito version with a verification metadata file regenerated from scratch."
doc_status: active
updated: 2026-11-04T00:00:00Z
type: Changelog
kind: Changelog
related:
  - plans/engineering-notes/test-suite-assessment-2026-11-03
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if a refusal test now names a code, or if you wonder why Mockito is declared though no test uses it.
---

# 2026-11-04 — Refusal checks pin their code; Mockito follows Spring Boot's version

Two follow-ups from the [test suite assessment](/plans/engineering-notes/test-suite-assessment-2026-11-03/):

- **Refusal checks pin their code**
  ([#369](https://github.com/spec-kitty/spec-kitty-charter/issues/369)). The 15 `packs` tests that accepted
  any `CharterRefusal` now assert the refusal code the storage or registration path raises. A change that
  refuses with the wrong code now fails a test. No test was added, and no test counts assertion shapes.
- **Mockito stays, at Spring Boot's version**
  ([#373](https://github.com/spec-kitty/spec-kitty-charter/issues/373)). No test uses Mockito, but
  `spring-boot-starter-test` brings it to `deployable` and `packs` anyway, so it stays declared and attached
  as a Java agent, with the reason written beside it. The catalog now pins the version Spring Boot manages
  (5.23.0), so every test JVM resolves one version. The agent resolves consistently with the test runtime
  classpath, so a later Spring Boot bump moves both together.
- **The verified dependency list was regenerated from scratch.** Gradle never removes entries from
  `gradle/verification-metadata.xml`, so superseded versions had piled up. It now lists 569 components
  instead of 578. The superseded versions were removed, and so was one superseded file (the
  commons-codec 1.17.1 jar) inside a component that stays. 6 components were added: the native build plugin's test configurations resolve
  them. No checksum of a kept artifact changed. The
  [build guide](/guides/service-build/) now says how to prune the file.
- **A pack that is not configured is not found on the read side too.** The storage's read side refused an
  identifier that matches no configured pack as `pack_unreadable`, while the write side refused it as
  `pack_not_found`. Both now refuse it as `pack_not_found`, "Pack X was not found.". The HTTP answer does
  not change: the read services already turned it into a 404 `pack_not_found`.
