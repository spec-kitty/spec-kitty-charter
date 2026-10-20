---
title: Building the service
description: How to build and test the charter service locally, what each verification task checks, and how to change the contract or a dependency.
doc_status: active
updated: 2026-10-19T00:00:00Z
type: Guide
kind: How-To
related:
  - architecture/hexagonal-service
  - architecture/contract-and-dtos
  - adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you build, test or change the contract.
---

# Building the service

How to build the charter service and run every check CI runs.

## Prerequisites

- **Java 25.** Gradle never downloads a toolchain (`org.gradle.java.installations.auto-download=false`).
  A machine without Java 25 can pass `-Pcharter.javaVersion=21` for a local build; CI never
  does.
- **A `spec-kitty-open-packs` checkout**, by default beside this repository
  (`../spec-kitty-open-packs`), or anywhere with `-PopenPacksDir=<path>`. The checkout must be
  clean and at `openPacksRef` in `gradle.properties` (currently
  `01edb4ebab308249a10ed4a4a01d77741ac1d14b`); the build refuses otherwise, and prints the
  command that fixes it (a `git fetch` and `git checkout` of the pinned commit, after you
  stash or clean the checkout).
- **The full Git history and tags.** The breaking-change check compares the contract with its
  last released version (see [the released baseline](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md#the-released-baseline)) and refuses a shallow clone. In a clone
  you cannot deepen, pass `-PcontractBaseline=none` to skip that one check on purpose; CI
  never does. `verifyAll` in any shallow clone needs `-PcontractBaseline=none`, because the build
  refuses a shallow clone before it looks at tags. In a full clone, the check needs the
  `contract-charter-v*` tag of its baseline, merged into HEAD: the newest release the CHANGELOG
  records with the contract's major version. A release whose tag is not yet merged (as on a
  release PR) is skipped, and a new major version has no baseline, so it needs no tag. Fetch the
  tags if one is missing.
- **Network access on the first run.** The contract checks download vacuum and oasdiff at
  pinned versions and verify each archive's SHA-256 before using it (Linux and macOS, x86-64
  and arm64).

## Run everything

```bash
./gradlew verifyAll
```

`verifyAll` runs the `check` task of every project:

| Task | Checks |
|---|---|
| `:contracts:check` | The charter's own contract checks: `bundleContract`, `lintContract`, `lintFixtures`, `checkClosedResponseObjects`, `compatibilityFixtures` and `checkContractCompatibility` ([`contracts/lint/README.md`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md) says what each one does) |
| `:charter-api:validateContract` | The split OpenAPI document parses and validates |
| `:charter-api:generateContract` | Spring interfaces and DTOs are generated; controllers that drift from the contract fail to compile |
| `:<module>:spotlessCheck` | Hand-written main and test sources are formatted by palantir-java-format |
| `:<module>:checkstyleMain`, `checkstyleTest` | The Checkstyle rules in `config/checkstyle/checkstyle.xml`; any violation fails |
| `:<module>:compileJava` | `-Xlint:all -Werror`, Error Prone, and NullAway on `@NullMarked` code (test sources get Error Prone without NullAway) |
| `:<module>:javadoc` | Javadoc of main sources with doclint on and warnings as errors |
| `:<module>:test`, `jacocoTestReport` | Unit tests of each module, and its coverage report |
| `:charter-service:test` | The ArchUnit rules (dependency direction, a plain-Java core, `@NullMarked` packages) and every `@contract` scenario in `contracts/charter/scenarios/` |
| `:adapter-out-yaml:openPacksRoundTrip` | Every open-packs YAML file renders byte for byte, survives a rewrite of every scalar, and every pack loads and round-trips its graph. It fails, never skips, without a checkout |
| `:charter-reports:aggregateJavadoc` | One Javadoc site over the five hand-written service modules, doclint as errors ([Java API reference](/api/javadoc/)) |
| `:charter-reports:testCodeCoverageReport` | One JaCoCo report over the same modules |

The quality gates come from the `charter.hand-written` convention plugin, applied by every
hand-written module. The generated `charter-api` does not apply it: it applies
`charter.contract-api`, which reaches `charter.java-base` through `charter.spring-conventions`,
so it is exempt by construction and still compiles. A new hand-written module under `service/` must
also be listed in `service/charter-reports/build.gradle.kts`; the build fails, naming it,
until it is.

Not in `verifyAll`: mutation testing. `./gradlew pitest` runs PIT on `charter-domain` and
`charter-read-app`; `.github/workflows/mutation-nightly.yml` runs it nightly, and no pull
request runs it.

To fix formatting rather than report it, run `./gradlew spotlessApply`.

## Exceptions to a gate

`config/checkstyle/suppressions.xml` is the one register of exceptions to the hand-written
gates. It holds no entry today. An entry names the rule, the file, the reason and a sunset
date; no gate is relaxed anywhere else, and blanket suppressions are not allowed.

## Change the contract

1. Edit the split files under `contracts/charter/` and list new files in the directory's
   `_index.yaml`.
2. Write or change the scenario in `contracts/charter/scenarios/` that pins the behavior.
3. Add an entry to `contracts/charter/CHANGELOG.md`; a scenario change gets one too.
4. Run `./gradlew :contracts:check`, then `./gradlew verifyAll`.

The contract checks follow the conventions in
[`contracts/lint/README.md`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md).
A new lint rule lands with at least one planted fixture that fails it, and only it; that page
describes the steps.

Adding a property to a response schema is a breaking change, because response schemas are
closed ([Contract and DTOs](/architecture/contract-and-dtos/#versioning)).

## Release a contract version

Releasing a contract version is a short procedure with its own rules, kept in one place:
[the released baseline](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md#the-released-baseline). Follow it from a topic branch, and run
`./gradlew :contracts:check` before you open the pull request.

`contracts/_shared/` started as a copy of core's shared vocabulary. This repository's copy is
canonical ([ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/)): change
it here when there is reason to, and list the change in `contracts/_shared/VENDORED.md`.

## Change a dependency

Versions live in `gradle/libs.versions.toml`, tool plugins (Spotless, Error Prone, PIT)
included; convention plugins read them from the catalog. Dependency verification is strict,
so a new or changed artifact needs its checksum recorded:

```bash
./gradlew --write-verification-metadata sha256 verifyAll
```

Review the diff of `gradle/verification-metadata.xml`; never edit it by hand. The vacuum and
oasdiff binaries are not Gradle dependencies: their versions and SHA-256 checksums are pinned
in `contracts/build.gradle.kts`.

A module that is not a Spring module may still import the Spring Boot BOM as a platform, to
pin a transitive dependency to the version the service uses. `adapter-out-yaml` does this for
the Jackson and slf4j versions that networknt's JSON Schema validator brings in.
