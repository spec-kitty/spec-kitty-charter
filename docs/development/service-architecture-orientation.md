---
title: Service architecture orientation
description: A contributor's first read before touching service/ — the three patterns, the module map, the inward-only rule, how ArchUnit gates it, and the gotchas that bite.
doc_status: active
updated: 2026-10-22T00:00:00Z
type: Guide
kind: Explanation
related:
  - architecture/architectural-patterns-applied
  - architecture/hexagonal-service
  - development/how-to-ddd
  - development/code-style-and-conventions
  - adr/0006-onion-vertical-slices-archunit
audience:
  - profile: charter-service-contributor
    guidance_text: Read this first, before you add or move any code under service/, to build the mental model the rest of the docs assume.
  - profile: core-maintainer
    guidance_text: Read this for a one-sitting orientation to the service's shape and the invariants its build gate holds.
  - profile: evaluator
    guidance_text: Read this to see the architecture, how it is enforced, and the traps it is designed to prevent.
---

# Service architecture orientation

Read this before you touch anything under `service/`. It is the mental model the rest of the
service docs assume: the shape in one sitting, then the traps that cost the most when you miss
them. It synthesises [Architectural patterns applied](/architecture/architectural-patterns-applied/),
[Hexagonal service](/architecture/hexagonal-service/), [How to DDD here](/development/how-to-ddd/)
and [Code style and conventions](/development/code-style-and-conventions/) — go to those for the
depth; start here for the map.

The running example throughout is the `packs` slice, the first and so far only slice built.

## Three patterns, one gate

The `service/` codebase composes three architectural patterns, each doing a distinct job.

- **Hexagonal (ports and adapters).** The domain declares the ports it needs. Adapters implement
  them (the driven, storage side) or call inward (the driving, API side). The core never names an
  adapter.
- **Onion layering.** Dependencies point inward only. The compiler cannot enforce this inside a
  module, so ArchUnit does.
- **Screaming architecture.** The top-level modules are named for the subject (`packs`, `kernel`),
  not the technology. The earlier role-named modules (`charter-domain`, `adapter-in-rest`,
  `adapter-out-yaml`) are superseded.

## The module map

The top-level cut is by **vertical concern slice** — one Gradle module per slice, with the onion
layers as packages *within* the module. Only `packs` is built today; `missiontypes`, `profiles`
and `artifacts` are designed, not built.

| Module | Role | Holds |
|---|---|---|
| `kernel` | Shared kernel, pure | `kernel.domain` (the cross-slice Pack and DRG vocabulary) and `kernel.validation` (a `java`- and `jspecify`-only guard library) |
| `packs` | The first concern slice | The layer packages `domain`, `application`, `infra.storage` and `infra.api` |
| `deployable` | Composition root | The `@SpringBootApplication` and an aggregate `DeployableConfiguration`; renamed from `bootstrap`; the only module that knows every slice |
| `charter-api` | Generated contract | OpenAPI server interfaces and transport DTOs, referenced only from each slice's `infra.api` |

The read/write (CQRS) distinction is **not** a top-level boundary. It survives as a sub-split
inside each slice's `application` ring, where core's read-first/write-later strangler runs.

## The one rule: dependencies point inward, and application is an *edge*

Arrows point inward only. The `infra.api` and `infra.storage` rings depend on `application`, which
depends on `domain`, which depends on nothing outward except `kernel.domain`. Repository and
gateway **ports live in the domain**; their implementations live in `infra.storage`.

The idea that reorganises everything is the 2026-10-21 revision of
[ADR-0006](/adr/0006-onion-vertical-slices-archunit/): **only `domain` and `kernel` are pure.**
`application`, `infra.storage` and `infra.api` are peer **edge** rings around that pure core — not
a layered stack with `application` as an inner sanctum. Application use cases are Spring `@Service`
beans and may use the framework. The gate rule renamed from `APPLICATION_IS_PLAIN_JAVA` to
`APPLICATION_IS_EDGE`: `application` may depend on `domain`, `kernel` and the framework, but never
on another slice's `infra`, another slice's `domain`, or the `deployable` composition root.

Wiring lives in the composition root. Each edge owns a `@Configuration` in a `config` sub-package,
and `deployable`'s `DeployableConfiguration` **component-scans** those sub-packages (it does not
hand-import them), so a test can load one slice's wiring without the whole application.

## How it is gated

`HexagonalArchitectureTest`, in `deployable`, imports the whole service and pins every direction
with named ArchUnit rules: `DOMAIN_IS_PURE`, `NO_INFRA_ANNOTATIONS_IN_DOMAIN`,
`ONION_DEPENDS_INWARD`, `ENTRYPOINT_OVER_APPLICATION`, `STORAGE_RETURNS_DOMAIN`,
`PORTS_LIVE_IN_DOMAIN`, `SLICES_DO_NOT_CROSS`, `TRANSPORT_STAYS_IN_ADAPTER`, `APPLICATION_IS_EDGE`
and the kernel and validation-library purity rules.

