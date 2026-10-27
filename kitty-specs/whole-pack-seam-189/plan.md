# Implementation Plan: Whole-pack load and write through the Pack aggregate

**Branch**: `feat/189-whole-pack-seam` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)
**Decision record**: [ADR-0036](../../docs/adr/0036-kind-keyed-pack-registry-and-whole-pack-seam.md) (Proposed)

## Summary

Replace the per-kind wiring of the packs slice with a kind-keyed aggregate and a storage-side kind
registry, then build the whole-pack load and write on top of it. All work stays in the `packs`
slice; the kernel, the contract and the HTTP layer are untouched.

## Technical Context

Java 25 (CI), builds on 21 with `-Pcharter.javaVersion=21`; Gradle; JUnit 5, AssertJ, ArchUnit;
snakeyaml-engine through `ParsedYaml` and `YamlDumpEmitter`; schemas from `contracts/packs/schemas`.

## Charter Check

- ADR-0006 slices: the domain map holds `CharterArtifact` and `ArtifactKind` (kernel) only; bindings,
  formats and layout live in `packs.infra.storage`.
- ADR-0026 D3: registration still loads, mutates (`Pack.with`), revalidates and commits the delta.
- Charter: architecture change → ADR-0036 Proposed, operator acceptance before merge.

## Design

### Domain (`packs.domain`)

- `Pack`: `EnumMap<ArtifactKind, List<CharterArtifact>>`, descriptor, `List<GraphDocument>`,
  violations. Invariants: belongs-to-pack and unique local id per kind (same messages as today),
  unique graph document file. API: `artifacts()`, `artifacts(kind)`, `artifacts(kind, type)`, the
  five typed views, `graphs()`, `with(artifact)`, `toBuilder()`. Builder: `artifact(..)`, typed
  delegates, `graph(..)`.
- `GraphDocument`: a sealed value with `Shard` (schema_version, generated_at, generated_by?,
  nodes with urn/kind/label?/provenance?/tags, edges) and `Fragment` (nodes with
  id/kind/title?/body_path?, edges); edges carry source/target/relation/when?/reason?/impacts?.
  Equality of the edge set is canonical (order and duplicates do not count) through
  `canonicalEdges()`.

### Storage (`packs.infra.storage`)

- `KindBinding<D, E>`: kind, entity type, DSO type, `idOf(D)`, `toEntity(pack, D)`, `toDso(E)`. One
  binding per kind (`TacticBinding`, …), taking over the mappers from `DomainRepositoryService`.
- `KindBindings`: the registry (ordered list), `of(kind)`, `kinds()`.
- `YamlFormat<D>`: schema kind, read from the parsed root, write. One per kind, moved out of
  `YamlSerializationStrategy`.
- `SerializationStrategy`: `read(binding, text, file)`, `write(binding, dso)`, descriptor read and
  write, graph document read and write.
- `PackFileSet`: kinds from `KindBindings` (replaces `LoadedKind`); digest unchanged in content.
- `FilesystemPackStore`: `PackRead(descriptor, Map<kind, List<located DSO>>, violations)`; generic
  `readAll`, `readOne`, `write`.
- `DomainRepositoryService`: per-kind ports delegate to generic helpers; `assemble` iterates the
  registry; `writeArtifact` resolves the binding.
- `WholePackStore`: `load(Path) → LoadedPack(Pack, PackLayout)`, `write(LoadedPack, Path target)`;
  staging directory beside the target, then rename; refuses an existing target and a pack with
  violations.
- `PackLayout`: artifact URN → pack-relative path; `pathOf(artifact)` falls back to
  `<plural>/<id>.<token>.yaml`.

## Testing

- Existing suites stay green (FR-007).
- New: `PackAggregateTest` cases for `with` and generic invariants; `KindBindingsTest` (every bound
  kind has a format and a schema); `GraphDocumentRoundTripTest`; `WholePackStoreTest` (refusals,
  atomicity, layout); `WholePackRoundTripTest` over the fixture packs and, in the
  `openPacksRoundTrip` task, the open-packs packs.
