---
title: Hexagonal service
description: The Gradle modules of the Java charter service and the rule that infrastructure depends inward on a plain-Java domain.
doc_status: draft
updated: 2026-10-13
type: Architecture
kind: Explanation
related:
  - architecture/overview
  - architecture/contract-and-dtos
---

# Hexagonal service

> **State:** design only. The module names are proposals for the first Gradle build; core
> ADR `2026-10-03-1` fixes the dependency direction, not the names.

## Stack

The charter service follows the Mission Status Read API service (core ADR `2026-10-01-2`,
Amendment 2026-10-03):

- **Runtime:** Java 25, Spring Boot 4, Spring MVC on virtual threads.
- **Build:** Gradle multi-project, with strict dependency verification like core's
  `contracts/` build.
- **Distribution:** a GraalVM native binary per OS is the sibling's model. Whether the charter
  ships native or as a JVM artifact is a deferred decision ([open questions](/plans/open-questions/)).

## Modules

| Module | Depends on | Holds |
|---|---|---|
| `charter-domain` | nothing outside the JDK | Charter meaning and invariants: packs, artifacts, the DRG, layers, activation state, resolution services, repository **ports** |
| `charter-read-app` | domain | Read use cases: action-scoped context, profile resolution, graph queries, pack and activation listings |
| `charter-write-app` | domain | Write use cases, one per migrated operation (activation commit, charter document save, compile) |
| `charter-api` | — (generated) | Server interfaces and transport DTOs generated from `contracts/charter/openapi.yaml` |
| `adapter-in-rest` | read-app, write-app, `charter-api`, Spring | REST controllers that map transport DTOs to use-case inputs and back |
| `adapter-in-mcp` | read-app | MCP tools over the same read use cases. Planned; ships only after its authorisation decision |
| `adapter-out-yaml` | domain | Lossless YAML document models, source fingerprints, and implementations of the repository ports |
| `charter-service` | everything above | Spring Boot wiring, configuration, loopback binding and credential checks |
| `charter-conformance` | read-app, fixtures | Runs the shared fixture corpus against the service |

## The rule

```text
adapter-in-rest ──┐
adapter-in-mcp  ──┴─> read application ──┐
adapter-in-write ───> write application ─┴─> domain (models + ports)
adapter-out-yaml / later SQL ──────────────> domain repository ports
```

- The domain is plain Java. It imports no YAML, JSON, HTTP, MCP, database or framework
  library. Spring and the generated OpenAPI types live in the inbound adapters only.
- The read and write applications share domain types and semantic services. They do not
  depend on each other and they share no transport or persistence models.
- Repository interfaces and invariants belong to the domain; their implementations belong to
  outbound adapters. A later SQL projection can replace or sit beside the YAML adapter
  without touching the domain, the inbound adapters or the core seam.

An ArchUnit (or equivalent) test pins this direction, so the rule is a gate, not a habit.

## Three models, kept apart

| Model | Optimised for | Lives in |
|---|---|---|
| **API models** (transport DTOs) | Consumers: versioned, closed REST/JSON/MCP shapes | `charter-api`, generated |
| **Domain models** | Charter meaning and invariants | `charter-domain` |
| **Document models** | Source syntax and provenance, byte-preserving | `adapter-out-yaml` |

The application layer maps across these through ports. Passing a transport DTO into the
domain, or a YAML node out of an adapter, is a design error.
