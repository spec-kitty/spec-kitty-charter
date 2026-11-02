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

- The natural seam is a domain service in `service/packs/.../domain` used by `OfferingReadService`, not a second read
  path: the resolver consumes configured packs plus descriptors, and returns the ordered closure the offering serves.
- There is an existing `CompositeComponentResolver` (application) that composes components for artifact reads. It
  resolves composition of one artifact's parts, not pack-level composition; the two are different graphs and must not
  be merged. The new resolver is a sibling, not a replacement.
- Refusals travel as `CharterRefusal` with a `RefusalCode` (kernel); the wire codes are authored in
  `contracts/charter/schemas/CharterRefusalCode.yaml`, whose status mapping and x-refusal table the new codes join.

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
