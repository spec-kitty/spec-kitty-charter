---
title: Java API reference (Javadoc)
description: The Javadoc of the charter service's hand-written Java modules, built by the main build and published with this site as one searchable reference.
doc_status: active
updated: 2026-10-19T00:00:00Z
type: API
kind: Reference
related:
  - api/service-contract
  - architecture/contract-and-dtos
audience:
  - profile: charter-service-contributor
    guidance_text: Read this to find the reference for the service's Java types, and how it is built and checked.
  - profile: contract-consumer
    guidance_text: Read this to see the Java types behind the service contract; the contract itself stays the source of record.
---

# Java API reference (Javadoc)

**[Open the Javadoc](/javadoc/index.html)**

The Javadoc covers the five hand-written modules of the charter service, one group per
module on its overview page, with one index and one search across all of them:

| Module | Package | What it holds |
|---|---|---|
| `charter-domain` | `ai.speckitty.charter.domain` | Charter meaning and invariants: packs, the Doctrine Reference Graph and the repository ports |
| `charter-read-app` | `ai.speckitty.charter.read` | The read use cases over the domain |
| `adapter-out-yaml` | `ai.speckitty.charter.yaml` | The file-based adapter: the YAML document models and the repository adapters that implement those ports over a pack directory |
| `adapter-in-rest` | `ai.speckitty.charter.rest` | The inbound REST adapter, which implements the generated contract interfaces |
| `charter-service` | `ai.speckitty.charter.service` | The Spring Boot wiring of the runnable service |

It does not cover `charter-api`, the module generated from `contracts/charter/openapi.yaml`:
those types are the contract's, and the [service contract](/api/service-contract/) is where
they are specified. Where a hand-written type uses one, the reference names it without a
link.

## How it is built

`./gradlew verifyAll` builds the reference through the `service/charter-reports` project,
whose `aggregateJavadoc` task runs javadoc over the sources of the five modules with all of
javadoc's lint checks on and warnings as errors, so a public type or member without a
comment, or with a malformed one, fails the build. Each module's own Javadoc runs under the same rules.

The pages use this site's colors and type, in light and dark; dark follows your system
setting, because the site's theme switch does not reach them. The link at the foot of every
page leads back here.

To see the reference in a local build of the site, stage it first:

```sh
./gradlew :charter-reports:stageJavadocForSite   # copies it to site/public/javadoc
```

The same project builds the aggregated test coverage report of the five modules
(`testCodeCoverageReport`).