**Every rule has a known-bad control** — a small violating fixture the test asserts the rule
*fails* on. A rule that has never been seen to fail is not proven to guard anything. The `kernel`
is additionally held pure at build time by the `charter.domain-conventions` plugin, which rejects
any external dependency at configuration time; each slice's `domain` is held pure by ArchUnit
alone.

## The gotchas that bite

These are the traps that cost the most when missed. Most have a corresponding rule or doc section;
the point here is to name them before you hit them.

1. **`application` is an edge, but the purity line is still absolute.** Because you *can* put Spring
   in `application`, it feels as if anything goes there — but the `domain` and `kernel` allowlist is
   framework-free and narrow. Put a logger, a Jackson type or any framework class into `domain` or
   `kernel` and the build goes red. Logging and diagnostics belong at the inbound edge
   (`infra.api`), not in the application ring.
2. **Identity and fidelity are different questions — never answer one with the other.**
   `Tactic.equals` and `hashCode` compare the identifier only. If you assert round-trip fidelity
   ("did the file survive a load and a save unchanged?") against the entity's identity-equality,
   the test passes whenever the ids match, even when a step was silently dropped — it cannot fail,
   so it proves nothing. Assert fidelity on the DSO record (field-wise value equality) in the
   `infra.storage` tests. See
   [How to DDD here](/development/how-to-ddd/#identity-and-fidelity-are-different-concerns).
3. **The mirror trap: coverage disguised as a duplicate.** Because `Tactic` uses identity equality,
   the per-field assertions in the round-trip and wiring tests are *the real fidelity check* — they
   look like redundant boilerplate but are not. A test-economy pass that trims them as duplicates
   deletes the only assertions with teeth.
4. **The gate has a single-slice blind spot.** `SLICES` is a hand-kept list — `List.of("packs")`
   today — and some rules are pinned to `packs`. Add a second slice module and forget to register
   it, and you get a green build with an ungated slice: its purity, edge confinement and isolation
   go unchecked, and nothing says so. The `SLICE_COVERAGE` rule (`EVERY_SLICE_IS_REGISTERED`) now
   closes this in one direction — it discovers every slice that owns a `domain` ring and fails the
   build when one is absent from `SLICES`. Parameterizing the `packs`-pinned `APPLICATION_IS_EDGE`
   and `PACKS_DOMAIN_IS_PURE` over `SLICES`, so registering a slice also purity- and edge-gates it,
   is the remaining companion follow-up.
5. **`kernel` is double-gated; slice domains are not.** `kernel` is held pure by both the build
   plugin and ArchUnit; `packs.domain` is held pure by ArchUnit alone. Do not describe the slice
   domains as double-gated.
6. **Outcomes are values; exceptions are for the edge.** A broken file in a pack is a `PackFinding`
   the inspection query reports, not an exception that aborts the load — one bad tactic must not
   hide the rest of the pack. Convert a value to an exception or a Problem response only in
   `infra.api`. A not-found that hides an underlying failure (an unreadable pack answered as
   `pack_not_found`) keeps the original refusal as its cause, so the inbound edge can still log a
   diagnostic trail even though the 404 body names nothing of it.
7. **No public wide constructor, and no self-persisting aggregate.** Entities are `final` classes
   with a package-private all-args constructor and a public fluent builder, and identity equality.
   A domain object never holds a repository and never saves itself — constructing one has no side
   effect. Saving is the storage ring's job.
8. **`@NullMarked` everywhere; `Optional` is return-only.** Every package is `@NullMarked`, so
   non-null is the default; use `@Nullable` or return `Optional` for genuine absence, never
   `Optional` for fields or parameters. And `spotlessCheck` (palantir-java-format) is part of
   `verifyAll`, so unformatted code fails the build — run `./gradlew spotlessApply` before you push.

## Where to go next

- [Architectural patterns applied](/architecture/architectural-patterns-applied/) — the realised
  layout, the concern-to-pattern-role table, the C4 and class diagrams, and a walk from a request
  through every ring.
- [Hexagonal service](/architecture/hexagonal-service/) — the Gradle modules and the inward
  dependency rule in full.
- [How to DDD here](/development/how-to-ddd/) — classifying an entity, value object, aggregate
  root, port or adapter, and the cautionary examples not to copy.
- [Code style and conventions](/development/code-style-and-conventions/) — how each type is
  modelled, how invariants fail fast, and how tests are named.
- [ADR-0006](/adr/0006-onion-vertical-slices-archunit/) — the decision, its revisions, and the
  named ArchUnit rule set.
