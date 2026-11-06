# Implementation Plan: a generated Java client and a "generate your own client" how-to

**Branch**: `claude/client-java-218-224` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/client-java-and-generate-your-own-01M6P952/spec.md`;
research: [research.md](research.md).

## Summary

A new Gradle module `:client-java` (directory `client-java/`) generates the charter client with openapi-generator's
`java` generator and `okhttp-gson` library from the published bundle, compiles it for Java 21, and checks it inside
`verifyAll`. A build-logic normalizer makes the client tolerate members a later 0.x minor adds; a generator option
does the same for enum values. One hand-written class, `ProblemDetails`, reads an error body as the contract's
Problem. A docsite how-to tells any reader how to fetch, verify and generate a Java or Python client with the same
generator version and the same tolerance.

## Planning answers (operator rulings on #217, #218, #224)

| Question | Answer |
|---|---|
| Generator and library | openapi-generator `java` / `okhttp-gson`, the catalog's pin (7.26.0) |
| Consumer Java | 21 and 25; compile `--release 21` on the Java 25 toolchain |
| Coordinates | package `ai.speckitty.charter.client`; group `ai.speckitty.charter`, artifact `charter-client` (decision 01M6P9CHWRCVGDZN6CA7GG191W, raised for confirmation) |
| Licence | MIT; `SPDX-License-Identifier: MIT` plus a do-not-edit note in every generated file |
| Problem helper | ships in the client, hand-written, with tests |
| Python | no published client; a Python section in the how-to |
| Timing | now, against `main`'s snapshot; #219–#223 and #225 after 0.1.0 |

## Technical Context

**Language/Version**: Java 25 toolchain, bytecode for Java 21; Kotlin DSL (build-logic)
**Primary Dependencies**: openapi-generator 7.26.0 (Gradle plugin, already in build-logic); client runtime okhttp
4.12.0, logging-interceptor 4.12.0, gson 2.11.0, gson-fire 1.9.0, jakarta.annotation-api 3.0.0
**Storage**: N/A
**Testing**: JUnit 6 + AssertJ in `client-java/src/test`; a Gradle check task for reproducibility; build-logic
normalizer test
**Target Platform**: any Java 21+ JVM
**Project Type**: one library module beside the service modules
**Performance Goals**: N/A (build adds one generation and one compile, a few seconds)
**Constraints**: strict SHA-256 dependency verification; configuration cache; no contract change
**Scale/Scope**: about 70 generated models, 9 API classes, one hand-written class

## Charter Check

| Gate | Status |
|---|---|
| `verifyAll` on Java 25, strict verification | `:client-java:check` joins `verifyAll` by inclusion; two new verified artifacts, additions only (NFR-001). |
| Style gates on hand-written code | `ProblemDetails` and the tests keep every gate; generated code is exempt by source set, as AOT code is in `charter.native-image`. |
| Complexity gate (ADR-0058) | `ProblemDetails.from` is one method, cyclomatic 4, nested-if depth 1. |
| Docs checks | The how-to, the build-guide section and the changelog entry. |
| Adversarial squad | Pre-PR squad (closeout), lenses: architecture/build, contract consumer, docs/supply chain. |
| No unverified dependency | See R5. |
| Contract and packs untouched | Yes (C-002). |
| Designed without regard to core | Core is named only as one consumer that generates its own client (ADR-0021). |

## Design

### D-1: The module and its source sets

- `client-java/build.gradle.kts` applies `charter.client-java` only; the build lives in the convention plugin, as
  `charter-api`'s does in `charter.contract-api`.
- Source set `generated`: the generator's `src/main/java`. Its compile drops `-Xlint:all -Werror` and Error Prone;
  `checkstyleGenerated` is disabled. Spotless reads only `src/`.
- `main`: `ProblemDetails`. `test`: the helper's tests and the output checks.
- `jar` and `sourcesJar` carry both source sets. `group`, `archivesName` and `version` (= the contract's
  `info.version`) record the coordinates; nothing is published (#220, #225).

### D-2: Input and options

- Input: the `contractBundle` variant, filtered to `charter-openapi.yaml`.
- `enumUnknownDefaultCase=true`, `disallowAdditionalPropertiesIfNotPresent=false`, `openApiNullable=false`,
  `dateLibrary=java8`, `hideGenerationTimestamp=true`, `useJakartaEe=true`, `annotationLibrary=none`,
  `documentationProvider=none`, `serializableModel=false`; no tests or docs generated.
- `NORMALIZER_CLASS = TolerantClientNormalizer` (research R2).
- `templateDir = client-java/templates`, holding only `licenseInfo.mustache`.

### D-3: Checks

| Check | Where | Proves |
|---|---|---|
| `ProblemDetailsTest` | test | FR-009, FR-010 |
| `AdditiveMinorTest` | test | FR-004, FR-005 (the fuller suite is #221's) |
| `ClientOutputTest` | test | FR-006 (class-file major 65), FR-007 (header) |
| `checkClientReproducible` | Gradle, in `check` | FR-012 |
| `SingleModelPerSchemaNormalizerTest`, `TolerantClientNormalizerTest` | build-logic | the walker refactor keeps the server's behaviour; the client's normalizer opens closed objects and keeps open maps |

### D-4: CI

- `verifyAll` already runs every subproject's `check`, so `service.yml`'s Verify step builds and checks the client.
  No new job.
- `ci.yml`'s `service_re` and `service.yml`'s `push.paths` gain `client-java/`, so a change to the client alone
  runs the service group. Appended only (C-006).

### D-5: The how-to (`docs/guides/generate-a-client.md`)

1. Get the contract: the docsite copy and its `.sha256`; or `GET /api/v1/openapi.yaml` from a running service
   (with the worktree token), whose ETag is the same digest. From 0.1.0, the release assets.
2. Verify: `sha256sum -c`.
3. The generator: `openapi-generator-cli` at the pinned version, its jar checked against Central's `.sha1`.
4. Java: open the closed objects (a short Python script), generate okhttp-gson with our options, build. Or wait
   for this repository's client (#220).
5. Python: generate with `disallowAdditionalPropertiesIfNotPresent=false` and `enumUnknownDefaultCase=true` plus a
   one-method enum template override.
6. Caveats for any language: unknown enum values, closed objects, nullability as type arrays, problem+json, the
   literal write paths beside templated reads, the 0.x versioning rule.
7. A test in `:client-java` checks that the how-to names the catalog's generator version, so a bump cannot leave
   the page stale.

### D-6: Documentation of the build

`docs/guides/service-build.md` gains a short "The Java client" section; `docs/guides/README.md` lists the how-to;
a changelog entry `docs/changelog/2026-11-05-generated-java-client.md`.

## Project Structure

```text
build-logic/src/main/kotlin/
├── charter.client-java.gradle.kts      # new: the client build
├── CheckTreesIdentical.kt              # new: the reproducibility check task
├── SchemaWalker.kt                     # new: extracted from SingleModelPerSchemaNormalizer
├── SingleModelPerSchemaNormalizer.kt   # uses SchemaWalker; behaviour unchanged
└── TolerantClientNormalizer.kt         # new
build-logic/src/test/kotlin/TolerantClientNormalizerTest.kt
client-java/
├── build.gradle.kts
├── templates/licenseInfo.mustache
└── src/{main,test}/java/ai/speckitty/charter/client/
docs/guides/generate-a-client.md        # new how-to
docs/guides/service-build.md, docs/guides/README.md, docs/changelog/2026-11-05-*.md
gradle/libs.versions.toml, gradle/verification-metadata.xml, settings.gradle.kts
.github/workflows/ci.yml, .github/workflows/service.yml   # path filters only
```

## Implementation Concern Map

| IC | Concern | FRs | WP |
|---|---|---|---|
| IC-01 | Client module, normalizer, header, checks, dependencies | FR-001–FR-013 | WP01 |
| IC-02 | CI path filters and the build guide | FR-013, FR-016 | WP02 |
| IC-03 | The how-to and its version guard | FR-014, FR-015 | WP03 |

## Risks

| Risk | Mitigation |
|---|---|
| #240 and #125/#126 change the contract | Generation is part of the build; nothing generated is committed. |
| A generator bump changes `pojo.mustache` or the enum handling | `AdditiveMinorTest` fails on any loss of tolerance; the how-to's version guard flags the doc. |
| The `additionalProperties` map changes `equals` for models with unknown members | Accepted; documented in the how-to's caveats. |
| Coordinates ruling differs from the brief | Recorded as a decision and raised on #217 and to the orchestrator; two lines to change. |
| Parallel session A edits `ci.yml` | Append-only edit; rebase on `main` before review. |
