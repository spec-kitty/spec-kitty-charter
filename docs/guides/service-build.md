---
title: Building the service
description: How to build and test the charter service locally, what each verification task checks, and how to change the contract or a dependency.
doc_status: active
updated: 2026-10-18
type: Guide
kind: How-to
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
  (`../spec-kitty-open-packs`), or anywhere with `-PopenPacksDir=<path>`. CI checks out the
  commit pinned in `.github/workflows/service.yml`.

## Run everything

```bash
./gradlew verifyAll
```

| Task | Checks |
|---|---|
| `:charter-api:validateContract` | The split OpenAPI document parses and validates |
| `:charter-api:generateContract` | Spring interfaces and DTOs are generated; controllers that drift from the contract fail to compile |
| `:<module>:test` | Unit tests of each module |
| `:charter-service:test` | The ArchUnit dependency rule and every scenario in `contracts/charter/scenarios/` |
| `:adapter-out-yaml:openPacksRoundTrip` | Every open-packs YAML file renders byte for byte, survives a rewrite of every scalar, and every pack loads and round-trips its graph. It fails, never skips, without a checkout |

## Change the contract

1. Edit the split files under `contracts/charter/` and list new files in the directory's
   `_index.yaml`.
2. Write or change the scenario in `contracts/charter/scenarios/` that pins the behavior.
3. Add an entry to `contracts/charter/CHANGELOG.md`.
4. Run `./gradlew verifyAll`.

`contracts/_shared/` is a vendored copy of core's shared vocabulary; change it in core and
refresh the copy (`contracts/_shared/VENDORED.md`).

## Change a dependency

Versions live in `gradle/libs.versions.toml`. Dependency verification is strict, so a new or
changed artifact needs its checksum recorded:

```bash
./gradlew --write-verification-metadata sha256 verifyAll
```

Review the diff of `gradle/verification-metadata.xml`; never edit it by hand.
