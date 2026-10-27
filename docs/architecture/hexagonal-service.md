---
title: Hexagonal service
description: The Gradle modules of the Java charter service and the rule that infrastructure depends inward on a plain-Java domain.
doc_status: draft
updated: 2026-10-18T00:00:00Z
type: Architecture
kind: Explanation
related:
  - architecture/overview
  - architecture/contract-and-dtos
  - architecture/pack-registration-distribution
  - adr/0006-onion-vertical-slices-archunit
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the Gradle modules and the dependency rule you must keep.
---

# Hexagonal service

The Gradle modules of the Java service and the one dependency rule that organizes them: infrastructure depends inward on a plain-Java domain.

> **Status:** built for the first slice. The skeleton under `service/` is now `kernel`, `packs`, `profiles` (the agent-profile domain), `charter-api` and `deployable` (renamed from `bootstrap`) (with `charter-reports` and convention plugins in `build-logic/`), the realization of [ADR-0006](/adr/0006-onion-vertical-slices-archunit/); the earlier flat modules (`charter-domain`, `charter-read-app`, `adapter-in-rest`, `adapter-out-yaml`, `charter-service`) are superseded. [Architectural patterns applied](/architecture/architectural-patterns-applied/) shows the realized layout. The further slices in the table below remain design. Core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) (the staged Java service strangler) fixes the dependency direction, not the names. [What the labels mean](/#status).
>
> **Module shape updated.** [ADR-0006](/adr/0006-onion-vertical-slices-archunit/) partitions the service **top-level by vertical concern slice** (one Gradle module per slice, layers as packages within), not by the earlier horizontal read/write module split. The read/write distinction survives as a **sub-split inside each slice's application ring**, and core ADR `2026-10-03-1`'s staged read-first/write-later strangler runs within that shape. The [pack registration and distribution](/architecture/pack-registration-distribution/) design is the worked example.

## Stack

The charter service follows the Mission Status Read API (core ADR [`2026-10-01-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-01-2-mission-status-read-api-and-dashboard-extraction.md) (the Mission Status Read API),
Amendment 2026-10-03):

- **Runtime:** Java 25, Spring Boot 4, Spring MVC on virtual threads.
- **Build:** Gradle multi-project, with strict dependency verification like core's
  `contracts/` build.
- **Distribution:** a GraalVM native binary per OS is the sibling's model. Whether the charter
  ships native or as a JVM artifact is a deferred decision ([open questions](/plans/open-questions/)).

## Modules

One Gradle module per **vertical concern slice** ([ADR-0006](/adr/0006-onion-vertical-slices-archunit/)); the onion layers are packages *within* each slice (`domain`, `application` with `read`/`write` sub-packages, and `infra` with `api.rest`, a reserved `api.graphql`, and `storage`).

| Module | Depends on | Holds |
|---|---|---|
| `kernel` | nothing outside the JDK | The shared domain kernel: the cross-slice vocabulary — packs, the CRG, `PackSource`, artifact kinds, refusal codes. Pure; no infra |
| `packs` | `kernel` | The packs slice (pack registration, validation and storage), built; its layers are: `domain` (models, ports, services), `application` (read/write use cases), `infra` (`api.rest`, reserved `api.graphql`, `storage`) |
| `missiontypes` | `kernel` | The mission-types slice, same four layers as packages |
| `profiles` | `kernel` | The agent-profiles slice, built with its `domain` ring only (`AgentProfile`); `packs.infra.storage` stores the kind ([ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/), amendment of 2026-10-27) |
| `artifacts` | `kernel` | The other-charter-artifacts slice (directives, tactics, styleguides, procedures, …), same four layers as packages |
| `charter-api` | — (generated) | Server interfaces and transport DTOs generated from `contracts/charter/openapi.yaml` (authored here, ADR-0002); referenced only from each slice's `infra.api` |
| `deployable` | every slice | The composition root, renamed from `bootstrap`: the `@SpringBootApplication`, an aggregate `DeployableConfiguration` that imports each layer's `@Configuration`, loopback binding and credential checks. The only module that knows every slice; it owns the ArchUnit gate |
| `charter-conformance` | the slices, fixtures | Runs the conformance corpus against the service |

Within a slice, the read and write use cases are packages inside its `application` ring, not separate modules: core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md)'s staged read-first/write-later strangler runs within each slice.

## The rule

```text
infra.api.rest / infra.api.graphql ──┐
                                     ├─> application (read / write) ──> domain (models + ports)
infra.storage (file / db / blob /    │
               resolver) ────────────┴───────────────────────────────> domain repository ports
```

- Arrows point **inward only**: the entry-point infra ring knows the application ring; the
  application and storage infra rings know the domain ring; the domain ring knows only itself
  and the `kernel` domain.
- **Only the domain and the kernel are pure.** The `application`, `infra.storage` and `infra.api`
  rings are peer **edges** around that core. Application use cases are Spring `@Service` beans that
  orchestrate domain ports ([ADR-0006](/adr/0006-onion-vertical-slices-archunit/) revision
  2026-10-21). The `APPLICATION_IS_EDGE` rule keeps an application class from reaching another
  slice's infra, another slice's domain, or `deployable`.
- The domain is plain Java. It imports no YAML, JSON, HTTP, MCP, GraphQL, database or framework
  library, and carries no infra annotations. Spring and the generated OpenAPI types live in the
  infra adapters only.
- The read and write use cases share domain types and services. They do not depend on each
  other and share no transport or persistence models.
- A slice's domain depends on no other slice except `kernel`; cross-slice vocabulary goes
  through the kernel.
- Repository interfaces and invariants belong to the domain; their implementations belong to
  `infra.storage`. A later SQL or object-store projection can replace or sit beside the
  file-based adapter without touching the domain, the entry-point adapters or the core seam.

An ArchUnit test pins these directions, so the rule is a gate, not a habit (the named rule set
is in [pack registration and distribution](/architecture/pack-registration-distribution/) §6).
`HexagonalArchitectureTest` (in `deployable`) pins these directions over the whole service. The
`kernel` module additionally applies the `charter.domain-conventions` plugin, which refuses any
external dependency at configuration time — so the kernel is held pure both at build time and by
ArchUnit, while each slice's `domain` (for example `packs.domain`) is kept pure by ArchUnit.

## Three models, kept apart

| Model | Optimized for | Lives in |
|---|---|---|
| **API models** (transport DTOs) | Consumers: versioned, closed REST/JSON/GraphQL shapes | each slice's `infra.api`, generated into `charter-api` |
| **Domain models** | Charter meaning and invariants | each slice's `domain`, plus `kernel.domain` |
| **Document models** | Source syntax and provenance, byte-preserving | each slice's `infra.storage` (the file-based adapter) |

The application layer maps across these through ports. Passing a transport DTO into the
domain, or a YAML node out of an adapter, is a design error.
