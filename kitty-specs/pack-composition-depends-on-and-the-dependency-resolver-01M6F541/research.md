# Research: Pack composition depends_on and the dependency resolver

**Date**: 2026-11-02 | **Mission**: pack-composition-depends-on-and-the-dependency-resolver-01M6F541

## How a descriptor is read today

- `service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackDescriptor.java` is the authored `pack.yaml`
  as facts: `packId`, `packVersion`, `parentPack`, `name`, `declaredLevel`, `requiresCore`, `paths`. The closed schema
  means a key the schema does not declare never reaches the record (ADR-0036 decision 3).
- `contracts/packs/schemas/pack-descriptor.schema.json` is the source of truth for the shape. It is closed
  (`additionalProperties: false`), and `pack_level` is optional today (an absent level is the `pack_level_missing`
  diagnostic, and the configured level is used).
- The schemas are copied onto the test classpath as `packs/schemas/<kind>.schema.json`
  (`service/packs/build.gradle.kts`, the copy task at the comment "classpath:packs/schemas/<kind>.schema.json").
  `PackSchemasTest` enumerates `contracts/packs/schemas` so a schema file that is not exercised fails.
- Validation is `com.networknt` JSON Schema (ADR-0017), not generated DTOs.

## How the offering is read today

- `OfferingReadService` (application) builds the offering from `PackCatalogRepository` and
  `PackDescriptorRepository`, inside one `ConsistentRead` that digests the inputs twice and refuses `stale_input` on a
  difference. The order is `PackLevel` declaration order then pack id by code point.
- `ConfiguredPack(PackIdentifier id, PackLevel level)` is the configured identity; storage decides the level from
  `pack.yaml` (ADR-0038 decision 3), and nothing above storage recomputes it.
- `PackLevel` (kernel) declares the tiers in order: built-in, org, project. The level rule this mission enforces is a
  direction over that order: a pack may depend on its own level or a lower one.

## Where composition belongs

- The natural seam is an **application** service in `service/packs/.../application`, between
  `PackCatalogRepository.configured()` and `OfferingReadService.ORDER`: it consumes configured packs plus descriptors,
  and returns the ordered closure the offering serves. `OfferingReadService` and the pack reads consume it, and
  `ConsistentRead` already digests every configured pack, so resolution inherits the freshness check.
- There is an existing `CompositeComponentResolver` (application) that composes components for artifact reads, over
  the `ComponentResolver` port. It resolves artifact URNs inside one pack and has no pack graph; the two are different
  graphs and must not be merged. The new resolver is a sibling, not a replacement.
- Refusals travel as `CharterRefusal` with a `RefusalCode` (kernel); the wire codes are authored in
  `contracts/charter/schemas/CharterRefusalCode.yaml`, whose status mapping and x-refusal table the new codes join.

## What the codebase map settles

- `PackDescriptor` has exactly two production constructions (`infra/storage/PackDescriptorReader` and
  `infra/storage/PackDescriptorDso`) and **35 test constructions**. Adding a descriptor component is therefore a wide
  edit; a separate `PackDependency` record plus a dependency list on the descriptor is still the right shape, but the
  work package must budget for the test sites.
- Runtime descriptor validation happens per read (`PackDescriptorReader` -> `PackSchemas.bundled().validate`), and
  `:packs:processResources` copies the schema onto the classpath; `PackSchemasTest` enumerates the schema files, so a
  schema edit forces a rerun.
- Adding a refusal code is a five-part change: the YAML enum and `x-refusal` row, the `RefusalCode` constant,
  `RefusalHandler.statusOf` (an exhaustive switch that fails compile until branched), the `errors.feature` pin, and the
  parity tests (`WireTokenParityTest`, `ReservedRefusalCodesTest`, `RefusalHandlerTest`).
- An additive **response** property is graded `ERR` by the compatibility check (`contracts/compat/severity-levels.txt`),
  so exposing the resolved dependencies on the offering needs `info.version` to move and a `### Breaking` fragment. That
  is a contract decision for FR-020, not a free addition.
