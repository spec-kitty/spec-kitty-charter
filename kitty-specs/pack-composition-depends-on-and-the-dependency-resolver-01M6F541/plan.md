# Implementation Plan: Pack composition: depends_on and the dependency resolver

**Branch**: `issue-145-pack-composition` | **Date**: 2026-11-02 | **Spec**: [spec.md](./spec.md)
**Input**: Mission specification from `kitty-specs/pack-composition-depends-on-and-the-dependency-resolver-01M6F541/spec.md`

## Summary

Give a pack descriptor a `depends_on` list and resolve it. The descriptor gains a closed dependency item (`pack_id`,
`range` in PEP 440); the service gains a resolver that orders the closure, loads a shared dependency once, enforces the
level rule and refuses a cycle, an unknown id, an out-of-range version, an unresolvable parent, a duplicate pack name
and a sibling refinement that no distribution pack settles. Cross-pack references become pack-qualified
(`<pack-name>::<urn>`), with bare in-pack references unchanged and a bare reference that only resolves in another pack
refused. A compatibility shim keeps today's single `built-in` tree loading. The contract gains named refusal codes and
the dependency facts on the pack reads.

## Technical Context

**Language/Version**: Java 25 (build enforces it; CI never builds with `-Pcharter.javaVersion=21`)
**Primary Dependencies**: Spring Boot, Jackson, `com.networknt` JSON Schema validator, `packaging`-equivalent PEP 440
handling (no new dependency: the range grammar is parsed by the existing specifier-set support the descriptor's
`requires_core` already uses)
**Storage**: the filesystem (configured pack roots); no database
**Testing**: JUnit 5 + AssertJ unit tests in `service/packs/src/test`, BDD scenarios under
`contracts/charter/scenarios/` driving the contract suite
**Target Platform**: Linux server, Java 25
**Project Type**: multi-module Gradle service (`service/kernel`, `service/packs`, `service/bootstrap`, `service/deployable`)
**Performance Goals**: a 50-pack compose with 200 edges resolves in under 250 ms at p95 (NFR-003)
**Constraints**: the single `built-in` tree must load unchanged; no URN kind-token work (the ruling moves tokens to
kebab, and this mission does not parse them); contract changes follow ADR-0022
**Scale/Scope**: 20 FR, 5 NFR, 6 constraints; 5 implementation concerns; one contract change set

## Charter Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **No unverified dependency enters the build**: the resolver is written against the JDK and the already-verified
  Spring/Jackson/JSON-Schema set; no new dependency is added for PEP 440 or graph work. PASS.
- **The OpenAPI contract keeps its rules**: the new refusal codes and the additive pack fields follow ADR-0022; any
  field a consumer cannot ignore carries a `### Breaking` entry. PASS, with the breaking set recorded in
  `contracts/` under this mission.
- **Open-packs YAML round-trips without loss of meaning**: the descriptor schema stays closed and additive; the
  `depends_on` item is authored, never derived. PASS.
- **Divergence from the charter's own model**: the resolver implements ADR-0048 decisions 7 to 10 as amended and
  ADR-0053 decisions 4, 6, 7, 8 and 9, no more. PASS.
- **Practices in force**: scenarios first (BDD), tests held to production quality, DDD terms, C4 not needed for a
  library-level change. PASS.

## Project Structure

### Documentation (this mission)

```
kitty-specs/pack-composition-depends-on-and-the-dependency-resolver-01M6F541/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output: the contract change set
├── checklists/          # requirements checklist
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```
contracts/
├── packs/schemas/pack-descriptor.schema.json      # depends_on, conditional pack_level
└── charter/
    ├── schemas/CharterRefusalCode.yaml            # new resolver codes
    ├── schemas/PackPin.yaml                       # the resolved dependency facts
    ├── schemas/OfferingPack.yaml                  # additive dependency view
    └── scenarios/                                 # BDD scenarios for the resolver

service/
├── kernel/src/main/java/.../kernel/domain/        # PackDescriptor, PackLevel, RefusalCode
└── packs/
    ├── src/main/java/.../packs/domain/            # PackDependency, dependency value objects
    ├── src/main/java/.../packs/application/       # PackCompositionResolver, offering read
    └── src/test/java/.../packs/                   # unit tests, one per refusal
```

**Structure Decision**: the resolver is an application service in `service/packs/.../application`, beside the offering
read it feeds (ArchUnit allows application to see domain and kernel). The descriptor's new value object lives in
`service/kernel/.../domain` next to `PackDescriptor`, because the descriptor is a kernel type. Contract changes stay in
`contracts/`, which generates the DTOs; no hand-written DTO is added.

## Complexity Tracking

No charter violations to justify.

## Implementation Concern Map

- **IC-01 Descriptor shape.** The closed `depends_on` item and the conditional `pack_level` requirement, plus the
  kernel value object. Informs the schema and kernel work packages.
- **IC-02 Closure resolution.** Topological order, dedupe by `pack_id`, range overlap, the level rule, one closure at
  one level. Informs the resolver work package.
- **IC-03 Refusals.** Cycle, unknown dependency, out-of-range version, unresolvable parent, level violation,
  duplicate pack name, sibling refinement without `overrides`. Informs the resolver and contract work packages.
- **IC-04 Reference grammar.** `<pack-name>::<urn>` parsing and resolution, bare in-pack references, the refusal of a
  bare reference that only resolves elsewhere, and the closure check naming the missing pack.
- **IC-05 Compatibility and evidence.** The single-tree shim, the offering read's unchanged answers, and the contract
  change set with its breaking entries.
- **IC-06 Documentation.** The logical model and architecture pages describe composition; the glossary and contextive
  definitions carry Compose, Closure, Dependency edge and Qualified reference; the work breakdown marks items 2 and 3
  done; a changelog entry records the change. Every page that describes the pack model either states the new shape or
  links to the page that does.

## Phase 0: Research

See [research.md](./research.md). It records how a descriptor is read today, where the resolver sits, how refusals
reach the contract, and which tests already pin the single-tree behaviour.

## Phase 1: Design

See [data-model.md](./data-model.md) for the dependency and compose value objects, and
[contracts/](./contracts/) for the contract change set and the refusal codes.

## Phase 2: Tasks

`tasks.md` translates the five concerns into work packages; the dependency between the descriptor schema and the
resolver is the only ordering constraint, and the contract set lands as one package.
