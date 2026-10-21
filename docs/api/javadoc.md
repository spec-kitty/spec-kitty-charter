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

The Javadoc covers the three hand-written modules of the charter service, one group per
module on its overview page, with one index and one search across all of them:

| Module | Package | What it holds |
|---|---|---|
| `kernel` | `ai.speckitty.charter.kernel` | The shared kernel: the pack and Doctrine Reference Graph vocabulary (`kernel.domain`) and the pure validation library (`kernel.validation`) |
| `packs` | `ai.speckitty.charter.packs` | The packs slice: the domain and its repository ports, the read use cases (`application`), the file-based storage adapter (`infra.storage`) and the inbound REST adapter (`infra.api`) |
| `bootstrap` | `ai.speckitty.charter.bootstrap` | The Spring Boot wiring of the runnable service, the composition root |

It does not cover `charter-api`, the module generated from `contracts/charter/openapi.yaml`:
those types are the contract's, and the [service contract](/api/service-contract/) is where
they are specified. Where a hand-written type uses one, the reference names it without a
link.

## How it is built

`./gradlew verifyAll` builds the reference through the `service/charter-reports` project,
whose `aggregateJavadoc` task runs javadoc over the sources of the three modules with all of
javadoc's lint checks on and warnings as errors, so a public type or member without a
comment, or with a malformed one, fails the build. Each module's own Javadoc runs under the same rules.

The pages use this site's colors and type, in light and dark; dark follows your system
setting, because the site's theme switch does not reach them. The link at the foot of every
page leads back here.

To see the reference in a local build of the site, stage it first:

```sh
./gradlew :charter-reports:stageJavadocForSite   # copies it to site/public/javadoc
```

The same project builds the aggregated test coverage report of the three modules
(`testCodeCoverageReport`).
