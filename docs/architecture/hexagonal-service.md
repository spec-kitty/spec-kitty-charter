---
title: Hexagonal service
description: The Gradle modules of the Java charter service and the rule that infrastructure depends inward on a plain-Java domain.
doc_status: draft
updated: 2026-11-10T00:00:00Z
type: Architecture
kind: Explanation
related:
  - architecture/overview
  - architecture/contract-and-dtos
  - architecture/pack-registration-distribution
  - guides/compose-packs
  - changelog/2026-11-07-a-pack-registration
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

One Gradle module per **vertical concern slice** ([ADR-0006](/adr/0006-onion-vertical-slices-archunit/)); the onion layers are packages *within* each slice (`domain`, `application` with `read`/`write` sub-packages, and `infra` with `api.rest`, a reserved `api.graphql`, `storage` and, in the packs slice, the outbound `fetch` adapter).

| Module | Depends on | Holds |
|---|---|---|
| `kernel` | nothing outside the JDK | The shared domain kernel: the cross-slice vocabulary — packs, the CRG, `PackSource`, artifact kinds, refusal codes, the `markdown` form's line rules (#299), and `config.StartupRefusal`, the refusal to start on a setting that every slice's wiring throws (#454). Pure; no infra |
| `packs` | `kernel`, `web-transport` | The packs slice (pack registration, validation and storage), built; its layers are: `domain` (models, repository ports, services), `application` (read/write use cases, and the ports of what a use case needs from outside the domain: `GuideFormat`, `GuideFetcher`), `infra` (`api.rest`, reserved `api.graphql`, `storage`, `fetch`, the outbound adapter that fetches a toolguide's guide by URI ([ADR-0043](/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides/)) and a pack from an https archive, a git repository or a local path, and `archive`, which reads an uploaded or fetched pack archive into staging) |
| `activations` | `kernel`, `packs`, `web-transport` | The activation register: the document, its storage, its write rules and its HTTP adapter (with its request-body advice and problem mapping); asks `packs` through one port (`AvailablePacks`, implemented in `infra.packs`). Its layers are `domain`, `application`, `infra.api` and `infra.packs`; the composition root chooses where its documents are stored |
| `missions` | `kernel` | The missions slice, domain ring only (`MissionType`, its steps and expected artifacts); its other rings are in `missions-edge`. `packs.infra.storage` stores the kind |
| `missions-edge` | `missions`, `packs`, `activations`, `web-transport`, `charter-api` | The `application`, `infra.api`, `infra.packs` and `infra.activations` rings of the missions slice, in the slice's own package names. They live apart because `packs` depends on `missions`, like `profiles-edge` ([ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/), amendment of 2026-11-09). `infra.packs` and `infra.activations` are the only packages that name those slices |
| `artifacts-edge` | `kernel`, `packs`, `activations`, `web-transport`, `charter-api` | The `application`, `infra.api`, `infra.packs` and `infra.activations` rings of the asset, skill and references reads, under `ai.speckitty.charter.artifacts`, for both trees (a pack and an activation). It has no domain module: its application ring speaks in kernel types and its own value records. The reads live here because `activations` names `packs` only from its own adapter, so it cannot render a packs `Asset` or `Skill`; it is a domain-less read-composition edge ([ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/), amendment of 2026-11-13, beside the slice edges of 2026-11-09). `infra.packs` and `infra.activations` are the only packages that name those slices, and neither slice names the edge |
| `profiles` | `kernel` | The agent-profiles slice, domain ring only (`AgentProfile`); its other rings are in `profiles-edge`. `packs.infra.storage` stores the kind ([ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/), amendment of 2026-10-27) |
| `profiles-edge` | `profiles`, `packs`, `activations`, `web-transport`, `charter-api` | The `application`, `infra.api`, `infra.packs` and `infra.activations` rings of the profiles slice, in the slice's own package names. They live apart because `packs` depends on `profiles`: rings that ask `packs` would make a cycle inside `profiles` ([ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/), amendment of 2026-11-09). `infra.packs` and `infra.activations` are the only packages that name those slices |
| `artifacts` | `kernel` | The other-charter-artifacts slice (directives, tactics, styleguides, procedures, …), same four layers as packages |
| `pack-validation` | `kernel`, `profiles`, `missions`, the parsing libraries | Every pack check as a ruleset of the kernel's validation vocabulary, with the codecs, the classification and the load they need, under `ai.speckitty.charter.packvalidation`. No Spring, HTTP, commit store or lock: the `PACK_VALIDATION_IS_FRAMEWORK_FREE` rule pins its classes and its build pins its dependencies. The packs slice and `pack-check` run it ([ADR-0075](/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check/)); a skeleton until #553 moves the load in |
| `pack-check` | `pack-validation`, a JSON writer | The standalone `pack-check` CLI under `ai.speckitty.charter.packcheck`: a thin main that checks packs without the service's closure walk and writes the contract's problem JSON, its own jar on the `service-v*` release ([ADR-0075](/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check/)). Held to `PACK_VALIDATION_IS_FRAMEWORK_FREE` too; a skeleton until #478 adds the main |
| `charter-api` | — (generated) | Server interfaces and transport DTOs generated from `contracts/charter/openapi.yaml` (authored here, ADR-0002); referenced only from each slice's `infra.api` and the shared `web-transport` |
| `web-transport` | `kernel`, `charter-api` | The service-wide HTTP transport (package `ai.speckitty.charter.web`): problem rendering (`Problems`, `ProblemHandler`, and `InvalidParameter`, the wording of a refused request parameter, #411), refusal mapping (`RefusalHandler`), the JSON body guard (`JsonBodyGuard`) and the problem and link JSON (`ProblemJson`). An adapter ring shared by every slice's `infra.api`; it names no slice |
| `deployable` | every slice | The composition root, renamed from `bootstrap`: the `@SpringBootApplication`, an aggregate `DeployableConfiguration` that imports each layer's `@Configuration`, loopback binding and credential checks, and `StartupRefusalAnalyzer`, which reports a startup refusal as its one line. The only module that knows every slice; it owns the ArchUnit gate |
| `charter-conformance` | the slices, fixtures | Runs the conformance corpus against the service |

Within a slice, the read and write use cases are packages inside its `application` ring, not separate modules: core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md)'s staged read-first/write-later strangler runs within each slice.

## The rule

```text
infra.api.rest / infra.api.graphql ──┐
                                     ├─> application (read / write) ──> domain (models + ports)
infra.storage (file / db / blob /    │      application ports (GuideFormat, GuideFetcher)
               resolver) ────────────┤        ^
infra.fetch (outbound HTTPS) ────────┴────────┴──────────────────────> domain repository ports
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
- **Application-ring ports.** A port that a use case needs and that is not about the domain's
  state lives in `application`, not in `domain`: `GuideFormat` (the guide's Markdown codec,
  implemented in `infra.storage` by `MarkdownGuideFormat`, beside the stored guide's model) and
  `GuideFetcher` (an outbound fetch, implemented in `infra.fetch` by `HttpsGuideFetcher`). They
  carry a wire format and a network call, which the pure domain does not know of; a domain
  repository port, by contrast, speaks only domain models. The domain ring stays free of both.
- **`infra.fetch`** is an outbound adapter like `infra.storage`: it implements an application-ring
  port and is composed by `deployable`. No other adapter names it, and it names no other adapter
  (the `FETCH_ADAPTER_STANDS_ALONE` rule); the entry point reaches it only through the application
  ring (`ENTRYPOINT_OVER_APPLICATION`).
- **`infra.archive`** is a ring of its own, beside `infra.fetch`. It reads a tar.gz or zip archive into a staging
  directory, within bounds (32 MiB of body and of expanded content, 10,000 entries, path rules), and implements the application-ring port
  `PackArchiveReader`. `deployable` composes it. No other adapter names it, and it names no other adapter
  (`ARCHIVE_ADAPTER_STANDS_ALONE`, `ARCHIVE_ADAPTER_NAMES_NO_OTHER_ADAPTER`). The application ring sees the port only.
- **One package starts a process.** The system `git` is a host binary the git source runs as a subprocess (no JGit, no
  work tree). Only `packs.infra.fetch.git` names `ProcessBuilder` or `Process`
  (`PROCESSES_START_ONLY_IN_THE_GIT_ADAPTER`, in `NetworkCodeBoundaryTest`). Every other class, in every ring, is
  refused if it does. The git requirement is in [Building the service](/guides/service-build/#registering-packs).

### A slice over another slice

A slice may build on another slice's use cases, never on its rings directly. Exactly one adapter package of the
upper slice, `<slice>.infra.<other>`, names the other slice, and only its `application` ring (with the `domain`
values that ring's answers expose); it never names the other slice's adapters. That adapter implements one
application-ring port of the upper slice, so the upper slice's own rings ask through the port and never see the other
slice. The `activations` slice is the first: `activations.infra.packs.PacksAvailability` implements
`activations.application.AvailablePacks` over `packs.application`
(`ACTIVATIONS_NAME_PACKS_ONLY_FROM_ADAPTER`, `ACTIVATIONS_ADAPTER_NAMES_PACKS_APPLICATION`). This extends
[ADR-0006](/adr/0006-onion-vertical-slices-archunit/)'s slices; the slice and its port are the planning decision of
2026-11-05 (mission `activation-register-and-closure-01M6NXBD`). The per-slice rules (`SLICE_RINGS`) hold every slice
listed in `SLICES` to the same rings as `packs`.

### Decision (2026-11-05, activation register review): the shared web ring

The HTTP transport every inbound adapter shares (problem bodies, the refusal mapping, the JSON body guard) lives in its
own module, `web-transport`, package `ai.speckitty.charter.web`, not in the `packs` adapter. Before, the activation
adapter could not name it there, so the activation body advice and problem mapping sat in `deployable`, and
`TRANSPORT_STAYS_IN_ADAPTER` had to exempt the composition root. The decision, recorded here rather than in a new ADR:

- `ai.speckitty.charter.web` is an **adapter ring shared by the slices' `infra.api`**. Every slice's inbound adapter
  may depend on it; no `domain` or `application` ring may (`WEB_IS_AN_ADAPTER_RING`).
- The web ring **names no slice**: it depends on the kernel and the generated contract only (`WEB_NAMES_NO_SLICE`).
  A slice's own envelopes and refusals stay in its adapter (`packs.infra.api.ArtifactJson`,
  `activations.infra.api.ActivationBodyAdvice` and `ActivationProblems`).
- **No `domain` ring depends on `kernel.transport`**, the kernel's `kernel.domain` included
  (`DOMAIN_NAMES_NO_TRANSPORT`): a precondition, an entity tag or the body limit is a question for the application
  ring or an adapter.
- `TRANSPORT_STAYS_IN_ADAPTER` has **dropped its `deployable` exemption**: the generated contract types are named by a
  slice's `infra.api` and by the web ring only, and the composition root names none.

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
