---
okf_version: "0.2"
title: A generated Java client, and how to generate your own
description: The build generates an okhttp-gson Java client from the contract bundle for Java 21 and 25; a new how-to shows how to generate a Java or Python client from the contract.
doc_status: active
updated: 2026-11-05T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/generate-a-client
  - guides/service-build
  - adr/0021-contract-first-seam-generated-client-and-routing-proxy
audience:
  - profile: contract-consumer
    guidance_text: Read this to learn what the Java client tolerates and where to start generating your own client.
  - profile: charter-service-contributor
    guidance_text: Read this if you change the contract or the generator pin; the client regenerates in the build.
---

# 2026-11-05 — A generated Java client, and how to generate your own

**`:client-java` generates the charter client from the contract bundle**
([#218](https://github.com/spec-kitty/spec-kitty-charter/issues/218), epic
[#217](https://github.com/spec-kitty/spec-kitty-charter/issues/217)). It uses openapi-generator's `java` generator and
`okhttp-gson` library at the pinned version, compiles for Java 21 on the Java 25 toolchain, and runs inside `verifyAll`:

- A member or an enum value that a later 0.x minor adds is read, not refused. Closed objects are read as open on the
  client side only, and unknown enum values map to `UNKNOWN_DEFAULT_OPEN_API`.
- `ProblemDetails.from(ApiException)` reads an error body as the contract's `Problem`, and is empty for a body that
  is not one.
- Every generated file carries an MIT SPDX header and a do-not-edit note; two generations are byte-identical.

Nothing is published yet: the release zip, the JDK matrix and the quickstart wait for contract 0.1.0.

**[Generate your own client](/guides/generate-a-client/)** ([#224](https://github.com/spec-kitty/spec-kitty-charter/issues/224))
shows how to fetch and verify the contract, and how to generate a Java or Python client with the same generator version
and the same tolerance. The contract is a snapshot until 0.1.0.