- `PackPolicy` is where descriptor-level rules and their finding texts live; invalid ranges may belong there as
  findings rather than as resolver refusals.
- `kernel.domain.Pep440` only **validates** a specifier set (`isValidSpecifierSet`); it does not resolve, compare or
  satisfy one. Satisfying `range` against `pack_version` (and proving two ranges overlap) is therefore its own piece
  of work inside the resolver package: a PEP 440 version comparator and a specifier-set satisfaction check, with the
  pre/post/dev/epoch cases pinned by tests. The resolver must not reach for a new dependency (charter: no unverified
  dependency enters the build).
- BDD scenarios under `contracts/charter/scenarios/*.feature` are picked up automatically by
  `CharterContractSuite`; `HexagonalArchitectureTest` enforces the layer and `@NullMarked` rules a new class must
  satisfy, and `import` of generated DTOs outside `infra.api` is refused.

## Touch map

| Path | Does | This mission |
|---|---|---|
| `contracts/packs/schemas/pack-descriptor.schema.json` | closed descriptor schema | add the `depends_on` item |
| `service/kernel/.../domain/PackDescriptor.java` | descriptor facts | add the dependency list |
| `service/kernel/.../domain/PackDependency.java` | new value object | add |
| `service/packs/.../storage/PackDescriptorReader.java` | YAML to descriptor | map `depends_on` |
| `service/packs/.../storage/PackDescriptorDso.java` | persistence mirror | add the field and both directions |
| `service/packs/.../storage/YamlSerializationStrategy.java` | writes `pack.yaml` key order | emit `depends_on` |
| `service/packs/.../application/PackCompositionResolver.java` | new resolver | add |
| `service/packs/.../application/OfferingReadService.java` | orders and lists packs | call the resolver |
| `service/kernel/.../domain/RefusalCode.java` | refusal constants | add the resolver codes |
| `service/packs/.../infra/api/RefusalHandler.java` | HTTP mapping | branch the new codes |
| `contracts/charter/schemas/CharterRefusalCode.yaml` | authored codes | enum and x-refusal rows |
| `contracts/charter/scenarios/errors.feature` | refusal pin | new rows |
| `contracts/charter/changes/*.md` | changelog fragment | one new file |
| `docs/architecture/logical-model.md`, `docs/architecture/descriptors.md`, `docs/glossary/*`, `docs/plans/pack-ecosystem-work-breakdown.md` | the model as documented | FR-021 to FR-023 |

## Reference grammar and the ruling

ADR-0053 open question 3 was ruled on 2026-11-02 while this mission was being planned: multi-word URN kind tokens move
to kebab case **directly**, with no split-migration reader, because the service has no consumers yet. This mission
still writes no URN-parsing code; it only adds the `<pack-name>::` prefix to references whose URN part it treats as
opaque. That keeps C-005 satisfiable and leaves the URN token change to the split.

## Build and test facts

- Java 25 is installed at `/usr/lib/jvm/temurin-25`; `JAVA_HOME` must point at it. `java` is not on `PATH`.
- The build reads a sibling `spec-kitty-open-packs` checkout at `openPacksRef` (`gradle.properties`). The local
  checkout is at a different commit, so tasks that read it refuse; targeted module tests that do not read it still
  compile and run.
- BDD scenarios live in `contracts/charter/scenarios/*.feature` (Gherkin, one file per area) plus
  `contracts/charter/scenarios/journeys/`. The contract suite runs them; a new resolver scenario belongs in
  `offering.feature` or its own file.
- `service/packs/src/test` holds the unit tests; `ArchUnit` tests enforce layer and dependency rules, so the resolver
  stays in `domain` and depends only on kernel types.

## What this means for the work packages

1. The descriptor change and the kernel value objects can land independently of the resolver and must come first,
   because the resolver reads them.
2. The resolver, its refusals and the reference grammar are one package: the refusals are the resolver's contract.
3. The compatibility shim is a property of the offering read, tested by pinning today's single-tree answers.
4. The contract change set is one package and must not interleave with another contract PR (C-006).
