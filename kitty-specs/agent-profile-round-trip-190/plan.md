# Implementation Plan: Agent profiles round-trip through the domain

**Branch**: `feat/190-agent-profile-round-trip` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)

## Summary

Add a `:profiles` module holding the `AgentProfile` entity and its value objects. Bind the kind in
the pack store like the five existing kinds (DSO, YAML format, binding), with two defaults on
`KindBinding` for the kinds whose schema name and id key differ from the token and `id`. Turn the
gate's `agent-profile` entry green.

## Decisions

1. **Slice (squad A1).** The entity lives in `profiles.domain`, as ADR-0035 decision 4 requires.
   Persistence stays in `packs.infra.storage`, which owns the pack format, the schemas and the
   fingerprint; it depends inward on `profiles.domain`. This is allowed by ADR-0006 (only a slice's
   *domain* may not cross) and keeps `profiles` free of `packs`. It reads ADR-0037 decision 6's
   "contribution seam wired by deployable" as "the pack store binds the kind, depending on the
   owning slice's domain"; ADR-0037 is amended to say so. A contribution seam stays possible if a
   kind ever needs its own persistence. No new ADR.
2. **Seam (squad A2, P1).** `KindBinding.schemaName()` and `KindBinding.idKey()`, defaulting to the
   token and `id`. No schema file is renamed: `contracts/packs` is consumed by core.
3. **Fidelity (squad R1 to R4).** The DSO mirrors the file: lists default to empty (absent equals
   empty for a declared field), every defaulted scalar is optional, integers and booleans are typed,
   and `excluding` is carried verbatim as a list or a map of lists.
4. **Edges (squad A7).** `relationships()` returns none; the graph shard is the source of profile
   edges.

## Structure

```text
service/profiles/                         new module, charter.domain-conventions
  src/main/java/ai/speckitty/charter/profiles/domain/
    AgentProfile, AgentProfileIdentifier, Specialization, Collaboration, ModeDefault,
    SpecializationContext, DirectiveReference, ArtifactReference, SelfReviewStep, Exclusion
service/packs/src/main/java/.../packs/infra/storage/
  AgentProfileDso, AgentProfileYaml, AgentProfileBinding     new
  KindBinding (+schemaName, +idKey), KindBindings, YamlFormats, PackFileSet,
  YamlSerializationStrategy, FilesystemPackStore, DomainRepositoryService (+save)
service/packs/src/test/...                 format, binding and load tests; gate adapter; baseline
service/deployable/src/test/...            HexagonalArchitectureTest; fixture move
settings.gradle.kts, service/charter-reports/build.gradle.kts, service/packs/build.gradle.kts
```

## Risks

- **#191 lands the same seam.** Whichever merges second rebases onto the other's `schemaName()` /
  `idKey()`; the defaults are identical by construction. Raised on #190.
- **Fingerprint.** Packs with agent files get a new `Charter-Source-Fingerprint` once.
- **Local Java 21.** The session has no Java 25; it builds with `-Pcharter.javaVersion=21`, CI on 25.
